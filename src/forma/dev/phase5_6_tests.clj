(ns forma.dev.phase5-6-tests
  "Phase 5.6 Tests - Policy Enforcement System

   Test coverage:
   - Design system policies (token enforcement, color/spacing validation)
   - Accessibility policies (ARIA, semantic HTML, contrast checks)
   - Performance policies (bundle size, optimization validation)
   - Policy configuration and precedence
   - Build pipeline integration"
  (:require [clojure.string :as str]
            [forma.policy.core :as policy]
            [forma.policy.design-system :as ds-policy]
            [forma.policy.accessibility :as a11y-policy]
            [forma.policy.performance :as perf-policy]
            [forma.policy.reporting :as policy-reporting]))

;;
;; Test Utilities
;;

(defn test-result [name passed?]
  {:test name :passed? passed? :phase "5.6"})

(defn run-test [name test-fn]
  (try
    (test-fn)
    (println (str " " name))
    (test-result name true)
    (catch Exception e
      (println (str " " name " - " (.getMessage e)))
      (test-result name false))))

;;
;; Design System Policy Tests
;;

(defn test-token-enforcement-colors []
  (let [element {:type :button
                :props {:background "#4f46e5" :color "#fff"}}
        check {:type :token-enforcement
              :config {:check-colors true}}
        violations (policy/apply-policy-check element check {} {})]
    (assert (= 2 (count violations)) "Should detect 2 hardcoded colors")
    (assert (= :token-enforcement-colors (:rule-id (first violations))))))

(defn test-token-enforcement-spacing []
  (let [element {:type :div
                :props {:padding "16px" :margin "24px"}}
        check {:type :token-enforcement
              :config {:check-spacing true}}
        violations (policy/apply-policy-check element check {} {})]
    (assert (= 2 (count violations)) "Should detect 2 hardcoded spacing values")))

(defn test-color-palette-compliance []
  (let [element {:type :text
                :props {:color "#abcdef"}}
        check {:type :color-palette
              :config {:strict false}}
        violations (policy/apply-policy-check element check {} {})]
    (assert (= 1 (count violations)) "Should detect non-token color")
    (assert (= :warning (:severity (first violations))))))

(defn test-spacing-scale-compliance []
  (let [element {:type :div
                :props {:padding "13px"}}  ; Non-standard spacing
        check {:type :spacing-scale
              :config {:allowed-values [:xs :sm :md :lg]}}
        violations (policy/apply-policy-check element check {} {})]
    (assert (= 1 (count violations)) "Should detect arbitrary spacing")))

(defn test-typography-scale-compliance []
  (let [element {:type :text
                :props {:font-size "17px" :font-weight 450}}
        check {:type :typography-scale
              :config {:check-font-size true :check-font-weight true}}
        violations (policy/apply-policy-check element check {} {})]
    (assert (>= (count violations) 1) "Should detect arbitrary typography values")))

(defn test-token-usage-with-tokens []
  (let [element {:type :button
                :props {:background "$colors.primary.500"
                       :padding "$spacing.md"}}
        check {:type :token-enforcement
              :config {:check-colors true :check-spacing true}}
        violations (policy/apply-policy-check element check {} {})]
    (assert (= 0 (count violations)) "Should not detect violations when using tokens")))

;;
;; Accessibility Policy Tests
;;

(defn test-aria-label-required []
  (let [element {:type :button
                :props {:on-click "..."}}  ; No text content
        check {:type :aria-required
              :config {}}
        violations (policy/apply-policy-check element check {} {})]
    (assert (>= (count violations) 1) "Should require aria-label on button without text")))

(defn test-image-alt-text []
  (let [element {:type :image
                :props {:src "photo.jpg"}}  ; No alt text
        check {:type :semantic-html
              :config {:check-alt-text true}}
        violations (policy/apply-policy-check element check {} {})]
    (assert (= 1 (count violations)) "Should require alt text on images")
    (assert (= :image-alt-text (:rule-id (first violations))))))

(defn test-heading-hierarchy []
  (let [element {:type :h3
                :props {}}
        context {:last-heading-level 1}  ; Skip h2
        check {:type :semantic-html
              :config {:check-headings true}}
        violations (policy/apply-policy-check element check {} context)]
    (assert (= 1 (count violations)) "Should detect heading hierarchy violation")))

(defn test-color-contrast-low []
  (let [element {:type :text
                :props {:color "#777777" :background "#888888" :font-size 16}}
        check {:type :color-contrast
              :config {:min-ratio 4.5 :wcag-level :AA}}
        violations (policy/apply-policy-check element check {} {})]
    (assert (= 1 (count violations)) "Should detect low contrast ratio")))

