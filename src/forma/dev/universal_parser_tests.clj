(ns forma.dev.universal-parser-tests
  "Test suite for universal parser (Phase 2 implementation).

  Tests:
  - HTML → Forma EDN parsing
  - JSX → Forma EDN parsing
  - Bidirectional attribute mappings
  - Transform registry (kebab<->camel, etc.)
  - Metadata preservation (sync mode)
  - Round-trip compilation (parse(compile(x)) == x)"
  (:require [forma.parsers.universal :as parser]
            [forma.compiler :as compiler]
            [forma.output.transformer :as transformer]
            [clojure.test :refer [deftest is testing]]))

;; =============================================================================
;; Test Fixtures
;; =============================================================================

(def html-config (compiler/load-platform-config :html))
(def react-config (compiler/load-platform-config :react))

;; =============================================================================
;; Transform Registry Tests
;; =============================================================================

(deftest test-transform-registry
  (testing "kebab<->camel bidirectional transform"
    (let [forward (parser/get-transform :kebab<->camel :forward)
          reverse (parser/get-transform :kebab<->camel :reverse)]

      ;; Forward: kebab → camel
      (is (= "onClick" (forward :on-click)))
      (is (= "backgroundColor" (forward :background-color)))
      (is (= "dataId" (forward :data-id)))

      ;; Reverse: camel → kebab
      (is (= "on-click" (reverse "onClick")))
      (is (= "background-color" (reverse "backgroundColor")))
      (is (= "data-id" (reverse "dataId")))))

  (testing "kebab<->pascal bidirectional transform"
    (let [forward (parser/get-transform :kebab<->pascal :forward)
          reverse (parser/get-transform :kebab<->pascal :reverse)]

      ;; Forward: kebab → Pascal
      (is (= "MyComponent" (forward :my-component)))
      (is (= "UserProfile" (forward :user-profile)))

      ;; Reverse: Pascal → kebab
      (is (= "my-component" (reverse "MyComponent")))
      (is (= "user-profile" (reverse "UserProfile")))))

  (testing "preserve-kebab (no transformation)"
    (let [forward (parser/get-transform :preserve-kebab :forward)
          reverse (parser/get-transform :preserve-kebab :reverse)]

      (is (= "aria-label" (forward :aria-label)))
      (is (= "aria-label" (reverse :aria-label)))
      (is (= "data-id" (forward :data-id)))
      (is (= "data-id" (reverse :data-id))))))

;; =============================================================================
;; HTML Parsing Tests
;; =============================================================================

(deftest test-html-parsing-basic
  (testing "Parse simple HTML to Forma EDN"
    (let [html "<div class=\"card\">Hello World</div>"
          result (parser/parse html {:input-format :html
                                      :platform-config html-config})]
      (is (= [:div {:class "card"} "Hello World"] result))))

  (testing "Parse HTML with nested elements"
    (let [html "<div class=\"container\"><h1>Title</h1><p>Content</p></div>"
          result (parser/parse html {:input-format :html
                                      :platform-config html-config})]
      (is (= [:div {:class "container"}
              [:h1 "Title"]
              [:p "Content"]]
             result))))

  (testing "Parse HTML with multiple attributes"
    (let [html "<input type=\"text\" placeholder=\"Enter name\" class=\"input\" />"
          result (parser/parse html {:input-format :html
                                      :platform-config html-config})]
      (is (= [:input {:type "text"
                      :placeholder "Enter name"
                      :class "input"}]
             result)))))

(deftest test-html-attribute-mapping
  (testing "Map href to :url (Forma convention)"
    (let [html "<a href=\"/about\" class=\"link\">About</a>"
          result (parser/parse html {:input-format :html
                                      :platform-config html-config})]
      (is (= [:a {:url "/about" :class "link"} "About"] result))))

  (testing "Parse event handlers (onclick → on-click)"
    (let [html "<button onclick=\"handleClick()\" class=\"btn\">Click</button>"
          result (parser/parse html {:input-format :html
                                      :platform-config html-config})]
      (is (= [:button {:on-click "handleClick()" :class "btn"} "Click"] result))))

  (testing "Preserve ARIA attributes"
    (let [html "<div aria-label=\"Main content\" role=\"main\">Content</div>"
          result (parser/parse html {:input-format :html
                                      :platform-config html-config})]
      (is (= [:div {:aria-label "Main content" :role "main"} "Content"] result)))))

;; =============================================================================
;; JSX Parsing Tests
;; =============================================================================

