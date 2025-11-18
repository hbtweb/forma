(ns forma.tokens.registry
  "Build token registry from resolved values, enable reverse lookup.

  This module enables reconstruction of token definitions from flattened/resolved
  values, supporting import workflows where original tokens are unknown."
  (:require [clojure.string :as str]
            [clojure.set :as set]))

;; Token Pattern Detection
;; ============================================================================

(def ^:private token-patterns
  "Patterns for detecting values that should be tokenized."
  {:color {:regex #"^(#[0-9a-fA-F]{3,8}|rgba?\([^)]+\)|hsla?\([^)]+\))$"
           :min-frequency 5
           :category :colors}
   :spacing {:regex #"^-?\d+(\.\d+)?(rem|px|em)$"
             :min-frequency 5
             :category :spacing}
   :font-size {:regex #"^\d+(\.\d+)?(rem|px|em|pt)$"
               :min-frequency 3
               :category [:typography :sizes]}
   :font-family {:regex #"^[\w\s,'-]+$"
                 :min-frequency 2
                 :category [:typography :families]}
   :border-radius {:regex #"^\d+(\.\d+)?(rem|px|em|%)$"
                   :min-frequency 5
                   :category [:borders :radius]}
   :shadow {:regex #"^[\d\w\s#(),.-]+$"
            :min-frequency 3
            :category [:effects :shadows]}})

(defn- matches-pattern?
  "Check if a value matches a token pattern."
  [value pattern-type]
  (when-let [pattern (get token-patterns pattern-type)]
    (and (string? value)
         (re-matches (:regex pattern) value))))

(defn- detect-pattern-type
  "Detect which pattern type a value matches."
  [value]
  (cond
    (matches-pattern? value :color) :color
    (matches-pattern? value :spacing) :spacing
    (matches-pattern? value :font-size) :font-size
    (matches-pattern? value :font-family) :font-family
    (matches-pattern? value :border-radius) :border-radius
    (matches-pattern? value :shadow) :shadow
    :else nil))

;; Property to Token Category Mapping
;; ============================================================================

(def ^:private property-to-category
  "Map CSS properties to likely token categories."
  {:background :color
   :background-color :color
   :color :color
   :border-color :color
   :fill :color
   :stroke :color

   :padding :spacing
   :margin :spacing
   :gap :spacing
   :padding-top :spacing
   :padding-right :spacing
   :padding-bottom :spacing
   :padding-left :spacing
   :margin-top :spacing
   :margin-right :spacing
   :margin-bottom :spacing
   :margin-left :spacing

   :font-size :font-size
   :font-family :font-family
   :border-radius :border-radius
   :box-shadow :shadow})

(defn- infer-category-from-property
  "Infer token category from property name."
  [property-key]
  (or (get property-to-category property-key)
      (detect-pattern-type (name property-key))))

;; Frequency Analysis
;; ============================================================================

(defn- count-value-frequencies
  "Count how often each value appears for a given property."
  [flattened-edn property-key]
  (let [values (atom {})]
    (letfn [(collect [node]
              (cond
                ;; Map with properties
                (map? node)
                (do
                  (when-let [value (get node property-key)]
                    (swap! values update value (fnil inc 0)))
                  (doseq [[_ v] node]
                    (collect v)))

                ;; Vector (hiccup-like)
                (vector? node)
                (doseq [item node]
                  (collect item))

                ;; Sequential collection
                (sequential? node)
                (doseq [item node]
                  (collect item))))]
      (collect flattened-edn)
      @values)))

(defn detect-token-patterns
  "Detect common patterns that should be tokenized.

  Returns: [{:type :color :values [...] :frequency N}]"
  [flattened-edn]
  (let [patterns (atom [])]
    ;; Check each property that could be tokenized
    (doseq [[prop-key pattern-info] property-to-category]
      (let [frequencies (count-value-frequencies flattened-edn prop-key)
            pattern-type (or pattern-info (detect-pattern-type (name prop-key)))
            min-freq (get-in token-patterns [pattern-type :min-frequency] 3)]
        ;; Only suggest tokenization for values used >= min-frequency
        (doseq [[value freq] frequencies]
          (when (>= freq min-freq)
            (swap! patterns conj
                   {:type pattern-type
                    :property prop-key
                    :value value
                    :frequency freq
                    :suggested-category (get-in token-patterns
                                                [pattern-type :category]
                                                pattern-type)})))))
    @patterns))

;; Token Registry Construction
;; ============================================================================

