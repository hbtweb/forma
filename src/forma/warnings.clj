(ns forma.warnings
  "Warning emission system for styling conflicts and edge cases.

  Integrates with forma.provenance.tracker to detect conflicts and emit
  configurable warnings during compilation. Supports:
  - Class conflict warnings (bg-blue-500 + bg-red-500)
  - Duplicate property warnings
  - Extension overlap warnings
  - Custom warning types

  Warnings can be configured per-project and per-component with levels:
  - :error - Halt compilation
  - :warn - Emit warning, continue
  - :info - Log informational message
  - :silent - Suppress warning"
  (:require [clojure.string :as str]
            [forma.provenance.tracker :as provenance]))

;; ============================================================================
;; Warning Types
;; ============================================================================

(def warning-types
  "Supported warning types and their default levels"
  {:class-conflict {:level :warn
                    :description "Conflicting CSS classes detected"}
   :duplicate-property {:level :warn
                       :description "Duplicate CSS properties detected"}
   :extension-overlap {:level :info
                      :description "Styling system extension overlap"}
   :token-resolution-failed {:level :warn
                            :description "Token reference could not be resolved"}
   :empty-class {:level :info
                :description "Empty or whitespace-only class string"}
   :missing-variant {:level :warn
                    :description "Requested variant not found in styling system"}
   :invalid-config {:level :error
                   :description "Invalid configuration value"}})

;; ============================================================================
;; Warning Record
;; ============================================================================

(defrecord Warning
  [type        ; Keyword - warning type from warning-types
   level       ; Keyword - :error :warn :info :silent
   message     ; String - human-readable warning message
   context     ; Map - contextual information (element, file, line)
   source      ; Map - source location {:file :line :column}
   suggestions ; Vector - suggested fixes
   metadata])  ; Map - additional metadata

(defn make-warning
  "Create a warning record"
  [type level message context & {:keys [source suggestions metadata]}]
  (->Warning type level message context source suggestions metadata))

;; ============================================================================
;; Warning Configuration
;; ============================================================================

(defn get-warning-level
  "Get effective warning level for a type given configuration.

  Precedence:
  1. Element-level override
  2. Component-level override
  3. Project-level override
  4. Default level"
  [warning-type element-config component-config project-config]
  (or
   (get-in element-config [:warnings warning-type :level])
   (get-in component-config [:warnings warning-type :level])
   (get-in project-config [:warnings warning-type :level])
   (get-in warning-types [warning-type :level])
   :warn))

(defn should-emit-warning?
  "Determine if warning should be emitted based on level and config"
  [warning-level effective-level]
  (let [level-priority {:silent 0 :info 1 :warn 2 :error 3}]
    (>= (level-priority warning-level 0)
        (level-priority effective-level 0))))

;; ============================================================================
;; Warning Formatters
;; ============================================================================

