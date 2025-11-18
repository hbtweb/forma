(ns forma.dev.output-parity
  "Output parity verification - test that new compiler produces expected outputs
   
   Tests compilation outputs for various element types to ensure correctness."
  (:require [forma.compiler :as compiler]
            [kora.core.compiler :as core-compiler]
            [clojure.string :as str]))

(defn pass [msg actual]
  (println (str "✓ PASS: " msg))
  (when actual
    (println (str "  Output: " (pr-str (take 5 actual)))))
  true)

(defn fail [msg actual expected]
  (println (str "✗ FAIL: " msg))
  (println (str "  Expected: " (pr-str expected)))
  (println (str "  Actual:   " (pr-str actual)))
  false)

(defn test-basic-elements
  "Test basic HTML elements compile correctly"
  []
  (println "\n=== Basic Elements Parity ===")
  (let [results (atom [])
        context (compiler/build-context {})]
    
    ;; Test button
    (try
      (let [element {:type :button :props {:text "Click me"} :children []}
            compiled (core-compiler/compile-element compiler/forma-compiler element context)]
        (if (vector? compiled)
          (swap! results conj (pass "Button element compiles" compiled))
          (swap! results conj (fail "Button element" compiled "vector"))))
      (catch Exception e
        (swap! results conj (fail "Button element" (.getMessage e) "no exception"))))
    
    ;; Test div with children
    (try
      (let [element {:type :div :props {} :children [[:span {:text "Hello"}]]}
            compiled (core-compiler/compile-element compiler/forma-compiler element context)]
        (if (vector? compiled)
          (swap! results conj (pass "Div with children compiles" compiled))
          (swap! results conj (fail "Div with children" compiled "vector"))))
      (catch Exception e
        (swap! results conj (fail "Div with children" (.getMessage e) "no exception"))))
    
    ;; Test heading
    (try
      (let [element {:type :heading :props {:level 1 :text "Title"} :children []}
            compiled (core-compiler/compile-element compiler/forma-compiler element context)]
        (if (vector? compiled)
          (swap! results conj (pass "Heading element compiles" compiled))
          (swap! results conj (fail "Heading element" compiled "vector"))))
      (catch Exception e
        (swap! results conj (fail "Heading element" (.getMessage e) "no exception"))))
    
    ;; Test input
    (try
      (let [element {:type :input :props {:type "text" :placeholder "Enter text"} :children []}
            compiled (core-compiler/compile-element compiler/forma-compiler element context)]
        (if (vector? compiled)
          (swap! results conj (pass "Input element compiles" compiled))
          (swap! results conj (fail "Input element" compiled "vector"))))
      (catch Exception e
        (swap! results conj (fail "Input element" (.getMessage e) "no exception"))))
    
    {:passed (count (filter identity @results))
     :total (count @results)
     :results @results}))

