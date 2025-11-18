(ns forma.architecture-refactoring-test
  "Comprehensive tests for architecture refactoring:
   - Optimization logic extraction
   - Styling system isolation
   - Platform-specific code isolation
   - Optimization implementation
   - Build-context refactoring"
  (:require [clojure.test :refer :all]
            [forma.compiler :as compiler]
            [forma.styling.core :as styling]
            [forma.platforms.html :as html-platform]
            [forma.platforms.css :as css-platform]
            [forma.optimization :as optimization]
            [kora.core.compiler :as core-compiler]))

;; ============================================================================
;; Test Helpers
;; ============================================================================

(defn mock-load-resource
  "Mock load-resource function for testing"
  [path project-name]
  (cond
    (= path "styles/shadcn-ui.edn")
    {:theme :shadcn-ui
     :components {:button {:base ["btn" "btn-primary"]
                           :variants {:primary ["bg-blue-600" "text-white"]}}}}
    
    (= path "styles/tailwind.edn")
    {:theme :tailwind
     :components {:button {:base ["inline-flex" "items-center"]}}}
    
    (= path "styles/custom.edn")
    {:theme :custom
     :extends :shadcn-ui
     :components {:button {:variants {:primary ["custom-primary"]}}}}

    (= path "styles/cycle-a.edn")
    {:theme :cycle-a
     :extends :cycle-b}

    (= path "styles/cycle-b.edn")
    {:theme :cycle-b
     :extends :cycle-a}
    
    :else {}))

;; ============================================================================
;; Phase 1: Optimization Logic Extraction Tests
;; ============================================================================

(deftest test-apply-optimization-if-enabled
  (testing "Returns context unchanged when optimization disabled"
    (let [elements [[:button {:text "Click"}]]
          context {:optimization {:pre-compilation false}}]
      (is (= context (compiler/apply-optimization-if-enabled elements context)))))
  
  (testing "Applies optimization when enabled"
    (let [elements [[:button {:text "Click"}]]
          context {:optimization {:pre-compilation true}
                   :components {:button {}}
                   :tokens {}}]
      (let [result (compiler/apply-optimization-if-enabled elements context)]
        (is (contains? result :components))
        (is (contains? result :tokens))))))

;; ============================================================================
;; Phase 2: Styling System Isolation Tests
;; ============================================================================

(deftest test-styling-deep-merge
  (testing "Deep merge nested maps"
    (is (= {:a 1 :b {:c 2 :d 3}}
           (styling/deep-merge {:a 1 :b {:c 2}} {:b {:d 3}}))))
  
  (testing "Right map overrides left"
    (is (= {:a 2}
           (styling/deep-merge {:a 1} {:a 2}))))
  
  (testing "Deep merge with non-map values"
    (is (= {:a 2 :b 4}
           (styling/deep-merge {:a 1 :b 2} {:a 2 :b 4})))))

(deftest test-load-styling-system
  (testing "Load standalone styling system"
    (let [result (styling/load-styling-system :shadcn-ui mock-load-resource)]
      (is (= :shadcn-ui (:theme result)))
      (is (contains? result :components))))
  
  (testing "Load styling system with extension"
    (let [result (styling/load-styling-system :custom mock-load-resource)]
      (is (= :custom (:theme result)))
      ;; Should have merged base classes from shadcn-ui
      (is (contains? (get-in result [:components :button :base]) "btn"))))
  
  (testing "Detect styling system cycles"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"Styling system cycle detected"
         (styling/load-styling-system :cycle-a mock-load-resource)))))

(deftest test-load-styling-stack
  (testing "Load multiple styling systems"
    (let [result (styling/load-styling-stack [:tailwind :shadcn-ui] mock-load-resource)]
      (is (= 2 (count result)))
      (is (= :tailwind (:theme (first result))))
      (is (= :shadcn-ui (:theme (second result)))))))

