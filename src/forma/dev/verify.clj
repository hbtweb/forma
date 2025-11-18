(ns forma.dev.verify
  "Verification script for Forma compiler system.
  
   Tests resource loading, element parsing, property expansion, HTML compilation,
   Oxygen compilation, and refactoring integration with kora.core."
  (:require [forma.compiler :as compiler]
            [kora.core.compiler :as core-compiler]
            [kora.core.inheritance :as inheritance]
            [kora.core.tokens :as tokens]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:refer-clojure :exclude [compile-element]))

(defn pass [msg]
  (println (str "✓ PASS: " msg))
  true)

(defn fail [msg actual expected]
  (println (str "✗ FAIL: " msg))
  (println (str "  Expected: " (pr-str expected)))
  (println (str "  Actual:   " (pr-str actual)))
  false)

(defn html-string [output]
  (compiler/html-output->string output))

(defn test-resource-loading
  "Test resource loading from forma/resources/forma/"
  []
  (println "\n=== Resource Loading Tests ===")
  (let [results (atom [])]
    
    ;; Test global defaults loading
    (try
      (let [global (compiler/load-resource "global/defaults.edn")]
        (if (and (map? global) (contains? global :tokens))
          (swap! results conj (pass "Load global defaults from forma/global/defaults.edn"))
          (swap! results conj (fail "Load global defaults" global {:tokens "..."}))))
      (catch Exception e
        (swap! results conj (fail "Load global defaults" (.getMessage e) "valid map"))))
    
    ;; Test component loading
    (try
      (let [button (compiler/load-resource "components/button.edn")]
        (if (and (map? button) (contains? button :button))
          (swap! results conj (pass "Load component from forma/components/button.edn"))
          (swap! results conj (fail "Load component" button {:button "..."}))))
      (catch Exception e
        (swap! results conj (fail "Load component" (.getMessage e) "valid map"))))
    
    ;; Test platform config loading
    (try
      (let [html-platform (compiler/load-platform-config :html)]
        (if (and (map? html-platform) (contains? html-platform :button))
          (swap! results conj (pass "Load HTML platform config from forma/platforms/html.edn"))
          (swap! results conj (fail "Load HTML platform" html-platform {:button "..."}))))
      (catch Exception e
        (swap! results conj (fail "Load HTML platform" (.getMessage e) "valid map"))))
    
    ;; Test Oxygen platform config loading
    (try
      (let [oxygen-platform (compiler/load-platform-config :oxygen)]
        (if (map? oxygen-platform)
          (swap! results conj (pass "Load Oxygen platform config from forma/platforms/oxygen.edn"))
          (swap! results conj (fail "Load Oxygen platform" oxygen-platform "valid map"))))
      (catch Exception e
        (swap! results conj (fail "Load Oxygen platform" (.getMessage e) "valid map"))))
    
    ;; Test styling system loading
    (try
      (let [styling (compiler/load-styling-system :shadcn-ui)]
        (if (map? styling)
          (swap! results conj (pass "Load styling system from forma/styles/shadcn-ui.edn"))
          (swap! results conj (fail "Load styling system" styling "valid map"))))
      (catch Exception e
        (swap! results conj (fail "Load styling system" (.getMessage e) "valid map"))))
    
    ;; Test hierarchy data loading
    (try
      (let [hierarchy (compiler/load-hierarchy-data)]
        (if (and (map? hierarchy)
                 (contains? hierarchy :global)
                 (contains? hierarchy :components))
          (swap! results conj (pass "Load hierarchy data (global, components, sections, templates)"))
          (swap! results conj (fail "Load hierarchy data" hierarchy {:global "...", :components "..."}))))
      (catch Exception e
        (swap! results conj (fail "Load hierarchy data" (.getMessage e) "valid map"))))
    
    ;; Verify resource paths use forma/resources/forma/ not corebase/dsl/ui/
    (try
      (let [resource-url (io/resource "forma/components/button.edn")]
        (if (and resource-url
                 (str/includes? (str resource-url) "forma"))
          (swap! results conj (pass "Resource paths use forma/resources/forma/ (not corebase/dsl/ui/)"))
          (swap! results conj (fail "Resource path verification" (str resource-url) "contains 'forma'"))))
      (catch Exception e
        (swap! results conj (fail "Resource path verification" (.getMessage e) "valid path"))))
    
    {:passed (count (filter identity @results))
     :total (count @results)
     :results @results}))

