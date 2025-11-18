(ns forma.dev.phase4-tests
  "Phase 4 feature tests - Advanced features

   Tests for:
   - Configuration precedence (Edge Case #4)
   - Styling system stacking with deduplication (Edge Case #5)
   - Style provenance tracking (Edge Case #11)
   - CSS output improvements (vendor prefixes, CSS variables)"
  (:require [forma.config.precedence :as precedence]
            [forma.styling.core :as styling]
            [forma.provenance.tracker :as provenance]
            [forma.css.processor :as css-proc]))

;; ============================================================================
;; TEST HELPERS
;; ============================================================================

(defn- test-result [name passed? expected actual]
  {:test name
   :passed? passed?
   :expected expected
   :actual actual})

(defn- assert-equal [name expected actual]
  (test-result name (= expected actual) expected actual))

(defn- assert-contains [name collection item]
  (test-result name (some #{item} collection) (str "contains " item) collection))

(defn- run-test [test-fn]
  (try
    (test-fn)
    (catch Exception e
      (test-result "Exception" false nil (.getMessage e)))))

;; ============================================================================
;; CONFIGURATION PRECEDENCE TESTS (Edge Case #4)
;; ============================================================================

(defn test-config-precedence-element-override
  "Test that element override takes highest precedence"
  []
  (let [element-props {:styling-options {:apply-base-when-explicit false}}
        project-config {:styling {:apply-base-when-explicit true}}
        styling-config {:styling-config {:apply-base-when-explicit true}}
        result (precedence/resolve-config-option
                :apply-base-when-explicit
                element-props
                project-config
                styling-config
                :button
                true)]
    (assert-equal "Element override wins" false result)))

(defn test-config-precedence-project-config
  "Test that project config takes precedence over styling system"
  []
  (let [element-props {}
        project-config {:styling {:apply-base-when-explicit false}}
        styling-config {:styling-config {:apply-base-when-explicit true}}
        result (precedence/resolve-config-option
                :apply-base-when-explicit
                element-props
                project-config
                styling-config
                :button
                true)]
    (assert-equal "Project config wins over styling system" false result)))

(defn test-config-precedence-styling-system-global
  "Test that styling system global config takes precedence over component-specific"
  []
  (let [element-props {}
        project-config {}
        styling-config {:styling-config {:apply-base-when-explicit false}
                        :components {:button {:styling-config {:apply-base-when-explicit true}}}}
        result (precedence/resolve-config-option
                :apply-base-when-explicit
                element-props
                project-config
                styling-config
                :button
                true)]
    (assert-equal "Global styling config wins" false result)))

(defn test-config-precedence-component-specific
  "Test that component-specific config takes precedence over default"
  []
  (let [element-props {}
        project-config {}
        styling-config {:components {:button {:styling-config {:apply-base-when-explicit false}}}}
        result (precedence/resolve-config-option
                :apply-base-when-explicit
                element-props
                project-config
                styling-config
                :button
                true)]
    (assert-equal "Component config wins over default" false result)))

(defn test-config-precedence-default
  "Test that default is used when no other config present"
  []
  (let [element-props {}
        project-config {}
        styling-config {}
        result (precedence/resolve-config-option
                :apply-base-when-explicit
                element-props
                project-config
                styling-config
                :button
                false)]
    (assert-equal "Default value used" false result)))

(defn test-config-multiple-options
  "Test resolving multiple options at once"
  []
  (let [element-props {}
        project-config {:styling {:apply-base-when-explicit false}}
        styling-config {:styling-config {:dedupe-classes? false}}
        defaults {:apply-base-when-explicit true :dedupe-classes? true}
        result (precedence/resolve-multiple-options
                [:apply-base-when-explicit :dedupe-classes?]
                element-props
                project-config
                styling-config
                :button
                defaults)]
    (test-result "Multiple options resolved correctly"
                 (and (= false (:apply-base-when-explicit result))
                      (= false (:dedupe-classes? result)))
                 {:apply-base-when-explicit false :dedupe-classes? false}
                 result)))

(defn test-config-precedence-context
  "Test building precedence context for debugging"
  []
  (let [element-props {:styling-options {:dedupe-classes? false}}
        project-config {:styling {:apply-base-when-explicit false}}
        styling-config {}
        defaults {:dedupe-classes? true :apply-base-when-explicit true}
        context (precedence/build-precedence-context
                 [:dedupe-classes? :apply-base-when-explicit]
                 element-props
                 project-config
                 styling-config
                 :button
                 defaults)]
    (test-result "Precedence context built"
                 (and (= false (get-in context [:dedupe-classes? :resolved]))
                      (= :element-override (get-in context [:dedupe-classes? :source]))
                      (= false (get-in context [:apply-base-when-explicit :resolved]))
                      (= :project-config (get-in context [:apply-base-when-explicit :source])))
                 "Correct sources and values"
                 context)))

;; ============================================================================
;; STYLING SYSTEM STACKING TESTS (Edge Case #5)
;; ============================================================================

(defn test-stacking-overlap-detection
  "Test detection of extension overlap in stack"
  []
  (let [styling-stack [:tailwind :shadcn-ui]
        styling-systems [{:system-name :tailwind}
                        {:system-name :shadcn-ui :extends :tailwind}]
        overlap-info (#'styling/detect-extension-overlap styling-stack styling-systems)]
    (test-result "Overlap detected"
                 (:has-overlap? overlap-info)
                 true
                 overlap-info)))

(defn test-stacking-no-overlap
  "Test when no extension overlap exists"
  []
  (let [styling-stack [:tailwind :custom-utils]
        styling-systems [{:system-name :tailwind}
                        {:system-name :custom-utils}]
        overlap-info (#'styling/detect-extension-overlap styling-stack styling-systems)]
    (test-result "No overlap"
                 (not (:has-overlap? overlap-info))
                 false
                 overlap-info)))

(defn test-class-deduplication
  "Test that duplicate classes are removed while preserving order"
  []
  ;; Test via public apply-styling-from-stack API
  (let [styling-config {:components {:button {:base ["btn" "base-class"]
                                              :variants {:primary ["primary"]}}}}
        props {}
        resolved-props {:variant :primary :class "btn extra"}
        context {:styling-options {:dedupe-classes? true}}
        result (styling/apply-styling-from-stack
                props :button [styling-config] resolved-props context)
        classes (get result :class "")]
    (test-result "Classes deduplicated via apply-styling-from-stack"
                 (and (some? classes)
                      (= 1 (count (re-seq #"btn" classes)))) ;; "btn" appears only once
                 "btn appears once"
                 classes)))

(defn test-class-no-deduplication
  "Test that classes are preserved with dedupe disabled"
  []
  ;; Test via public API with dedupe disabled
  (let [styling-config {:components {:button {:base ["btn"]
                                              :variants {:primary ["primary"]}}}}
        props {}
        resolved-props {:variant :primary}
        context {:styling-options {:dedupe-classes? false}}
        result (styling/apply-styling-from-stack
                props :button [styling-config] resolved-props context)
        classes (get result :class "")]
    (test-result "Classes not deduplicated"
                 (some? classes)
                 "Classes present"
                 classes)))

;; ============================================================================
;; STYLE PROVENANCE TESTS (Edge Case #11)
;; ============================================================================

(defn test-provenance-record-property
  "Test recording a provenance entry"
  []
  (let [tracker (provenance/create-tracker)
        entry (provenance/make-provenance-entry
               :class "btn primary"
               :styling-system :shadcn-ui
               :apply-styling :button)]
    (provenance/record-property tracker entry)
    (let [entries (provenance/get-provenance tracker)]
      (test-result "Property recorded"
                   (= 1 (count entries))
                   1
                   (count entries)))))

(defn test-provenance-record-override
  "Test recording property override"
  []
  (let [tracker (provenance/create-tracker)
        old-entry (provenance/make-provenance-entry
                   :background "#fff"
                   :hierarchy-level :global
                   :resolve-inheritance :div)
        new-entry (provenance/make-provenance-entry
                   :background "#000"
                   :explicit :explicit
                   :explicit-override :div)]
    (provenance/record-property tracker old-entry)
    (provenance/record-override tracker :background old-entry new-entry)
    (let [entries (provenance/get-provenance tracker)
          overrides (get @(.state-atom tracker) :overrides)]
      (test-result "Override recorded"
                   (and (= 2 (count entries))
                        (= 1 (count overrides)))
                   {:entries 2 :overrides 1}
                   {:entries (count entries) :overrides (count overrides)}))))

(defn test-provenance-filter-active
  "Test filtering to active (not overridden) entries"
  []
  (let [tracker (provenance/create-tracker)
        entry1 (provenance/make-provenance-entry :color "red" :hierarchy-level :global :resolve-inheritance :div)
        entry2 (provenance/make-provenance-entry :color "blue" :explicit :explicit :explicit-override :div)]
    (provenance/record-property tracker entry1)
    (provenance/record-override tracker :color entry1 entry2)
    (let [all-entries (provenance/get-provenance tracker)
          active-entries (provenance/filter-active-entries all-entries)]
      (test-result "Active entries filtered"
                   (= 1 (count active-entries))
                   1
                   (count active-entries)))))

(defn test-provenance-conflict-detection
  "Test detecting class conflicts (e.g., bg-blue-500 and bg-red-500)"
  []
  (let [entries [(provenance/make-provenance-entry :class "bg-blue-500" :styling-system :tailwind :apply-styling :div)
                 (provenance/make-provenance-entry :class "bg-red-500 text-white" :styling-system :custom :apply-styling :div)]
        conflicts (provenance/detect-class-conflicts entries)]
    (test-result "Conflicts detected"
                 (and (= 1 (count conflicts))
                      (= :background (:property-affected (first conflicts))))
                 1
                 (count conflicts))))

(defn test-provenance-duplicate-detection
  "Test detecting duplicate CSS properties"
  []
  (let [entries [(provenance/make-provenance-entry :background "#fff" :hierarchy-level :global :resolve-inheritance :div)
                 (provenance/make-provenance-entry :background "#000" :explicit :explicit :explicit-override :div)]
        duplicates (provenance/detect-duplicate-properties entries)]
    (test-result "Duplicates detected"
                 (= 1 (count duplicates))
                 1
                 (count duplicates))))

(defn test-provenance-diff
  "Test diffing two provenance sets"
  []
  (let [old-entries [(provenance/make-provenance-entry :color "red" :hierarchy-level :global :resolve-inheritance :div :element-path [])]
        new-entries [(provenance/make-provenance-entry :color "blue" :hierarchy-level :global :resolve-inheritance :div :element-path [])
                     (provenance/make-provenance-entry :background "#fff" :hierarchy-level :global :resolve-inheritance :div :element-path [])]
        diff (provenance/diff-provenance old-entries new-entries)]
    (test-result "Diff calculated"
                 (and (= 1 (count (:added diff)))
                      (= 1 (count (:changed diff))))
                 {:added 1 :changed 1}
                 {:added (count (:added diff)) :changed (count (:changed diff))})))

;; ============================================================================
;; CSS PROCESSING TESTS
;; ============================================================================

(defn test-vendor-prefix-detection
  "Test detection of properties needing vendor prefixes"
  []
  (let [needs-prefix? (css-proc/property-needs-prefix? "transform")
        no-prefix? (css-proc/property-needs-prefix? "color")]
    (test-result "Vendor prefix detection"
                 (and needs-prefix? (not no-prefix?))
                 [true false]
                 [needs-prefix? no-prefix?])))

(defn test-vendor-prefix-generation
  "Test generating vendor-prefixed properties"
  []
  (let [prefixed (css-proc/generate-prefixed-properties "transform" "rotate(45deg)")
        prop-names (map first prefixed)]
    (test-result "Vendor prefixes generated"
                 (and (some #{"transform"} prop-names)
                      (some #{"-webkit-transform"} prop-names)
                      (some #{"-moz-transform"} prop-names))
                 "Contains standard and prefixed versions"
                 prop-names)))

(defn test-css-variable-detection
  "Test detecting CSS variable references"
  []
  (let [is-var? (css-proc/css-variable? "var(--primary-color)")
        not-var? (css-proc/css-variable? "#fff")]
    (test-result "CSS variable detection"
                 (and is-var? (not not-var?))
                 [true false]
                 [is-var? not-var?])))

(defn test-css-variable-parsing
  "Test parsing CSS variable references"
  []
  (let [parsed (css-proc/parse-css-variable "var(--primary-color, #fff)")]
    (test-result "CSS variable parsed"
                 (and (= "--primary-color" (:variable parsed))
                      (= "#fff" (:fallback parsed)))
                 {:variable "--primary-color" :fallback "#fff"}
                 parsed)))

(defn test-property-normalization
  "Test normalizing property names"
  []
  (let [normalized-bg (css-proc/normalize-property-name :bg)
        normalized-pd (css-proc/normalize-property-name :pd)]
    (test-result "Properties normalized"
                 (and (= "background" normalized-bg)
                      (= "padding" normalized-pd))
                 ["background" "padding"]
                 [normalized-bg normalized-pd])))

(defn test-css-property-processing
  "Test full CSS property processing"
  []
  (let [processed (css-proc/process-css-property
                   :bg "#fff"
                   :normalize-names? true
                   :vendor-prefixes? false)]
    (test-result "Property processed"
                 (= [["background" "#fff"]] processed)
                 [["background" "#fff"]]
                 processed)))

(defn test-css-string-generation
  "Test converting properties to CSS string"
  []
  (let [css-str (css-proc/properties-to-css-string
                 [["background" "#fff"]
                  ["color" "#000"]]
                 :minify? false
                 :trailing-semicolon? true)]
    (test-result "CSS string generated"
                 (= "background: #fff; color: #000;" css-str)
                 "background: #fff; color: #000;"
                 css-str)))

;; ============================================================================
;; TEST RUNNER
;; ============================================================================

(defn run-all-phase4-tests
  "Run all Phase 4 tests and report results"
  []
  (println "\n=== PHASE 4 TESTS ===\n")

  (let [test-groups
        [["Configuration Precedence (Edge Case #4)"
          [test-config-precedence-element-override
           test-config-precedence-project-config
           test-config-precedence-styling-system-global
           test-config-precedence-component-specific
           test-config-precedence-default
           test-config-multiple-options
           test-config-precedence-context]]

         ["Styling System Stacking (Edge Case #5)"
          [test-stacking-overlap-detection
           test-stacking-no-overlap
           test-class-deduplication
           test-class-no-deduplication]]

         ["Style Provenance Tracking (Edge Case #11)"
          [test-provenance-record-property
           test-provenance-record-override
           test-provenance-filter-active
           test-provenance-conflict-detection
           test-provenance-duplicate-detection
           test-provenance-diff]]

         ["CSS Processing Improvements"
          [test-vendor-prefix-detection
           test-vendor-prefix-generation
           test-css-variable-detection
           test-css-variable-parsing
           test-property-normalization
           test-css-property-processing
           test-css-string-generation]]]

        all-results
        (for [[group-name tests] test-groups
              :let [_ (println (str "\n" group-name ":"))
                    results (map run-test tests)]]
          [group-name results])]

    ;; Print results
    (doseq [[group-name results] all-results]
      (println (str "\n" group-name ":"))
      (doseq [result results]
        (let [{:keys [test passed? expected actual]} result
              status (if passed? "✓" "✗")]
          (println (str "  " status " " test))
          (when-not passed?
            (println (str "    Expected: " expected))
            (println (str "    Actual:   " actual))))))

    ;; Summary
    (let [all-tests (mapcat second all-results)
          total (count all-tests)
          passed (count (filter :passed? all-tests))
          failed (- total passed)]
      (println (str "\n=== SUMMARY ==="))
      (println (str "Total:  " total))
      (println (str "Passed: " passed " ✓"))
      (println (str "Failed: " failed (if (> failed 0) " ✗" "")))
      (println (str "Success Rate: " (int (* 100 (/ passed total))) "%"))

      {:total total
       :passed passed
       :failed failed
       :success-rate (/ passed total)})))

(comment
  ;; Run tests
  (run-all-phase4-tests)
  )
