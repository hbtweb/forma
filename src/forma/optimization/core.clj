(ns forma.optimization.core
  "Pre-compilation optimization for Forma projects.

  This module analyzes compiled output and optimizes before writing files:
  - Dead code elimination (unused tokens, components, properties)
  - CSS deduplication (merge duplicate rules)
  - Property inlining (inline frequently-used tokens)

  Phase 5.1 implementation."
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.walk :as walk]))

;;
;; Dead Code Elimination
;;

(defn extract-token-references
  "Recursively extract all token references ($token.path) from EDN data.

  Args:
    edn-data - Forma EDN (flattened or hierarchical)

  Returns:
    Set of token references as strings (e.g., #{\"$colors.primary\" \"$spacing.md\"})"
  [edn-data]
  (let [refs (atom #{})]
    (walk/postwalk
     (fn [node]
       (when (and (string? node)
                  (str/starts-with? node "$"))
         (swap! refs conj node))
       node)
     edn-data)
    @refs))

(defn extract-token-definitions
  "Extract all token definitions from token registry.

  Args:
    token-registry - Token registry map (e.g., {:colors {:primary \"#4f46e5\"}})

  Returns:
    Map of token path → value (e.g., {\"$colors.primary\" \"#4f46e5\"})"
  [token-registry]
  (letfn [(flatten-tokens [prefix m]
            (reduce-kv
             (fn [acc k v]
               (let [path (if (empty? prefix)
                           (str "$" (name k))
                           (str prefix "." (name k)))]
                 (if (map? v)
                   (merge acc (flatten-tokens path v))
                   (assoc acc path v))))
             {}
             m))]
    (flatten-tokens "" token-registry)))

(defn find-unused-tokens
  "Find tokens that are defined but never referenced.

  Args:
    token-registry - Token registry map
    compiled-edn - Compiled Forma EDN (all elements)

  Returns:
    {:unused #{\"$colors.unused\" ...}
     :used #{\"$colors.primary\" ...}
     :total 25
     :unused-count 3}"
  [token-registry compiled-edn]
  (let [defined-tokens (set (keys (extract-token-definitions token-registry)))
        referenced-tokens (extract-token-references compiled-edn)
        unused-tokens (set/difference defined-tokens referenced-tokens)]

    {:unused unused-tokens
     :used referenced-tokens
     :total (count defined-tokens)
     :unused-count (count unused-tokens)}))

(defn eliminate-unused-tokens
  "Remove unused tokens from token registry.

  Args:
    token-registry - Token registry map
    compiled-edn - Compiled Forma EDN
    opts - Options:
           :aggressive? - Also remove tokens used only once (default: false)
           :keep-patterns - Regex patterns to always keep (default: nil)

  Returns:
    {:optimized-registry {...}   ; Registry with unused tokens removed
     :removed [\"$colors.unused\" ...]
     :kept [\"$colors.primary\" ...]}"
  [token-registry compiled-edn opts]
  (let [token-analysis (find-unused-tokens token-registry compiled-edn)
        unused (:unused token-analysis)
        keep-patterns (:keep-patterns opts)

        ;; Filter unused tokens by keep-patterns
        actually-unused
        (if keep-patterns
          (remove (fn [token-path]
                   (some #(re-find % token-path) keep-patterns))
                  unused)
          unused)

        ;; Build optimized registry (remove unused)
        optimized-registry
        (let [token-defs (extract-token-definitions token-registry)]
          ;; Reconstruct nested structure from flat paths
          (reduce
           (fn [acc [token-path value]]
             (if (contains? (set actually-unused) token-path)
               acc  ; Skip unused token
               (let [path-parts (-> token-path
                                    (str/replace #"^\$" "")
                                    (str/split #"\.")
                                    (->> (mapv keyword)))]
                 (assoc-in acc path-parts value))))
           {}
           token-defs))]

    {:optimized-registry optimized-registry
     :removed (vec actually-unused)
     :kept (vec (set/difference (set (keys (extract-token-definitions token-registry)))
                                (set actually-unused)))}))

;;
;; CSS Deduplication
;;

(defn parse-css-rule
  "Parse a CSS rule string into [selector properties-map].

  Example:
    (parse-css-rule \".btn { padding: 1rem; color: #fff; }\")
    ;; => [\".btn\" {:padding \"1rem\" :color \"#fff\"}]"
  [css-rule]
  (let [[_ selector props-str] (re-find #"([^{]+)\s*\{([^}]+)\}" css-rule)
        properties
        (when props-str
          (->> (str/split props-str #";")
               (map str/trim)
               (remove str/blank?)
               (map #(let [[k v] (str/split % #":" 2)]
                      [(keyword (str/trim k)) (str/trim v)]))
               (into {})))]
    [(str/trim selector) properties]))

(defn serialize-css-rule
  "Serialize a CSS rule from [selector properties-map] to string.

  Example:
    (serialize-css-rule [\".btn\" {:padding \"1rem\" :color \"#fff\"}])
    ;; => \".btn { padding: 1rem; color: #fff; }\""
  [selector properties]
  (let [props-str (->> properties
                       (map (fn [[k v]] (str (name k) ": " v)))
                       (str/join "; "))]
    (str selector " { " props-str "; }")))

(defn deduplicate-css-rules
  "Merge duplicate CSS rules with identical properties.

  Args:
    css-rules - Vector of CSS rule strings

  Returns:
    {:optimized-css [\"merged rule 1\" ...]
     :original-count 100
     :optimized-count 75
     :savings-percent 25.0}"
  [css-rules]
  (let [;; Parse all rules
        parsed-rules (map parse-css-rule css-rules)

        ;; Group by properties (find duplicates)
        grouped-by-props
        (group-by second parsed-rules)

        ;; Merge selectors for identical properties
        merged-rules
        (map (fn [[props rules]]
               (let [selectors (map first rules)
                     merged-selector (str/join ", " selectors)]
                 [merged-selector props]))
             grouped-by-props)

        ;; Serialize back to CSS strings
        optimized-css
        (map (fn [[selector props]] (serialize-css-rule selector props))
             merged-rules)]

    {:optimized-css (vec optimized-css)
     :original-count (count css-rules)
     :optimized-count (count optimized-css)
     :savings-percent (if (pos? (count css-rules))
                       (* 100.0 (/ (- (count css-rules) (count optimized-css))
                                   (count css-rules)))
                       0.0)}))

(defn deduplicate-css-properties
  "Remove duplicate properties within a single rule (last value wins).

  Args:
    css-string - CSS string (may have duplicate properties)

  Returns:
    Deduplicated CSS string

  Example:
    (deduplicate-css-properties \"color: red; padding: 1rem; color: blue;\")
    ;; => \"padding: 1rem; color: blue;\""
  [css-string]
  (let [properties
        (->> (str/split css-string #";")
             (map str/trim)
             (remove str/blank?)
             (map #(let [[k v] (str/split % #":" 2)]
                    [(keyword (str/trim k)) (str/trim v)]))
             ;; Build map (last value wins for duplicates)
             (reduce (fn [acc [k v]]
                      (assoc acc k v))
                     {}))]
    (->> properties
         (map (fn [[k v]] (str (name k) ": " v)))
         (str/join "; "))))

;;
;; Property Inlining
;;

(defn analyze-token-usage-frequency
  "Analyze token usage frequency across compiled output.

  Args:
    compiled-edn - Compiled Forma EDN

  Returns:
    {\"$colors.primary\" 25   ; Used 25 times
     \"$spacing.md\" 18
     ...}"
  [compiled-edn]
  (let [freq (atom {})]
    (walk/postwalk
     (fn [node]
       (when (and (string? node)
                  (str/starts-with? node "$"))
         (swap! freq update node (fnil inc 0)))
       node)
     compiled-edn)
    @freq))

(defn should-inline-token?
  "Determine if a token should be inlined based on usage frequency.

  Args:
    token-path - Token reference string (e.g., \"$colors.primary\")
    frequency - Usage frequency (number of times used)
    threshold - Minimum frequency for inlining (default: 5)

  Returns:
    true if token should be inlined, false otherwise"
  [token-path frequency threshold]
  (>= frequency threshold))

(defn inline-tokens
  "Inline frequently-used tokens (replace references with actual values).

  Args:
    compiled-edn - Compiled Forma EDN
    token-registry - Token registry map
    opts - Options:
           :threshold - Minimum usage frequency for inlining (default: 5)
           :inline-all? - Inline all tokens regardless of frequency (default: false)

  Returns:
    {:optimized-edn {...}
     :inlined [\"$colors.primary\" ...]
     :kept [\"$colors.danger\" ...]
     :inlined-count 10}"
  [compiled-edn token-registry opts]
  (let [threshold (:threshold opts 5)
        inline-all? (:inline-all? opts false)
        usage-freq (analyze-token-usage-frequency compiled-edn)
        token-defs (extract-token-definitions token-registry)

        ;; Determine which tokens to inline
        tokens-to-inline
        (if inline-all?
          (set (keys token-defs))
          (set (filter (fn [token-path]
                        (should-inline-token? token-path
                                             (get usage-freq token-path 0)
                                             threshold))
                      (keys token-defs))))

        ;; Replace tokens with actual values
        optimized-edn
        (walk/postwalk
         (fn [node]
           (if (and (string? node)
                    (str/starts-with? node "$")
                    (contains? tokens-to-inline node))
             (get token-defs node node)  ; Replace with actual value
             node))
         compiled-edn)]

    {:optimized-edn optimized-edn
     :inlined (vec tokens-to-inline)
     :kept (vec (set/difference (set (keys token-defs)) tokens-to-inline))
     :inlined-count (count tokens-to-inline)}))

;;
;; Optimization Pipeline
;;

(defn optimize-compilation
  "Run complete optimization pipeline on compiled output.

  Args:
    compiled-edn - Compiled Forma EDN
    token-registry - Token registry map
    opts - Options:
           :dead-code-elimination? - Remove unused tokens (default: true)
           :css-deduplication? - Merge duplicate CSS rules (default: true)
           :inline-tokens? - Inline frequently-used tokens (default: false)
           :inline-threshold - Frequency threshold for inlining (default: 5)

  Returns:
    {:optimized-edn {...}
     :optimized-registry {...}
     :optimizations {:dead-code {...} :css {...} :inlining {...}}
     :summary \"Removed 3 unused tokens, merged 25 CSS rules, inlined 10 tokens\"}"
  [compiled-edn token-registry opts]
  (let [dead-code? (:dead-code-elimination? opts true)
        css-dedup? (:css-deduplication? opts true)
        inline? (:inline-tokens? opts false)

        ;; Step 1: Dead code elimination
        dead-code-result
        (when dead-code?
          (eliminate-unused-tokens token-registry compiled-edn opts))

        optimized-registry (if dead-code?
                            (:optimized-registry dead-code-result)
                            token-registry)

        ;; Step 2: Property inlining (before CSS dedup)
        inline-result
        (when inline?
          (inline-tokens compiled-edn optimized-registry opts))

        optimized-edn (if inline?
                       (:optimized-edn inline-result)
                       compiled-edn)

        ;; Step 3: CSS deduplication (extract CSS from EDN first)
        ;; TODO: Implement CSS extraction from EDN
        css-result nil

        ;; Build summary
        summary-parts []
        summary-parts (if dead-code?
                       (conj summary-parts
                             (str "Removed " (count (:removed dead-code-result)) " unused tokens"))
                       summary-parts)
        summary-parts (if inline?
                       (conj summary-parts
                             (str "Inlined " (:inlined-count inline-result) " tokens"))
                       summary-parts)
        summary-parts (if css-result
                       (conj summary-parts
                             (str "Merged " (- (:original-count css-result)
                                              (:optimized-count css-result))
                                 " duplicate CSS rules"))
                       summary-parts)]

    {:optimized-edn optimized-edn
     :optimized-registry optimized-registry
     :optimizations {:dead-code dead-code-result
                    :css css-result
                    :inlining inline-result}
     :summary (str/join ", " summary-parts)}))

;;
;; Configuration
;;

(defn default-optimization-config
  "Default optimization configuration.

  Returns: Configuration map"
  []
  {:dead-code-elimination? true
   :css-deduplication? true
   :inline-tokens? false
   :inline-threshold 5
   :keep-patterns []
   :aggressive? false})

(defn load-optimization-config
  "Load optimization configuration from project config.

  Args:
    project-name - Project name (optional)

  Returns:
    Configuration map (merged with defaults)"
  [project-name]
  ;; TODO: Load from project config.edn → :optimization
  (default-optimization-config))