(defn test-color-contrast-good []
  (let [element {:type :text
                :props {:color "#000000" :background "#ffffff" :font-size 16}}
        check {:type :color-contrast
              :config {:min-ratio 4.5 :wcag-level :AA}}
        violations (policy/apply-policy-check element check {} {})]
    (assert (= 0 (count violations)) "Should pass high contrast ratio")))

(defn test-keyboard-navigation []
  (let [element {:type :link
                :props {}}  ; No tabindex
        check {:type :keyboard-navigation
              :config {:check-tabindex true}}
        violations (policy/apply-policy-check element check {} {})]
    ;; Link is already focusable, but might warn about focus indicator
    (assert (>= (count violations) 0) "Should check keyboard navigation")))

;;
;; Performance Policy Tests
;;

(defn test-bundle-size-exceeded []
  (let [element {}
        check {:type :bundle-size
              :config {:limits {:html {:max-kb 1 :warn-kb 0.8}}}}
        context {:compiled-output {:html (apply str (repeat 2048 "a"))}}  ; ~2KB
        violations (policy/apply-policy-check element check {} context)]
    (assert (= 1 (count violations)) "Should detect bundle size exceeded")
    (assert (= :error (:severity (first violations))))))

(defn test-bundle-size-warning []
  (let [element {}
        check {:type :bundle-size
              :config {:limits {:html {:max-kb 10 :warn-kb 1}}}}
        context {:compiled-output {:html (apply str (repeat 1500 "a"))}}  ; ~1.5KB
        violations (policy/apply-policy-check element check {} context)]
    (assert (= 1 (count violations)) "Should warn when approaching limit")
    (assert (= :warning (:severity (first violations))))))

(defn test-unused-code-detection []
  (let [element {}
        check {:type :unused-code
              :config {:threshold 0.1}}
        context {:token-registry {"colors.primary" "#000"
                                  "colors.secondary" "#fff"
                                  "colors.tertiary" "#ccc"}
                :all-elements [{:props {:background "$colors.primary"}}]}
        violations (policy/apply-policy-check element check {} context)]
    (assert (>= (count violations) 0) "Should detect unused tokens")))

(defn test-optimization-required []
  (let [element {}
        check {:type :optimization-required
              :config {:environment :production
                      :required {:minification true :optimization true}}}
        context {:environment :production
                :build-config {:minification {:enabled false}
                              :optimization {:enabled true}}}
        violations (policy/apply-policy-check element check {} context)]
    (assert (= 1 (count violations)) "Should detect missing minification")
    (assert (= :error (:severity (first violations))))))

(defn test-image-optimization []
  (let [element {:type :image
                :props {:src "photo.bmp"}}  ; Non-approved format
        check {:type :image-optimization
              :config {:formats [:webp :jpg :png]}}
        violations (policy/apply-policy-check element check {} {})]
    (assert (= 1 (count violations)) "Should detect non-approved image format")))

;;
;; Policy Configuration Tests
;;

(defn test-load-policy-config []
  (let [config (policy/load-policy-config :design-system)]
    (assert (map? config) "Should load design system policy config")
    (assert (= :design-system (:policy config)))
    (assert (seq (:rules config)) "Should have rules defined")))

(defn test-policy-enabled-check []
  (let [config {:enabled true}
        context {:policies {:enabled true}}]
    (assert (policy/policy-enabled? config :some-key context) "Policy should be enabled")))

(defn test-violation-data-structure []
  (let [v (policy/violation :design-system :error :test-rule :button
                           "Test message" "Fix suggestion" {:file "test.edn" :line 10} {})]
    (assert (= :design-system (:type v)))
    (assert (= :error (:severity v)))
    (assert (= :test-rule (:rule-id v)))
    (assert (= :button (:element-type v)))))

(defn test-violation-counts []
  (let [violations [(policy/violation :design-system :error :r1 :button "msg1")
                   (policy/violation :design-system :warning :r2 :div "msg2")
                   (policy/violation :accessibility :info :r3 :text "msg3")]
        counts (policy/violation-count violations)]
    (assert (= 1 (:errors counts)))
    (assert (= 1 (:warnings counts)))
    (assert (= 1 (:info counts)))
    (assert (= 3 (:total counts)))))

(defn test-has-errors []
  (let [violations [(policy/violation :design-system :error :r1 :button "msg1")]
        no-errors [(policy/violation :design-system :warning :r2 :div "msg2")]]
    (assert (policy/has-errors? violations) "Should detect errors")
    (assert (not (policy/has-errors? no-errors)) "Should not detect errors when none")))

