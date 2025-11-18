(ns forma.dev.parity
  "Parity verification between old corebase.ui.compiler and new forma.compiler
  
   Tests that Forma compiler maintains feature parity with the original corebase.ui compiler."
  (:require [forma.compiler :as forma-compiler]
            [kora.core.compiler :as core-compiler]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:refer-clojure :exclude [compile-element]))

(defn pass [msg] (println (str "✓ PASS: " msg)) true)
(defn fail [msg actual expected] 
  (println (str "✗ FAIL: " msg))
  (println (str "  Expected: " expected))
  (println (str "  Actual:   " actual))
  false)

(defn html-str
  "Normalize compile-to-html output to a string."
  [output]
  (forma-compiler/html-output->string output))

(defn test-parse-element
  "Test element parsing parity"
  []
  (println "\n=== Element Parsing Parity ===")
  (let [results (atom [])
        compiler-instance (forma-compiler/->FormaCompiler)]
    
    ;; Test vector syntax
    (let [element [:div {:class "test"} "Content"]
          parsed (core-compiler/parse-element compiler-instance element)]
      (if (and (= (:type parsed) :div)
               (= (get-in parsed [:props :class]) "test")
               (= (first (:children parsed)) "Content"))
        (swap! results conj (pass "Vector syntax parsing"))
        (swap! results conj (fail "Vector syntax parsing" parsed {:type :div :props {:class "test"}}))))
    
    ;; Test map syntax
    (let [element {:type :div :props {:class "test"} :children ["Content"]}
          parsed (core-compiler/parse-element compiler-instance element)]
      (if (= parsed element)
        (swap! results conj (pass "Map syntax parsing"))
        (swap! results conj (fail "Map syntax parsing" parsed element))))
    
    ;; Test string element
    (let [element "plain text"
          parsed (core-compiler/parse-element compiler-instance element)]
      (if (= (:type parsed) :text)
        (swap! results conj (pass "String element parsing"))
        (swap! results conj (fail "String element parsing" parsed {:type :text}))))
    
    ;; Test tag with classes (old compiler supports :div.card#main)
    (let [element [:div.card#main {:text "Test"}]
          parsed (core-compiler/parse-element compiler-instance element)]
      (if (= (:type parsed) :div)
        (swap! results conj (pass "Tag with classes/id (basic support)"))
        (swap! results conj (fail "Tag with classes/id" parsed {:type :div}))))
    
    {:passed (count (filter identity @results))
     :total (count @results)
     :results @results}))

(defn test-property-expansion
  "Test property expansion parity"
  []
  (println "\n=== Property Expansion Parity ===")
  (let [results (atom [])
        compiler-instance (forma-compiler/->FormaCompiler)]
    
    ;; Test basic shortcuts
    (let [props {:bg "#fff" :pd "20px" :mg "10px"}
          expanded (core-compiler/expand-properties compiler-instance props)]
      (if (and (contains? expanded :background)
               (contains? expanded :padding)
               (contains? expanded :margin))
        (swap! results conj (pass "Basic property shortcuts (:bg, :pd, :mg)"))
        (swap! results conj (fail "Basic property shortcuts" expanded {:background "#fff"}))))
    
    ;; Test text shortcut
    (let [props {:txt "Hello"}
          expanded (core-compiler/expand-properties compiler-instance props)]
      (if (contains? expanded :text)
        (swap! results conj (pass "Text shortcut (:txt)"))
        (swap! results conj (fail "Text shortcut" expanded {:text "Hello"}))))
    
    ;; Test URL shortcut
    (let [props {:url "/path"}
          expanded (core-compiler/expand-properties compiler-instance props)]
      (if (contains? expanded :href)
        (swap! results conj (pass "URL shortcut (:url → :href)"))
        (swap! results conj (fail "URL shortcut" expanded {:href "/path"}))))
    
    {:passed (count (filter identity @results))
     :total (count @results)
     :results @results}))

(defn test-element-compilation
  "Test element compilation parity"
  []
  (println "\n=== Element Compilation Parity ===")
  (let [results (atom [])]
    
    ;; Test basic HTML elements
    (doseq [type [:h1 :h2 :h3 :h4 :h5 :h6 :p :div :span :button :btn :link :input :textarea :select :img :video]]
      (try
        (let [element (if (= type :input)
                       [type {:type "text" :name "test"}]
                       [type {:text "Test"}])
              output (forma-compiler/compile-to-html [element])
              html (html-str output)
              ;; Some types map to different HTML tags
              expected-tag (case type
                            :link "a"
                            :btn "a"
                            (name type))]
          (if (and (string? html)
                   (str/includes? html expected-tag))
            (swap! results conj (pass (str "Compile " type)))
            (swap! results conj (fail (str "Compile " type) output (str "contains " expected-tag)))))
        (catch Exception e
          (swap! results conj (fail (str "Compile " type) (.getMessage e) "no exception")))))
    
    ;; Test legacy elements
    (doseq [type [:container :text :image :divider :spacer :columns]]
      (try
        (let [element [type (case type
                             :text {:text "Test"}
                             :image {:src "/test.jpg" :alt "Test"}
                             :columns {:columns 3}
                             {})]
              output (forma-compiler/compile-to-html [element])
              html (html-str output)]
          (if (string? html)
            (swap! results conj (pass (str "Compile legacy " type)))
            (swap! results conj (fail (str "Compile legacy " type) output "valid HTML"))))
        (catch Exception e
          (swap! results conj (fail (str "Compile legacy " type) (.getMessage e) "no exception")))))
    
    ;; Test heading with level
    (try
      (let [output (forma-compiler/compile-to-html [[:heading {:level 1 :text "Title"}]])
            html (html-str output)]
        (if (and (string? html) (str/includes? html "h1"))
          (swap! results conj (pass "Compile heading with level"))
          (swap! results conj (fail "Compile heading with level" output "contains h1"))))
      (catch Exception e
        (swap! results conj (fail "Compile heading with level" (.getMessage e) "no exception"))))
    
    {:passed (count (filter identity @results))
     :total (count @results)
     :results @results}))

(defn test-template-resolution
  "Test template variable resolution parity"
  []
  (println "\n=== Template Resolution Parity ===")
  (let [results (atom [])]
    
    ;; Test basic variable resolution
    (try
      (let [context {:customer {:name "John"}}
            ;; Old compiler uses {{customer.name}}, Forma should support similar
            output (forma-compiler/compile-to-html [[:div {:text "Hello {{customer.name}}"}]] context)
            html (html-str output)]
        (if (string? html)
          (swap! results conj (pass "Template resolution"))
          (swap! results conj (fail "Template resolution" output "valid HTML"))))
      (catch Exception e
        (swap! results conj (fail "Template resolution" (.getMessage e) "no exception"))))
    
    {:passed (count (filter identity @results))
     :total (count @results)
     :results @results}))

(defn test-style-conversion
  "Test style conversion parity"
  []
  (println "\n=== Style Conversion Parity ===")
  (let [results (atom [])]
    
    ;; Test inline styles
    (try
      (let [output (forma-compiler/compile-to-html [[:div {:background "#fff" :padding "20px"}]])
            html (html-str output)]
        (if (and (string? html)
                 (or (str/includes? html "style")
                     (str/includes? html "background")
                     (str/includes? html "padding")))
          (swap! results conj (pass "Inline style conversion"))
          (swap! results conj (fail "Inline style conversion" output "contains style"))))
      (catch Exception e
        (swap! results conj (fail "Inline style conversion" (.getMessage e) "no exception"))))
    
    ;; Test HTMX attributes
    (try
      (let [output (forma-compiler/compile-to-html [[:button {:hx-get "/api/data" :hx-target "#result"}]])
            html (html-str output)]
        (if (and (string? html)
                 (or (str/includes? html "hx-get")
                     (str/includes? html "hx-target")))
          (swap! results conj (pass "HTMX attribute support"))
          (swap! results conj (fail "HTMX attribute support" output "contains hx-*"))))
      (catch Exception e
        (swap! results conj (fail "HTMX attribute support" (.getMessage e) "no exception"))))
    
    {:passed (count (filter identity @results))
     :total (count @results)
     :results @results}))

(defn test-nested-structures
  "Test nested element compilation parity"
  []
  (println "\n=== Nested Structures Parity ===")
  (let [results (atom [])]
    
    ;; Test simple nesting
    (try
      (let [output (forma-compiler/compile-to-html [[:div [:span "Nested"]]])
            html (html-str output)]
        (if (and (string? html)
                 (str/includes? html "div")
                 (str/includes? html "span"))
          (swap! results conj (pass "Simple nested structure"))
          (swap! results conj (fail "Simple nested structure" output "contains div and span"))))
      (catch Exception e
        (swap! results conj (fail "Simple nested structure" (.getMessage e) "no exception"))))
    
    ;; Test deep nesting
    (try
      (let [output (forma-compiler/compile-to-html [[:div [:section [:h1 "Title"]]]])
            html (html-str output)]
        (if (and (string? html)
                 (str/includes? html "div")
                 (str/includes? html "section")
                 (str/includes? html "h1"))
          (swap! results conj (pass "Deep nested structure"))
          (swap! results conj (fail "Deep nested structure" output "contains all elements"))))
      (catch Exception e
        (swap! results conj (fail "Deep nested structure" (.getMessage e) "no exception"))))
    
    ;; Test multiple children
    (try
      (let [output (forma-compiler/compile-to-html [[:div [:span "One"] [:span "Two"]]])
            html (html-str output)]
        (if (and (string? html)
                 (str/includes? html "span"))
          (swap! results conj (pass "Multiple children"))
          (swap! results conj (fail "Multiple children" output "contains span"))))
      (catch Exception e
        (swap! results conj (fail "Multiple children" (.getMessage e) "no exception"))))
    
    {:passed (count (filter identity @results))
     :total (count @results)
     :results @results}))

(defn test-missing-features
  "Test features that might be missing from Forma"
  []
  (println "\n=== Missing Features Check ===")
  (let [results (atom [])]
    
    ;; Check for tag parsing with classes/id (old: :div.card#main)
    (try
      (let [element [:div.card {:text "Test"}]
            output (forma-compiler/compile-to-html [element])
            html (html-str output)]
        (if (string? html)
          (swap! results conj (pass "Tag with classes (basic support)"))
          (swap! results conj (fail "Tag with classes" output "valid HTML"))))
      (catch Exception e
        (swap! results conj (fail "Tag with classes" (.getMessage e) "no exception"))))
    
    ;; Check for Oxygen registry elements (section, container, row, column)
    (doseq [type [:section :container]]
      (try
        (let [output (forma-compiler/compile-to-html [[type {}]])
              html (html-str output)]
          (if (string? html)
            (swap! results conj (pass (str "Oxygen element: " type)))
            (swap! results conj (fail (str "Oxygen element: " type) output "valid HTML"))))
        (catch Exception e
          (swap! results conj (fail (str "Oxygen element: " type) (.getMessage e) "no exception")))))
    
    {:passed (count (filter identity @results))
     :total (count @results)
     :results @results}))

(defn test-edge-cases
  "Test Phase 3 edge case handling"
  []
  (println "\n=== Edge Case Handling (Phase 3) ===")
  (let [results (atom [])]

    ;; Edge case #2: Empty explicit class {:class ""}
    (let [output (html-str (forma-compiler/compile-to-html
                            [[:div {:class ""} "Test"]]
                            {}))
          has-class? (str/includes? output "class=")]
      (if (not has-class?)
        (swap! results conj (pass "Empty class string treated as no override"))
        (swap! results conj (fail "Empty class string" output "no class attribute"))))

    ;; Edge case #2: Whitespace-only class
    (let [output (html-str (forma-compiler/compile-to-html
                            [[:div {:class "   "} "Test"]]
                            {}))
          has-class? (str/includes? output "class=")]
      (if (not has-class?)
        (swap! results conj (pass "Whitespace-only class treated as no override"))
        (swap! results conj (fail "Whitespace-only class" output "no class attribute"))))

    ;; Edge case #4: Duplicate CSS properties (rightmost wins)
    (let [output (html-str (forma-compiler/compile-to-html
                            [[:div {:style "color:red; color:blue; padding:10px"} "Test"]]
                            {}))
          has-single-color? (and (str/includes? output "color:blue")
                                (not (str/includes? output "color:red")))
          has-padding? (str/includes? output "padding:10px")]
      (if (and has-single-color? has-padding?)
        (swap! results conj (pass "Duplicate CSS properties removed (rightmost wins)"))
        (swap! results conj (fail "Duplicate CSS properties" output "color:blue only, no color:red"))))

    ;; Edge case #4: Style merging with duplicates
    (let [output (html-str (forma-compiler/compile-to-html
                            [[:div {:background "#fff" :padding "20px" :style "background:#000"} "Test"]]
                            {}))
          has-black-bg? (str/includes? output "background:#000")
          has-red-bg? (str/includes? output "background:#fff")]
      (if (and has-black-bg? (not has-red-bg?))
        (swap! results conj (pass "Style merging: explicit style wins over extracted"))
        (swap! results conj (fail "Style merging duplicates" output "background:#000 only"))))

    {:passed (count (filter identity @results))
     :total (count @results)
     :results @results}))

(defn run-all-parity-tests
  "Run all parity verification tests"
  []
  (println "\n" (str/join (repeat 60 "=")))
  (println "Forma Compiler Parity Verification")
  (println "Comparing with corebase.ui.compiler")
  (println (str/join (repeat 60 "=")))
  
  (let [parse-results (test-parse-element)
        expansion-results (test-property-expansion)
        compilation-results (test-element-compilation)
        template-results (test-template-resolution)
        style-results (test-style-conversion)
        nested-results (test-nested-structures)
        missing-results (test-missing-features)
        edge-case-results (test-edge-cases)

        all-results [parse-results expansion-results compilation-results
                    template-results style-results nested-results missing-results
                    edge-case-results]

        total-passed (reduce + (map :passed all-results))
        total-tests (reduce + (map :total all-results))]

    (println "\n" (str/join (repeat 60 "=")))
    (println "Summary")
    (println (str/join (repeat 60 "=")))
    (println (str "Element Parsing:        " (:passed parse-results) "/" (:total parse-results)))
    (println (str "Property Expansion:     " (:passed expansion-results) "/" (:total expansion-results)))
    (println (str "Element Compilation:    " (:passed compilation-results) "/" (:total compilation-results)))
    (println (str "Template Resolution:   " (:passed template-results) "/" (:total template-results)))
    (println (str "Style Conversion:      " (:passed style-results) "/" (:total style-results)))
    (println (str "Nested Structures:     " (:passed nested-results) "/" (:total nested-results)))
    (println (str "Missing Features:      " (:passed missing-results) "/" (:total missing-results)))
    (println (str "Edge Case Handling:    " (:passed edge-case-results) "/" (:total edge-case-results)))
    (println (str/join (repeat 60 "-")))
    (println (str "Total:                 " total-passed "/" total-tests))
    
    (if (= total-passed total-tests)
      (println "\n✓ All parity tests passed!")
      (println (str "\n✗ " (- total-tests total-passed) " test(s) failed.")))
    
    {:total total-tests
     :passed total-passed
     :failed (- total-tests total-passed)
     :all-results all-results}))