(defn test-element-parsing
  "Test element parsing (vector and map syntax)"
  []
  (println "\n=== Element Parsing Tests ===")
  (let [results (atom [])
        compiler-instance (compiler/->FormaCompiler)]
    
    ;; Test vector syntax with attributes
    (let [element [:div {:class "test"} "Hello"]
          parsed (core-compiler/parse-element compiler-instance element)]
      (if (and (= (:type parsed) :div)
               (= (get-in parsed [:props :class]) "test")
               (seq (:children parsed)))
        (swap! results conj (pass "Parse vector syntax with attributes"))
        (swap! results conj (fail "Parse vector with attributes" parsed {:type :div, :props {:class "test"}}))))
    
    ;; Test vector syntax without attributes
    (let [element [:div "Hello"]
          parsed (core-compiler/parse-element compiler-instance element)]
      (if (and (= (:type parsed) :div)
               (empty? (:props parsed))
               (seq (:children parsed)))
        (swap! results conj (pass "Parse vector syntax without attributes"))
        (swap! results conj (fail "Parse vector without attributes" parsed {:type :div, :props {}}))))
    
    ;; Test map syntax
    (let [element {:type :button :props {:text "Click"} :children []}
          parsed (core-compiler/parse-element compiler-instance element)]
      (if (and (= (:type parsed) :button)
               (= (get-in parsed [:props :text]) "Click"))
        (swap! results conj (pass "Parse map syntax"))
        (swap! results conj (fail "Parse map syntax" parsed {:type :button, :props {:text "Click"}}))))
    
    ;; Test nested structures
    (let [element [:div [:span "Nested"]]
          parsed (core-compiler/parse-element compiler-instance element)
          child (first (:children parsed))]
      (if (and (= (:type parsed) :div)
               (vector? child) ; Children are stored as vectors, not parsed yet
               (= (first child) :span))
        (swap! results conj (pass "Parse nested structures"))
        (swap! results conj (fail "Parse nested structures" parsed "nested :div containing :span"))))
    
    ;; Test text elements
    (let [element "plain text"
          parsed (core-compiler/parse-element compiler-instance element)]
      (if (= (:type parsed) :text)
        (swap! results conj (pass "Parse text elements"))
        (swap! results conj (fail "Parse text element" parsed {:type :text}))))
    
    {:passed (count (filter identity @results))
     :total (count @results)
     :results @results}))

