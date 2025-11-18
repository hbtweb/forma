(ns forma.parsers.html
  "HTML to Forma EDN parser for bidirectional compilation.

  This parser converts HTML strings back to Hiccup-style EDN structures,
  enabling round-trip compilation: Forma EDN → HTML → Forma EDN.

  Features:
  - Parse HTML elements to Hiccup vectors
  - Preserve attributes and structure
  - Handle self-closing tags
  - Parse inline styles to property maps
  - Handle text nodes and comments
  - Support for nested structures

  Example:
    '<div class=\"card\">Hello</div>'
    → [:div {:class \"card\"} \"Hello\"]"
  (:require [clojure.string :as str]
            [clojure.walk :as walk]))

;; =============================================================================
;; Attribute Parsing (must be defined before tokenize)
;; =============================================================================

(defn- parse-attributes
  "Parse HTML attribute string to map.

  Input: 'class=\"card\" id=\"main\" data-value=\"test\"'
  Output: {:class \"card\" :id \"main\" :data-value \"test\"}"
  [attr-str]
  (when-not (str/blank? attr-str)
    (let [attr-pattern #"([a-zA-Z][a-zA-Z0-9\-]*)\s*=\s*\"([^\"]*)\""
          matches (re-seq attr-pattern attr-str)]
      (into {}
            (map (fn [[_ name value]]
                   [(keyword name) value])
                 matches)))))

;; =============================================================================
;; HTML Tokenization
;; =============================================================================

(def ^:private self-closing-tags
  "HTML tags that are self-closing."
  #{:area :base :br :col :embed :hr :img :input :link :meta :param :source :track :wbr})

(defn- tokenize
  "Tokenize HTML string into a sequence of tokens.

  Returns:
  [{:type :open-tag :name \"div\" :attrs {...}}
   {:type :text :content \"Hello\"}
   {:type :close-tag :name \"div\"}]"
  [html]
  (let [tag-pattern #"<(/?)([a-zA-Z][a-zA-Z0-9]*)((?:\s+[^>]*)?)(/?)>"
        tokens (atom [])]

    (loop [remaining html
           pos 0]
      (if (empty? remaining)
        @tokens

        (if-let [match (re-find tag-pattern remaining)]
          (let [full-match (first match)
                closing? (= (nth match 1) "/")
                tag-name (nth match 2)
                attrs-str (str/trim (nth match 3))
                self-closing? (= (nth match 4) "/")
                match-start (.indexOf remaining full-match)
                text-before (subs remaining 0 match-start)]

            ;; Add text before tag (if any)
            (when-not (str/blank? text-before)
              (swap! tokens conj {:type :text :content text-before}))

            ;; Add tag token
            (if closing?
              (swap! tokens conj {:type :close-tag :name tag-name})
              (do
                (swap! tokens conj {:type :open-tag
                                    :name tag-name
                                    :attrs (parse-attributes attrs-str)
                                    :self-closing? self-closing?})
                (when self-closing?
                  (swap! tokens conj {:type :close-tag :name tag-name}))))

            ;; Continue with rest
            (recur (subs remaining (+ match-start (count full-match)))
                   (+ pos match-start (count full-match))))

          ;; No more tags, rest is text
          (do
            (when-not (str/blank? remaining)
              (swap! tokens conj {:type :text :content remaining}))
            @tokens))))))

(defn- parse-style-attribute
  "Parse inline style string to property map.

  Input: 'color: red; background: blue;'
  Output: {:color 'red' :background 'blue'}"
  [style-str]
  (when style-str
    (->> (str/split style-str #";")
         (map str/trim)
         (filter #(not (str/blank? %)))
         (map (fn [decl]
                (let [[prop val] (str/split decl #":" 2)]
                  (when (and prop val)
                    [(keyword (str/trim prop)) (str/trim val)]))))
         (filter some?)
         (into {}))))

(defn- normalize-attributes
  "Normalize parsed attributes.

  - Parse style string to map (optional)
  - Convert boolean attributes (checked, disabled, etc.)"
  [attrs opts]
  (when attrs
    (let [parse-style? (get opts :parse-style? true)
          ;; Parse inline styles if present
          attrs (if (and parse-style? (:style attrs))
                  (update attrs :style parse-style-attribute)
                  attrs)]
      ;; Convert boolean attributes
      (walk/postwalk (fn [x]
                       (cond
                         (= x "true") true
                         (= x "false") false
                         :else x))
                     attrs))))

;; =============================================================================
;; Tree Building
;; =============================================================================

(defn- build-tree
  "Build Hiccup tree from tokens.

  Returns [element remaining-tokens]"
  [tokens opts]
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

          :open-tag
          (let [tag-name (keyword (:name token))
                attrs (normalize-attributes (:attrs token) opts)
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
              (let [[child-elements remaining] (build-tree rest-tokens opts)
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
  "Parse HTML string to Forma EDN (Hiccup).

  Options:
    :parse-style? - Parse inline style strings to maps (default: true)
    :preserve-whitespace? - Preserve whitespace in text nodes (default: false)

  Example:
    (parse \"<div class=\\\"card\\\"><p>Hello</p></div>\")
    → [:div {:class \"card\"} [:p \"Hello\"]]"
  ([html] (parse html {}))
  ([html opts]
   (let [tokens (tokenize html)
         [tree _] (build-tree tokens opts)]
     (if (= 1 (count tree))
       (first tree)
       tree))))

(defn parse-fragment
  "Parse HTML fragment (multiple root elements).

  Returns a vector of elements, not wrapped in a container."
  ([html] (parse-fragment html {}))
  ([html opts]
   (let [tokens (tokenize html)
         [tree _] (build-tree tokens opts)]
     tree)))

(defn parse-attribute
  "Parse a single HTML attribute value.

  Useful for parsing specific attributes like style or class."
  [attr-name attr-value]
  (case attr-name
    :style (parse-style-attribute attr-value)
    :class (str/split attr-value #"\s+")
    attr-value))

;; =============================================================================
;; Helper Functions
;; =============================================================================

(defn extract-text
  "Extract all text content from parsed HTML tree.

  Example:
    (extract-text [:div [:p \"Hello\"] [:p \"World\"]])
    → \"Hello World\""
  [tree]
  (cond
    (string? tree)
    tree

    (vector? tree)
    (let [[_tag props & children] tree
          has-props? (map? props)
          actual-children (if has-props? children (cons props children))]
      (str/join " " (map extract-text actual-children)))

    :else
    ""))

(defn find-elements
  "Find all elements matching a predicate.

  Example:
    (find-elements #(= :div (first %)) tree)
    → [[:div ...] [:div ...]]"
  [pred tree]
  (let [results (atom [])]
    (walk/prewalk
     (fn [node]
       (when (and (vector? node) (pred node))
         (swap! results conj node))
       node)
     tree)
    @results))

(defn find-by-tag
  "Find all elements with a specific tag.

  Example:
    (find-by-tag :div tree)
    → [[:div ...] [:div ...]]"
  [tag tree]
  (find-elements #(= tag (first %)) tree))

(defn find-by-class
  "Find all elements with a specific class.

  Example:
    (find-by-class \"card\" tree)
    → [[:div {:class \"card\"} ...]]"
  [class-name tree]
  (find-elements
   (fn [node]
     (let [[_tag props] node]
       (and (map? props)
            (or (= class-name (:class props))
                (and (string? (:class props))
                     (str/includes? (:class props) class-name))))))
   tree))

(defn find-by-id
  "Find element with a specific ID.

  Example:
    (find-by-id \"main\" tree)
    → [:div {:id \"main\"} ...]"
  [id tree]
  (first (find-elements
          (fn [node]
            (let [[_tag props] node]
              (and (map? props)
                   (= id (:id props)))))
          tree)))