(deftest test-jsx-parsing-basic
  (testing "Parse simple JSX to Forma EDN"
    (let [jsx "<div className=\"card\">Hello React</div>"
          result (parser/parse jsx {:input-format :jsx
                                     :platform-config react-config})]
      (is (= [:div {:class "card"} "Hello React"] result))))

  (testing "Parse JSX with camelCase event handlers"
    (let [jsx "<button onClick={handleClick} className=\"btn\">Click</button>"
          result (parser/parse jsx {:input-format :jsx
                                     :platform-config react-config})]
      (is (= [:button {:on-click "handleClick" :class "btn"} "Click"] result))))

  (testing "Parse JSX with multiple camelCase attributes"
    (let [jsx "<input type=\"text\" autoComplete=\"off\" autoFocus={true} />"
          result (parser/parse jsx {:input-format :jsx
                                     :platform-config react-config})]
      (is (= [:input {:type "text"
                      :autocomplete "off"
                      :autofocus "true"}]
             result)))))

(deftest test-jsx-attribute-mapping
  (testing "className → :class"
    (let [jsx "<div className=\"container\" />"
          result (parser/parse jsx {:input-format :jsx
                                     :platform-config react-config})]
      (is (= [:div {:class "container"}] result))))

  (testing "Event handlers: onClick → :on-click"
    (let [jsx "<button onClick={handler} onMouseEnter={enter}>Hover</button>"
          result (parser/parse jsx {:input-format :jsx
                                     :platform-config react-config})]
      (is (= [:button {:on-click "handler" :on-mouse-enter "enter"} "Hover"] result))))

  (testing "Preserve ARIA attributes in JSX"
    (let [jsx "<div aria-label=\"content\" data-testid=\"test-div\">Content</div>"
          result (parser/parse jsx {:input-format :jsx
                                     :platform-config react-config})]
      (is (= [:div {:aria-label "content" :data-testid "test-div"} "Content"] result)))))

;; =============================================================================
;; Metadata Preservation Tests (Sync Mode)
;; =============================================================================

(deftest test-metadata-preservation
  (testing "Extract Forma metadata from data attributes"
    (let [html "<button class=\"btn\" data-forma-type=\"button\" data-forma-variant=\"primary\">Click</button>"
          result (parser/parse html {:input-format :html
                                      :platform-config html-config
                                      :preserve-metadata? true})]
      ;; Note: Metadata values are keywordized for better EDN representation
      (is (= [:button {:class "btn"
                       :_forma-metadata {:type :button
                                         :variant :primary}}
              "Click"]
             result))))

  (testing "Parse without metadata preservation (export mode)"
    (let [html "<button class=\"btn\" data-forma-type=\"button\">Click</button>"
          result (parser/parse html {:input-format :html
                                      :platform-config html-config
                                      :preserve-metadata? false})]
      ;; data-forma-* attributes should be treated as regular data attributes
      (is (contains? (second result) :data-forma-type))))

  (testing "Metadata preservation with nested elements"
    (let [html "<div class=\"card\" data-forma-component=\"card\">
                  <h2 data-forma-slot=\"title\">Title</h2>
                  <p data-forma-slot=\"content\">Content</p>
                </div>"
          result (parser/parse html {:input-format :html
                                      :platform-config html-config
                                      :preserve-metadata? true})]
      (is (= :card (get-in result [1 :_forma-metadata :component])))
      (is (= :title (get-in result [2 1 :_forma-metadata :slot])))
      (is (= :content (get-in result [3 1 :_forma-metadata :slot]))))))

;; =============================================================================
;; Round-Trip Compilation Tests
;; =============================================================================

(deftest test-round-trip-html
  (testing "HTML round-trip: EDN → HTML → EDN"
    (let [original-edn [:div {:class "card"} [:h1 "Title"] [:p "Content"]]
          compiled-html (compiler/compile-to-html original-edn {:platform-stack [:html]})
          parsed-edn (parser/parse compiled-html {:input-format :html
                                                   :platform-config html-config})]
      (is (= original-edn parsed-edn))))

  (testing "HTML round-trip with attributes"
    (let [original-edn [:button {:class "btn btn-primary"
                                 :on-click "handleClick()"
                                 :aria-label "Submit button"}
                        "Submit"]
          compiled-html (compiler/compile-to-html original-edn {:platform-stack [:html]})
          parsed-edn (parser/parse compiled-html {:input-format :html
                                                   :platform-config html-config})]
      (is (= original-edn parsed-edn)))))