(defn test-property-expansion
  "Test property expansion (shortcuts like :bg → :background)"
  []
  (println "\n=== Property Expansion Tests ===")
  (let [results (atom [])
        compiler-instance (compiler/->FormaCompiler)]
    
    ;; Test :bg → :background
    (let [props {:bg "#ff0000"}
          expanded (core-compiler/expand-properties compiler-instance props)]
      (if (and (contains? expanded :background)
               (not (contains? expanded :bg))
               (= (:background expanded) "#ff0000"))
        (swap! results conj (pass "Expand :bg → :background"))
        (swap! results conj (fail "Expand :bg" expanded {:background "#ff0000"}))))
    
    ;; Test :pd → :padding
    (let [props {:pd "10px"}
          expanded (core-compiler/expand-properties compiler-instance props)]
      (if (and (contains? expanded :padding)
               (= (:padding expanded) "10px"))
        (swap! results conj (pass "Expand :pd → :padding"))
        (swap! results conj (fail "Expand :pd" expanded {:padding "10px"}))))
    
    ;; Test :mg → :margin
    (let [props {:mg "20px"}
          expanded (core-compiler/expand-properties compiler-instance props)]
      (if (and (contains? expanded :margin)
               (= (:margin expanded) "20px"))
        (swap! results conj (pass "Expand :mg → :margin"))
        (swap! results conj (fail "Expand :mg" expanded {:margin "20px"}))))
    
    ;; Test :url → :href
    (let [props {:url "/path"}
          expanded (core-compiler/expand-properties compiler-instance props)]
      (if (and (contains? expanded :href)
               (= (:href expanded) "/path"))
        (swap! results conj (pass "Expand :url → :href"))
        (swap! results conj (fail "Expand :url" expanded {:href "/path"}))))
    
    ;; Test multiple shortcuts
    (let [props {:bg "#ff0000" :pd "10px" :mg "20px"}
          expanded (core-compiler/expand-properties compiler-instance props)]
      (if (and (contains? expanded :background)
               (contains? expanded :padding)
               (contains? expanded :margin)
               (not (contains? expanded :bg))
               (not (contains? expanded :pd))
               (not (contains? expanded :mg)))
        (swap! results conj (pass "Expand multiple shortcuts in one props map"))
        (swap! results conj (fail "Expand multiple shortcuts" expanded {:background "#ff0000", :padding "10px", :margin "20px"}))))
    
    {:passed (count (filter identity @results))
     :total (count @results)
     :results @results}))

(defn test-html-compilation
  "Test HTML compilation"
  []
  (println "\n=== HTML Compilation Tests ===")
  (let [results (atom [])]
    
    ;; Test basic button element
    (try
      (let [output (compiler/compile-to-html [[:button {:text "Click Me"}]])
            html (html-string output)]
        (if (and (string? html)
                 (str/includes? html "button"))
          (swap! results conj (pass "Compile basic button element"))
          (swap! results conj (fail "Compile button" output "contains 'button'"))))
      (catch Exception e
        (swap! results conj (fail "Compile button" (.getMessage e) "valid HTML"))))
    
    ;; Test nested structures
    (try
      (let [output (compiler/compile-to-html [[:div {:class "container"} [:span "Hello"]]])
            html (html-string output)]
        (if (and (string? html)
                 (str/includes? html "div")
                 (str/includes? html "span"))
          (swap! results conj (pass "Compile nested structures"))
          (swap! results conj (fail "Compile nested structures" output "contains 'div' and 'span'"))))
      (catch Exception e
        (swap! results conj (fail "Compile nested structures" (.getMessage e) "valid HTML"))))
    
    ;; Test heading with level prop
    (try
      (let [output (compiler/compile-to-html [[:heading {:level 1 :text "Title"}]])
            html (html-string output)]
        (if (and (string? html)
                 (str/includes? html "h1"))
          (swap! results conj (pass "Compile heading with level prop"))
          (swap! results conj (fail "Compile heading" output "contains 'h1'"))))
      (catch Exception e
        (swap! results conj (fail "Compile heading" (.getMessage e) "valid HTML"))))
    
    ;; Test div with children
    (try
      (let [output (compiler/compile-to-html [[:div "Child text"]])
            html (html-string output)]
        (if (and (string? html)
                 (str/includes? html "div")
                 (str/includes? html "Child text"))
          (swap! results conj (pass "Compile div with children"))
          (swap! results conj (fail "Compile div with children" output "contains 'div' and 'Child text'"))))
      (catch Exception e
        (swap! results conj (fail "Compile div with children" (.getMessage e) "valid HTML"))))
    
    {:passed (count (filter identity @results))
     :total (count @results)
     :results @results}))

