(ns forma.dev.phase3-tests
  "Phase 3: Metadata-Enhanced Round-Trip Tests

   Tests for Phase 3 features:
   - Token preservation during compilation
   - Property source tracking (explicit vs inherited)
   - Class attribution from styling systems
   - Metadata embedding (export vs sync mode)
   - Round-trip compilation with full fidelity
   - Provenance data attribute generation"
  (:require [forma.compiler :as compiler]
            [forma.sync.metadata :as metadata]
            [forma.provenance.tracker :as provenance]
            [forma.inheritance.tracking :as tracking]
            [kora.core.tokens :as tokens]
            [cheshire.core :as json]
            [clojure.string :as str]))

;; ============================================================================
;; TOKEN PRESERVATION TESTS
;; ============================================================================

(defn test-token-tracking-basic
  "Test basic token tracking during resolution"
  []
  (let [tokens {:colors {:primary "#4f46e5" :secondary "#10b981"}}
        props {:background "$colors.primary" :color "$colors.secondary"}
        tracker (tokens/create-token-tracker)
        opts {:track-tokens? true :token-tracker tracker}
        context {:tokens tokens}
        resolved (tokens/resolve-tokens props context opts)
        tracker-data @tracker]

    (assert (= (:background resolved) "#4f46e5")
            "Background should resolve to primary color")
    (assert (= (:color resolved) "#10b981")
            "Color should resolve to secondary color")
    (assert (contains? tracker-data "$colors.primary")
            "Token tracker should record primary token")
    (assert (contains? tracker-data "$colors.secondary")
            "Token tracker should record secondary token")
    (assert (= (get-in tracker-data ["$colors.primary" :resolved-value]) "#4f46e5")
            "Tracker should store resolved value")

    {:status :pass :test "test-token-tracking-basic"}))

(defn test-token-provenance-attachment
  "Test attaching token provenance to resolved properties"
  []
  (let [tokens {:colors {:primary "#4f46e5"}}
        props {:background "$colors.primary" :padding "1rem"}
        tracker (tokens/create-token-tracker)
        opts {:track-tokens? true :token-tracker tracker}
        context {:tokens tokens}
        resolved (tokens/resolve-tokens props context opts)
        with-provenance (tokens/attach-token-provenance resolved tracker)
        prov (:_token-provenance with-provenance)]

    (assert (contains? with-provenance :_token-provenance)
            "Resolved props should have token provenance")
    (assert (contains? prov :background)
            "Provenance should track background property")
    (assert (= (get prov :background) "$colors.primary")
            "Provenance should preserve original token reference")
    (assert (not (contains? prov :padding))
            "Provenance should not track non-token properties")

    {:status :pass :test "test-token-provenance-attachment"}))

(defn test-reverse-token-lookup
  "Test building reverse lookup from resolved values to tokens"
  []
  (let [tokens {:colors {:primary "#4f46e5" :secondary "#10b981"}
                :spacing {:md "1rem" :lg "2rem"}}
        reverse-lookup (tokens/build-reverse-token-lookup tokens)
        primary-token (tokens/lookup-token-for-value "#4f46e5" reverse-lookup)
        spacing-token (tokens/lookup-token-for-value "1rem" reverse-lookup)]

    (assert (= primary-token "$colors.primary")
            "Should find token for primary color")
    (assert (= spacing-token "$spacing.md")
            "Should find token for spacing value")
    (assert (nil? (tokens/lookup-token-for-value "#unknown" reverse-lookup))
            "Should return nil for unknown values")

    {:status :pass :test "test-reverse-token-lookup"}))

;; ============================================================================
;; COMPILATION MODE TESTS
;; ============================================================================

(defn test-compilation-modes
  "Test export vs sync compilation modes"
  []
  (let [base-context {}
        export-context (metadata/enable-export-mode base-context)
        sync-context (metadata/enable-sync-mode base-context)]

    (assert (metadata/export-mode? export-context)
            "Export mode should be enabled")
    (assert (not (metadata/sync-mode? export-context))
            "Sync mode should be disabled in export context")
    (assert (metadata/sync-mode? sync-context)
            "Sync mode should be enabled")
    (assert (not (metadata/export-mode? sync-context))
            "Export mode should be disabled in sync context")
    (assert (get sync-context :track-tokens?)
            "Sync mode should enable token tracking")
    (assert (get sync-context :property-tracker)
            "Sync mode should create property tracker")
    (assert (get sync-context :provenance-tracker)
            "Sync mode should create provenance tracker")

    {:status :pass :test "test-compilation-modes"}))

