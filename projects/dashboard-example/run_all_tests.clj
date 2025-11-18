(ns dashboard-example.run-all-tests
  (:require [clojure.java.io :as io]))

(println "=== Dashboard Example Test Suite ===\n")

(def pass-count (atom 0))
(def fail-count (atom 0))
(def skip-count (atom 0))

(defn test-pass [name]
  (println (str "✓ PASS: " name))
  (swap! pass-count inc))

(defn test-fail [name error]
  (println (str "✗ FAIL: " name))
  (println "  Error:" error)
  (swap! fail-count inc))

(defn test-skip [name reason]
  (println (str "⚠ SKIP: " name " - " reason))
  (swap! skip-count inc))

;; Test 1: test-single-button.clj
(println "Test 1: Single Button Test")
(try
  (load-file "projects/dashboard-example/test-single-button.clj")
  (test-pass "test-single-button")
  (catch Exception e
    (test-fail "test-single-button" (.getMessage e))))

;; Test 2: build.clj
(println "\nTest 2: Build Test")
(try
  (load-file "projects/dashboard-example/build.clj")
  (test-pass "build")
  (catch Exception e
    (test-fail "build" (.getMessage e))))

;; Test 3: build-fixed.clj
(println "\nTest 3: Build Fixed Test")
(try
  (load-file "projects/dashboard-example/build-fixed.clj")
  (test-pass "build-fixed")
  (catch Exception e
    (test-fail "build-fixed" (.getMessage e))))

;; Test 4: debug-compilation.clj
(println "\nTest 4: Debug Compilation")
(try
  (load-file "projects/dashboard-example/debug-compilation.clj")
  (test-pass "debug-compilation")
  (catch Exception e
    (test-fail "debug-compilation" (.getMessage e))))

;; Test 5: debug-pipeline.clj
(println "\nTest 5: Debug Pipeline")
(try
  (load-file "projects/dashboard-example/debug-pipeline.clj")
  (test-pass "debug-pipeline")
  (catch Exception e
    (test-fail "debug-pipeline" (.getMessage e))))

;; Test 6: debug-element-lookup.clj
(println "\nTest 6: Debug Element Lookup")
(try
  (load-file "projects/dashboard-example/debug-element-lookup.clj")
  (test-pass "debug-element-lookup")
  (catch Exception e
    (test-fail "debug-element-lookup" (.getMessage e))))

;; Test 7: debug-html-config.clj
(println "\nTest 7: Debug HTML Config")
(try
  (load-file "projects/dashboard-example/debug-html-config.clj")
  (test-pass "debug-html-config")
  (catch Exception e
    (test-fail "debug-html-config" (.getMessage e))))

;; Test 8: debug-attributes.clj
(println "\nTest 8: Debug Attributes")
(try
  (load-file "projects/dashboard-example/debug-attributes.clj")
  (test-pass "debug-attributes")
  (catch Exception e
    (test-fail "debug-attributes" (.getMessage e))))

;; Test 9: debug-build.clj
(println "\nTest 9: Debug Build")
(try
  (load-file "projects/dashboard-example/debug-build.clj")
  (test-pass "debug-build")
  (catch Exception e
    (test-fail "debug-build" (.getMessage e))))

;; Summary
(println "\n=== Test Summary ===")
(println (str "Passed: " @pass-count))
(println (str "Failed: " @fail-count))
(println (str "Skipped: " @skip-count))
(println (str "Total: " (+ @pass-count @fail-count @skip-count)))

(if (zero? @fail-count)
  (println "\n✓ All tests passed!")
  (println (str "\n✗ " @fail-count " test(s) failed")))

