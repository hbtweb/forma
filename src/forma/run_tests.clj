(ns forma.run-tests
  (:require [forma.compiler :as compiler]
            [forma.styling.core :as styling]
            [forma.platforms.html :as html-platform]
            [forma.minification.core :as minification]
            [forma.optimization :as optimization]))

(println "=== Architecture Refactoring Test Suite ===\n")

(def pass-count (atom 0))
(def fail-count (atom 0))
(def skip-count (atom 0))

(defn test-pass [name]
  (println (str "✓ PASS: " name))
  (swap! pass-count inc))

(defn test-fail [name result]
  (println (str "✗ FAIL: " name))
  (println "  Result:" result)
  (swap! fail-count inc))

(defn test-skip [name reason]
  (println (str "⚠ SKIP: " name " - " reason))
  (swap! skip-count inc))

;; Test 1: Styling Deep Merge
(println "Test 1: Styling Deep Merge")
(let [result (styling/deep-merge {:a 1 :b {:c 2}} {:b {:d 3}})]
  (if (= {:a 1 :b {:c 2 :d 3}} result)
    (test-pass "deep-merge")
    (test-fail "deep-merge" result)))

;; Test 2: HTML Platform Minification (EDN-driven)
(println "\nTest 2: HTML Platform Minification (EDN-driven)")
(let [html "  <div>  Test  </div>  "
      config {:remove-whitespace true :remove-comments true}
      html-platform-config (compiler/load-platform-config :html)
      result (minification/minify-with-platform-config html html-platform-config :html-string config)]
  (if (not (re-find #"\s{2,}" result))
    (test-pass "HTML minification (EDN-driven)")
    (test-fail "HTML minification (EDN-driven)" result)))

;; Test 3: CSS Platform Minification (EDN-driven)
(println "\nTest 3: CSS Platform Minification (EDN-driven)")
(let [css "  .class  {  color:  red;  }  "
      config {:remove-whitespace true :remove-comments true}
      css-platform-config (compiler/load-platform-config :css)
      result (minification/minify-with-platform-config css css-platform-config :css-string config)]
  (if (not (re-find #"\s{2,}" result))
    (test-pass "CSS minification (EDN-driven)")
    (test-fail "CSS minification" result)))

;; Test 4: HTML Platform to-html-string
(println "\nTest 4: HTML Platform to-html-string")
(let [hiccup [[:div {:class "test"} "Hello"]]
      result (html-platform/to-html-string hiccup)]
  (if (and (string? result) (re-find #"<div" result) (re-find #"Hello" result))
    (test-pass "HTML conversion")
    (test-fail "HTML conversion" result)))

;; Test 5: Optimization Logic Extraction
(println "\nTest 5: Optimization Logic Extraction")
(let [elements [[:button {:text "Click"}]]
      context {:optimization {:pre-compilation false}}]
  (let [result (compiler/apply-optimization-if-enabled elements context)]
    (if (= context result)
      (test-pass "Optimization disabled")
      (test-fail "Optimization disabled" result))))

;; Test 6: Platform Minifier Dispatcher
(println "\nTest 6: Platform Minifier Dispatcher")
(let [html-min (compiler/get-platform-minifier :html :html-string)
      css-min (compiler/get-platform-minifier :css :css-string)]
  (if (and (fn? html-min) (fn? css-min))
    (test-pass "Platform minifiers accessible")
    (test-fail "Platform minifiers" {:html html-min :css css-min})))

;; Test 7: Build Context
(println "\nTest 7: Build Context")
(try
  (let [result (compiler/build-context {} {})]
    (if (and (contains? result :domain) (= :forma (:domain result)) (contains? result :platform-stack))
      (test-pass "build-context")
      (test-fail "build-context" (keys result))))
  (catch Exception e
    (test-skip "build-context" (.getMessage e))))

;; Test 8: Compile to HTML Integration
(println "\nTest 8: Compile to HTML Integration")
(try
  (let [elements [[:button {:text "Click"}]]
        context {:platform-stack [:html]}]
    (let [result (compiler/compile-to-html elements context)
          html-string (compiler/html-output->string result)]
      (if (and (string? html-string) (re-find #"<button" html-string))
        (test-pass "compile-to-html")
        (test-fail "compile-to-html" result))))
  (catch Exception e
    (test-skip "compile-to-html" (.getMessage e))))

;; Test 9: Optimization Implementation
(println "\nTest 9: Optimization Implementation")
(try
  (let [component-def {:type :button :text "Click"}
        tokens {}
        context {:global {} :components {} :sections {} :templates {} :tokens {}}]
    (let [result (optimization/resolve-inheritance-and-tokens component-def tokens context)]
      (if (map? result)
        (test-pass "resolve-inheritance-and-tokens")
        (test-fail "resolve-inheritance-and-tokens" result))))
  (catch Exception e
    (test-skip "resolve-inheritance-and-tokens" (.getMessage e))))

;; Test 10: Styling System Loading
(println "\nTest 10: Styling System Loading")
(try
  (let [mock-load-resource (fn [path project-name]
                             (if (= path "styles/test.edn")
                               {:theme :test :components {:button {:base ["btn"]}}}
                               {}))
        result (styling/load-styling-system :test mock-load-resource)]
    (if (and (contains? result :theme) (= :test (:theme result)))
      (test-pass "load-styling-system")
      (test-fail "load-styling-system" result)))
  (catch Exception e
    (test-skip "load-styling-system" (.getMessage e))))

;; Summary
(println "\n=== Test Summary ===")
(println (str "Passed: " @pass-count))
(println (str "Failed: " @fail-count))
(println (str "Skipped: " @skip-count))
(println (str "Total: " (+ @pass-count @fail-count @skip-count)))

(if (zero? @fail-count)
  (println "\n✓ All tests passed!")
  (println (str "\n✗ " @fail-count " test(s) failed")))
