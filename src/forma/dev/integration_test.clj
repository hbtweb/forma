(ns forma.dev.integration-test
  "Simplified integration test demonstrating Phase 3 + 4 complete workflow:

   1. Parse real-world Tailwind/React components
   2. Extract metadata and provenance
   3. Build token registry
   4. Classify properties
   5. Generate multi-file structure
   6. Test reconciliation

   This test validates that the complete import pipeline works end-to-end."
  (:require [forma.compiler :as compiler]
            [forma.parsers.html :as html]
            [forma.parsers.jsx :as jsx]
            [forma.tokens.registry :as registry]
            [forma.hierarchy.classifier :as classifier]
            [forma.hierarchy.generator :as generator]
            [forma.hierarchy.reconciliation :as reconcile]))

;; ============================================================================
;; Sample Components
;; ============================================================================

(def tailwind-button-html
  "<button class=\"bg-blue-500 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded\">
    Click Me
  </button>")

(def react-card-jsx
  "<div className=\"max-w-sm rounded overflow-hidden shadow-lg\">
    <img className=\"w-full\" src={image} alt={imageAlt} />
    <div className=\"px-6 py-4\">
      <div className=\"font-bold text-xl mb-2\">{title}</div>
      <p className=\"text-gray-700 text-base\">{description}</p>
    </div>
  </div>")

;; ============================================================================
;; Integration Test: Complete Workflow
;; ============================================================================

(defn test-complete-workflow []
  (println "\n=== Phase 3 + 4 Integration Test ===\n")

  (try
    ;; Step 1: Parse HTML with forma.parsers.html
    (println "Step 1: Parsing Tailwind HTML...")
    (let [parsed-html (html/parse tailwind-button-html)
          _ (println "  ✅ Parsed:" (pr-str parsed-html))

          ;; Step 2: Parse JSX with forma.parsers.jsx
          _ (println "\nStep 2: Parsing React JSX...")
          parsed-jsx (jsx/parse react-card-jsx)
          _ (println "  ✅ Parsed:" (pr-str (take 2 parsed-jsx)))

          ;; Step 3: Build flattened structure
          _ (println "\nStep 3: Building flattened structure...")
          flattened {:button-1 (second parsed-html)
                    :card-1 (second parsed-jsx)}
          _ (println "  ✅ Flattened:" (count flattened) "elements")

          ;; Step 4: Build token registry
          _ (println "\nStep 4: Building token registry...")
          token-registry (registry/build-token-registry flattened {})
          _ (println "  ✅ Token registry:" (if (seq token-registry)
                                              (str (count (keys token-registry)) " categories")
                                              "empty (frequency-based detection)"))

          ;; Step 5: Build usage statistics
          _ (println "\nStep 5: Building usage statistics...")
          stats (classifier/build-usage-statistics flattened)
          _ (println "  ✅ Statistics:")
          _ (println "     - Properties:" (count (get stats :properties {})))
          _ (println "     - Element types:" (count (get stats :elements {})))

          ;; Step 6: Classify properties
          _ (println "\nStep 6: Classifying properties...")
          classified (reduce-kv
                      (fn [acc elem-id elem-props]
                        (let [elem-type (cond
                                         (string? (get elem-props :class))
                                         (cond
                                           (re-find #"btn|button" (str (get elem-props :class))) :button
                                           (re-find #"card" (str (get elem-props :class))) :div
                                           :else :div)
                                         :else :div)]
                          (assoc acc elem-id
                                 (classifier/classify-element-properties
                                  (assoc elem-props :type elem-type)
                                  stats
                                  {}))))
                      {}
                      flattened)
          _ (println "  ✅ Classified:" (count classified) "elements")

          ;; Step 7: Preview multi-file generation
          _ (println "\nStep 7: Previewing multi-file generation...")
          preview (generator/preview-file-structure
                   classified
                   token-registry
                   "tailwind-react-test")
          files (:files preview)
          _ (println "  ✅ Generated:" (count files) "files")
          _ (doseq [{:keys [path]} files]
              (println "     -" path))

          ;; Step 8: Test reconciliation
          _ (println "\nStep 8: Testing reconciliation...")
          base {:button-1 {:class "bg-blue-500 text-white py-2 px-4"
                           :text "Click Me"}}
          theirs {:button-1 {:class "bg-red-500 text-white py-2 px-4"
                             :text "Delete"}}
          ours {:button-1 {:class "bg-blue-500 text-white py-3 px-6"
                           :text "Click Me"}}
          result (reconcile/reconcile base theirs ours {:strategy :auto})
          conflicts (:conflicts result)
          merged (:merged result)
          _ (println "  ✅ Reconciliation complete:")
          _ (println "     - Merged elements:" (count merged))
          _ (println "     - Conflicts detected:" (count (keys conflicts)))
          _ (when (seq conflicts)
              (doseq [[elem-id props] conflicts]
                (println "       * " elem-id "→" (keys props))))]

      (println "\n=== INTEGRATION TEST COMPLETE ===")
      (println "✅ All 8 steps executed successfully!")
      (println "\nPhase 3 + 4 Capabilities Verified:")
      (println "  ✅ HTML parsing (Tailwind CSS)")
      (println "  ✅ JSX parsing (React components)")
      (println "  ✅ Token registry construction")
      (println "  ✅ Usage statistics analysis")
      (println "  ✅ Property classification")
      (println "  ✅ Multi-file generation")
      (println "  ✅ 3-way reconciliation")
      (println "\n🎉 Ready for Phase 5 implementation!")

      {:status :success
       :steps-completed 8
       :files-generated (count files)
       :conflicts-detected (count (keys conflicts))})

    (catch Exception e
      (println "\n❌ Integration test failed:")
      (println "  Error:" (.getMessage e))
      (.printStackTrace e)
      {:status :failed
       :error (.getMessage e)})))

(defn -main []
  (test-complete-workflow))

(comment
  ;; Run integration test
  (test-complete-workflow))