(deftest test-apply-styling-from-config
  (testing "Apply base classes"
    (let [props {}
          element-type :button
          styling-config {:components {:button {:base ["btn" "btn-primary"]}}}
          resolved-props {}]
      (let [result (styling/apply-styling-from-config props element-type styling-config resolved-props)]
        (is (= "btn btn-primary" (:class result))))))
  
  (testing "Apply variant classes"
    (let [props {}
          element-type :button
          styling-config {:components {:button {:base ["btn"]
                                                :variants {:primary ["bg-blue-600"]}}}}
          resolved-props {:variant :primary}]
      (let [result (styling/apply-styling-from-config props element-type styling-config resolved-props)]
        (is (= "btn bg-blue-600" (:class result))))))
  
  (testing "Merge with existing classes"
    (let [props {:class "existing-class"}
          element-type :button
          styling-config {:components {:button {:base ["btn"]}}}
          resolved-props {}]
      (let [result (styling/apply-styling-from-config props element-type styling-config resolved-props)]
        (is (= "btn existing-class" (:class result))))))
  
  (testing "Handle vector classes"
    (let [props {}
          element-type :button
          styling-config {:components {:button {:base ["btn" "btn-primary"]}}}
          resolved-props {}]
      (let [result (styling/apply-styling-from-config props element-type styling-config resolved-props)]
        (is (= "btn btn-primary" (:class result))))))
  
  (testing "No styling for unknown element type"
    (let [props {:class "existing"}
          element-type :unknown
          styling-config {:components {:button {:base ["btn"]}}}
          resolved-props {}]
      (is (= props (styling/apply-styling-from-config props element-type styling-config resolved-props)))))
  
  (testing "Treat blank class string as no override by default"
    (let [props {:class "   "}
          element-type :button
          styling-config {:components {:button {:base ["btn"]}}}
          resolved-props {}]
      (let [result (styling/apply-styling-from-config props element-type styling-config resolved-props)]
        (is (= "btn" (:class result))))))
  
  (testing "Respect project configuration disabling base when explicit classes provided"
    (let [props {:class "custom"}
          element-type :button
          styling-config {:components {:button {:base ["btn"]
                                                :variants {:primary ["primary"]}}}}
          resolved-props {:variant :primary}
          context {:styling {:apply-base-when-explicit false}}]
      (let [result (styling/apply-styling-from-config props element-type styling-config resolved-props context)]
        (is (= "custom" (:class result))))))
  
  (testing "Deduplicate classes when configured"
    (let [props {:class "btn"}
          element-type :button
          styling-config {:components {:button {:base ["btn" "btn"]
                                                :variants {:primary ["btn" "primary"]}}}}
          resolved-props {:variant :primary}
          context {:styling {:dedupe-classes? true}}]
      (let [result (styling/apply-styling-from-config props element-type styling-config resolved-props context)]
        (is (= "btn primary" (:class result))))))
  
  (testing "Treat blank class as explicit when configured"
    (let [props {:class "   "}
          element-type :button
          styling-config {:components {:button {:base ["btn"]}}}
          resolved-props {}
          context {:styling {:blank-class->nil? false
                             :apply-base-when-explicit false}}]
      (let [result (styling/apply-styling-from-config props element-type styling-config resolved-props context)]
        (is (nil? (:class result))))))
  
  (testing "Records class provenance metadata"
    (let [props {}
          element-type :button
          styling-config {:theme :demo
                          :components {:button {:base ["btn"]
                                                :variants {:primary ["variant"]}}}}
          resolved-props {:variant :primary}]
      (let [result (styling/apply-styling-from-config props element-type styling-config resolved-props)
            classes-prov (get-in result [:forma.provenance :classes])
            systems (get-in result [:forma.provenance :styling-systems])]
        (is (contains? classes-prov "btn"))
        (is (contains? classes-prov "variant"))
        (is (= [:base] (map :source (get classes-prov "btn"))))
        (is (= [:variant] (map :source (get classes-prov "variant"))))
        (is (= [:demo] systems)))))
  
  (testing "Records duplicate class diagnostics when dedupe runs"
    (let [props {:class "btn"}
          element-type :button
          styling-config {:theme :demo
                          :components {:button {:base ["btn"]
                                                :variants {:primary ["btn"]}}}}
          resolved-props {:variant :primary}]
      (let [result (styling/apply-styling-from-config props element-type styling-config resolved-props)
            warnings (get-in result [:forma.provenance :warnings])]
        (is (seq warnings))
        (is (= :duplicate-class (:type (first warnings))))
        (is (= "btn" (:class (first warnings))))))))

