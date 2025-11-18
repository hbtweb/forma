(ns forma.parsers.universal
  "Universal platform-agnostic parser for bidirectional compilation.

  This parser converts any platform format (HTML, JSX, JSON, Oxygen) back to
  Forma EDN structures using EDN-driven transformation rules from platform configs.

  Key Features:
  - Multi-method dispatch on :input-format
  - EDN-driven attribute mappings (bidirectional)
  - Pluggable tokenization strategies
  - Transform registry for case conversions
  - Metadata preservation for round-trip compilation

  Architecture:
  ```
  Platform String (HTML/JSX/etc)
      ↓
  1. Tokenize (format-specific: HTML/JSX/JSON/XML)
      ↓
  2. Build Tree (generic tree builder)
      ↓
  3. Normalize Attributes (EDN-driven reverse mappings)
      ↓
  4. Extract Metadata (optional, for sync mode)
      ↓
  Forma EDN
  ```

  Example Usage:
  ```clojure
  ;; Parse HTML with platform config
  (parse \"<div className=\\\"card\\\">Hello</div>\"
         {:input-format :jsx
          :platform-config (load-platform-config :react)})

  ;; Parse with metadata preservation (sync mode)
  (parse html-str
         {:input-format :html
          :platform-config (load-platform-config :html)
          :preserve-metadata? true})
  ```"
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [clojure.data :as data]
            [forma.output.transformer :as transformer]
            [forma.parsers.html :as html-parser]
            [forma.parsers.jsx :as jsx-parser]))

;; =============================================================================
;; Transform Registry (Bidirectional)
;; =============================================================================

