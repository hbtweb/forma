(ns forma.dev.phase4-hierarchy-tests
  "Phase 4: Hierarchy Reconstruction test suite.

  Tests for property classification, token reconstruction, multi-file generation,
  and project reconciliation."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [forma.hierarchy.classifier :as classifier]
            [forma.tokens.registry :as registry]
            [forma.hierarchy.generator :as generator]))

;;
;; Phase 4.1: Property Classification Tests
;;

(deftest test-classify-property-by-metadata
  (testing "Classification with explicit metadata (highest confidence)"
    (let [metadata {:property-sources {:background {:level :global
                                                     :explicit true}}}
          classification (classifier/classify-property
                          :background "#4f46e5" :button {} metadata)]

      (is (= :global (:level classification)))
      (is (= 1.0 (:confidence classification)))
      (is (str/includes? (:reason classification) "metadata")))))

(deftest test-classify-property-by-token-provenance
  (testing "Classification with token provenance (token reference preserved)"
    (let [metadata {:token-provenance {:background "$colors.primary"}}
          classification (classifier/classify-property
                          :background "#4f46e5" :button {} metadata)]

      (is (= :global (:level classification)))
      (is (= 0.95 (:confidence classification)))
      (is (str/includes? (:reason classification) "$colors.primary")))))

(deftest test-classify-property-by-frequency-high
  (testing "Classification by high frequency + consistent value → :global"
    (let [usage-stats {:properties {:background {:frequency 25
                                                 :values #{"#4f46e5"}
                                                 :variance 0.0}}
                       :elements {:button {:count 30}}}
          classification (classifier/classify-property
                          :background "#4f46e5" :button usage-stats {})]

      (is (= :global (:level classification)))
      (is (>= (:confidence classification) 0.7))
      (is (str/includes? (:reason classification) "frequency")))))

(deftest test-classify-property-by-frequency-medium
  (testing "Classification by medium frequency → :components"
    (let [usage-stats {:properties {:padding {:frequency 12
                                              :values #{"1rem"}
                                              :variance 0.0}}
                       :elements {:button {:count 30}}}
          classification (classifier/classify-property
                          :padding "1rem" :button usage-stats {})]

      (is (= :components (:level classification)))
      (is (>= (:confidence classification) 0.6)))))

(deftest test-classify-base-class
  (testing "Classification of base class (present in 80%+ instances) → :components"
    (let [usage-stats {:properties {:class {:frequency 28
                                            :values #{"btn" "btn primary"}
                                            :variance 0.1}}
                       :elements {:button {:count 30}}}
          classification (classifier/classify-property
                          :class "btn" :button usage-stats {})]

      (is (= :components (:level classification)))
      (is (>= (:confidence classification) 0.8))
      (is (str/includes? (:reason classification) "Base class")))))

(deftest test-classify-property-by-variance-high
  (testing "Classification by high variance → :pages (instance-specific)"
    (let [usage-stats {:properties {:text {:frequency 25
                                           :values #{"Submit" "Cancel" "OK" "Close" "Save"
                                                     "Delete" "Edit" "New" "Search" "Filter"
                                                     "Export" "Import" "Copy" "Paste" "Undo"
                                                     "Redo" "Refresh" "Settings" "Help" "About"}
                                           :variance 0.8}}
                       :elements {:button {:count 25}}}
          classification (classifier/classify-property
                          :text "Submit" :button usage-stats {})]

      (is (= :pages (:level classification)))
      (is (>= (:confidence classification) 0.8))
      (is (str/includes? (:reason classification) "variance")))))