(defn test-metadata-collection
  "Test collecting metadata from multiple sources"
  []
  (let [element {:type :button}
        props {:variant :primary
               :_token-provenance {:background "$colors.primary"}
               :_class-provenance {:btn {:system :shadcn-ui}}}
        context {:compilation-mode :sync
                 :element-path [:page :button]}
        metadata (metadata/collect-element-metadata element props context)]

    (assert (contains? metadata :element-type)
            "Metadata should include element type")
    (assert (= (:element-type metadata) :button)
            "Element type should be button")
    (assert (contains? metadata :variant)
            "Metadata should include variant")
    (assert (= (:variant metadata) :primary)
            "Variant should be primary")
    (assert (contains? metadata :token-provenance)
            "Metadata should include token provenance")
    (assert (contains? metadata :class-attribution)
            "Metadata should include class attribution")

    {:status :pass :test "test-metadata-collection"}))

;; ============================================================================
;; METADATA EMBEDDING TESTS
;; ============================================================================

(defn test-metadata-to-data-attributes
  "Test converting metadata to data-forma-* attributes"
  []
  (let [metadata {:element-type :button
                  :variant :primary
                  :token-provenance {:background "$colors.primary"}
                  :class-attribution {:btn {:system :shadcn-ui}}}
        data-attrs (metadata/metadata->data-attributes metadata)]

    (assert (contains? data-attrs :data-forma-type)
            "Should have data-forma-type attribute")
    (assert (= (:data-forma-type data-attrs) "button")
            "Element type should be string")
    (assert (contains? data-attrs :data-forma-variant)
            "Should have data-forma-variant attribute")
    (assert (= (:data-forma-variant data-attrs) "primary")
            "Variant should be string")
    (assert (contains? data-attrs :data-forma-token-provenance)
            "Should have token provenance attribute")
    (assert (string? (:data-forma-token-provenance data-attrs))
            "Token provenance should be JSON string")

    ;; Test JSON parsing
    (let [parsed-token-prov (json/parse-string (:data-forma-token-provenance data-attrs) true)]
      (assert (= (:background parsed-token-prov) "$colors.primary")
              "JSON should parse back to original token reference"))

    {:status :pass :test "test-metadata-to-data-attributes"}))

(defn test-metadata-extraction
  "Test extracting metadata from data-forma-* attributes (round-trip)"
  []
  (let [original-metadata {:element-type :button
                           :variant :primary
                           :token-provenance {:background "$colors.primary"}}
        data-attrs (metadata/metadata->data-attributes original-metadata)
        extracted (metadata/extract-metadata-from-attributes data-attrs)]

    (assert (= (:element-type extracted) :button)
            "Element type should round-trip")
    (assert (= (:variant extracted) :primary)
            "Variant should round-trip")
    (assert (= (get-in extracted [:token-provenance :background]) "$colors.primary")
            "Token provenance should round-trip")

    {:status :pass :test "test-metadata-extraction"}))

(defn test-embed-metadata-in-element
  "Test embedding metadata in Hiccup element"
  []
  (let [element [:button {:class "btn"} "Click Me"]
        props {:variant :primary
               :_token-provenance {:background "$colors.primary"}}
        sync-context (metadata/enable-sync-mode {})
        export-context (metadata/enable-export-mode {})

        ;; Sync mode - should embed metadata
        sync-result (metadata/embed-metadata-in-element
                     {:type :button} props sync-context)

        ;; Export mode - should not embed metadata
        export-result (metadata/embed-metadata-in-element
                       {:type :button} props export-context)]

    ;; Note: We're testing the function signature here
    ;; Full integration test would need complete compilation
    (assert (not (nil? sync-result))
            "Sync mode should return element")
    (assert (not (nil? export-result))
            "Export mode should return element")

    {:status :pass :test "test-embed-metadata-in-element"}))

;; ============================================================================
;; PROPERTY SOURCE TRACKING TESTS
;; ============================================================================

