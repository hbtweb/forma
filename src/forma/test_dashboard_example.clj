(ns forma.test-dashboard-example
  (:require [forma.compiler :as compiler]
            [kora.core.compiler :as core-compiler]
            [forma.minification.core :as minification]
            [hiccup.core :as h]
            [clojure.string :as str]))

(println "=== Dashboard Example Test Verification ===\n")

(def pass-count (atom 0))
(def fail-count (atom 0))

(defn test-pass [name]
  (println (str "✓ PASS: " name))
  (swap! pass-count inc))

(defn test-fail [name error]
  (println (str "✗ FAIL: " name))
  (when error (println "  Error:" error))
  (swap! fail-count inc))

;; Test 1: Single Button Test
(println "Test 1: Single Button")
(try
  (let [button-element [:button {:class "btn" :hx-get "/api/refresh" :hx-target ".main-content" :hx-swap "outerHTML"} "Refresh Data"]
        context (compiler/build-context {} {:project-name "dashboard-example" :platform-stack [:html :css :htmx]})
        compiled (core-compiler/compile-with-pipeline compiler/forma-compiler button-element context compiler/forma-hierarchy-levels)
        html-str (h/html compiled)
        has-hx-get (str/includes? html-str "hx-get")
        has-class (str/includes? html-str "class")]
    (if (and has-hx-get has-class)
      (test-pass "Single button compilation")
      (test-fail "Single button compilation" (str "Missing attributes - hx-get: " has-hx-get ", class: " has-class))))
  (catch Exception e
    (test-fail "Single button compilation" (.getMessage e))))

;; Test 2: Build Context
(println "\nTest 2: Build Context")
(try
  (let [context (compiler/build-context {} {:project-name "dashboard-example" :platform-stack [:html :css :htmx]})]
    (if (and (contains? context :domain)
             (= :forma (:domain context))
             (contains? context :platform-stack)
             (= [:html :css :htmx] (:platform-stack context)))
      (test-pass "Build context")
      (test-fail "Build context" "Missing required keys")))
  (catch Exception e
    (test-fail "Build context" (.getMessage e))))

;; Test 3: Compile to HTML
(println "\nTest 3: Compile to HTML")
(try
  (let [elements [[:button {:text "Click"}]]
        context {:platform-stack [:html]}
        result (compiler/compile-to-html elements context)
        html (compiler/html-output->string result)]
    (if (and (string? html) (str/includes? html "<button"))
      (test-pass "compile-to-html")
      (test-fail "compile-to-html" "Invalid output")))
  (catch Exception e
    (test-fail "compile-to-html" (.getMessage e))))

