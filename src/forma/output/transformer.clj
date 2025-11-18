(ns forma.output.transformer
  "Generic EDN transformation engine driven by EDN rules.

  This namespace implements a pure data-driven transformation system that converts
  Forma EDN structures to any target platform format without writing platform-specific
  Clojure code. All transformation rules are defined in EDN configuration files.

  Key principles:
  - Zero platform-specific Clojure code
  - Convention over configuration
  - Composable transformation rules
  - Bidirectional transformations (EDN → Platform → EDN)

  Example transformation rules (from react.edn):
  {:attribute-transforms {
     :class {:target :className}
     :on-* {:transform :kebab->camelCase}
     :aria-* {:transform :preserve-kebab}
   }
   :style-transform {
     :from :css-string
     :to :jsx-style-object
     :property-transform :kebab->camelCase
   }}"
  (:require [clojure.string :as str]
            [clojure.walk :as walk]))

;; =============================================================================
;; String Transformation Utilities
;; =============================================================================

(defn kebab->camel-case
  "Convert kebab-case to camelCase.

  Examples:
    on-click → onClick
    data-id → dataId
    background-color → backgroundColor"
  [s]
  (let [parts (str/split (name s) #"-")]
    (str (first parts)
         (str/join "" (map str/capitalize (rest parts))))))

(defn kebab->pascal-case
  "Convert kebab-case to PascalCase.

  Examples:
    my-component → MyComponent
    user-profile → UserProfile"
  [s]
  (str/join "" (map str/capitalize (str/split (name s) #"-"))))

(defn camel->kebab-case
  "Convert camelCase to kebab-case.

  Examples:
    onClick → on-click
    backgroundColor → background-color"
  [s]
  (-> s
      str
      (str/replace #"([a-z])([A-Z])" "$1-$2")
      str/lower-case))

(defn preserve-kebab
  "Preserve kebab-case as-is."
  [s]
  (name s))

;; =============================================================================
;; Attribute Transformation
;; =============================================================================

(defn match-pattern?
  "Check if attribute name matches a pattern.

  Patterns:
    :on-* - Matches on-click, on-change, etc.
    :aria-* - Matches aria-label, aria-hidden, etc.
    :data-* - Matches data-id, data-value, etc.
    :* - Matches anything (wildcard)"
  [attr-name pattern]
  (let [attr-str (name attr-name)
        pattern-str (name pattern)]
    (cond
      (= pattern-str "*")
      true

      (str/ends-with? pattern-str "*")
      (let [prefix (str/replace pattern-str #"\*$" "")]
        (str/starts-with? attr-str prefix))

      :else
      (= attr-str pattern-str))))

(defn find-matching-rule
  "Find the first transformation rule that matches the attribute.

  Rules are checked in order, first match wins."
  [attr-name transform-rules]
  (some (fn [[pattern rule]]
          (when (match-pattern? attr-name pattern)
            rule))
        transform-rules))

(defn apply-transform
  "Apply a transformation function to a value.

  Transform types:
    :kebab->camelCase - onClick
    :kebab->PascalCase - MyComponent
    :camel->kebab - on-click
    :preserve-kebab - keep as-is
    :custom - call custom function"
  [transform-type value]
  (case transform-type
    :kebab->camelCase (kebab->camel-case value)
    :kebab->PascalCase (kebab->pascal-case value)
    :camel->kebab (camel->kebab-case value)
    :preserve-kebab (preserve-kebab value)
    value))

(defn transform-attribute
  "Transform a single attribute according to rules.

  Returns [new-key new-value] or nil if attribute should be dropped."
  [attr-name attr-value transform-rules]
  (if-let [rule (find-matching-rule attr-name transform-rules)]
    (let [;; Transform the attribute name
          new-name (if-let [target (:target rule)]
                     target
                     (if-let [transform (:transform rule)]
                       (keyword (apply-transform transform attr-name))
                       attr-name))

          ;; Transform the attribute value (if specified)
          new-value (if-let [value-transform (:value-transform rule)]
                      (apply-transform value-transform attr-value)
                      attr-value)]

      [new-name new-value])

    ;; No rule found, keep as-is
    [attr-name attr-value]))

(defn transform-attributes
  "Transform all attributes in a props map according to rules.

  Returns new props map with transformed attributes."
  [props transform-rules]
  (into {}
        (keep (fn [[k v]]
                (transform-attribute k v transform-rules))
              props)))

;; =============================================================================
;; Style Transformation
;; =============================================================================

(defn parse-css-string
  "Parse CSS string into property map.

  Input: 'color: red; background: blue;'
  Output: {:color 'red' :background 'blue'}"
  [css-str]
  (when css-str
    (->> (str/split css-str #";")
         (map str/trim)
         (filter #(not (str/blank? %)))
         (map (fn [decl]
                (let [[prop val] (str/split decl #":" 2)]
                  [(keyword (str/trim prop)) (str/trim val)])))
         (into {}))))

(defn css-map-to-string
  "Convert property map to CSS string.

  Input: {:color 'red' :background 'blue'}
  Output: 'color: red; background: blue;'"
  [css-map]
  (str/join "; "
            (map (fn [[k v]]
                   (str (name k) ": " v))
                 css-map)))

(defn transform-style-properties
  "Transform style property names according to rules.

  Example: {:background-color 'red'} → {:backgroundColor 'red'} (for React)"
  [style-map property-transform]
  (into {}
        (map (fn [[k v]]
               [(keyword (apply-transform property-transform k)) v])
             style-map)))

(defn transform-style
  "Transform style attribute according to platform rules.

  Supports:
  - :css-string → :css-string (pass through)
  - :css-string → :jsx-style-object (for React)
  - :jsx-style-object → :css-string (reverse)"
  [style-value style-transform-config]
  (let [from (:from style-transform-config)
        to (:to style-transform-config)
        prop-transform (:property-transform style-transform-config :preserve-kebab)]

    (cond
      ;; CSS string to CSS string (identity)
      (and (= from :css-string) (= to :css-string))
      style-value

      ;; CSS string to JSX style object
      (and (= from :css-string) (= to :jsx-style-object))
      (-> style-value
          parse-css-string
          (transform-style-properties prop-transform))

      ;; JSX style object to CSS string
      (and (= from :jsx-style-object) (= to :css-string))
      (css-map-to-string style-value)

      ;; Unknown transformation, pass through
      :else
      style-value)))

;; =============================================================================
;; Element Transformation
;; =============================================================================

(defn transform-element-name
  "Transform element tag name according to rules.

  Examples:
    :div → :div (HTML)
    :button → :button (HTML)
    :my-component → :MyComponent (React)"
  [tag element-transform]
  (if element-transform
    (apply-transform element-transform tag)
    tag))

(defn transform-element
  "Transform a single Hiccup-style element according to platform rules.

  Input: [:div {:class 'card' :on-click 'fn()' :style 'color: red'}]
  Output: [:div {:className 'card' :onClick 'fn()' :style {:color 'red'}}]"
  [element platform-config]
  (if-not (vector? element)
    element  ; Not an element, pass through

    (let [[tag props & children] element
          has-props? (map? props)
          actual-props (if has-props? props {})
          actual-children (if has-props? children (cons props children))

          ;; Get transformation rules
          attr-transforms (get-in platform-config [:compiler :attribute-transforms])
          style-transform (get-in platform-config [:compiler :style-transform])
          element-transform (get-in platform-config [:compiler :element-transform])

          ;; Transform tag name
          new-tag (transform-element-name tag element-transform)

          ;; Transform attributes
          new-props (transform-attributes actual-props attr-transforms)

          ;; Transform style separately
          new-props (if-let [style (:style new-props)]
                      (assoc new-props :style (transform-style style style-transform))
                      new-props)

          ;; Recursively transform children
          new-children (map #(if (vector? %)
                              (transform-element % platform-config)
                              %)
                           actual-children)]

      ;; Reconstruct element
      (if (empty? new-props)
        (into [new-tag] new-children)
        (into [new-tag new-props] new-children)))))

;; =============================================================================
;; Output Format Conversion
;; =============================================================================

(defn hiccup->jsx-string
  "Convert Hiccup to JSX string.

  [:div {:className 'card'} 'Hello'] → '<div className=\"card\">Hello</div>'"
  [element]
  (if-not (vector? element)
    (str element)

    (let [[tag props & children] element
          has-props? (map? props)
          actual-props (if has-props? props {})
          actual-children (if has-props? children (cons props children))

          ;; Build attribute string
          attr-str (str/join " "
                            (map (fn [[k v]]
                                   (if (map? v)
                                     ;; Style object
                                     (str (name k) "={{"
                                          (str/join ", "
                                                   (map (fn [[sk sv]]
                                                          (str (name sk) ": '" sv "'"))
                                                        v))
                                          "}}")
                                     ;; Regular attribute
                                     (str (name k) "=\"" v "\"")))
                                 actual-props))

          ;; Build children string
          children-str (str/join "" (map hiccup->jsx-string actual-children))]

      ;; Build element
      (if (empty? actual-children)
        (str "<" (name tag)
             (when (not (str/blank? attr-str)) (str " " attr-str))
             " />")
        (str "<" (name tag)
             (when (not (str/blank? attr-str)) (str " " attr-str))
             ">"
             children-str
             "</" (name tag) ">")))))

(defn hiccup->react-create-element
  "Convert Hiccup to React.createElement call.

  [:div {:className 'card'} 'Hello']
  → 'React.createElement(\"div\", {className: \"card\"}, \"Hello\")'"
  [element]
  (if-not (vector? element)
    (pr-str element)

    (let [[tag props & children] element
          has-props? (map? props)
          actual-props (if has-props? props {})
          actual-children (if has-props? children (cons props children))

          ;; Convert props to JS object string
          props-str (if (empty? actual-props)
                      "null"
                      (str "{"
                           (str/join ", "
                                    (map (fn [[k v]]
                                           (str (name k) ": " (pr-str v)))
                                         actual-props))
                           "}"))

          ;; Convert children
          children-str (str/join ", " (map hiccup->react-create-element actual-children))]

      (str "React.createElement(\""
           (name tag) "\", "
           props-str
           (when-not (empty? actual-children)
             (str ", " children-str))
           ")"))))

;; =============================================================================
;; Public API
;; =============================================================================

(defn transform
  "Transform Forma EDN to target platform format.

  Args:
    forma-edn - Hiccup-style EDN structure (single element or vector of elements)
    platform-config - Platform configuration (from react.edn, etc.)
    output-format - :hiccup, :jsx, :react-create-element

  Returns:
    Transformed structure in the requested format"
  [forma-edn platform-config output-format]
  (let [;; Check if it's a single Hiccup element (starts with keyword) or multiple elements
        single-element? (and (vector? forma-edn) (keyword? (first forma-edn)))

        ;; First, transform the structure according to platform rules
        transformed (if single-element?
                      (transform-element forma-edn platform-config)
                      (mapv #(transform-element % platform-config) forma-edn))]

    ;; Then, convert to output format
    (case output-format
      :hiccup transformed

      :jsx (if single-element?
             (hiccup->jsx-string transformed)
             (str/join "\n" (map hiccup->jsx-string transformed)))

      :react-create-element (if single-element?
                              (hiccup->react-create-element transformed)
                              (str/join ",\n" (map hiccup->react-create-element transformed)))

      ;; Default: return transformed Hiccup
      transformed)))

(defn transform-batch
  "Transform multiple Forma EDN structures.

  Useful for batch processing of components or pages."
  [forma-edn-seq platform-config output-format]
  (mapv #(transform % platform-config output-format) forma-edn-seq))