(defn- extract-tokens-from-metadata
  "Extract token definitions from metadata (data-forma-token-provenance)."
  [metadata]
  (let [tokens (atom {})]
    (doseq [[_elem-path elem-metadata] metadata]
      (when-let [token-prov (:token-provenance elem-metadata)]
        (doseq [[prop-key token-ref] token-prov]
          (when (and (string? token-ref)
                     (str/starts-with? token-ref "$"))
            ;; Parse token path: "$colors.primary" -> [:colors :primary]
            (let [token-str (subs token-ref 1)  ; Remove "$" prefix
                  path (mapv keyword (str/split token-str #"\."))
                  ;; Get resolved value from element properties
                  resolved-value (get-in elem-metadata [:properties prop-key])]
              (when resolved-value
                (swap! tokens assoc-in path resolved-value)))))))
    @tokens))

(defn- extract-tokens-from-frequency
  "Build token registry from frequency analysis (no metadata)."
  [flattened-edn]
  (let [patterns (detect-token-patterns flattened-edn)
        tokens (atom {})]
    ;; Group by category and create token names
    (doseq [{:keys [value frequency suggested-category]} patterns]
      (let [;; Convert category to path: :colors or [:typography :sizes]
            category-path (if (vector? suggested-category)
                           suggested-category
                           [suggested-category])
            ;; Generate token name based on value characteristics
            token-name (cond
                        ;; Color: try to use semantic name
                        (= (first category-path) :colors)
                        (keyword (str "color-" (subs (str (hash value)) 0 6)))

                        ;; Spacing: use value as name (e.g., :1rem, :2rem)
                        (= (first category-path) :spacing)
                        (keyword (str/replace value #"\." "-"))

                        ;; Default: hash-based name
                        :else
                        (keyword (str "token-" (subs (str (hash value)) 0 6))))
            token-path (conj category-path token-name)]
        (swap! tokens assoc-in token-path value)))
    @tokens))

(defn build-token-registry
  "Analyze flattened structure + metadata, extract token definitions.

  Returns: {:colors {:primary \"#4f46e5\" :secondary \"#64748b\"}
            :spacing {:sm \"0.5rem\" :md \"1rem\"}}"
  ([flattened-edn]
   (build-token-registry flattened-edn nil))
  ([flattened-edn metadata]
   (if metadata
     ;; Prefer metadata tokens (100% confidence)
     (let [metadata-tokens (extract-tokens-from-metadata metadata)
           ;; Also detect frequent patterns not in metadata
           frequency-tokens (extract-tokens-from-frequency flattened-edn)]
       ;; Merge with metadata taking precedence
       (merge-with merge frequency-tokens metadata-tokens))
     ;; No metadata: frequency-based only
     (extract-tokens-from-frequency flattened-edn))))

;; Reverse Token Lookup
;; ============================================================================

(defn- flatten-token-paths
  "Flatten nested token map to {path value} pairs.

  Example: {:colors {:primary \"#fff\"}} -> {[:colors :primary] \"#fff\"}"
  [token-map]
  (letfn [(flatten-path [m prefix]
            (reduce-kv
             (fn [acc k v]
               (let [path (conj prefix k)]
                 (if (map? v)
                   (merge acc (flatten-path v path))
                   (assoc acc path v))))
             {}
             m))]
    (flatten-path token-map [])))

(defn- build-reverse-index
  "Build value -> [token-paths] index for fast reverse lookup."
  [token-map]
  (let [flat-tokens (flatten-token-paths token-map)]
    (reduce-kv
     (fn [acc path value]
       (update acc value (fnil conj []) path))
     {}
     flat-tokens)))

(defn reverse-lookup-token
  "Find token reference for a resolved value.

  Returns: {:token-path \"$colors.primary\"
            :confidence 0.9
            :alternatives [\"$colors.accent\"]}  ; If multiple tokens have same value"
  [value token-registry]
  (let [reverse-index (build-reverse-index token-registry)
        matching-paths (get reverse-index value)]
    (cond
      ;; No match
      (empty? matching-paths)
      {:token-path nil
       :confidence 0.0
       :alternatives []}

      ;; Single match (high confidence)
      (= 1 (count matching-paths))
      {:token-path (str "$" (str/join "." (map name (first matching-paths))))
       :confidence 0.95
       :alternatives []}

      ;; Multiple matches (collision - lower confidence)
      :else
      {:token-path (str "$" (str/join "." (map name (first matching-paths))))
       :confidence 0.7
       :alternatives (mapv #(str "$" (str/join "." (map name %)))
                          (rest matching-paths))})))

(defn reconstruct-token-references
  "Replace resolved values with token references where possible.

  Returns: {:background \"$colors.primary\"}  ; Instead of {:background \"#4f46e5\"}"
  [properties token-registry]
  (reduce-kv
   (fn [acc prop-key value]
     (let [lookup (reverse-lookup-token value token-registry)]
       (if (and (:token-path lookup)
                (>= (:confidence lookup) 0.7))
         (assoc acc prop-key (:token-path lookup))
         (assoc acc prop-key value))))
   {}
   properties))

;; Public API Summary
;; ============================================================================

(comment
  ;; Build token registry from metadata
  (build-token-registry
   flattened-edn
   {:elem-1 {:token-provenance {:background "$colors.primary"}
             :properties {:background "#4f46e5"}}})

  ;; Build token registry from frequency
  (build-token-registry flattened-edn)

  ;; Reverse lookup
  (reverse-lookup-token "#4f46e5" token-registry)
  ;; => {:token-path "$colors.primary" :confidence 0.95 :alternatives []}

  ;; Reconstruct references
  (reconstruct-token-references
   {:background "#4f46e5" :padding "1rem"}
   token-registry)
  ;; => {:background "$colors.primary" :padding "$spacing.md"}

  ;; Detect patterns
  (detect-token-patterns flattened-edn)
  ;; => [{:type :color :value "#4f46e5" :frequency 25}
  ;;     {:type :spacing :value "1rem" :frequency 18}]
  )