(def transform-registry
  "Registry of bidirectional transformation functions.

  Each transform has:
  - :forward - Transform for compilation (EDN → Platform)
  - :reverse - Transform for parsing (Platform → EDN)
  - :description - Human-readable description"
  {:kebab<->camel {
                   :forward transformer/kebab->camel-case
                   :reverse transformer/camel->kebab-case
                   :description "kebab-case ↔ camelCase"}

   :kebab<->pascal {
                    :forward transformer/kebab->pascal-case
                    :reverse (fn [s]
                              (-> s
                                  str
                                  (str/replace #"([a-z])([A-Z])" "$1-$2")
                                  str/lower-case))
                    :description "kebab-case ↔ PascalCase"}

   :preserve-kebab {
                    :forward transformer/preserve-kebab
                    :reverse transformer/preserve-kebab
                    :description "Preserve kebab-case (no transformation)"}

   :identity {
              :forward identity
              :reverse identity
              :description "No transformation (pass-through)"}})

(defn get-transform
  "Get a transformation function from the registry.

  Args:
    transform-type - Keyword identifying the transform (:kebab<->camel, etc.)
    direction - :forward (EDN → Platform) or :reverse (Platform → EDN)

  Returns:
    Transformation function or identity if not found."
  [transform-type direction]
  (let [transform (get transform-registry transform-type)]
    (if transform
      (get transform direction identity)
      identity)))

;; =============================================================================
;; Attribute Mapping (Bidirectional)
;; =============================================================================

(defn- reverse-attribute-map
  "Reverse an attribute mapping for parsing.

  Forward mapping (compile): :url → :href
  Reverse mapping (parse):   :href → :url

  Args:
    attr-map - Map of {:forma-attr :platform-attr}

  Returns:
    Reversed map {:platform-attr :forma-attr}"
  [attr-map]
  (into {} (map (fn [[k v]] [v k]) attr-map)))

(defn- build-attribute-mappings
  "Build attribute mapping table from platform config :parser section.

  Platform config format:
  ```edn
  {:parser {
     :attribute-mappings {
       ;; Simple mapping: Platform → Forma
       :href :url
       :onclick :on-click

       ;; Pattern-based: :aria-* {:transform :identity}
       :aria-* {:transform :identity}}}}
  ```

  Args:
    platform-config - Platform configuration map

  Returns:
    Map of {platform-attr → forma-attr} plus pattern rules"
  [platform-config]
  (let [parser-config (get platform-config :parser {})
        attr-mappings (get parser-config :attribute-mappings {})]

    ;; Separate exact mappings from pattern rules
    (reduce-kv
     (fn [acc platform-attr forma-spec]
       (cond
         ;; Simple keyword mapping: :href :url
         (keyword? forma-spec)
         (assoc-in acc [:exact platform-attr] forma-spec)

         ;; Pattern-based transform: :aria-* {:transform :identity}
         (and (map? forma-spec) (:transform forma-spec))
         (let [is-pattern? (str/includes? (name platform-attr) "*")]
           (if is-pattern?
             (update acc :patterns conj [platform-attr (:transform forma-spec)])
             ;; Non-pattern transform (apply transform to attr name)
             (let [transform-type (:transform forma-spec)
                   transform-fn (get-transform transform-type :reverse)
                   forma-attr (keyword (transform-fn platform-attr))]
               (assoc-in acc [:exact platform-attr] forma-attr))))

         ;; Unknown format, skip
         :else
         acc))
     {:exact {} :patterns []}
     attr-mappings)))

(defn- apply-attribute-mapping
  "Apply attribute mapping to normalize platform attributes to Forma EDN.

  Args:
    attr-name - Platform attribute name (e.g., :onclick, :href)
    attr-value - Attribute value
    mappings - Attribute mappings {:exact {...} :patterns [...]}

  Returns:
    [forma-attr-name forma-attr-value] or [attr-name attr-value] if no rule"
  [attr-name attr-value mappings]
  (let [{:keys [exact patterns]} mappings]

    ;; First check exact mappings
    (if-let [forma-attr (get exact attr-name)]
      [forma-attr attr-value]

      ;; Then check pattern-based rules
      (let [matching-pattern (some (fn [[pattern transform-type]]
                                     (when (transformer/match-pattern? attr-name pattern)
                                       transform-type))
                                   patterns)]
        (if matching-pattern
          (let [transform-fn (get-transform matching-pattern :reverse)
                forma-attr (keyword (transform-fn attr-name))]
            [forma-attr attr-value])

          ;; No mapping found, keep as-is
          [attr-name attr-value])))))

(defn- normalize-attributes
  "Normalize platform-specific attributes to Forma EDN format.

  Args:
    attrs - Attribute map from platform (e.g., {:onclick \"handler\" :href \"/about\"})
    platform-config - Platform configuration with :parser section

  Returns:
    Normalized attribute map (e.g., {:on-click \"handler\" :url \"/about\"})"
  [attrs platform-config]
  (when attrs
    (let [mappings (build-attribute-mappings platform-config)]
      (reduce-kv
       (fn [result attr-name attr-value]
         (let [[new-name new-value] (apply-attribute-mapping attr-name attr-value mappings)]
           (assoc result new-name new-value)))
       {}
       attrs))))

;; =============================================================================
;; Generic Tokenization (Reuse Existing Parsers)
;; =============================================================================

(defmulti tokenize
  "Tokenize platform-specific format to generic token stream.

  Dispatch on :input-format (:html, :jsx, :json, :xml)

  Args:
    input-str - Platform-specific string (HTML, JSX, JSON, etc.)
    opts - Options map with :input-format

  Returns:
    Vector of tokens [{:type :open-tag :name \"div\" :attrs {...}} ...]"
  (fn [_input-str opts] (:input-format opts)))

;; Import existing HTML tokenizer
(defmethod tokenize :html
  [html-str _opts]
  (html-parser/parse html-str))

;; Import existing JSX tokenizer
(defmethod tokenize :jsx
  [jsx-str _opts]
  (jsx-parser/parse jsx-str))

;; JSON tokenizer (for Oxygen, WordPress, etc.)
(defmethod tokenize :json
  [json-str _opts]
  ;; We'll implement this when needed
  (throw (ex-info "JSON parsing not yet implemented"
                  {:format :json})))

;; Default: treat as HTML
(defmethod tokenize :default
  [input-str opts]
  (tokenize input-str (assoc opts :input-format :html)))

;; =============================================================================
;; Tree Normalization
;; =============================================================================

(defn- normalize-element
  "Normalize a single element (recursively) to Forma EDN format.

  Args:
    element - Hiccup-style element [:tag {:attrs} ...children]
    platform-config - Platform configuration for attribute normalization

  Returns:
    Normalized element with Forma EDN attributes"
  [element platform-config]
  (cond
    ;; String nodes (text content)
    (string? element)
    element

    ;; Vector nodes (elements)
    (vector? element)
    (let [[tag props & children] element
          has-props? (map? props)
          actual-props (when has-props? props)
          actual-children (if has-props?
                           children
                           (if (nil? props) [] (cons props children)))]

      ;; Normalize attributes
      (let [normalized-props (normalize-attributes actual-props platform-config)
            normalized-children (filter some? (map #(normalize-element % platform-config) actual-children))]

        ;; Rebuild element
        (cond
          ;; Has props and children
          (and (seq normalized-props) (seq normalized-children))
          (into [tag normalized-props] normalized-children)

          ;; Has only props
          (seq normalized-props)
          [tag normalized-props]

          ;; Has only children
          (seq normalized-children)
          (into [tag] normalized-children)

          ;; Empty element
          :else
          [tag])))

    ;; Other types (numbers, keywords, etc.)
    :else
    element))

;; =============================================================================
;; Metadata Extraction (for Sync Mode)
;; =============================================================================

(defn- extract-forma-metadata
  "Extract Forma metadata from data attributes (sync mode).

  In sync mode, HTML includes metadata like:
  ```html
  <div data-forma-provenance='{...}'
       data-forma-type='button'
       data-forma-variant='primary'>
  ```

  Args:
    attrs - Attribute map

  Returns:
    [cleaned-attrs metadata-map]"
  [attrs]
  (let [metadata-attrs (filter (fn [[k _v]]
                                  (str/starts-with? (name k) "data-forma-"))
                               attrs)
        clean-attrs (apply dissoc attrs (map first metadata-attrs))
        metadata (reduce (fn [m [k v]]
                          (let [meta-key (keyword (str/replace (name k) #"^data-forma-" ""))
                                ;; Keywordize string values if they look like keywords
                                meta-value (if (and (string? v)
                                                   (not (str/includes? v " "))
                                                   (not (str/includes? v "{")))
                                            (keyword v)
                                            v)]
                            (assoc m meta-key meta-value)))
                        {}
                        metadata-attrs)]
    [clean-attrs metadata]))

(defn- parse-with-metadata
  "Parse element preserving Forma metadata (sync mode).

  Args:
    element - Parsed element from tokenizer
    platform-config - Platform configuration
    opts - Parse options {:preserve-metadata? true}

  Returns:
    Element with metadata attached as :_forma-metadata"
  [element platform-config opts]
  (if-not (:preserve-metadata? opts)
    element

    (cond
      (string? element)
      element

      (vector? element)
      (let [[tag props & children] element
            has-props? (map? props)
            actual-props (when has-props? props)
            actual-children (if has-props? children (cons props children))]

        (if actual-props
          (let [[clean-attrs metadata] (extract-forma-metadata actual-props)
                normalized-attrs (normalize-attributes clean-attrs platform-config)
                final-attrs (if (seq metadata)
                             (assoc normalized-attrs :_forma-metadata metadata)
                             normalized-attrs)
                normalized-children (map #(parse-with-metadata % platform-config opts) actual-children)]

            (into [tag final-attrs] normalized-children))

          ;; No props, just normalize children
          (let [normalized-children (map #(parse-with-metadata % platform-config opts) actual-children)]
            (into [tag] normalized-children))))

      :else
      element)))

;; =============================================================================
;; Public API
;; =============================================================================

(defn parse
  "Universal parser for platform-specific formats to Forma EDN.

  Args:
    input - Platform-specific string (HTML, JSX, JSON, etc.)
    opts - Options map:
           :input-format - Platform format (:html, :jsx, :json, :xml)
           :platform-config - Platform configuration (required for normalization)
           :preserve-metadata? - Extract and preserve Forma metadata (default: false)

  Returns:
    Forma EDN (Hiccup-style vector)

  Example:
  ```clojure
  (parse \"<div className=\\\"card\\\">Hello</div>\"
         {:input-format :jsx
          :platform-config (load-platform-config :react)})
  ;; => [:div {:class \"card\"} \"Hello\"]

  (parse html-with-metadata
         {:input-format :html
          :platform-config (load-platform-config :html)
          :preserve-metadata? true})
  ;; => [:div {:class \"card\" :_forma-metadata {...}} \"Hello\"]
  ```"
  [input opts]
  (let [platform-config (:platform-config opts)
        _ (when-not platform-config
            (throw (ex-info "Platform config required for parsing"
                           {:opts opts})))

        ;; Tokenize using platform-specific tokenizer
        parsed-tree (tokenize input opts)]

    ;; Normalize and optionally preserve metadata
    (if (:preserve-metadata? opts)
      (parse-with-metadata parsed-tree platform-config opts)
      (normalize-element parsed-tree platform-config))))

(defn parse-fragment
  "Parse multiple root elements (fragment).

  Args:
    input - Platform-specific string
    opts - Options map (same as parse)

  Returns:
    Vector of normalized elements (not wrapped in container)"
  [input opts]
  (let [result (parse input opts)]
    (if (vector? result)
      result
      [result])))

;; =============================================================================
;; Round-Trip Validation
;; =============================================================================

(defn validate-round-trip
  "Validate that parse(compile(edn)) == edn (round-trip property).

  Args:
    edn - Original Forma EDN
    compiled - Compiled platform output (HTML/JSX string)
    opts - Parse options

  Returns:
    {:valid? true/false
     :original edn
     :round-trip parsed-edn
     :differences [...]}  (if not valid)"
  [edn compiled opts]
  (let [parsed (parse compiled opts)
        valid? (= edn parsed)
        differences (when-not valid?
                     (data/diff edn parsed))]
    {:valid? valid?
     :original edn
     :round-trip parsed
     :differences differences}))

(comment
  ;; Example usage

  ;; Load platform config
  (require '[forma.compiler :as compiler])
  (def react-config (compiler/load-platform-config :react))
  (def html-config (compiler/load-platform-config :html))

  ;; Parse JSX
  (parse "<div className=\"card\" onClick={handleClick}>Hello</div>"
         {:input-format :jsx
          :platform-config react-config})
  ;; => [:div {:class "card" :on-click "handleClick"} "Hello"]

  ;; Parse HTML
  (parse "<button class=\"btn btn-primary\">Click Me</button>"
         {:input-format :html
          :platform-config html-config})
  ;; => [:button {:class "btn btn-primary"} "Click Me"]

  ;; Parse with metadata (sync mode)
  (parse "<button class=\"btn\" data-forma-type=\"button\" data-forma-variant=\"primary\">Click</button>"
         {:input-format :html
          :platform-config html-config
          :preserve-metadata? true})
  ;; => [:button {:class "btn" :_forma-metadata {:type "button" :variant "primary"}} "Click"]

  ;; Round-trip validation
  (let [edn [:div {:class "card"} "Hello"]
        compiled (compile edn {:platform-stack [:html]})
        result (validate-round-trip edn compiled {:input-format :html
                                                   :platform-config html-config})]
    (:valid? result))
  ;; => true
  )