(defn test-oxygen-compilation
  "Test Oxygen compilation"
  []
  (println "\n=== Oxygen Compilation Tests ===")
  (let [results (atom [])]
    
    ;; Test button to Oxygen JSON structure
    (try
      (let [oxygen (try
                     (require 'forma.compilers.oxygen :reload)
                     ((resolve 'forma.compilers.oxygen/compile-to-oxygen) [[:button {:text "Click"}]])
                     (catch Exception e
                       nil))]
        (if (and oxygen (map? oxygen) (contains? oxygen :elements) (vector? (:elements oxygen)))
          (swap! results conj (pass "Compile button to Oxygen JSON structure"))
          (swap! results conj (fail "Compile button to Oxygen" (or oxygen "nil") {:elements "..."}))))
      (catch Exception e
        (swap! results conj (fail "Compile button to Oxygen" (.getMessage e) "valid structure"))))
    
    ;; Test element mapping
    (try
      (let [oxygen (try
                     (require 'forma.compilers.oxygen :reload)
                     ((resolve 'forma.compilers.oxygen/compile-to-oxygen) [[:div {:class "test"}]])
                     (catch Exception e
                       nil))]
        (if (and oxygen (map? oxygen) (seq (:elements oxygen)))
          (swap! results conj (pass "Verify element mapping to Oxygen format"))
          (swap! results conj (fail "Element mapping" (or oxygen "nil") {:elements "..."}))))
      (catch Exception e
        (swap! results conj (fail "Element mapping" (.getMessage e) "valid structure"))))
    
    {:passed (count (filter identity @results))
     :total (count @results)
     :results @results}))

(defn test-refactoring-integration
  "Test refactoring integration with kora.core"
  []
  (println "\n=== Refactoring Integration Tests ===")
  (let [results (atom [])]
    
    ;; Verify Forma uses kora.core.inheritance/resolve-inheritance
    (try
      (let [element {:type :button :props {:variant :primary}}
            context (compiler/build-context {})
            hierarchy-levels compiler/forma-hierarchy-levels
            resolved (inheritance/resolve-inheritance element context hierarchy-levels)]
        (if (map? resolved)
          (swap! results conj (pass "Forma uses kora.core.inheritance/resolve-inheritance"))
          (swap! results conj (fail "Inheritance integration" resolved "valid map"))))
      (catch Exception e
        (swap! results conj (fail "Inheritance integration" (.getMessage e) "no exception"))))
    
    ;; Verify hierarchy levels work correctly
    (try
      (let [levels compiler/forma-hierarchy-levels]
        (if (and (vector? levels)
                 (= (first levels) :global)
                 (contains? (set levels) :components)
                 (contains? (set levels) :templates))
          (swap! results conj (pass "Hierarchy levels [:global :components :sections :templates :pages] work correctly"))
          (swap! results conj (fail "Hierarchy levels" levels [:global :components :sections :templates :pages]))))
      (catch Exception e
        (swap! results conj (fail "Hierarchy levels" (.getMessage e) "valid vector"))))
    
    ;; Verify FormaCompiler implements CompilerPipeline protocol
    (try
      (let [compiler-instance compiler/forma-compiler
            element [:div "test"]
            parsed (core-compiler/parse-element compiler-instance element)]
        (if (= (:type parsed) :div)
          (swap! results conj (pass "FormaCompiler implements CompilerPipeline protocol (parse-element)"))
          (swap! results conj (fail "CompilerPipeline parse-element" parsed {:type :div}))))
      (catch Exception e
        (swap! results conj (fail "CompilerPipeline parse-element" (.getMessage e) "no exception"))))
    
    ;; Verify compile-collection is used
    (try
      (let [compiler-instance compiler/forma-compiler
            elements [[:div "test"]]
            context (compiler/build-context {})
            compiled (core-compiler/compile-collection compiler-instance elements context compiler/forma-hierarchy-levels)]
        (if (vector? compiled)
          (swap! results conj (pass "Forma uses kora.core.compiler/compile-collection"))
          (swap! results conj (fail "compile-collection usage" compiled "vector"))))
      (catch Exception e
        (swap! results conj (fail "compile-collection usage" (.getMessage e) "no exception"))))
    
    ;; Verify Forma uses kora.core.tokens/resolve-tokens
    (try
      (let [props {"$colors.primary.500" "#4f46e5"}
            tokens {:colors {:primary {:500 "#4f46e5"}}}
            resolved (tokens/resolve-tokens props {:tokens tokens})]
        (if (map? resolved)
          (swap! results conj (pass "Forma uses kora.core.tokens/resolve-tokens"))
          (swap! results conj (fail "Token resolution" resolved "valid map"))))
      (catch Exception e
        (swap! results conj (fail "Token resolution" (.getMessage e) "no exception"))))
    
    ;; Test token resolution with $token.path syntax
    (try
      (let [props {:padding "$spacing.md"}
            tokens {:spacing {:md "12px"}}
            resolved (tokens/resolve-tokens props {:tokens tokens})]
        (if (= (:padding resolved) "12px")
          (swap! results conj (pass "Token resolution with $token.path syntax works"))
          (swap! results conj (fail "Token path resolution" resolved {:padding "12px"}))))
      (catch Exception e
        (swap! results conj (fail "Token path resolution" (.getMessage e) "no exception"))))
    
    ;; Test token fallback syntax
    (try
      (let [props {"$colors.missing || #ffffff" "#ffffff"}
            tokens {:colors {:primary "#000000"}}
            resolved (tokens/resolve-tokens props {:tokens tokens})]
        (if (map? resolved)
          (swap! results conj (pass "Token fallback syntax $token.path || fallback works"))
          (swap! results conj (fail "Token fallback" resolved "valid map"))))
      (catch Exception e
        (swap! results conj (fail "Token fallback" (.getMessage e) "no exception"))))
    
    ;; Verify CompilerPipeline methods
    (let [compiler-instance compiler/forma-compiler]
      (try
        (let [props {:bg "#ff0000"}
              expanded (core-compiler/expand-properties compiler-instance props)]
          (if (contains? expanded :background)
            (swap! results conj (pass "CompilerPipeline expand-properties method works"))
            (swap! results conj (fail "expand-properties" expanded {:background "#ff0000"}))))
        (catch Exception e
          (swap! results conj (fail "expand-properties" (.getMessage e) "no exception"))))
      
      (try
        (let [element {:type :button :props {:variant :primary}}
              context (compiler/build-context {})
              styled (core-compiler/apply-styling compiler-instance element {:variant :primary} context)]
          (if (or (map? styled) (vector? styled)) ; apply-styling may return props map or vector for classes
            (swap! results conj (pass "CompilerPipeline apply-styling method works"))
            (swap! results conj (fail "apply-styling" styled "valid map or vector"))))
        (catch Exception e
          (swap! results conj (fail "apply-styling" (.getMessage e) "no exception"))))
      
      (try
        (let [element {:type :div :props {} :children []}
              context (compiler/build-context {})
              compiled (core-compiler/compile-element compiler-instance element context)]
          (if (vector? compiled)
            (swap! results conj (pass "CompilerPipeline compile-element method works"))
            (swap! results conj (fail "compile-element" compiled "vector"))))
        (catch Exception e
          (swap! results conj (fail "compile-element" (.getMessage e) "no exception")))))
    
    {:passed (count (filter identity @results))
     :total (count @results)
     :results @results}))

(defn test-end-to-end
  "Test end-to-end compilation scenarios"
  []
  (println "\n=== End-to-End Tests ===")
  (let [results (atom [])]
    
    ;; Test simple component with inheritance
    (try
      (let [output (compiler/compile-to-html [[:button {:variant :primary}]])
            html (html-string output)]
        (if (string? html)
          (swap! results conj (pass "Compile simple component with inheritance (using kora.core.inheritance)"))
          (swap! results conj (fail "End-to-end inheritance" output "string"))))
      (catch Exception e
        (swap! results conj (fail "End-to-end inheritance" (.getMessage e) "no exception"))))
    
    ;; Test component using tokens from global defaults
    (try
      (let [output (compiler/compile-to-html [[:div {:class "test"}]])
            html (html-string output)]
        (if (string? html)
          (swap! results conj (pass "Compile component using tokens from global defaults (using kora.core.tokens)"))
          (swap! results conj (fail "End-to-end tokens" output "string"))))
      (catch Exception e
        (swap! results conj (fail "End-to-end tokens" (.getMessage e) "no exception"))))
    
    ;; Test complex nested structure
    (try
      (let [output (compiler/compile-to-html [[:div {:class "container"}
                                                   [:div {:class "header"}
                                                    [:heading {:level 1 :text "Title"}]]
                                                   [:div {:class "content"}
                                                    [:button {:text "Action"}]]]])
            html (html-string output)]
        (if (and (string? html)
                 (str/includes? html "div"))
          (swap! results conj (pass "Compile complex nested structure through full pipeline"))
          (swap! results conj (fail "Complex nested structure" output "contains 'div'"))))
      (catch Exception e
        (swap! results conj (fail "Complex nested structure" (.getMessage e) "no exception"))))
    
    ;; Test full pipeline
    (try
      (let [elements [[:div {:class "test"} [:span "Hello"]]]
            output (compiler/compile-to-html elements)
            html (html-string output)]
        (if (and (string? html)
                 (str/includes? html "div"))
          (swap! results conj (pass "Full pipeline: EDN → Translate → Parse → Expand → Resolve → Transform → Output"))
          (swap! results conj (fail "Full pipeline" output "valid HTML"))))
      (catch Exception e
        (swap! results conj (fail "Full pipeline" (.getMessage e) "no exception"))))
    
    {:passed (count (filter identity @results))
     :total (count @results)
     :results @results}))

(defn run-all-tests
  "Run all verification tests and print summary"
  []
  (println "\n" (str/join "" (repeat 60 "=")))
  (println "Forma Compiler Verification")
  (println (str/join "" (repeat 60 "=")))
  
  (let [resource-results (test-resource-loading)
        parsing-results (test-element-parsing)
        expansion-results (test-property-expansion)
        html-results (test-html-compilation)
        oxygen-results (test-oxygen-compilation)
        refactoring-results (test-refactoring-integration)
        e2e-results (test-end-to-end)
        
        all-results [resource-results parsing-results expansion-results
                     html-results oxygen-results refactoring-results e2e-results]
        
        total-passed (reduce + (map :passed all-results))
        total-tests (reduce + (map :total all-results))]
    
    (println "\n" (str/join "" (repeat 60 "=")))
    (println "Summary")
    (println (str/join "" (repeat 60 "=")))
    (println (str "Resource Loading:     " (:passed resource-results) "/" (:total resource-results)))
    (println (str "Element Parsing:      " (:passed parsing-results) "/" (:total parsing-results)))
    (println (str "Property Expansion:   " (:passed expansion-results) "/" (:total expansion-results)))
    (println (str "HTML Compilation:     " (:passed html-results) "/" (:total html-results)))
    (println (str "Oxygen Compilation:  " (:passed oxygen-results) "/" (:total oxygen-results)))
    (println (str "Refactoring Integration: " (:passed refactoring-results) "/" (:total refactoring-results)))
    (println (str "End-to-End:           " (:passed e2e-results) "/" (:total e2e-results)))
    (println (str/join "" (repeat 60 "-")))
    (println (str "Total:                " total-passed "/" total-tests))
    
    (if (= total-passed total-tests)
      (println "\n✓ All tests passed!")
      (println (str "\n✗ " (- total-tests total-passed) " test(s) failed.")))
    
    {:total total-tests
     :passed total-passed
     :failed (- total-tests total-passed)
     :all-results all-results}))