;; Test 4: Platform Stack Loading
(println "\nTest 4: Platform Stack Loading")
(try
  (let [platform-configs (compiler/load-platform-stack-memo [:html :css :htmx] "dashboard-example")]
    (if (and (= 3 (count platform-configs))
             (some #(= :html (:platform %)) platform-configs)
             (some #(= :css (:platform %)) platform-configs)
             (some #(= :htmx (:platform %)) platform-configs))
      (test-pass "Platform stack loading")
      (test-fail "Platform stack loading" (str "Expected 3 platforms, got " (count platform-configs)))))
  (catch Exception e
    (test-fail "Platform stack loading" (.getMessage e))))

;; Test 5: Element Compilation Pipeline
(println "\nTest 5: Element Compilation Pipeline")
(try
  (let [element [:header {:class "header"} [:h1 {} "Dashboard"]]
        context (compiler/build-context {} {:project-name "dashboard-example" :platform-stack [:html :css :htmx]})
        parsed (core-compiler/parse-element compiler/forma-compiler element)
        compiled (core-compiler/compile-with-pipeline compiler/forma-compiler element context compiler/forma-hierarchy-levels)]
    (if (and (vector? compiled) (= :header (first compiled)))
      (test-pass "Element compilation pipeline")
      (test-fail "Element compilation pipeline" "Invalid compilation result")))
  (catch Exception e
    (test-fail "Element compilation pipeline" (.getMessage e))))

;; Test 6: HTMX Attributes
(println "\nTest 6: HTMX Attributes")
(try
  (let [element [:button {:hx-get "/api/test" :hx-target ".target"} "Click"]
        context (compiler/build-context {} {:project-name "dashboard-example" :platform-stack [:html :css :htmx]})
        compiled (core-compiler/compile-with-pipeline compiler/forma-compiler element context compiler/forma-hierarchy-levels)
        attrs (second compiled)
        has-hx-get (contains? attrs :hx-get)]
    (if has-hx-get
      (test-pass "HTMX attributes")
      (test-fail "HTMX attributes" "hx-get not found in compiled attributes")))
  (catch Exception e
    (test-fail "HTMX attributes" (.getMessage e))))

;; Test 7: Styling System
(println "\nTest 7: Styling System")
(try
  (let [context (compiler/build-context {} {:project-name "dashboard-example" :styling-stack [:shadcn-ui]})
        styling-configs (compiler/load-styling-stack-memo (:styling-stack context) (:project-name context))]
    (if (and (seq styling-configs) (contains? (first styling-configs) :components))
      (test-pass "Styling system loading")
      (test-fail "Styling system loading" "Styling configs not loaded")))
  (catch Exception e
    (test-fail "Styling system loading" (.getMessage e))))

;; Test 8: Nested Elements
(println "\nTest 8: Nested Elements")
(try
  (let [element [:div {} [:header {} [:h1 {} "Test"]] [:div {} "Content"]]
        context (compiler/build-context {} {:project-name "dashboard-example" :platform-stack [:html :css :htmx]})
        compiled (core-compiler/compile-with-pipeline compiler/forma-compiler element context compiler/forma-hierarchy-levels)]
    (if (and (vector? compiled) (> (count compiled) 2))
      (test-pass "Nested elements")
      (test-fail "Nested elements" "Children not compiled")))
  (catch Exception e
    (test-fail "Nested elements" (.getMessage e))))

;; Test 9: Full Dashboard Build
(println "\nTest 9: Full Dashboard Build")
(try
  (let [element [:div {} [:header {:class "header"} [:h1 {} "Dashboard"]] [:div {:class "container"} [:div {:class "stat-card"} "Test"]]]
        context {:platform-stack [:html]}
        result (compiler/compile-to-html [element] context)
        html (compiler/html-output->string result)]
    (if (and (string? html)
             (str/includes? html "Dashboard")
             (str/includes? html "header"))
      (test-pass "Full dashboard build")
      (test-fail "Full dashboard build" "Missing expected content")))
  (catch Exception e
    (test-fail "Full dashboard build" (.getMessage e))))

;; Test 10: Platform Isolation (EDN-driven minification)
(println "\nTest 10: Platform Isolation (EDN-driven minification)")
(try
  (let [html-platform-config (compiler/load-platform-config :html)
        css-platform-config (compiler/load-platform-config :css)
        html-min (minification/minify-with-platform-config "  <div>  Test  </div>  " html-platform-config :html-string {:remove-whitespace true})
        css-min (minification/minify-with-platform-config "  .class  {  color:  red;  }  " css-platform-config :css-string {:remove-whitespace true})]
    (if (and (string? html-min) (string? css-min))
      (test-pass "Platform isolation (EDN-driven)")
      (test-fail "Platform isolation (EDN-driven)" "Platform functions not working")))
  (catch Exception e
    (test-fail "Platform isolation (EDN-driven)" (.getMessage e))))

;; Summary
(println "\n=== Test Summary ===")
(println (str "Passed: " @pass-count))
(println (str "Failed: " @fail-count))
(println (str "Total: " (+ @pass-count @fail-count)))

(if (zero? @fail-count)
  (println "\n✓ All dashboard example tests passed!")
  (println (str "\n✗ " @fail-count " test(s) failed")))

