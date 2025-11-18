(ns forma.inheritance.tracking
  "Explicit property tracking for CSS extraction.

  Edge Case #9: Track which CSS properties were set explicitly vs inherited.
  This enables :only-extract-explicit mode where only explicitly-set properties
  are extracted to inline styles.

  Key features:
  - Track property source (explicit vs inherited from hierarchy level)
  - Support :only-extract-explicit? configuration option
  - Per-property overrides with :always-extract list
  - Integration with inheritance resolution pipeline")

;; ============================================================================
;; Property Source Tracking
;; ============================================================================

(defrecord PropertySource
  [property     ; Keyword - property name
   value        ; Any - property value
   source-type  ; Keyword - :explicit :inherited
   source-level ; Keyword - hierarchy level (:global :components :sections :templates :pages)
   source-file  ; String - source file path (optional)
   overrides])  ; Map - what this property overrode (optional)

(defn make-property-source
  "Create a property source record"
  [property value source-type source-level & {:keys [source-file overrides]}]
  (->PropertySource property value source-type source-level source-file overrides))

;; ============================================================================
;; Property Tracking Protocol
;; ============================================================================

(defprotocol PropertyTracker
  "Protocol for tracking property sources during inheritance resolution"
  (track-property [this property value source-type source-level opts]
    "Track a property with its source information")
  (get-property-source [this property]
    "Get source information for a property")
  (get-explicit-properties [this]
    "Get all explicitly-set properties")
  (get-inherited-properties [this]
    "Get all inherited properties")
  (is-explicit? [this property]
    "Check if property was explicitly set")
  (get-all-tracked [this]
    "Get all tracked properties"))

(deftype AtomPropertyTracker [tracking-atom]
  PropertyTracker
  (track-property [_ property value source-type source-level opts]
    (let [source (make-property-source property value source-type source-level
                                      :source-file (:source-file opts)
                                      :overrides (:overrides opts))]
      (swap! tracking-atom assoc property source)
      source))

  (get-property-source [_ property]
    (get @tracking-atom property))

  (get-explicit-properties [_]
    (filter #(= (:source-type %) :explicit) (vals @tracking-atom)))

  (get-inherited-properties [_]
    (filter #(= (:source-type %) :inherited) (vals @tracking-atom)))

  (is-explicit? [_ property]
    (= (:source-type (get @tracking-atom property)) :explicit))

  (get-all-tracked [_]
    (vals @tracking-atom)))

(defn create-property-tracker
  "Create a new property tracker"
  []
  (->AtomPropertyTracker (atom {})))

;; ============================================================================
;; Inheritance Resolution with Tracking
;; ============================================================================

(defn merge-with-tracking
  "Deep merge two maps while tracking property sources.

  Arguments:
  - left: left map (lower priority)
  - right: right map (higher priority)
  - tracker: PropertyTracker instance
  - source-level: hierarchy level of right map
  - source-type: :explicit or :inherited

  Returns: merged map"
  [left right tracker source-level source-type]
  (reduce-kv
   (fn [acc k v]
     (let [left-val (get left k)
           merged-val (if (and (map? left-val) (map? v))
                       ;; Recursive merge for nested maps
                       (merge-with-tracking left-val v tracker source-level source-type)
                       ;; Right value wins for scalars
                       v)]
       ;; Track the property
       (when tracker
         (track-property tracker k merged-val source-type source-level
                        {:overrides (when (and left-val (not= left-val v))
                                     {:previous-value left-val})}))
       (assoc acc k merged-val)))
   left
   right))

(defn resolve-with-tracking
  "Resolve inheritance hierarchy with property tracking.

  Arguments:
  - hierarchy-data: vector of [level-key data] pairs in precedence order
    Example: [[:global {...}] [:components {...}] [:pages {...}]]
  - explicit-props: explicitly-set properties (highest priority)
  - tracker: PropertyTracker instance (optional)

  Returns: resolved properties map"
  [hierarchy-data explicit-props tracker]
  (let [;; Merge inherited levels
        inherited (reduce
                   (fn [acc [level-key data]]
                     (merge-with-tracking acc data tracker level-key :inherited))
                   {}
                   hierarchy-data)
        ;; Merge explicit properties (highest priority)
        final (merge-with-tracking inherited explicit-props tracker :explicit :explicit)]
    final))

;; ============================================================================
;; CSS Extraction with Explicit Tracking
;; ============================================================================

(def css-properties
  "Set of CSS properties that can be extracted to inline styles"
  #{:background :background-color :color :font-size :font-weight :font-family
    :padding :padding-top :padding-right :padding-bottom :padding-left
    :margin :margin-top :margin-right :margin-bottom :margin-left
    :border :border-color :border-width :border-radius
    :width :height :min-width :min-height :max-width :max-height
    :display :flex-direction :justify-content :align-items
    :position :top :right :bottom :left
    :z-index :opacity :transform :transition})

(defn is-css-property?
  "Check if property is a CSS property"
  [property]
  (contains? css-properties property))

(defn filter-explicit-properties
  "Filter properties to only include explicitly-set ones.

  Edge Case #9: :only-extract-explicit mode.

  Arguments:
  - props: properties map
  - tracker: PropertyTracker instance
  - opts: extraction options
    - :only-extract-explicit? - Only extract explicit properties (default: false)
    - :always-extract - Vector of properties to always extract (even if inherited)
    - :css-properties-only? - Only extract CSS properties (default: true)

  Returns: filtered properties map"
  [props tracker opts]
  (let [only-explicit? (get opts :only-extract-explicit? false)
        always-extract (set (get opts :always-extract []))
        css-only? (get opts :css-properties-only? true)]
    (if (and only-explicit? tracker)
      ;; Filter to explicit or always-extract properties
      (into {}
            (filter (fn [[k v]]
                     (and (or (not css-only?) (is-css-property? k))
                          (or (is-explicit? tracker k)
                              (contains? always-extract k))))
                   props))
      ;; No filtering, return all CSS properties
      (if css-only?
        (into {} (filter (fn [[k _]] (is-css-property? k)) props))
        props))))

(defn extract-css-properties
  "Extract CSS properties for inline style generation.

  Edge Case #9: Respects :only-extract-explicit? configuration.

  Arguments:
  - props: properties map
  - tracker: PropertyTracker instance (optional)
  - opts: extraction options (see filter-explicit-properties)

  Returns: map of CSS properties to extract"
  [props tracker opts]
  (filter-explicit-properties props tracker opts))

;; ============================================================================
;; Property Metadata
;; ============================================================================

(defn attach-property-metadata
  "Attach property tracking metadata to props.

  Useful for debugging and tooling to see which properties came from where.

  Arguments:
  - props: properties map
  - tracker: PropertyTracker instance

  Returns: props with :meta :property-sources attached"
  [props tracker]
  (if tracker
    (assoc-in props [:meta :property-sources]
              (into {}
                    (map (fn [src]
                           [(:property src)
                            {:source-type (:source-type src)
                             :source-level (:source-level src)
                             :source-file (:source-file src)}])
                         (get-all-tracked tracker))))
    props))

;; ============================================================================
;; Reporting
;; ============================================================================

(defn property-source-report
  "Generate a report of property sources.

  Arguments:
  - tracker: PropertyTracker instance

  Returns: formatted string report"
  [tracker]
  (let [all-props (get-all-tracked tracker)
        explicit (get-explicit-properties tracker)
        inherited (get-inherited-properties tracker)
        by-level (group-by :source-level all-props)]
    (str "=== PROPERTY SOURCE REPORT ===\n"
         "Total Properties: " (count all-props) "\n"
         "Explicit: " (count explicit) "\n"
         "Inherited: " (count inherited) "\n"
         "\nBy Level:\n"
         (apply str
                (for [[level props] by-level]
                  (str "  " (name level) ": " (count props) " properties\n")))
         "\nExplicit Properties:\n"
         (apply str
                (for [prop explicit]
                  (str "  " (:property prop) " = " (:value prop) "\n")))
         "\nInherited Properties:\n"
         (apply str
                (for [prop inherited]
                  (str "  " (:property prop) " = " (:value prop)
                       " (from " (name (:source-level prop)) ")\n"))))))

(defn property-extraction-preview
  "Preview what properties would be extracted with given options.

  Arguments:
  - props: properties map
  - tracker: PropertyTracker instance
  - opts: extraction options

  Returns: {:extracted {...} :excluded {...}}"
  [props tracker opts]
  (let [extracted (extract-css-properties props tracker opts)
        all-css (filter-explicit-properties props tracker (assoc opts :only-extract-explicit? false))
        excluded (into {} (filter (fn [[k _]] (not (contains? extracted k))) all-css))]
    {:extracted extracted
     :excluded excluded
     :extraction-mode (if (get opts :only-extract-explicit?) :explicit-only :all)
     :total-css-props (count all-css)
     :extracted-count (count extracted)
     :excluded-count (count excluded)}))

;; ============================================================================
;; Integration Helpers
;; ============================================================================

(defn should-create-tracker?
  "Determine if property tracking should be enabled.

  Arguments:
  - context: compilation context

  Returns: boolean"
  [context]
  (or (get-in context [:styling-options :only-extract-explicit?] false)
      (get-in context [:styling-options :track-property-sources?] false)
      (get-in context [:debug :track-properties?] false)))

(defn create-tracker-if-needed
  "Create property tracker if needed based on context.

  Arguments:
  - context: compilation context

  Returns: PropertyTracker instance or nil"
  [context]
  (when (should-create-tracker? context)
    (create-property-tracker)))