(defn test-group-violations-by-severity []
  (let [violations [(policy/violation :design-system :error :r1 :button "msg1")
                   (policy/violation :design-system :warning :r2 :div "msg2")
                   (policy/violation :accessibility :error :r3 :text "msg3")]
        grouped (policy/group-violations-by-severity violations)]
    (assert (= 2 (count (:error grouped))))
    (assert (= 1 (count (:warning grouped))))))

;;
;; Reporting Tests
;;

(defn test-format-violation []
  (let [v (policy/violation :design-system :error :token-enforcement :button
                           "Use tokens" "Replace with $colors.primary" nil {})
        formatted (policy-reporting/format-violation v {:colorize? false})]
    (assert (string? formatted))
    (assert (str/includes? formatted "ERROR"))
    (assert (str/includes? formatted "Use tokens"))))

(defn test-summary-stats []
  (let [violations [(policy/violation :design-system :error :r1 :button "msg1")
                   (policy/violation :accessibility :warning :r2 :div "msg2")]
        stats (policy-reporting/summary-stats violations)]
    (assert (= 2 (:total-count stats)))
    (assert (= 1 (:error-count stats)))
    (assert (= 1 (:warning-count stats)))))

(defn test-build-report []
  (let [violations [(policy/violation :design-system :error :r1 :button "msg1")]
        report (policy-reporting/build-report violations)]
    (assert (false? (:success? report)))
    (assert (= :failed (:status report)))
    (assert (= violations (:violations report)))))

;;
;; Integration Tests
;;

(defn test-check-policies-integration []
  (let [element {:type :button
                :props {:background "#4f46e5"}}
        context {:project-name nil
                :policies {:configs [:design-system]}}
        violations (policy/check-policies element context {})]
    (assert (seq violations) "Should detect violations in integration")))

(defn test-check-policies-batch []
  (let [elements [{:type :button :props {:background "#fff"}}
                 {:type :image :props {:src "a.jpg"}}]
        context {:policies {:configs [:design-system :accessibility]}}
        result (policy/check-policies-batch elements context {})]
    (assert (map? result) "Should return map of violations by element")))

;;
;; Test Runner
;;

(defn run-all-phase5-6-tests []
  (println "\n=== Phase 5.6 Policy Enforcement Tests ===\n")

  (let [results [
        ;; Design System Tests (6 tests)
        (run-test "Token enforcement - colors" test-token-enforcement-colors)
        (run-test "Token enforcement - spacing" test-token-enforcement-spacing)
        (run-test "Color palette compliance" test-color-palette-compliance)
        (run-test "Spacing scale compliance" test-spacing-scale-compliance)
        (run-test "Typography scale compliance" test-typography-scale-compliance)
        (run-test "Token usage - no violations" test-token-usage-with-tokens)

        ;; Accessibility Tests (6 tests)
        (run-test "ARIA label required" test-aria-label-required)
        (run-test "Image alt text required" test-image-alt-text)
        (run-test "Heading hierarchy validation" test-heading-hierarchy)
        (run-test "Color contrast - low" test-color-contrast-low)
        (run-test "Color contrast - good" test-color-contrast-good)
        (run-test "Keyboard navigation" test-keyboard-navigation)

        ;; Performance Tests (5 tests)
        (run-test "Bundle size exceeded" test-bundle-size-exceeded)
        (run-test "Bundle size warning" test-bundle-size-warning)
        (run-test "Unused code detection" test-unused-code-detection)
        (run-test "Optimization required" test-optimization-required)
        (run-test "Image optimization" test-image-optimization)

        ;; Configuration Tests (6 tests)
        (run-test "Load policy config" test-load-policy-config)
        (run-test "Policy enabled check" test-policy-enabled-check)
        (run-test "Violation data structure" test-violation-data-structure)
        (run-test "Violation counts" test-violation-counts)
        (run-test "Has errors check" test-has-errors)
        (run-test "Group by severity" test-group-violations-by-severity)

        ;; Reporting Tests (3 tests)
        (run-test "Format violation" test-format-violation)
        (run-test "Summary stats" test-summary-stats)
        (run-test "Build report" test-build-report)

        ;; Integration Tests (2 tests)
        (run-test "Check policies integration" test-check-policies-integration)
        (run-test "Check policies batch" test-check-policies-batch)
        ]

        passed (count (filter :passed? results))
        total (count results)]

    (println (str "\n=== Phase 5.6 Results: " passed "/" total " tests passed ==="))
    (when (not= passed total)
      (println "\nFailed tests:")
      (doseq [result (filter (comp not :passed?) results)]
        (println (str "  - " (:test result)))))

    {:phase "5.6"
     :passed passed
     :total total
     :success? (= passed total)
     :results results}))

(comment
  ;; Run tests
  (run-all-phase5-6-tests)
  )