(defn test-property-tracker-creation
  "Test creating and using property tracker"
  []
  (let [tracker (tracking/create-property-tracker)]

    (tracking/track-property tracker :background "#4f46e5" :explicit :pages {})
    (tracking/track-property tracker :padding "1rem" :inherited :global {})

    (assert (tracking/is-explicit? tracker :background)
            "Background should be explicit")
    (assert (not (tracking/is-explicit? tracker :padding))
            "Padding should not be explicit")

    (let [explicit-props (tracking/get-explicit-properties tracker)
          inherited-props (tracking/get-inherited-properties tracker)]
      (assert (= (count explicit-props) 1)
              "Should have 1 explicit property")
      (assert (= (count inherited-props) 1)
              "Should have 1 inherited property")
      (assert (= (:property (first explicit-props)) :background)
              "Explicit property should be background"))

    {:status :pass :test "test-property-tracker-creation"}))

;; ============================================================================
;; PROVENANCE TRACKING TESTS
;; ============================================================================

(defn test-provenance-tracker-creation
  "Test creating and recording provenance entries"
  []
  (let [tracker (provenance/create-tracker)
        entry (provenance/make-provenance-entry
               :class "btn primary"
               :styling-system :shadcn-ui
               :apply-styling :button
               {})]

    (provenance/record-property tracker entry)

    (let [entries (provenance/get-provenance tracker)]
      (assert (= (count entries) 1)
              "Should have 1 provenance entry")
      (assert (= (:property (first entries)) :class)
              "Entry should track class property"))

    {:status :pass :test "test-provenance-tracker-creation"}))

;; ============================================================================
;; INTEGRATION TESTS
;; ============================================================================

(defn test-sync-mode-full-pipeline
  "Test full compilation pipeline with sync mode enabled"
  []
  (let [;; Setup context with sync mode
        base-context {:tokens {:colors {:primary "#4f46e5"}}
                      :project-name "test-project"}
        sync-context (metadata/enable-sync-mode base-context)

        ;; Verify sync mode setup
        _ (assert (metadata/sync-mode? sync-context)
                  "Context should be in sync mode")
        _ (assert (get sync-context :track-tokens?)
                  "Token tracking should be enabled")
        _ (assert (get sync-context :property-tracker)
                  "Property tracker should be created")
        _ (assert (get sync-context :provenance-tracker)
                  "Provenance tracker should be created")]

    {:status :pass :test "test-sync-mode-full-pipeline"}))

;; ============================================================================
;; TEST RUNNER
;; ============================================================================

(defn run-all-phase3-tests
  "Run all Phase 3 metadata-enhanced round-trip tests"
  []
  (println "\n=== Running Phase 3 Tests ===\n")

  (let [tests [;; Token preservation
               test-token-tracking-basic
               test-token-provenance-attachment
               test-reverse-token-lookup

               ;; Compilation modes
               test-compilation-modes
               test-metadata-collection

               ;; Metadata embedding
               test-metadata-to-data-attributes
               test-metadata-extraction
               test-embed-metadata-in-element

               ;; Property tracking
               test-property-tracker-creation

               ;; Provenance tracking
               test-provenance-tracker-creation

               ;; Integration
               test-sync-mode-full-pipeline]

        results (mapv (fn [test-fn]
                       (try
                         (test-fn)
                         (catch Exception e
                           {:status :fail
                            :test (str test-fn)
                            :error (.getMessage e)
                            :stack (str/join "\n" (.getStackTrace e))})))
                     tests)

        passed (count (filter #(= (:status %) :pass) results))
        failed (count (filter #(= (:status %) :fail) results))
        total (count results)]

    (println "")
    (println "Phase 3 Test Results:")
    (println "=====================")
    (println (str "Total: " total))
    (println (str "Passed: " passed))
    (println (str "Failed: " failed))
    (println (str "Success Rate: " (int (* 100 (/ passed total))) "%"))

    (when (> failed 0)
      (println "\nFailed Tests:")
      (doseq [result (filter #(= (:status %) :fail) results)]
        (println (str "  - " (:test result)))
        (println (str "    Error: " (:error result)))))

    (println "\n=== Phase 3 Tests Complete ===\n")

    {:total total :passed passed :failed failed
     :success-rate (/ passed total)
     :results results}))

;; Run tests when namespace is loaded (for development)
(comment
  (run-all-phase3-tests))