(defn format-warning
  "Format warning for display"
  [{:keys [type level message context source suggestions]}]
  (let [level-str (str/upper-case (name level))
        type-str (name type)
        context-str (when context
                     (str "\n  Context: " (pr-str context)))
        source-str (when source
                    (str "\n  Source: " (:file source) ":" (:line source)))
        suggestions-str (when (seq suggestions)
                         (str "\n  Suggestions:\n"
                              (str/join "\n"
                                       (map #(str "    - " %) suggestions))))]
    (str "[" level-str "] " type-str ": " message
         context-str
         source-str
         suggestions-str)))

(defn format-warning-summary
  "Format summary of all warnings"
  [warnings]
  (let [by-level (group-by :level warnings)
        error-count (count (get by-level :error []))
        warn-count (count (get by-level :warn []))
        info-count (count (get by-level :info []))]
    (str "=== WARNING SUMMARY ===\n"
         "Errors: " error-count "\n"
         "Warnings: " warn-count "\n"
         "Info: " info-count "\n"
         "Total: " (count warnings))))

;; ============================================================================
;; Class Conflict Warnings (Edge Case #7)
;; ============================================================================

(defn detect-class-conflicts
  "Detect conflicting CSS classes using provenance data.

  Uses forma.provenance.tracker/detect-class-conflicts to find conflicts,
  then emits warnings based on configuration."
  [provenance-entries element-config component-config project-config]
  (let [conflicts (provenance/detect-class-conflicts provenance-entries)]
    (for [conflict conflicts]
      (let [effective-level (get-warning-level :class-conflict
                                              element-config
                                              component-config
                                              project-config)
            {:keys [property-affected classes entries]} conflict
            element-paths (mapv :element-path entries)
            suggestions (cond
                         (= property-affected :background)
                         ["Remove one of the background color classes"
                          "Use explicit :background property instead"
                          "Check styling system for duplicate definitions"]

                         (= property-affected :text-color)
                         ["Remove one of the text color classes"
                          "Use explicit :color property instead"]

                         :else
                         ["Remove duplicate classes"
                          "Check styling system configuration"])]
        (make-warning
         :class-conflict
         effective-level
         (str "Conflicting classes for " (name property-affected) ": "
              (str/join ", " classes))
         {:property property-affected
          :classes classes
          :element-paths element-paths}
         :suggestions suggestions)))))

(defn detect-duplicate-properties
  "Detect duplicate CSS properties using provenance data"
  [provenance-entries element-config component-config project-config]
  (let [duplicates (provenance/detect-duplicate-properties provenance-entries)]
    (for [duplicate duplicates]
      (let [effective-level (get-warning-level :duplicate-property
                                              element-config
                                              component-config
                                              project-config)
            {:keys [property values entries]} duplicate
            sources (mapv :source-name entries)]
        (make-warning
         :duplicate-property
         effective-level
         (str "Duplicate CSS property " (name property) " with values: "
              (str/join ", " values))
         {:property property
          :values values
          :sources sources}
         :suggestions ["Remove duplicate definitions"
                      "Use configuration precedence to resolve"
                      "Check styling system stacking order"])))))

;; ============================================================================
;; Extension Overlap Warnings (Edge Case #5)
;; ============================================================================

(defn warn-extension-overlap
  "Emit warning for styling system extension overlap"
  [system-name extends-name project-config]
  (let [effective-level (get-warning-level :extension-overlap nil nil project-config)]
    (make-warning
     :extension-overlap
     effective-level
     (str "Styling system " system-name " extends " extends-name
          " which is already in the stack")
     {:system system-name
      :extends extends-name}
     :suggestions ["Remove duplicate base system from stack"
                  "Enable :dedupe-extensions? in configuration"
                  "Use extension instead of stacking"])))

;; ============================================================================
;; Token Resolution Warnings (Edge Case #3)
;; ============================================================================

(defn warn-token-resolution-failed
  "Emit warning when token reference cannot be resolved"
  [token-path element-path fallback-strategy project-config]
  (let [effective-level (get-warning-level :token-resolution-failed nil nil project-config)
        suggestions (case fallback-strategy
                     :warn-remove ["Define the token in global defaults"
                                  "Use a fallback: $token.path || value"
                                  "Check token path for typos"]
                     :warn-passthrough ["Define the token in global defaults"
                                       "Token reference will be kept in output"
                                       "May cause runtime errors"]
                     :error ["Define the token in global defaults"
                            "Token resolution is set to error mode"]
                     ["Define the token in global defaults"])]
    (make-warning
     :token-resolution-failed
     effective-level
     (str "Token reference " token-path " could not be resolved")
     {:token-path token-path
      :element-path element-path
      :fallback-strategy fallback-strategy}
     :suggestions suggestions)))

;; ============================================================================
;; Empty Class Warnings (Edge Case #2)
;; ============================================================================

(defn warn-empty-class
  "Emit warning for empty or whitespace-only class strings"
  [element-path project-config]
  (let [effective-level (get-warning-level :empty-class nil nil project-config)]
    (make-warning
     :empty-class
     effective-level
     "Empty or whitespace-only class string treated as no override"
     {:element-path element-path}
     :suggestions ["Remove :class key if no override intended"
                  "Use explicit nil to clear classes"
                  "Check for unintended whitespace"])))

;; ============================================================================
;; Variant Warnings
;; ============================================================================

(defn warn-missing-variant
  "Emit warning when requested variant not found"
  [component-type variant-key variant-value styling-system project-config]
  (let [effective-level (get-warning-level :missing-variant nil nil project-config)]
    (make-warning
     :missing-variant
     effective-level
     (str "Variant " variant-key " = " variant-value
          " not found in " styling-system " for " component-type)
     {:component-type component-type
      :variant-key variant-key
      :variant-value variant-value
      :styling-system styling-system}
     :suggestions ["Check variant name spelling"
                  "Verify styling system configuration"
                  "Add variant definition to styling system"])))

;; ============================================================================
;; Warning Collector
;; ============================================================================

(defprotocol WarningCollector
  "Protocol for collecting warnings during compilation"
  (add-warning [this warning] "Add a warning to the collector")
  (get-warnings [this] "Get all collected warnings")
  (get-warnings-by-level [this level] "Get warnings filtered by level")
  (get-warnings-by-type [this type] "Get warnings filtered by type")
  (clear-warnings [this] "Clear all warnings")
  (has-errors? [this] "Check if any errors were collected"))

(deftype AtomWarningCollector [warnings-atom]
  WarningCollector
  (add-warning [_ warning]
    (swap! warnings-atom conj warning)
    warning)

  (get-warnings [_]
    @warnings-atom)

  (get-warnings-by-level [_ level]
    (filter #(= (:level %) level) @warnings-atom))

  (get-warnings-by-type [_ type]
    (filter #(= (:type %) type) @warnings-atom))

  (clear-warnings [_]
    (reset! warnings-atom []))

  (has-errors? [_]
    (boolean (seq (filter #(= (:level %) :error) @warnings-atom)))))

(defn create-warning-collector
  "Create a new warning collector"
  []
  (->AtomWarningCollector (atom [])))

;; ============================================================================
;; Warning Emission
;; ============================================================================

(defn emit-warning
  "Emit a warning to the collector and optionally print"
  [collector warning & {:keys [print? print-fn] :or {print? false print-fn println}}]
  (add-warning collector warning)
  (when print?
    (print-fn (format-warning warning)))
  warning)

(defn emit-warnings
  "Emit multiple warnings"
  [collector warnings & opts]
  (doseq [warning warnings]
    (apply emit-warning collector warning opts))
  warnings)

(defn check-and-emit-warnings
  "Check provenance for conflicts and emit warnings.

  This is the main entry point for warning detection during compilation."
  [provenance-entries collector element-config component-config project-config]
  (let [class-conflicts (detect-class-conflicts provenance-entries
                                               element-config
                                               component-config
                                               project-config)
        duplicate-props (detect-duplicate-properties provenance-entries
                                                    element-config
                                                    component-config
                                                    project-config)
        all-warnings (concat class-conflicts duplicate-props)]
    (emit-warnings collector all-warnings)
    all-warnings))

;; ============================================================================
;; Warning Report
;; ============================================================================

(defn generate-warning-report
  "Generate a comprehensive warning report"
  [collector & {:keys [include-silent?] :or {include-silent? false}}]
  (let [warnings (if include-silent?
                  (get-warnings collector)
                  (remove #(= (:level %) :silent) (get-warnings collector)))
        by-level (group-by :level warnings)
        by-type (group-by :type warnings)]
    {:summary (format-warning-summary warnings)
     :by-level by-level
     :by-type by-type
     :total-count (count warnings)
     :error-count (count (get by-level :error []))
     :warn-count (count (get by-level :warn []))
     :info-count (count (get by-level :info []))
     :warnings warnings}))

(defn print-warning-report
  "Print formatted warning report"
  [collector & opts]
  (let [report (apply generate-warning-report collector opts)
        {:keys [summary warnings]} report]
    (println summary)
    (println)
    (doseq [warning warnings]
      (println (format-warning warning))
      (println))))
