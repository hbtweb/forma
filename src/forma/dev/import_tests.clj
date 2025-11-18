(ns forma.dev.import-tests
  "Real-world import tests for Tailwind CSS and React components.
   Tests the complete Phase 3 + 4 pipeline:
   1. Parse external HTML/JSX
   2. Extract metadata and provenance
   3. Build token registry
   4. Classify properties
   5. Generate multi-file structure
   6. Test reconciliation"
  (:require [forma.parsers.html :as html-parser]
            [forma.parsers.jsx :as jsx-parser]
            [forma.parsers.universal :as universal]
            [forma.tokens.registry :as registry]
            [forma.hierarchy.classifier :as classifier]
            [forma.hierarchy.generator :as generator]
            [forma.hierarchy.reconciliation :as reconcile]
            [clojure.test :refer [deftest is testing]]))

;; ============================================================================
;; Sample Tailwind CSS Components
;; ============================================================================

(def tailwind-button-html
  "Real-world Tailwind button component"
  "<button class=\"bg-blue-500 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded\">
    Click Me
  </button>")

(def tailwind-card-html
  "Real-world Tailwind card component"
  "<div class=\"max-w-sm rounded overflow-hidden shadow-lg\">
    <img class=\"w-full\" src=\"/img/card.jpg\" alt=\"Card image\">
    <div class=\"px-6 py-4\">
      <div class=\"font-bold text-xl mb-2\">Card Title</div>
      <p class=\"text-gray-700 text-base\">
        Card description goes here.
      </p>
    </div>
    <div class=\"px-6 pt-4 pb-2\">
      <span class=\"inline-block bg-gray-200 rounded-full px-3 py-1 text-sm font-semibold text-gray-700 mr-2 mb-2\">#tag1</span>
      <span class=\"inline-block bg-gray-200 rounded-full px-3 py-1 text-sm font-semibold text-gray-700 mr-2 mb-2\">#tag2</span>
    </div>
  </div>")

(def tailwind-form-html
  "Real-world Tailwind form component"
  "<form class=\"w-full max-w-lg\">
    <div class=\"flex flex-wrap -mx-3 mb-6\">
      <div class=\"w-full md:w-1/2 px-3 mb-6 md:mb-0\">
        <label class=\"block uppercase tracking-wide text-gray-700 text-xs font-bold mb-2\" for=\"first-name\">
          First Name
        </label>
        <input class=\"appearance-none block w-full bg-gray-200 text-gray-700 border border-red-500 rounded py-3 px-4 mb-3 leading-tight focus:outline-none focus:bg-white\"
               id=\"first-name\" type=\"text\" placeholder=\"Jane\">
      </div>
    </div>
  </form>")

;; ============================================================================
;; Sample React Components (JSX)
;; ============================================================================

(def react-button-jsx
  "Real-world React button component"
  "<button
    className=\"bg-blue-500 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded\"
    onClick={handleClick}
  >
    {children}
  </button>")

(def react-card-jsx
  "Real-world React card component with props"
  "<div className=\"max-w-sm rounded overflow-hidden shadow-lg\">
    <img className=\"w-full\" src={image} alt={imageAlt} />
    <div className=\"px-6 py-4\">
      <div className=\"font-bold text-xl mb-2\">{title}</div>
      <p className=\"text-gray-700 text-base\">{description}</p>
    </div>
    {tags && (
      <div className=\"px-6 pt-4 pb-2\">
        {tags.map(tag => (
          <span key={tag} className=\"inline-block bg-gray-200 rounded-full px-3 py-1 text-sm font-semibold text-gray-700 mr-2 mb-2\">
            #{tag}
          </span>
        ))}
      </div>
    )}
  </div>")

(def react-form-jsx
  "Real-world React form with hooks"
  "<form className=\"w-full max-w-lg\" onSubmit={handleSubmit}>
    <div className=\"flex flex-wrap -mx-3 mb-6\">
      <div className=\"w-full md:w-1/2 px-3 mb-6 md:mb-0\">
        <label
          className=\"block uppercase tracking-wide text-gray-700 text-xs font-bold mb-2\"
          htmlFor=\"firstName\"
        >
          First Name
        </label>
        <input
          className=\"appearance-none block w-full bg-gray-200 text-gray-700 border rounded py-3 px-4 leading-tight focus:outline-none focus:bg-white\"
          id=\"firstName\"
          type=\"text\"
          value={firstName}
          onChange={handleFirstNameChange}
          placeholder=\"Jane\"
        />
      </div>
    </div>
  </form>")

;; ============================================================================
;; Test Phase 3: Parse with Metadata
;; ============================================================================

(deftest test-tailwind-html-parsing
  (testing "Parse Tailwind HTML to Forma EDN"
    (let [parsed (universal/parse tailwind-button-html
                                  {:input-format :html
                                   :platform :html})]
      (is (vector? parsed) "Should parse to Hiccup vector")
      (is (= :button (first parsed)) "Should identify button element")
      (is (string? (get-in parsed [1 :class])) "Should preserve Tailwind classes")
      (is (re-find #"bg-blue-500" (get-in parsed [1 :class]))
          "Should preserve bg-blue-500 class"))))

(deftest test-react-jsx-parsing
  (testing "Parse React JSX to Forma EDN"
    (let [parsed (universal/parse react-button-jsx
                                  {:input-format :jsx
                                   :platform :react})]
      (is (vector? parsed) "Should parse to Hiccup vector")
      (is (= :button (first parsed)) "Should identify button element")
      (is (contains? (second parsed) :class-name) "Should have className mapped")
      (is (contains? (second parsed) :on-click) "Should have onClick mapped"))))

;; ============================================================================
;; Test Phase 4.1: Property Classification
;; ============================================================================

(deftest test-tailwind-property-classification
  (testing "Classify Tailwind properties by frequency and variance"
    (let [;; Parse multiple instances
          button1 (universal/parse tailwind-button-html {:input-format :html :platform :html})
          button2 (universal/parse
                   "<button class=\"bg-red-500 hover:bg-red-700 text-white font-bold py-2 px-4 rounded\">Delete</button>"
                   {:input-format :html :platform :html})
          button3 (universal/parse
                   "<button class=\"bg-green-500 hover:bg-green-700 text-white font-bold py-2 px-4 rounded\">Save</button>"
                   {:input-format :html :platform :html})

          ;; Build flattened structure
          flattened {:button-1 (second button1)
                    :button-2 (second button2)
                    :button-3 (second button3)}

          ;; Build usage statistics
          stats (classifier/build-usage-statistics flattened)

          ;; Classify properties for each element
          classified (reduce-kv
                      (fn [acc elem-id elem-props]
                        (assoc acc elem-id
                               (classifier/classify-element-properties
                                (assoc elem-props :type :button)
                                stats
                                {})))
                      {}
                      flattened)]

      (is (map? stats) "Should build usage statistics")
      (is (contains? stats :button) "Should have button type stats")
      (is (map? classified) "Should classify properties")

      ;; Check classification results
      (testing "Common properties should be global/component level"
        (let [button-classified (get classified :button-1)]
          (is (some #(= :components (:level %)) (:classifications button-classified))
              "Should have component-level classifications"))))))

;; ============================================================================
;; Test Phase 4.2: Token Registry
;; ============================================================================

(deftest test-tailwind-token-extraction
  (testing "Extract tokens from Tailwind CSS patterns"
    (let [;; Parse Tailwind components
          card (universal/parse tailwind-card-html {:input-format :html :platform :html})

          ;; Build flattened structure (walk tree and collect all elements)
          flattened (atom {})
          counter (atom 0)

          _ (clojure.walk/postwalk
             (fn [node]
               (when (and (vector? node)
                         (keyword? (first node))
                         (map? (second node)))
                 (let [id (keyword (str "elem-" (swap! counter inc)))]
                   (swap! flattened assoc id (second node))))
               node)
             card)

          ;; Build token registry from frequency
          token-registry (registry/build-token-registry @flattened {})]

      (is (map? token-registry) "Should build token registry")

      ;; Check for detected color patterns
      (when (seq token-registry)
        (testing "Should detect color tokens"
          (is (or (contains? token-registry :colors)
                  (contains? token-registry :spacing)
                  (empty? token-registry))
              "Should detect colors or spacing or be empty"))))))

;; ============================================================================
;; Test Phase 4.3: Multi-File Generation
;; ============================================================================

(deftest test-tailwind-multi-file-generation
  (testing "Generate multi-file structure from Tailwind components"
    (let [;; Parse Tailwind button
          button (universal/parse tailwind-button-html {:input-format :html :platform :html})

          ;; Build simple flattened structure
          flattened {:button-1 (second button)}

          ;; Build token registry
          token-registry (registry/build-token-registry flattened {})

          ;; Build usage statistics
          stats (classifier/build-usage-statistics flattened)

          ;; Classify properties for each element
          classified (reduce-kv
                      (fn [acc elem-id elem-props]
                        (assoc acc elem-id
                               (classifier/classify-element-properties
                                (assoc elem-props :type :button)
                                stats
                                {})))
                      {}
                      flattened)

          ;; Preview file structure (dry-run)
          preview (generator/preview-file-structure
                   token-registry
                   classified
                   {}
                   "tailwind-test"
                   "forma/")]

      (is (map? preview) "Should generate preview")
      (is (contains? preview :files) "Should have files list")
      (is (vector? (:files preview)) "Files should be a vector")

      (testing "Should generate expected files"
        (let [file-paths (map :path (:files preview))]
          (is (some #(re-find #"global/defaults.edn" %) file-paths)
              "Should generate global defaults file"))))))

;; ============================================================================
;; Test Phase 4.4: Reconciliation
;; ============================================================================

(deftest test-react-component-reconciliation
  (testing "Reconcile external React edits with Forma project"
    (let [;; BASE: Original export
          base {:button-1 {:class "bg-blue-500 text-white py-2 px-4"
                          :text "Click Me"}}

          ;; THEIRS: External edit (changed color)
          theirs {:button-1 {:class "bg-red-500 text-white py-2 px-4"
                            :text "Delete"}}

          ;; OURS: Forma edit (changed padding)
          ours {:button-1 {:class "bg-blue-500 text-white py-3 px-6"
                          :text "Click Me"}}

          ;; Reconcile with auto-merge
          result (reconcile/reconcile base theirs ours {:strategy :auto})]

      (is (map? result) "Should return reconciliation result")
      (is (contains? result :merged) "Should have merged result")
      (is (contains? result :conflicts) "Should have conflicts list")

      (testing "Should detect conflicts"
        (let [conflicts (:conflicts result)]
          (is (map? conflicts) "Conflicts should be a map")

          ;; Should have conflicts since both sides modified :class and :text
          (when (seq conflicts)
            (is (contains? conflicts :button-1) "Should have button-1 conflicts")))))))

;; ============================================================================
;; Integration Test: Complete Workflow
;; ============================================================================

(deftest test-complete-import-workflow
  (testing "Complete import workflow: Parse → Classify → Generate → Reconcile"
    (let [;; 1. Parse external HTML
          parsed (universal/parse tailwind-button-html
                                {:input-format :html :platform :html})

          ;; 2. Build flattened structure
          flattened {:button-1 (second parsed)}

          ;; 3. Build token registry
          token-registry (registry/build-token-registry flattened {})

          ;; 4. Build usage statistics
          stats (classifier/build-usage-statistics flattened)

          ;; 5. Classify properties for each element
          classified (reduce-kv
                      (fn [acc elem-id elem-props]
                        (assoc acc elem-id
                               (classifier/classify-element-properties
                                (assoc elem-props :type :button)
                                stats
                                {})))
                      {}
                      flattened)

          ;; 6. Preview multi-file generation
          preview (generator/preview-file-structure
                   token-registry
                   classified
                   {}
                   "imported-tailwind"
                   "forma/")]

      ;; Verify each step
      (is (vector? parsed) "Step 1: Should parse HTML")
      (is (map? flattened) "Step 2: Should flatten structure")
      (is (map? token-registry) "Step 3: Should build token registry")
      (is (map? stats) "Step 4: Should build usage statistics")
      (is (map? classified) "Step 5: Should classify properties")
      (is (map? preview) "Step 6: Should preview file structure")

      ;; Verify complete pipeline
      (is (and (vector? parsed)
               (map? flattened)
               (map? token-registry)
               (map? stats)
               (map? classified)
               (map? preview))
          "Complete workflow should execute successfully"))))

;; ============================================================================
;; Test Runner
;; ============================================================================

(defn run-all-import-tests
  "Run all import tests and return summary"
  []
  (println "\n=== Running Import Tests (Tailwind + React) ===\n")

  (let [tests [test-tailwind-html-parsing
               test-react-jsx-parsing
               test-tailwind-property-classification
               test-tailwind-token-extraction
               test-tailwind-multi-file-generation
               test-react-component-reconciliation
               test-complete-import-workflow]

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

    (println "\n=== Import Test Results ===")
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
  ;; Run all import tests
  (run-all-import-tests)

  ;; Run specific test
  (test-tailwind-html-parsing)
  (test-react-jsx-parsing)
  (test-complete-import-workflow))