;; ============================================================================
;; Phase 3: Platform-Specific Code Isolation Tests
;; ============================================================================

(deftest test-html-platform-minify
  (testing "Minify HTML string - remove whitespace"
    (let [html "  <div>  Hello   World  </div>  "
          config {:remove-whitespace true :remove-comments true}]
      (is (not (re-find #"\s{2,}" (html-platform/minify-html-string html config))))))
  
  (testing "Minify HTML string - remove comments"
    (let [html "<div>Hello<!-- comment -->World</div>"
          config {:remove-whitespace true :remove-comments true}]
      (is (not (re-find #"<!--.*?-->" (html-platform/minify-html-string html config))))))
  
  (testing "Preserve content when minification disabled"
    (let [html "  <div>  Hello   World  </div>  "
          config {:remove-whitespace false :remove-comments false}]
      (is (= html (html-platform/minify-html-string html config))))))

(deftest test-html-platform-to-html-string
  (testing "Convert single Hiccup element to HTML string"
    (let [hiccup [[:div {:class "test"} "Hello"]]]
      (let [result (html-platform/to-html-string hiccup)]
        (is (string? result))
        (is (re-find #"<div" result))
        (is (re-find #"Hello" result)))))
  
  (testing "Convert multiple Hiccup elements to HTML string"
    (let [hiccup [[:div "One"] [:div "Two"]]]
      (let [result (html-platform/to-html-string hiccup)]
        (is (string? result))
        (is (re-find #"One" result))
        (is (re-find #"Two" result))))))

(deftest test-css-platform-minify
  (testing "Minify CSS string - remove whitespace"
    (let [css "  .class  {  color:  red;  }  "
          config {:remove-whitespace true :remove-comments true}]
      (is (not (re-find #"\s{2,}" (css-platform/minify-css-string css config))))))
  
  (testing "Minify CSS string - remove comments"
    (let [css ".class { color: red; /* comment */ }"
          config {:remove-whitespace true :remove-comments true}]
      (is (not (re-find #"/\*.*?\*/" (css-platform/minify-css-string css config))))))
  
  (testing "Preserve content when minification disabled"
    (let [css "  .class  {  color:  red;  }  "
          config {:remove-whitespace false :remove-comments false}]
      (is (= css (css-platform/minify-css-string css config))))))

;; ============================================================================
;; Phase 4: Platform Minification Dispatcher Tests
;; ============================================================================

(deftest test-get-platform-minifier
  (testing "Get HTML minifier for html-string format"
    (let [minifier (compiler/get-platform-minifier :html :html-string)]
      (is (fn? minifier))
      (is (= " <div>test</div> " (minifier "  <div>test</div>  " {:remove-whitespace true})))))
  
  (testing "Get CSS minifier for css-string format"
    (let [minifier (compiler/get-platform-minifier :css :css-string)]
      (is (fn? minifier))
      (is (= " .class{color:red;} " (minifier "  .class  {  color:  red;  }  " {:remove-whitespace true})))))
  
  (testing "Return nil for unsupported platform/format"
    (is (nil? (compiler/get-platform-minifier :unknown :html-string)))
    (is (nil? (compiler/get-platform-minifier :html :unknown))))))

(deftest test-should-minify
  (testing "Should minify when enabled and in production"
    (let [context {:minify {:enabled true
                            :environment :production
                            :html {:enabled true}}
                   :environment :production}]
      (is (compiler/should-minify? context :html-string :html))))
  
  (testing "Should not minify when disabled"
    (let [context {:minify {:enabled false}
                   :environment :production}]
      (is (not (compiler/should-minify? context :html-string :html)))))
  
  (testing "Should not minify when format-specific disabled"
    (let [context {:minify {:enabled true
                            :html-string {:enabled false}}
                   :environment :production}]
      (is (not (compiler/should-minify? context :html-string :html)))))
  
  (testing "Should respect environment lists"
    (let [context {:minify {:enabled true
                            :environment [:staging :production]
                            :html {:enabled true}}
                   :environment :production}]
      (is (compiler/should-minify? context :html-string :html)))))

;; ============================================================================
;; Phase 5: Optimization Implementation Tests
;; ============================================================================

(deftest test-resolve-inheritance-and-tokens
  (testing "Resolve inheritance and tokens for component"
    (let [component-def {:type :button :props {:text "Click"}}
          tokens {:color-primary "#4f46e5"}
          context {:global {:components {:button {:bg "$color-primary"}}}
                   :components {}
                   :sections {}
                   :templates {}
                   :tokens tokens}]
      (let [result (optimization/resolve-inheritance-and-tokens component-def tokens context)]
        ;; Should have resolved inheritance
        (is (map? result))
        ;; Should have resolved tokens (if token references exist)
        (is (contains? result :bg)))))
  
  (testing "Handle component without inheritance"
    (let [component-def {:type :button :props {:text "Click"}}
          tokens {}
          context {:global {}
                   :components {}
                   :sections {}
                   :templates {}
                   :tokens tokens}]
      (let [result (optimization/resolve-inheritance-and-tokens component-def tokens context)]
        (is (map? result))))))

;; ============================================================================
;; Phase 6: Build-Context Refactoring Tests
;; ============================================================================

(deftest test-build-context
  (testing "Build context with default options"
    (let [result (compiler/build-context {} {})]
      (is (contains? result :domain))
      (is (= :forma (:domain result)))
      (is (contains? result :platform-stack))
      (is (contains? result :styling-system))))
  
  (testing "Build context with project name"
    (let [result (compiler/build-context {} {:project-name "dashboard-example"})]
      (is (= "dashboard-example" (:project-name result)))))
  
  (testing "Build context with custom platform stack"
    (let [result (compiler/build-context {} {:platform-stack [:html :css]})]
      (is (= [:html :css] (:platform-stack result)))))
  
  (testing "Build context with styling stack"
    (let [result (compiler/build-context {} {:styling-stack [:tailwind :shadcn-ui]})]
      (is (= [:tailwind :shadcn-ui] (:styling-stack result)))))
  
  (testing "Build context includes hierarchy data"
    (let [result (compiler/build-context {} {})]
      (is (contains? result :global))
      (is (contains? result :components))
      (is (contains? result :sections))
      (is (contains? result :templates))))
  
  (testing "Build context merges config-driven output and minify (development overrides)"
    (let [result (compiler/build-context {} {:project-name "dashboard-example"
                                             :environment :development})]
      (is (= :hiccup (get-in result [:output :html])))
      (is (= :inline (get-in result [:output :css])))
      (is (= :attributes (get-in result [:output :htmx])))
      (is (= false (get-in result [:minify :enabled])))
      (is (= false (get-in result [:optimization :pre-compilation])))
      (is (= true (get-in result [:styling :dedupe-classes?])))))
  
  (testing "Build context merges production overrides"
    (let [result (compiler/build-context {} {:project-name "dashboard-example"
                                             :environment :production})]
      (is (= :html-file (get-in result [:output :html])))
      (is (= :css-string (get-in result [:output :css])))
      (is (= true (get-in result [:minify :enabled])))
      (is (= true (get-in result [:minify :html :enabled])))
      (is (= true (get-in result [:optimization :pre-compilation])))
      (is (= true (get-in result [:styling :dedupe-classes?]))))))

;; ============================================================================
;; Integration Tests
;; ============================================================================

(deftest test-compile-to-html-integration
  (testing "Compile simple element to HTML"
    (let [elements [[:button {:text "Click"}]]
          context {:platform-stack [:html]}]
      (let [result (compiler/compile-to-html elements context)]
        (is (string? result))
        (is (re-find #"<button" result)))))
  
  (testing "Compile with minification enabled"
    (let [elements [[:div "Test"]]
          context {:platform-stack [:html]
                   :minify {:enabled true
                           :environment :production
                           :html-string {:enabled true
                                        :remove-whitespace true}}}]
      (let [result (compiler/compile-to-html elements context)]
        (is (string? result))
        ;; Minified HTML should have less whitespace
        (is (not (re-find #"\s{2,}" result))))))
  
  (testing "Compile with optimization enabled"
    (let [elements [[:button {:text "Click"}]]
          context {:platform-stack [:html]
                   :optimization {:pre-compilation true}}]
      (let [result (compiler/compile-to-html elements context)]
        (is (string? result))
        (is (re-find #"<button" result)))))
  
  (testing "Compile respects development hiccup output selection"
    (let [elements [[:div {:class "dev"} "Test"]]
          context {:platform-stack [:html]
                   :project-name "dashboard-example"
                   :environment :development}]
      (let [result (compiler/compile-to-html elements context)]
        (is (vector? result))
        (let [first-element (first result)]
          (is (vector? first-element))
          (is (= :div (first first-element)))))))
  
  (testing "Compile respects production html-file output selection"
    (let [elements [[:div {:class "prod"} "Test"]]
          context {:platform-stack [:html]
                   :project-name "dashboard-example"
                   :environment :production}]
      (let [result (compiler/compile-to-html elements context)]
        (is (map? result))
        (is (= "index.html" (:output-path result)))
        (is (string? (:content result)))
        (is (re-find #"<div" (:content result)))))))

(deftest test-compile-to-stack-integration
  (testing "Compile with platform stack"
    (let [elements [[:button {:text "Click"}]]
          context {:platform-stack [:html :css :htmx]}]
      (let [result (compiler/compile-to-stack elements context)]
        (is (vector? result))
        (is (seq result)))))

(deftest test-styling-integration
  (testing "Styling system integration with compiler"
    (let [element {:type :button :props {:variant :primary}}
          resolved-props {:variant :primary}
          context {:styling-stack [:shadcn-ui]
                   :project-name nil}]
      ;; This tests that styling is applied through the compiler pipeline
      (let [styling-configs (compiler/load-styling-stack-memo (:styling-stack context) (:project-name context))]
        (is (seq styling-configs))
        (is (contains? (first styling-configs) :components))))))

(deftest test-platform-isolation
  (testing "HTML platform functions are isolated"
    (let [html "  <div>  Test  </div>  "
          config {:remove-whitespace true}]
      (let [minified (html-platform/minify-html-string html config)]
        (is (string? minified))
        (is (not= html minified)))))
  
  (testing "CSS platform functions are isolated"
    (let [css "  .class  {  color:  red;  }  "
          config {:remove-whitespace true}]
      (let [minified (css-platform/minify-css-string css config)]
        (is (string? minified))
        (is (not= css minified))))))

;; ============================================================================
;; Edge Cases and Error Handling
;; ============================================================================

(deftest test-edge-cases
  (testing "Handle empty styling stack"
    (let [result (styling/load-styling-stack [] mock-load-resource)]
      (is (empty? result))))
  
  (testing "Handle nil styling config"
    (let [props {:class "existing"}
          element-type :button
          styling-config nil
          resolved-props {}]
      (is (= props (styling/apply-styling-from-config props element-type styling-config resolved-props)))))
  
  (testing "Handle empty HTML string"
    (is (= "" (html-platform/minify-html-string "" {:remove-whitespace true}))))
  
  (testing "Handle empty CSS string"
    (is (= "" (css-platform/minify-css-string "" {:remove-whitespace true}))))
  
  (testing "Handle nil context in build-context"
    (let [result (compiler/build-context nil {})]
      (is (map? result))
      (is (contains? result :domain)))))

;; ============================================================================
;; Performance Tests
;; ============================================================================

(deftest test-performance
  (testing "Styling deep-merge performance"
    (let [large-map (into {} (map (fn [i] [i {:nested (into {} (map (fn [j] [j j]) (range 100)))}]) (range 100)))]
      (time
        (dotimes [_ 100]
          (styling/deep-merge large-map {:new-key :new-value})))))
  
  (testing "HTML minification performance"
    (let [large-html (apply str (repeat 1000 "<div>  Test  </div>  "))
          config {:remove-whitespace true :remove-comments true}]
      (time
        (dotimes [_ 100]
          (html-platform/minify-html-string large-html config))))))
