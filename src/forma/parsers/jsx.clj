(ns forma.parsers.jsx
  "JSX to Forma EDN parser for React bidirectional compilation.

  This parser converts JSX/React strings back to Hiccup-style EDN structures,
  enabling round-trip compilation: Forma EDN → JSX → Forma EDN.

  Features:
  - Parse JSX elements to Hiccup vectors
  - Convert className back to :class
  - Parse JSX style objects to CSS strings
  - Handle camelCase event handlers (onClick → on-click)
  - Support self-closing tags
  - Handle JSX expressions {...}

  Example:
    '<div className=\"card\" onClick={handleClick}>Hello</div>'
    → [:div {:class \"card\" :on-click \"handleClick\"} \"Hello\"]"
  (:require [clojure.string :as str]
            [clojure.walk :as walk]))

;; =============================================================================
;; String Utilities
;; =============================================================================

(defn camel->kebab
  "Convert camelCase to kebab-case.

  Examples:
    onClick → on-click
    backgroundColor → background-color"
  [s]
  (-> s
      str
      (str/replace #"([a-z])([A-Z])" "$1-$2")
      str/lower-case))

;; =============================================================================
;; JSX Attribute Parsing (must be defined before tokenization)
;; =============================================================================

(defn- parse-jsx-attributes
  "Parse JSX attribute string to map.

  Input: 'className=\"card\" onClick={handleClick} disabled'
  Output: {:class \"card\" :on-click \"handleClick\" :disabled true}"
  [attr-str]
  (when-not (str/blank? attr-str)
    (let [;; Match name=\"value\" or name={expression} or name
          attr-pattern #"([a-zA-Z][a-zA-Z0-9]*)\s*(?:=\s*(?:\"([^\"]*)\"|(\{[^}]+\}))|(?=\s|$))"
          matches (re-seq attr-pattern attr-str)]
      (into {}
            (map (fn [[_ name string-val expr-val]]
                   (let [;; Convert React attributes to Forma
                         attr-key (case name
                                    "className" :class
                                    ;; Convert camelCase to kebab-case
                                    (keyword (camel->kebab name)))
                         attr-val (cond
                                    string-val string-val
                                    expr-val (str/replace expr-val #"[{}]" "")
                                    :else true)] ;; Boolean attribute
                     [attr-key attr-val]))
                 matches)))))

;; =============================================================================
;; JSX Tokenization
;; =============================================================================

(def ^:private self-closing-tags
  "JSX tags that are typically self-closing."
  #{:br :hr :img :input :link :meta :area :base :col :embed :param :source :track :wbr})

(defn- tokenize-jsx
  "Tokenize JSX string into a sequence of tokens.

  Returns:
  [{:type :open-tag :name \"div\" :attrs {...}}
   {:type :text :content \"Hello\"}
   {:type :close-tag :name \"div\"}
   {:type :expression :content \"count\"}]"
  [jsx]
  (let [;; Match opening tags, closing tags, self-closing tags
        tag-pattern #"<(/?)([A-Z][a-zA-Z0-9]*|[a-z][a-zA-Z0-9]*)((?:\s+[^>]*)?)(/?)>"
        ;; Match JSX expressions {...}
        expr-pattern #"\{([^}]+)\}"
        tokens (atom [])]

    (loop [remaining jsx
           pos 0]
      (if (empty? remaining)
        @tokens

        ;; Try to match tag first
        (if-let [tag-match (re-find tag-pattern remaining)]
          (let [full-match (first tag-match)
                closing? (= (nth tag-match 1) "/")
                tag-name (nth tag-match 2)
                attrs-str (str/trim (nth tag-match 3))
                self-closing? (= (nth tag-match 4) "/")
                match-start (.indexOf remaining full-match)
                text-before (subs remaining 0 match-start)]

            ;; Add text/expressions before tag
            (when-not (str/blank? text-before)
              ;; Check for expressions in text
              (if-let [expr-match (re-find expr-pattern text-before)]
                (let [expr-start (.indexOf text-before (first expr-match))
                      text-part (subs text-before 0 expr-start)
                      expr-content (nth expr-match 1)]
                  (when-not (str/blank? text-part)
                    (swap! tokens conj {:type :text :content text-part}))
                  (swap! tokens conj {:type :expression :content expr-content}))
                (swap! tokens conj {:type :text :content text-before})))

            ;; Add tag token
            (if closing?
              (swap! tokens conj {:type :close-tag :name tag-name})
              (do
                (swap! tokens conj {:type :open-tag
                                    :name tag-name
                                    :attrs (parse-jsx-attributes attrs-str)
                                    :self-closing? self-closing?})
                (when self-closing?
                  (swap! tokens conj {:type :close-tag :name tag-name}))))

            ;; Continue with rest
            (recur (subs remaining (+ match-start (count full-match)))
                   (+ pos match-start (count full-match))))

          ;; No more tags, check for expressions or text
          (if-let [expr-match (re-find expr-pattern remaining)]
            (let [full-match (first expr-match)
                  expr-content (nth expr-match 1)
                  match-start (.indexOf remaining full-match)
                  text-before (subs remaining 0 match-start)]

              (when-not (str/blank? text-before)
                (swap! tokens conj {:type :text :content text-before}))

              (swap! tokens conj {:type :expression :content expr-content})

              (recur (subs remaining (+ match-start (count full-match)))
                     (+ pos match-start (count full-match))))

            ;; No more tags or expressions, rest is text
            (do
              (when-not (str/blank? remaining)
                (swap! tokens conj {:type :text :content remaining}))
              @tokens)))))))

(defn- parse-jsx-style-object
  "Parse JSX style object to CSS string.

  Input: '{backgroundColor: \"red\", color: \"white\"}'
  Output: 'background-color: red; color: white;'"
  [style-str]
  (when style-str
    (let [;; Remove braces
          clean-str (str/replace style-str #"[{}]" "")
          ;; Split by comma
          props (str/split clean-str #",")
          ;; Parse each property
          parsed (map (fn [prop]
                       (let [[key val] (str/split prop #":" 2)]
                         (when (and key val)
                           (let [prop-name (camel->kebab (str/trim key))
                                 prop-val (-> val
                                             str/trim
                                             (str/replace #"^['\"]|['\"]$" ""))]
                             (str prop-name ": " prop-val)))))
                     props)]
      (str/join "; " (filter some? parsed)))))

(defn- normalize-jsx-attributes
  "Normalize parsed JSX attributes.

  - Convert style object to CSS string
  - Handle boolean attributes"
  [attrs]
  (when attrs
    (cond-> attrs
      ;; Convert JSX style object to CSS string
      (:style attrs)
      (update :style (fn [style-val]
                       (if (str/starts-with? style-val "{")
                         (parse-jsx-style-object style-val)
                         style-val))))))

;; =============================================================================
;; Tree Building
;; =============================================================================

(defn- build-jsx-tree
  "Build Hiccup tree from JSX tokens.

  Returns [element remaining-tokens]"
  [tokens]
  (loop [current-tokens tokens
         children []]

    (if (empty? current-tokens)
      [children current-tokens]

      (let [token (first current-tokens)
            rest-tokens (rest current-tokens)]

        (case (:type token)
          :text
          (recur rest-tokens
                 (conj children (:content token)))

          :expression
          ;; JSX expression - preserve as placeholder
          (recur rest-tokens
                 (conj children (str "{" (:content token) "}")))

          :open-tag
          (let [tag-name (keyword (:name token))
                attrs (normalize-jsx-attributes (:attrs token))
                self-closing? (or (:self-closing? token)
                                  (contains? self-closing-tags tag-name))]

            (if self-closing?
              ;; Self-closing tag, no children
              (let [element (if (seq attrs)
                             [tag-name attrs]
                             [tag-name])]
                (recur (rest rest-tokens) ;; Skip close tag
                       (conj children element)))

              ;; Regular tag, parse children
              (let [[child-elements remaining] (build-jsx-tree rest-tokens)
                    element (if (seq attrs)
                             (into [tag-name attrs] child-elements)
                             (into [tag-name] child-elements))]
                (recur remaining
                       (conj children element)))))

          :close-tag
          ;; End of current level
          [children rest-tokens]

          ;; Unknown token type
          (recur rest-tokens children))))))

;; =============================================================================
;; Public API
;; =============================================================================

(defn parse
  "Parse JSX string to Forma EDN (Hiccup).

  Example:
    (parse \"<div className=\\\"card\\\"><p>Hello</p></div>\")
    → [:div {:class \"card\"} [:p \"Hello\"]]

  React-specific conversions:
    - className → :class
    - onClick → :on-click
    - style={{...}} → :style \"...\"
    - {expression} → \"{expression}\" (preserved as placeholder)"
  ([jsx] (parse jsx {}))
  ([jsx _opts]
   (let [tokens (tokenize-jsx jsx)
         [tree _] (build-jsx-tree tokens)]
     (if (= 1 (count tree))
       (first tree)
       tree))))

(defn parse-fragment
  "Parse JSX fragment (multiple root elements).

  Returns a vector of elements, not wrapped in a container."
  ([jsx] (parse-fragment jsx {}))
  ([jsx opts]
   (let [tokens (tokenize-jsx jsx)
         [tree _] (build-jsx-tree tokens)]
     tree)))

;; =============================================================================
;; React.createElement Parser
;; =============================================================================

(defn parse-react-create-element
  "Parse React.createElement call to Forma EDN.

  Example:
    'React.createElement(\"div\", {className: \"card\"}, \"Hello\")'
    → [:div {:class \"card\"} \"Hello\"]"
  [code-str]
  (let [;; Match React.createElement(tag, props, ...children)
        pattern #"React\.createElement\([\"']([^\"']+)[\"'],\s*(\{[^}]*\}|null),?\s*(.*)\)"
        match (re-find pattern code-str)]

    (when match
      (let [tag-name (keyword (nth match 1))
            props-str (nth match 2)
            children-str (nth match 3)

            ;; Parse props
            props (when (not= props-str "null")
                   (parse-jsx-attributes (str/replace props-str #"[{}]" "")))

            ;; Parse children (simple implementation)
            children (when-not (str/blank? children-str)
                      (let [child-parts (str/split children-str #",\s*")]
                        (map (fn [part]
                               (if (str/starts-with? part "React.createElement")
                                 (parse-react-create-element part)
                                 (str/replace part #"^['\"]|['\"]$" "")))
                             child-parts)))]

        (if (empty? props)
          (into [tag-name] children)
          (into [tag-name (normalize-jsx-attributes props)] children))))))

;; =============================================================================
;; Helper Functions
;; =============================================================================

(defn detect-component-type
  "Detect if JSX element is a React component (PascalCase) or HTML element.

  Returns :component or :html"
  [tag-name]
  (let [first-char (first (name tag-name))]
    (if (Character/isUpperCase first-char)
      :component
      :html)))

(defn extract-components
  "Extract all React component usages from JSX tree.

  Returns list of component names."
  [tree]
  (let [components (atom #{})]
    (walk/prewalk
     (fn [node]
       (when (vector? node)
         (let [tag (first node)]
           (when (= :component (detect-component-type tag))
             (swap! components conj tag))))
       node)
     tree)
    @components))

(defn jsx->hiccup
  "Convert JSX to Hiccup with full attribute normalization.

  This is an alias for parse with standard React conversions."
  [jsx]
  (parse jsx))
