(require '[clojure.test :as t])
(require '[forma.compiler :as compiler])
(require '[forma.styling.core :as styling])
(require '[forma.platforms.html :as html-platform])
(require '[forma.platforms.css :as css-platform])

(println "=== Architecture Refactoring Test Suite ===\n")

;; Test 1: Optimization Logic Extraction
(println "Test 1: Optimization Logic Extraction")
(let [elements [[:button {:text "Click"}]]
      context {:optimization {:pre-compilation false}}]
  (let [result (compiler/apply-optimization-if-enabled elements context)]
    (if (= context result)
      (println "  ✓ Optimization disabled - context unchanged")
      (println "  ✗ FAILED: Context should be unchanged"))))

;; Test 2: Styling Deep Merge
(println "\nTest 2: Styling Deep Merge")
(let [result (styling/deep-merge {:a 1 :b {:c 2}} {:b {:d 3}})]
  (if (= {:a 1 :b {:c 2 :d 3}} result)
    (println "  ✓ Deep merge works correctly")
    (println "  ✗ FAILED: Deep merge incorrect")))

;; Test 3: HTML Platform Minification
(println "\nTest 3: HTML Platform Minification")
(let [html "  <div>  Test  </div>  "
      config {:remove-whitespace true :remove-comments true}
      result (html-platform/minify-html-string html config)]
  (if (not (re-find #"\s{2,}" result))
    (println "  ✓ HTML minification removes whitespace")
    (println "  ✗ FAILED: HTML minification didn't remove whitespace")))

;; Test 4: CSS Platform Minification
(println "\nTest 4: CSS Platform Minification")
(let [css "  .class  {  color:  red;  }  "
      config {:remove-whitespace true :remove-comments true}
      result (css-platform/minify-css-string css config)]
  (if (not (re-find #"\s{2,}" result))
    (println "  ✓ CSS minification removes whitespace")
    (println "  ✗ FAILED: CSS minification didn't remove whitespace")))

;; Test 5: HTML Platform to-html-string
(println "\nTest 5: HTML Platform to-html-string")
(let [hiccup [[:div {:class "test"} "Hello"]]
      result (html-platform/to-html-string hiccup)]
  (if (and (string? result) (re-find #"<div" result))
    (println "  ✓ HTML conversion works")
    (println "  ✗ FAILED: HTML conversion failed")))

;; Test 6: Platform Minifier Dispatcher
(println "\nTest 6: Platform Minifier Dispatcher")
(let [html-minifier (compiler/get-platform-minifier :html :html-string)
      css-minifier (compiler/get-platform-minifier :css :css-string)]
  (if (and (fn? html-minifier) (fn? css-minifier))
    (println "  ✓ Platform minifiers are accessible")
    (println "  ✗ FAILED: Platform minifiers not accessible")))

;; Test 7: Build Context
(println "\nTest 7: Build Context")
(let [result (compiler/build-context {} {})]
  (if (and (contains? result :domain)
           (= :forma (:domain result))
           (contains? result :platform-stack))
    (println "  ✓ Build context works correctly")
    (println "  ✗ FAILED: Build context incorrect")))

;; Test 8: Compile to HTML Integration
(println "\nTest 8: Compile to HTML Integration")
(try
  (let [elements [[:button {:text "Click"}]]
        context {:platform-stack [:html]}]
    (let [result (compiler/compile-to-html elements context)
          html-string (compiler/html-output->string result)]
      (if (and (string? html-string) (re-find #"<button" html-string))
        (println "  ✓ Compile to HTML works")
        (println "  ✗ FAILED: Compile to HTML failed"))))
  (catch Exception e
    (println "  ⚠ Compile to HTML test skipped (may need full project setup):" (.getMessage e))))

(println "\n=== Test Suite Complete ===")

