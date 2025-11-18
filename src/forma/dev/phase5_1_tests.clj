(ns forma.dev.phase5-1-tests
  "Tests for Phase 5.1: Pre-Compilation Optimization

  Tests:
  - Dead code elimination (unused tokens, components)
  - CSS deduplication (merge duplicate rules)
  - Property inlining (inline frequently-used tokens)"
  (:require [forma.optimization.core :as opt]
            [clojure.test :refer [deftest is testing]]))

;;
;; Dead Code Elimination Tests
;;

(deftest test-extract-token-references
  (testing "Extract all token references from EDN"
    (let [edn {:button {:background "$colors.primary"
                       :padding "$spacing.md"
                       :color "#fff"}
               :card {:background "$colors.secondary"}}
          refs (opt/extract-token-references edn)]

      (is (set? refs) "Should return a set")
      (is (contains? refs "$colors.primary") "Should find $colors.primary")
      (is (contains? refs "$spacing.md") "Should find $spacing.md")
      (is (contains? refs "$colors.secondary") "Should find $colors.secondary")
      (is (not (contains? refs "#fff")) "Should not include non-token values"))))

(deftest test-extract-token-definitions
  (testing "Extract flat token definitions from nested registry"
    (let [registry {:colors {:primary "#4f46e5"
                            :secondary "#64748b"
                            :danger "#ef4444"}
                   :spacing {:sm "0.5rem"
                            :md "1rem"
                            :lg "1.5rem"}}
          defs (opt/extract-token-definitions registry)]

      (is (map? defs) "Should return a map")
      (is (= "$colors.primary" (first (keys (filter #(= "#4f46e5" (val %)) defs))))
          "Should map $colors.primary to #4f46e5")
      (is (contains? (set (keys defs)) "$spacing.md")
          "Should include $spacing.md"))))

(deftest test-find-unused-tokens
  (testing "Identify tokens that are defined but never used"
    (let [registry {:colors {:primary "#4f46e5"
                            :secondary "#64748b"
                            :unused "#ff0000"}}
          compiled {:button {:background "$colors.primary"}
                   :card {:background "$colors.secondary"}}
          analysis (opt/find-unused-tokens registry compiled)]

      (is (map? analysis) "Should return analysis map")
      (is (contains? (:unused analysis) "$colors.unused")
          "Should identify $colors.unused")
      (is (not (contains? (:unused analysis) "$colors.primary"))
          "Should not mark used tokens as unused")
      (is (= 1 (:unused-count analysis)) "Should count 1 unused token"))))

(deftest test-eliminate-unused-tokens
  (testing "Remove unused tokens from registry"
    (let [registry {:colors {:primary "#4f46e5"
                            :secondary "#64748b"
                            :unused "#ff0000"}}
          compiled {:button {:background "$colors.primary"}
                   :card {:background "$colors.secondary"}}
          result (opt/eliminate-unused-tokens registry compiled {})]

      (is (map? result) "Should return result map")
      (is (contains? result :optimized-registry) "Should include optimized registry")
      (is (contains? result :removed) "Should include removed tokens")

      (let [optimized (:optimized-registry result)]
        (is (contains? optimized :colors) "Should preserve colors category")
        (is (contains? (:colors optimized) :primary) "Should keep primary")
        (is (contains? (:colors optimized) :secondary) "Should keep secondary")
        (is (not (contains? (:colors optimized) :unused)) "Should remove unused")))))

(deftest test-eliminate-unused-tokens-with-patterns
  (testing "Keep tokens matching keep-patterns"
    (let [registry {:colors {:primary "#4f46e5"
                            :danger-light "#ff6b6b"
                            :danger-dark "#c92a2a"}}
          compiled {:button {:background "$colors.primary"}}
          ;; Keep all tokens with "danger" in the name
          result (opt/eliminate-unused-tokens registry compiled
                                             {:keep-patterns [#"danger"]})]

      (let [optimized (:optimized-registry result)]
        (is (contains? (:colors optimized) :primary) "Should keep used token")
        (is (contains? (:colors optimized) :danger-light) "Should keep danger-light (matches pattern)")
        (is (contains? (:colors optimized) :danger-dark) "Should keep danger-dark (matches pattern)")))))

;;
;; CSS Deduplication Tests
;;

(deftest test-parse-css-rule
  (testing "Parse CSS rule string into components"
    (let [[selector props] (opt/parse-css-rule ".btn { padding: 1rem; color: #fff; }")]
      (is (= ".btn" selector) "Should extract selector")
      (is (map? props) "Should parse properties to map")
      (is (= "1rem" (:padding props)) "Should extract padding")
      (is (= "#fff" (:color props)) "Should extract color"))))

(deftest test-serialize-css-rule
  (testing "Serialize CSS rule back to string"
    (let [css (opt/serialize-css-rule ".btn" {:padding "1rem" :color "#fff"})]
      (is (string? css) "Should return string")
      (is (re-find #"\.btn" css) "Should include selector")
      (is (re-find #"padding:\s*1rem" css) "Should include padding")
      (is (re-find #"color:\s*#fff" css) "Should include color"))))

(deftest test-deduplicate-css-rules
  (testing "Merge CSS rules with identical properties"
    (let [rules [".btn { padding: 1rem; color: #fff; }"
                ".button { padding: 1rem; color: #fff; }"
                ".card { padding: 2rem; }"]
          result (opt/deduplicate-css-rules rules)]

      (is (map? result) "Should return result map")
      (is (= 3 (:original-count result)) "Should track original count")
      (is (= 2 (:optimized-count result)) "Should merge duplicates")
      (is (pos? (:savings-percent result)) "Should calculate savings"))))

(deftest test-deduplicate-css-properties
  (testing "Remove duplicate properties (last value wins)"
    (let [css "color: red; padding: 1rem; color: blue;"
          optimized (opt/deduplicate-css-properties css)]

      (is (string? optimized) "Should return string")
      (is (re-find #"color:\s*blue" optimized) "Should keep last color value")
      (is (not (re-find #"color:\s*red" optimized)) "Should remove first color value")
      (is (re-find #"padding:\s*1rem" optimized) "Should keep padding"))))

;;
;; Property Inlining Tests
;;

(deftest test-analyze-token-usage-frequency
  (testing "Count token usage frequency"
    (let [compiled {:button-1 {:background "$colors.primary" :padding "$spacing.md"}
                   :button-2 {:background "$colors.primary" :padding "$spacing.md"}
                   :button-3 {:background "$colors.primary" :padding "$spacing.sm"}
                   :card {:background "$colors.secondary"}}
          freq (opt/analyze-token-usage-frequency compiled)]

      (is (map? freq) "Should return frequency map")
      (is (= 3 (get freq "$colors.primary")) "Should count $colors.primary = 3")
      (is (= 2 (get freq "$spacing.md")) "Should count $spacing.md = 2")
      (is (= 1 (get freq "$colors.secondary")) "Should count $colors.secondary = 1"))))

(deftest test-should-inline-token
  (testing "Determine if token should be inlined"
    (is (true? (opt/should-inline-token? "$colors.primary" 10 5))
        "Should inline token used >= threshold")
    (is (false? (opt/should-inline-token? "$colors.danger" 3 5))
        "Should not inline token used < threshold")))

(deftest test-inline-tokens
  (testing "Inline frequently-used tokens"
    (let [compiled {:button-1 {:background "$colors.primary"}
                   :button-2 {:background "$colors.primary"}
                   :button-3 {:background "$colors.primary"}
                   :button-4 {:background "$colors.primary"}
                   :button-5 {:background "$colors.primary"}
                   :card {:background "$colors.secondary"}}
          registry {:colors {:primary "#4f46e5"
                            :secondary "#64748b"}}
          result (opt/inline-tokens compiled registry {:threshold 5})]

      (is (map? result) "Should return result map")
      (is (contains? result :optimized-edn) "Should include optimized EDN")
      (is (contains? result :inlined) "Should include inlined tokens")

      (let [optimized (:optimized-edn result)]
        ;; $colors.primary used 5 times, should be inlined
        (is (= "#4f46e5" (get-in optimized [:button-1 :background]))
            "Should inline $colors.primary")
        ;; $colors.secondary used 1 time, should NOT be inlined
        (is (= "$colors.secondary" (get-in optimized [:card :background]))
            "Should keep $colors.secondary as reference")))))

(deftest test-inline-tokens-all
  (testing "Inline all tokens when :inline-all? true"
    (let [compiled {:button {:background "$colors.primary"}
                   :card {:background "$colors.secondary"}}
          registry {:colors {:primary "#4f46e5"
                            :secondary "#64748b"}}
          result (opt/inline-tokens compiled registry {:inline-all? true})]

      (let [optimized (:optimized-edn result)]
        (is (= "#4f46e5" (get-in optimized [:button :background]))
            "Should inline $colors.primary")
        (is (= "#64748b" (get-in optimized [:card :background]))
            "Should inline $colors.secondary")))))

;;
;; Optimization Pipeline Tests
;;

(deftest test-optimize-compilation-dead-code
  (testing "Full optimization pipeline with dead code elimination"
    (let [compiled {:button {:background "$colors.primary"}
                   :card {:background "$colors.secondary"}}
          registry {:colors {:primary "#4f46e5"
                            :secondary "#64748b"
                            :unused "#ff0000"}}
          result (opt/optimize-compilation compiled registry
                                          {:dead-code-elimination? true
                                           :inline-tokens? false})]

      (is (map? result) "Should return result map")
      (is (contains? result :optimized-registry) "Should include optimized registry")
      (is (string? (:summary result)) "Should include summary")

      (let [optimized (:optimized-registry result)]
        (is (not (contains? (:colors optimized) :unused))
            "Should remove unused tokens")))))

(deftest test-optimize-compilation-inlining
  (testing "Full optimization pipeline with token inlining"
    (let [compiled {:btn-1 {:bg "$colors.primary"}
                   :btn-2 {:bg "$colors.primary"}
                   :btn-3 {:bg "$colors.primary"}
                   :btn-4 {:bg "$colors.primary"}
                   :btn-5 {:bg "$colors.primary"}
                   :btn-6 {:bg "$colors.primary"}}
          registry {:colors {:primary "#4f46e5"}}
          result (opt/optimize-compilation compiled registry
                                          {:dead-code-elimination? false
                                           :inline-tokens? true
                                           :inline-threshold 5})]

      (is (map? result) "Should return result map")
      (let [optimized (:optimized-edn result)]
        (is (= "#4f46e5" (get-in optimized [:btn-1 :bg]))
            "Should inline frequently-used token")))))

(deftest test-optimize-compilation-combined
  (testing "Full optimization pipeline with all optimizations"
    (let [compiled {:btn-1 {:bg "$colors.primary"}
                   :btn-2 {:bg "$colors.primary"}
                   :btn-3 {:bg "$colors.primary"}
                   :btn-4 {:bg "$colors.primary"}
                   :btn-5 {:bg "$colors.primary"}
                   :card {:bg "$colors.secondary"}}
          registry {:colors {:primary "#4f46e5"
                            :secondary "#64748b"
                            :unused "#ff0000"}}
          result (opt/optimize-compilation compiled registry
                                          {:dead-code-elimination? true
                                           :inline-tokens? true
                                           :inline-threshold 5})]

      (is (map? result) "Should return result map")

      ;; Check dead code elimination
      (let [optimized-reg (:optimized-registry result)]
        (is (not (contains? (:colors optimized-reg) :unused))
            "Should remove unused token"))

      ;; Check token inlining
      (let [optimized-edn (:optimized-edn result)]
        (is (= "#4f46e5" (get-in optimized-edn [:btn-1 :bg]))
            "Should inline frequently-used token")
        (is (= "$colors.secondary" (get-in optimized-edn [:card :bg]))
            "Should keep infrequently-used token")))))

;;
;; Test Runner
;;

(defn run-all-phase5-1-tests
  "Run all Phase 5.1 optimization tests"
  []
  (println "\n=== Running Phase 5.1 Tests (Pre-Compilation Optimization) ===\n")

  (let [tests [test-extract-token-references
               test-extract-token-definitions
               test-find-unused-tokens
               test-eliminate-unused-tokens
               test-eliminate-unused-tokens-with-patterns
               test-parse-css-rule
               test-serialize-css-rule
               test-deduplicate-css-rules
               test-deduplicate-css-properties
               test-analyze-token-usage-frequency
               test-should-inline-token
               test-inline-tokens
               test-inline-tokens-all
               test-optimize-compilation-dead-code
               test-optimize-compilation-inlining
               test-optimize-compilation-combined]

        results (doall
                 (map (fn [test-fn]
                        (try
                          (test-fn)
                          {:test (str test-fn)
                           :status :pass}
                          (catch Exception e
                            {:test (str test-fn)
                             :status :fail
                             :error (.getMessage e)})))
                      tests))

        passed (count (filter #(= :pass (:status %)) results))
        failed (count (filter #(= :fail (:status %)) results))
        total (count results)]

    (println "\n=== Phase 5.1 Test Results ===")
    (println (format "Total: %d | Passed: %d | Failed: %d" total passed failed))
    (println (format "Success Rate: %.1f%%" (* 100.0 (/ passed total))))

    (when (pos? failed)
      (println "\nFailed Tests:")
      (doseq [{:keys [test error]} (filter #(= :fail (:status %)) results)]
        (println (format "  - %s: %s" test error))))

    (println "\n" (if (zero? failed) "✅ ALL TESTS PASSING" "❌ SOME TESTS FAILED"))

    {:total total
     :passed passed
     :failed failed
     :success-rate (* 100.0 (/ passed total))
     :results results}))

(comment
  ;; Run all Phase 5.1 tests
  (run-all-phase5-1-tests)

  ;; Run specific test
  (test-eliminate-unused-tokens)
  (test-inline-tokens))