(deftest test-classify-element-properties
  (testing "Classify all properties in an element"
    (let [element {:type :button
                   :background "#4f46e5"
                   :padding "1rem"
                   :class "btn"
                   :text "Submit"}

          usage-stats {:properties {:background {:frequency 25 :values #{"#4f46e5"} :variance 0.0}
                                    :padding {:frequency 20 :values #{"1rem"} :variance 0.0}
                                    :class {:frequency 28 :values #{"btn"} :variance 0.0}
                                    :text {:frequency 25
                                           :values #{"Submit" "Cancel" "OK" "Close"}
                                           :variance 0.8}}
                       :elements {:button {:count 30}}}

          metadata {:token-provenance {:background "$colors.primary"}}

          classified (classifier/classify-element-properties element usage-stats metadata)]

      ;; Background → :global (token provenance)
      (is (contains? (:global classified) :background))
      (is (= "#4f46e5" (get-in classified [:global :background])))

      ;; Class → :components (base class)
      (is (contains? (:components classified) :class))

      ;; Text → :pages (high variance)
      (is (contains? (:pages classified) :text))

      ;; Classifications recorded
      (is (contains? (:classifications classified) :background))
      (is (= :global (get-in classified [:classifications :background :level]))))))

(deftest test-build-usage-statistics
  (testing "Build usage statistics from flattened EDN"
    (let [edn-data {:page {:content [[:button {:background "#fff" :padding "1rem"}]
                                      [:button {:background "#fff" :padding "1rem"}]
                                      [:button {:background "#000" :padding "1rem"}]]}}

          stats (classifier/build-usage-statistics edn-data)]

      ;; Properties statistics
      (is (= 3 (get-in stats [:properties :background :frequency])))
      (is (= 3 (get-in stats [:properties :padding :frequency])))
      (is (= 2 (count (get-in stats [:properties :background :values]))))
      (is (= 1 (count (get-in stats [:properties :padding :values]))))

      ;; Variance: 2 unique values / 3 total = 0.67
      (is (>= (get-in stats [:properties :background :variance]) 0.6))
      (is (<= (get-in stats [:properties :background :variance]) 0.7))

      ;; Variance: 1 unique value / 3 total = 0.33
      (is (<= (get-in stats [:properties :padding :variance]) 0.4))

      ;; Element counts
      (is (= 3 (get-in stats [:elements :button :count]))))))

(deftest test-filter-by-level
  (testing "Filter classified properties by hierarchy level"
    (let [classified {:global {:background "#fff"}
                      :components {:class "btn"}
                      :pages {:text "Submit"}
                      :classifications {}}

          global-props (classifier/filter-by-level classified :global)
          component-props (classifier/filter-by-level classified :components)
          page-props (classifier/filter-by-level classified :pages)]

      (is (= {:background "#fff"} global-props))
      (is (= {:class "btn"} component-props))
      (is (= {:text "Submit"} page-props)))))

(deftest test-summarize-classifications
  (testing "Generate summary statistics of classifications"
    (let [classified {:global {:background "#fff" :padding "1rem"}
                      :components {:class "btn"}
                      :pages {:text "Submit" :on-click "handleClick"}
                      :classifications {:background {:level :global :confidence 0.95}
                                        :padding {:level :global :confidence 0.8}
                                        :class {:level :components :confidence 0.9}
                                        :text {:level :pages :confidence 0.9}
                                        :on-click {:level :pages :confidence 1.0}}}

          summary (classifier/summarize-classifications classified)]

      (is (= 5 (:total summary)))
      (is (= 2 (get-in summary [:by-level :global])))
      (is (= 1 (get-in summary [:by-level :components])))
      (is (= 2 (get-in summary [:by-level :pages])))
      (is (= 5 (get-in summary [:by-confidence :high]))))))

;;
;; Phase 4.2: Token Reverse Lookup & Registry Tests
;;

(deftest test-build-token-registry-from-metadata
  (testing "Extract token definitions from metadata (highest confidence)"
    (let [flattened-edn {:button {:background "#4f46e5" :padding "1rem"}}
          metadata {:elem-1 {:token-provenance {:background "$colors.primary"
                                                :padding "$spacing.md"}
                             :properties {:background "#4f46e5"
                                        :padding "1rem"}}}

          registry (registry/build-token-registry flattened-edn metadata)]

      ;; Tokens extracted from metadata
      (is (= "#4f46e5" (get-in registry [:colors :primary])))
      (is (= "1rem" (get-in registry [:spacing :md]))))))

(deftest test-build-token-registry-from-frequency
  (testing "Build token registry from frequency analysis (no metadata)"
    (let [edn-data {:page {:content [[:button {:background "#4f46e5"}]
                                      [:button {:background "#4f46e5"}]
                                      [:button {:background "#4f46e5"}]
                                      [:button {:background "#4f46e5"}]
                                      [:button {:background "#4f46e5"}]
                                      [:button {:background "#4f46e5"}]]}}

          registry (registry/build-token-registry edn-data)]

      ;; Should create color token (used 6 times, min frequency is 5)
      (is (map? (:colors registry)))
      (is (some #(= "#4f46e5" %) (vals (:colors registry)))))))

(deftest test-reverse-lookup-token-unique
  (testing "Reverse lookup with single token match (high confidence)"
    (let [token-registry {:colors {:primary "#4f46e5"
                                  :secondary "#64748b"}}

          lookup (registry/reverse-lookup-token "#4f46e5" token-registry)]

      (is (= "$colors.primary" (:token-path lookup)))
      (is (>= (:confidence lookup) 0.9))
      (is (empty? (:alternatives lookup))))))

(deftest test-reverse-lookup-token-collision
  (testing "Reverse lookup with multiple tokens having same value (collision)"
    (let [token-registry {:colors {:primary "#fff"
                                  :white "#fff"}}

          lookup (registry/reverse-lookup-token "#fff" token-registry)]

      ;; Returns one token path
      (is (not (nil? (:token-path lookup))))
      (is (str/starts-with? (:token-path lookup) "$colors."))

      ;; Lower confidence due to collision
      (is (< (:confidence lookup) 0.9))

      ;; Provides alternatives
      (is (= 1 (count (:alternatives lookup))))
      (is (every? #(str/starts-with? % "$colors.") (:alternatives lookup))))))

(deftest test-reverse-lookup-token-not-found
  (testing "Reverse lookup with no matching token"
    (let [token-registry {:colors {:primary "#4f46e5"}}

          lookup (registry/reverse-lookup-token "#ff0000" token-registry)]

      (is (nil? (:token-path lookup)))
      (is (= 0.0 (:confidence lookup)))
      (is (empty? (:alternatives lookup))))))

(deftest test-reconstruct-token-references
  (testing "Replace resolved values with token references"
    (let [properties {:background "#4f46e5"
                     :padding "1rem"
                     :color "#999999"}  ; Not in registry

          token-registry {:colors {:primary "#4f46e5"}
                         :spacing {:md "1rem"}}

          reconstructed (registry/reconstruct-token-references properties token-registry)]

      ;; Values in registry replaced with tokens
      (is (= "$colors.primary" (:background reconstructed)))
      (is (= "$spacing.md" (:padding reconstructed)))

      ;; Value not in registry unchanged
      (is (= "#999999" (:color reconstructed))))))

(deftest test-detect-token-patterns-colors
  (testing "Detect color values that should be tokenized"
    (let [edn-data {:page {:content [[:div {:background "#4f46e5"}]
                                      [:button {:background "#4f46e5"}]
                                      [:input {:background "#4f46e5"}]
                                      [:card {:background "#4f46e5"}]
                                      [:header {:background "#4f46e5"}]
                                      [:footer {:background "#4f46e5"}]]}}

          patterns (registry/detect-token-patterns edn-data)
          color-patterns (filter #(= :color (:type %)) patterns)]

      ;; Should detect #4f46e5 as tokenizable (frequency >= 5)
      (is (pos? (count color-patterns)))
      (is (some #(= "#4f46e5" (:value %)) color-patterns))
      (is (every? #(>= (:frequency %) 5) color-patterns)))))

(deftest test-detect-token-patterns-spacing
  (testing "Detect spacing values that should be tokenized"
    (let [edn-data {:page {:content [[:div {:padding "1rem"}]
                                      [:button {:padding "1rem"}]
                                      [:input {:padding "1rem"}]
                                      [:card {:padding "1rem"}]
                                      [:header {:padding "1rem"}]
                                      [:footer {:padding "1rem"}]]}}

          patterns (registry/detect-token-patterns edn-data)
          spacing-patterns (filter #(= :spacing (:type %)) patterns)]

      ;; Should detect "1rem" as tokenizable (frequency >= 5)
      (is (pos? (count spacing-patterns)))
      (is (some #(= "1rem" (:value %)) spacing-patterns))
      (is (every? #(>= (:frequency %) 5) spacing-patterns)))))

;;
;; Phase 4.3: Multi-File Generation Tests
;;

(deftest test-extract-components
  (testing "Extract component definitions from classified elements"
    (let [classified-elements
          [{:type :button
            :properties {:background "#fff" :padding "1rem" :text "Submit"}
            :classified {:global {:background "#fff"}
                        :components {:padding "1rem"}
                        :pages {:text "Submit"}}}
           {:type :button
            :properties {:background "#fff" :padding "1rem" :text "Cancel"}
            :classified {:global {:background "#fff"}
                        :components {:padding "1rem"}
                        :pages {:text "Cancel"}}}
           {:type :input
            :properties {:border "1px solid #ccc" :padding "0.5rem"}
            :classified {:components {:border "1px solid #ccc" :padding "0.5rem"}}}]

          components (generator/extract-components classified-elements)]

      ;; Button component extracted
      (is (contains? components :button))
      (is (= "1rem" (get-in components [:button :base :padding])))

      ;; Input component extracted
      (is (contains? components :input))
      (is (= "1px solid #ccc" (get-in components [:input :base :border])))
      (is (= "0.5rem" (get-in components [:input :base :padding]))))))

(deftest test-extract-global-defaults
  (testing "Extract global defaults from token registry and classified properties"
    (let [token-registry {:colors {:primary "#4f46e5" :secondary "#64748b"}
                         :spacing {:sm "0.5rem" :md "1rem" :lg "2rem"}}

          classified-elements
          [{:type :button
            :properties {:background "#4f46e5"}
            :classified {:global {:background "#4f46e5"}}}
           {:type :input
            :properties {:border-color "#64748b"}
            :classified {:global {:border-color "#64748b"}}}]

          global-data (generator/extract-global-defaults token-registry classified-elements)]

      ;; Token registry included
      (is (= token-registry (:tokens global-data)))

      ;; Global defaults extracted
      (is (= "#4f46e5" (get-in global-data [:defaults :button :background])))
      (is (= "#64748b" (get-in global-data [:defaults :input :border-color]))))))

(deftest test-extract-pages
  (testing "Extract page instances from classified elements"
    (let [page-defs
          [{:name :home
            :elements [{:type :button
                       :properties {:text "Submit"}
                       :classified {:pages {:text "Submit"}}}
                      {:type :button
                       :properties {:text "Cancel"}
                       :classified {:pages {:text "Cancel"}}}]}
           {:name :about
            :elements [{:type :heading
                       :properties {:text "About Us" :level 1}
                       :classified {:pages {:text "About Us" :level 1}}}]}]

          pages (generator/extract-pages page-defs)]

      ;; Home page extracted
      (is (contains? pages :home))
      (is (vector? (get-in pages [:home :content])))
      (is (= 2 (count (get-in pages [:home :content]))))

      ;; About page extracted
      (is (contains? pages :about))
      (is (= 1 (count (get-in pages [:about :content])))))))

(deftest test-serialize-global-defaults
  (testing "Serialize global defaults to EDN string"
    (let [global-data {:tokens {:colors {:primary "#4f46e5"}}
                      :defaults {:button {:background "$colors.primary"}}}

          edn-str (generator/serialize-global-defaults global-data)]

      ;; Valid EDN string
      (is (string? edn-str))
      (is (str/includes? edn-str ":tokens"))
      (is (str/includes? edn-str ":colors"))
      (is (str/includes? edn-str ":primary"))
      (is (str/includes? edn-str "#4f46e5")))))

(deftest test-serialize-component
  (testing "Serialize component definition to EDN string"
    (let [comp-def {:base {:padding "1rem" :background "#fff"}
                   :variants {:primary {:background "#4f46e5"}
                             :secondary {:background "#64748b"}}}

          edn-str (generator/serialize-component :button comp-def)]

      ;; Valid EDN string
      (is (string? edn-str))
      (is (str/includes? edn-str ":button"))
      (is (str/includes? edn-str ":base"))
      (is (str/includes? edn-str ":variants"))
      (is (str/includes? edn-str ":primary"))
      (is (str/includes? edn-str "#4f46e5")))))

(deftest test-serialize-page
  (testing "Serialize page definition to EDN string"
    (let [page-def {:content [[:button {:text "Submit"}]
                             [:button {:text "Cancel"}]]}

          edn-str (generator/serialize-page :home page-def)]

      ;; Valid EDN string
      (is (string? edn-str))
      (is (str/includes? edn-str ":home"))
      (is (str/includes? edn-str ":content"))
      (is (str/includes? edn-str ":button"))
      (is (str/includes? edn-str "Submit")))))

(deftest test-preview-file-structure
  (testing "Preview file structure without writing files"
    (let [token-registry {:colors {:primary "#4f46e5"}}

          classified-elements
          [{:type :button
            :properties {:background "#4f46e5" :padding "1rem" :text "Submit"}
            :classified {:global {:background "#4f46e5"}
                        :components {:padding "1rem"}
                        :pages {:text "Submit"}}}]

          preview (generator/preview-file-structure
                   classified-elements
                   token-registry
                   "test-project")]

      ;; Preview result structure
      (is (contains? preview :files))
      (is (contains? preview :summary))
      (is (vector? (:files preview)))

      ;; Files generated
      (is (pos? (count (:files preview))))

      ;; All files have preview status
      (is (every? #(= :preview (:status %)) (:files preview)))

      ;; Summary contains expected keys
      (is (contains? (:summary preview) :total-files))
      (is (contains? (:summary preview) :total-bytes))
      (is (= "test-project" (:project-name (:summary preview))))

      ;; Files include expected paths
      (let [paths (set (map :path (:files preview)))]
        (is (some #(str/includes? % "global/defaults.edn") paths))
        (is (some #(str/includes? % "components/button.edn") paths))
        (is (some #(str/includes? % "pages/index.edn") paths))))))

(deftest test-generate-from-flattened-simple
  (testing "Full pipeline: flattened EDN → classified → multi-file structure"
    (let [;; Simple flattened EDN (like parsed HTML)
          flattened-edn
          {:page {:content [[:button {:background "#4f46e5"
                                     :padding "1rem"
                                     :text "Submit"}]
                           [:button {:background "#4f46e5"
                                    :padding "1rem"
                                    :text "Cancel"}]
                           [:button {:background "#4f46e5"
                                    :padding "1rem"
                                    :text "OK"}]
                           [:button {:background "#4f46e5"
                                    :padding "1rem"
                                    :text "Close"}]
                           [:button {:background "#4f46e5"
                                    :padding "1rem"
                                    :text "Save"}]
                           [:button {:background "#4f46e5"
                                    :padding "1rem"
                                    :text "Delete"}]]}}

          ;; Preview mode (don't write files)
          token-registry (registry/build-token-registry flattened-edn)
          usage-stats (classifier/build-usage-statistics flattened-edn)
          elements (->> flattened-edn
                       (tree-seq coll? seq)
                       (filter vector?)
                       (filter #(and (keyword? (first %))
                                    (or (map? (second %))
                                        (nil? (second %)))))
                       (map (fn [[tag props & _]]
                              {:type tag
                               :properties (or props {})})))

          classified-elements
          (mapv
           (fn [elem]
             (let [classified (classifier/classify-element-properties
                              elem usage-stats {})]
               {:type (:type elem)
                :properties (:properties elem)
                :classified classified}))
           elements)

          preview (generator/preview-file-structure
                   classified-elements
                   token-registry
                   "test-import")]

      ;; Files generated
      (is (pos? (count (:files preview))))

      ;; Contains global defaults
      (is (some #(str/includes? (:path %) "global/defaults.edn")
                (:files preview)))

      ;; Contains button component
      (is (some #(str/includes? (:path %) "components/button.edn")
                (:files preview)))

      ;; Contains page instance
      (is (some #(str/includes? (:path %) "pages/index.edn")
                (:files preview)))

      ;; Token registry built (background color used 6 times)
      (is (map? token-registry))
      (is (or (contains? token-registry :colors)
              (contains? token-registry :spacing))))))

;;
;; Test Runner
;;

(defn run-phase4-tests
  "Run all Phase 4 tests and return summary"
  []
  (let [results (clojure.test/run-tests 'forma.dev.phase4-hierarchy-tests)]
    (println "\n=== Phase 4: Hierarchy Reconstruction Test Results ===")
    (println (format "Tests run: %d" (+ (:pass results) (:fail results) (:error results))))
    (println (format "Passed: %d" (:pass results)))
    (println (format "Failed: %d" (:fail results)))
    (println (format "Errors: %d" (:error results)))
    (println (format "Success rate: %.1f%%"
                     (* 100.0 (/ (:pass results)
                                 (+ (:pass results) (:fail results) (:error results))))))
    results))

(comment
  ;; Run tests
  (run-phase4-tests)

  ;; Run single test
  (test-classify-property-by-metadata)
  (test-build-usage-statistics)
  )