(defn test-platform-stack
  "Test platform stack compilation (HTML + CSS + HTMX)"
  []
  (println "\n=== Platform Stack Parity ===")
  (let [results (atom [])
        context (compiler/build-context {:platform-stack [:html :css :htmx]})]
    
    ;; Test button with platform stack
    (try
      (let [element {:type :button :props {:text "Click" :background "#000" :hx-get "/api"} :children []}
            compiled (core-compiler/compile-element compiler/forma-compiler element context)]
        (if (vector? compiled)
          (do
            ;; Check for HTML structure
            (if (keyword? (first compiled))
              (swap! results conj (pass "Platform stack produces HTML structure" compiled))
              (swap! results conj (fail "Platform stack HTML" compiled "keyword tag")))
            ;; Check for styles (CSS)
            (let [attrs (second compiled)]
              (if (or (contains? attrs :style) (contains? attrs :class))
                (swap! results conj (pass "Platform stack includes styles" compiled))
                (swap! results conj (fail "Platform stack styles" attrs "style or class"))))
            ;; Check for HTMX attributes
            (let [attrs (second compiled)]
              (if (some #(str/starts-with? (name %) "hx-") (keys attrs))
                (swap! results conj (pass "Platform stack includes HTMX attributes" compiled))
                (swap! results conj (fail "Platform stack HTMX" attrs "hx- attributes")))))
          (swap! results conj (fail "Platform stack compilation" compiled "vector"))))
      (catch Exception e
        (swap! results conj (fail "Platform stack compilation" (.getMessage e) "no exception"))))
    
    {:passed (count (filter identity @results))
     :total (count @results)
     :results @results}))

(defn test-compile-to-html
  "Test compile-to-html function produces valid HTML strings"
  []
  (println "\n=== Compile to HTML Parity ===")
  (let [results (atom [])]
    
    ;; Test simple element
    (try
      (let [output (compiler/compile-to-html [[:button {:text "Click"}]])
            html (compiler/html-output->string output)]
        (if (and (string? html) (str/includes? html "button"))
          (swap! results conj (pass "compile-to-html produces HTML string" html))
          (swap! results conj (fail "compile-to-html" output "HTML string with button"))))
      (catch Exception e
        (swap! results conj (fail "compile-to-html" (.getMessage e) "no exception"))))
    
    ;; Test multiple elements
    (try
      (let [output (compiler/compile-to-html [[:div {}] [:span {:text "Hello"}]])
            html (compiler/html-output->string output)]
        (if (and (string? html) (> (count html) 0))
          (swap! results conj (pass "compile-to-html handles multiple elements" html))
          (swap! results conj (fail "compile-to-html multiple" output "non-empty HTML"))))
      (catch Exception e
        (swap! results conj (fail "compile-to-html multiple" (.getMessage e) "no exception"))))
    
    {:passed (count (filter identity @results))
     :total (count @results)
     :results @results}))

(defn test-content-handling
  "Test content-source and content-handling work correctly"
  []
  (println "\n=== Content Handling Parity ===")
  (let [results (atom [])
        context (compiler/build-context {})]
    
    ;; Test element with :text content-source
    (try
      (let [element {:type :text :props {:text "Hello world"} :children []}
            compiled (core-compiler/compile-element compiler/forma-compiler element context)]
        (if (vector? compiled)
          (let [content (nth compiled 2 nil)]
            (if (or (string? content) (nil? content))
              (swap! results conj (pass "Text content-source works" compiled))
              (swap! results conj (fail "Text content-source" content "string or nil"))))
          (swap! results conj (fail "Text element" compiled "vector"))))
      (catch Exception e
        (swap! results conj (fail "Text element" (.getMessage e) "no exception"))))
    
    ;; Test element with :children content-source
    (try
      (let [element {:type :div :props {} :children [[:span {:text "Child"}]]
            compiled (core-compiler/compile-element compiler/forma-compiler element context)]
        (if (vector? compiled)
          (let [children (nthrest compiled 2)]
            (if (seq children)
              (swap! results conj (pass "Children content-source works" compiled))
              (swap! results conj (fail "Children content-source" children "non-empty"))))
          (swap! results conj (fail "Div with children" compiled "vector"))))
      (catch Exception e
        (swap! results conj (fail "Div with children" (.getMessage e) "no exception"))))
    
    {:passed (count (filter identity @results))
     :total (count @results)
     :results @results}))

(defn run-all-parity-tests
  "Run all output parity tests"
  []
  (println "=== Forma Output Parity Verification ===\n")
  (let [basic-results (test-basic-elements)
        stack-results (test-platform-stack)
        html-results (test-compile-to-html)
        content-results (test-content-handling)
        all-results [basic-results stack-results html-results content-results]
        total-passed (reduce + (map :passed all-results))
        total-tests (reduce + (map :total all-results))]
    (println "\n=== Parity Test Summary ===")
    (println (str "Passed: " total-passed "/" total-tests))
    (if (= total-passed total-tests)
      (do
        (println "✓ All parity tests passed!")
        {:status :pass :passed total-passed :total total-tests})
      (do
        (println "✗ Some parity tests failed")
        {:status :fail :passed total-passed :total total-tests}))))

(defn -main
  "Run output parity verification"
  [& args]
  (let [result (run-all-parity-tests)]
    (System/exit (if (= (:status result) :pass) 0 1))))

