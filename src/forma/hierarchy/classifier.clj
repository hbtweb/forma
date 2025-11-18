(ns forma.hierarchy.classifier
  "Classify properties by hierarchy level based on heuristics and metadata.

  This module determines which hierarchy level (:global, :components, :sections,
  :templates, or :pages) each property should belong to when reconstructing a
  multi-file Forma project from flattened HTML/JSX.

  Classification strategies:
  1. Metadata-driven (from sync mode) - highest confidence
  2. Frequency-based (used across many elements) - medium confidence
  3. Variance-based (value consistency) - medium confidence
  4. Semantic analysis (property type) - low confidence"
  (:require [clojure.string :as str]
            [clojure.set :as set]))

;;
;; Core Classification
;;

(defn classify-property
  "Classify a single property based on usage patterns and metadata.

  Args:
    property-key   - Property keyword (e.g., :background, :padding)
    property-value - Property value (e.g., \"#4f46e5\", \"1rem\")
    element-type   - Element type keyword (e.g., :button, :input)
    usage-stats    - Usage statistics map (from build-usage-statistics)
    metadata       - Metadata map (from forma.sync.metadata)

  Returns:
    {:level :global | :components | :sections | :templates | :pages
     :confidence 0.0-1.0
     :reason \"...\"}

  Classification priority:
  1. Metadata hints (if available) - 100% confidence
  2. Token references - 95% confidence → :global
  3. High frequency + consistent value - 80% confidence → :global
  4. Base classes (all instances) - 90% confidence → :components
  5. Variant classes (grouped) - 85% confidence → :components
  6. Unique values - 100% confidence → :pages"
  [property-key property-value element-type usage-stats metadata]
  (cond
    ;; 1. Metadata-driven classification (highest confidence)
    (get-in metadata [:property-sources property-key :level])
    {:level (get-in metadata [:property-sources property-key :level])
     :confidence 1.0
     :reason "Explicit metadata from sync mode"}

    ;; 2. Token provenance (original token reference preserved)
    (get-in metadata [:token-provenance property-key])
    {:level :global
     :confidence 0.95
     :reason (str "Token reference: " (get-in metadata [:token-provenance property-key]))}

    ;; 3. Frequency-based classification
    :else
    (let [prop-stats (get-in usage-stats [:properties property-key])
          frequency (:frequency prop-stats 0)
          total-elements (get-in usage-stats [:elements element-type :count] 1)
          frequency-ratio (/ frequency total-elements)
          value-variance (:variance prop-stats 0)
          unique-values (count (get prop-stats :values #{}))
          is-consistent? (<= unique-values 3)]  ; ≤3 unique values = consistent

      (cond
        ;; Class property with high frequency → :components (base class)
        ;; This check must come BEFORE general frequency checks
        (and (= property-key :class)
             (>= frequency-ratio 0.8))
        {:level :components
         :confidence 0.9
         :reason "Base class (present in 80%+ instances)"}

        ;; High frequency + consistent value → :global (likely token)
        (and (>= frequency-ratio 0.5)
             is-consistent?)
        {:level :global
         :confidence 0.8
         :reason (format "High frequency (%.0f%%) with consistent value (%d unique)"
                         (* (double frequency-ratio) 100) unique-values)}

        ;; Medium frequency + consistent → :components (component default)
        (and (>= frequency-ratio 0.3)
             is-consistent?)
        {:level :components
         :confidence 0.7
         :reason (format "Medium frequency (%.0f%%) with consistent value"
                         (* (double frequency-ratio) 100))}

        ;; High variance → :pages (instance-specific)
        (>= unique-values (* frequency 0.7))  ; 70%+ unique values
        {:level :pages
         :confidence 0.9
         :reason (format "High variance (%d unique values / %d uses)"
                         unique-values frequency)}

        ;; Default: instance-specific
        :else
        {:level :pages
         :confidence 0.6
         :reason "Default classification (insufficient data)"}))))

(defn classify-element-properties
  "Classify all properties in an element.

  Args:
    element      - Forma EDN element map
    usage-stats  - Usage statistics map (from build-usage-statistics)
    metadata     - Element metadata map (from forma.sync.metadata)

  Returns:
    {:global {...}       ; Properties classified as :global
     :components {...}   ; Properties classified as :components
     :sections {...}     ; Properties classified as :sections
     :templates {...}    ; Properties classified as :templates
     :pages {...}        ; Properties classified as :pages
     :classifications {...}}  ; Full classification details

  Example:
    (classify-element-properties
      {:type :button :background \"#4f46e5\" :padding \"1rem\" :text \"Submit\"}
      usage-stats
      metadata)
    ;; => {:global {:background \"#4f46e5\" :padding \"1rem\"}
    ;;     :pages {:text \"Submit\"}
    ;;     :classifications {...}}"
  [element usage-stats metadata]
  (let [element-type (:type element)
        properties (dissoc element :type :children :content)  ; Remove structural keys
        classifications
        (reduce-kv
         (fn [acc prop-key prop-value]
           (let [classification (classify-property prop-key prop-value element-type
                                                   usage-stats metadata)]
             (assoc acc prop-key classification)))
         {}
         properties)]

    ;; Group by level
    (reduce-kv
     (fn [acc prop-key classification]
       (let [level (:level classification)
             prop-value (get properties prop-key)]
         (-> acc
             (assoc-in [level prop-key] prop-value)
             (assoc-in [:classifications prop-key] classification))))
     {:global {}
      :components {}
      :sections {}
      :templates {}
      :pages {}
      :classifications {}}
     classifications)))

;;
;; Usage Statistics
;;

(defn extract-elements
  "Recursively extract all elements from nested structure.

  Handles both map-based elements {:type :button ...} and Hiccup vectors [:button {:} ...].
  Returns: [{:type :button :background \"#fff\" ...} ...]"
  [edn-data]
  (cond
    ;; Map with :type key → element
    (and (map? edn-data) (contains? edn-data :type))
    (cons edn-data (mapcat extract-elements (:children edn-data [])))

    ;; Hiccup vector [:button {...} ...] → element
    (and (vector? edn-data)
         (keyword? (first edn-data)))
    (let [[tag props & children] edn-data
          props-map (if (map? props) props {})
          element (assoc props-map :type tag)]
      (cons element (mapcat extract-elements children)))

    ;; Map without :type → recurse into values
    (map? edn-data)
    (mapcat extract-elements (vals edn-data))

    ;; Sequential (list, etc.) → recurse into elements
    (sequential? edn-data)
    (mapcat extract-elements edn-data)

    ;; Scalar → no elements
    :else []))

(defn- calculate-variance
  "Calculate variance of values (0.0 = all same, 1.0 = all different)"
  [values]
  (let [unique-count (count (set values))
        total-count (count values)]
    (if (zero? total-count)
      0.0
      (double (/ unique-count total-count)))))

(defn build-usage-statistics
  "Analyze flattened structure to build usage statistics.

  Args:
    flattened-edn - Flattened Forma EDN (parsed from HTML/JSX)

  Returns:
    {:properties {property-key {:frequency 25          ; # times property appears
                                :values #{\"#fff\" \"#000\"} ; Set of unique values
                                :variance 0.7}}        ; Value variance (0-1)
     :elements {element-type {:count 10}}}             ; # of each element type

  Example:
    (build-usage-statistics
      {:page {:content [[:button {:background \"#fff\"}]
                        [:button {:background \"#fff\"}]
                        [:button {:background \"#000\"}]]}})
    ;; => {:properties {:background {:frequency 3
    ;;                               :values #{\"#fff\" \"#000\"}
    ;;                               :variance 0.67}}
    ;;     :elements {:button {:count 3}}}"
  [flattened-edn]
  (let [elements (extract-elements flattened-edn)

        ;; Count element types
        element-counts
        (reduce
         (fn [acc elem]
           (let [elem-type (:type elem)]
             (update-in acc [elem-type :count] (fnil inc 0))))
         {}
         elements)

        ;; Collect property usage
        property-usage
        (reduce
         (fn [acc elem]
           (let [props (dissoc elem :type :children :content)]
             (reduce-kv
              (fn [acc2 prop-key prop-value]
                (-> acc2
                    (update-in [prop-key :frequency] (fnil inc 0))
                    (update-in [prop-key :values] (fnil conj #{}) prop-value)
                    (update-in [prop-key :all-values] (fnil conj []) prop-value)))
              acc
              props)))
         {}
         elements)

        ;; Calculate variance for each property
        property-stats
        (reduce-kv
         (fn [acc prop-key stats]
           (let [all-values (:all-values stats [])
                 variance (calculate-variance all-values)]
             (assoc acc prop-key (-> stats
                                     (assoc :variance variance)
                                     (dissoc :all-values)))))
         {}
         property-usage)]

    {:properties property-stats
     :elements element-counts}))

;;
;; Helper Functions
;;

(defn get-classification-level
  "Get the classification level for a property (convenience fn).

  Returns: :global | :components | :sections | :templates | :pages"
  [property-key classified-properties]
  (get-in classified-properties [:classifications property-key :level]))

(defn get-classification-confidence
  "Get the classification confidence for a property.

  Returns: 0.0-1.0"
  [property-key classified-properties]
  (get-in classified-properties [:classifications property-key :confidence]))

(defn filter-by-level
  "Filter classified properties by hierarchy level.

  Args:
    classified-properties - Result from classify-element-properties
    level                 - :global | :components | :sections | :templates | :pages

  Returns: Map of properties at specified level"
  [classified-properties level]
  (get classified-properties level {}))

(defn filter-by-confidence
  "Filter classified properties by minimum confidence threshold.

  Args:
    classified-properties - Result from classify-element-properties
    min-confidence        - Minimum confidence (0.0-1.0)

  Returns: Map of properties meeting confidence threshold"
  [classified-properties min-confidence]
  (let [classifications (:classifications classified-properties)]
    (reduce-kv
     (fn [acc prop-key classification]
       (if (>= (:confidence classification) min-confidence)
         (let [level (:level classification)]
           (assoc-in acc [level prop-key]
                     (get-in classified-properties [level prop-key])))
         acc))
     {:global {}
      :components {}
      :sections {}
      :templates {}
      :pages {}}
     classifications)))

(defn summarize-classifications
  "Generate summary statistics of classifications.

  Returns:
    {:total 25
     :by-level {:global 5 :components 8 :pages 12}
     :by-confidence {:high 20 :medium 3 :low 2}}"
  [classified-properties]
  (let [classifications (:classifications classified-properties)
        total (count classifications)

        by-level
        (reduce-kv
         (fn [acc _ classification]
           (update acc (:level classification) (fnil inc 0)))
         {}
         classifications)

        by-confidence
        (reduce-kv
         (fn [acc _ classification]
           (let [conf (:confidence classification)
                 category (cond
                            (>= conf 0.8) :high
                            (>= conf 0.6) :medium
                            :else :low)]
             (update acc category (fnil inc 0))))
         {:high 0 :medium 0 :low 0}
         classifications)]

    {:total total
     :by-level by-level
     :by-confidence by-confidence}))

;;
;; Pretty Printing
;;

(defn format-classification-report
  "Generate human-readable classification report.

  Returns: String report (Markdown format)"
  [classified-properties]
  (let [summary (summarize-classifications classified-properties)
        classifications (:classifications classified-properties)]

    (str
     "# Property Classification Report\n\n"

     "## Summary\n"
     (format "- **Total properties**: %d\n" (:total summary))
     (format "- **Global**: %d\n" (get-in summary [:by-level :global] 0))
     (format "- **Components**: %d\n" (get-in summary [:by-level :components] 0))
     (format "- **Sections**: %d\n" (get-in summary [:by-level :sections] 0))
     (format "- **Templates**: %d\n" (get-in summary [:by-level :templates] 0))
     (format "- **Pages**: %d\n\n" (get-in summary [:by-level :pages] 0))

     "## Confidence Distribution\n"
     (format "- **High (≥80%%)**: %d\n" (get-in summary [:by-confidence :high] 0))
     (format "- **Medium (60-79%%)**: %d\n" (get-in summary [:by-confidence :medium] 0))
     (format "- **Low (<60%%)**: %d\n\n" (get-in summary [:by-confidence :low] 0))

     "## Detailed Classifications\n\n"
     (->> classifications
          (sort-by (fn [[_ c]] [(name (:level c)) (:confidence c)]))
          (map (fn [[prop-key classification]]
                 (format "- `%s` → **%s** (%.0f%% confidence)\n  - %s\n"
                         (name prop-key)
                         (name (:level classification))
                         (* 100 (:confidence classification))
                         (:reason classification))))
          (str/join "\n")))))