(deftest test-round-trip-jsx
  (testing "JSX round-trip: EDN → JSX → EDN"
    (let [original-edn [:div {:class "container"} [:h1 "React App"]]
          ;; Note: We'll need to implement JSX compilation, for now test parsing only
          jsx-string "<div className=\"container\"><h1>React App</h1></div>"
          parsed-edn (parser/parse jsx-string {:input-format :jsx
                                                :platform-config react-config})]
      (is (= original-edn parsed-edn))))

  (testing "JSX round-trip with event handlers"
    (let [original-edn [:button {:class "btn"
                                 :on-click "handleClick"
                                 :on-mouse-enter "handleHover"}
                        "Hover Me"]
          jsx-string "<button className=\"btn\" onClick={handleClick} onMouseEnter={handleHover}>Hover Me</button>"
          parsed-edn (parser/parse jsx-string {:input-format :jsx
                                                :platform-config react-config})]
      (is (= original-edn parsed-edn)))))

(deftest test-round-trip-validation
  (testing "Validate round-trip property with validation function"
    (let [edn [:div {:class "test"} "Content"]
          html "<div class=\"test\">Content</div>"
          result (parser/validate-round-trip edn html {:input-format :html
                                                        :platform-config html-config})]
      (is (:valid? result))
      (is (= edn (:original result)))
      (is (= edn (:round-trip result)))
      (is (nil? (:differences result)))))

  (testing "Detect round-trip failures"
    (let [edn [:div {:class "original"} "Content"]
          html "<div class=\"modified\">Different</div>"
          result (parser/validate-round-trip edn html {:input-format :html
                                                        :platform-config html-config})]
      (is (not (:valid? result)))
      (is (some? (:differences result))))))

;; =============================================================================
;; Edge Cases
;; =============================================================================

(deftest test-edge-cases
  (testing "Parse self-closing tags"
    (let [html "<img src=\"logo.png\" alt=\"Logo\" />"
          result (parser/parse html {:input-format :html
                                      :platform-config html-config})]
      (is (= [:img {:src "logo.png" :alt "Logo"}] result))))

  (testing "Parse empty elements"
    (let [html "<div></div>"
          result (parser/parse html {:input-format :html
                                      :platform-config html-config})]
      (is (= [:div] result))))

  (testing "Parse elements with only text"
    (let [html "<p>Simple text</p>"
          result (parser/parse html {:input-format :html
                                      :platform-config html-config})]
      (is (= [:p "Simple text"] result))))

  (testing "Parse deeply nested structures"
    (let [html "<div><div><div><p>Deep</p></div></div></div>"
          result (parser/parse html {:input-format :html
                                      :platform-config html-config})]
      (is (= [:div [:div [:div [:p "Deep"]]]] result)))))

;; =============================================================================
;; Test Runner
;; =============================================================================

(defn run-universal-parser-tests
  "Run all universal parser tests and report results."
  []
  (println "\n========================================")
  (println "Universal Parser Tests (Phase 2)")
  (println "========================================\n")

  (let [test-vars [#'test-transform-registry
                   #'test-html-parsing-basic
                   #'test-html-attribute-mapping
                   #'test-jsx-parsing-basic
                   #'test-jsx-attribute-mapping
                   #'test-metadata-preservation
                   #'test-round-trip-html
                   #'test-round-trip-jsx
                   #'test-round-trip-validation
                   #'test-edge-cases]
        results (map (fn [test-var]
                      (let [result (clojure.test/test-var test-var)]
                        {:name (str test-var)
                         :pass (or (:pass result) 0)
                         :fail (or (:fail result) 0)
                         :error (or (:error result) 0)}))
                    test-vars)
        total-pass (reduce + (map :pass results))
        total-fail (reduce + (map :fail results))
        total-error (reduce + (map :error results))
        total-tests (count test-vars)
        success-rate (if (> total-tests 0)
                      (int (* 100 (/ (- total-tests (+ total-fail total-error)) total-tests)))
                      0)]

    (println "\n========================================")
    (println "Test Results:")
    (println "========================================")
    (println (format "Tests run: %d" total-tests))
    (println (format "Assertions passed: %d" total-pass))
    (println (format "Assertions failed: %d" total-fail))
    (println (format "Errors: %d" total-error))
    (println (format "Success rate: %d%%" success-rate))
    (println "========================================")

    (when (and (zero? total-fail) (zero? total-error))
      (println "✅ ALL UNIVERSAL PARSER TESTS PASSING!"))

    {:tests total-tests
     :pass total-pass
     :fail total-fail
     :error total-error
     :success-rate success-rate}))

(comment
  ;; Run tests
  (run-universal-parser-tests)

  ;; Run individual test
  (test-transform-registry)
  (test-html-parsing-basic)
  (test-jsx-parsing-basic)
  (test-metadata-preservation)
  (test-round-trip-html)
  )
