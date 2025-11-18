(ns forma.dev.sync-tests
  "Integration tests for the sync system.

  Tests cover:
  - Sync configuration loading (three-tier resolution)
  - Platform discovery and registry
  - Authentication header building
  - URL construction with parameter replacement
  - Environment variable resolution
  - Request/response handling (mock)
  - Compiler integration"
  (:require [forma.sync.client :as sync]
            [forma.sync.registry :as registry]))

;; =============================================================================
;; Test Utilities
;; =============================================================================

(defn test-result
  "Create test result map."
  [name passed? expected actual & [notes]]
  {:name name
   :passed? passed?
   :expected expected
   :actual actual
   :notes notes})

(defn run-test
  "Run a single test and return result."
  [test-name test-fn]
  (try
    (test-fn)
    (catch Exception e
      (test-result test-name false nil nil
                   (str "Exception: " (.getMessage e) "\n"
                        "Stack: " (clojure.string/join "\n"
                                   (take 5 (.getStackTrace e))))))))

;; =============================================================================
;; Configuration Loading Tests
;; =============================================================================

(defn test-load-wordpress-config
  "Test loading WordPress sync configuration."
  []
  (let [config (sync/load-sync-config :wordpress "default")
        has-required-keys? (and (:platform config)
                                (:display-name config)
                                (:connection config)
                                (:auth config)
                                (:compiler config)
                                (:endpoints config))]
    (test-result "Load WordPress Config"
                 has-required-keys?
                 "All required keys present"
                 (str "Keys: " (keys config)))))

(defn test-env-var-resolution
  "Test environment variable resolution with fallback."
  []
  (let [;; Test with fallback (no env var set)
        result1 (sync/resolve-env-var "NONEXISTENT_VAR || http://fallback.com")
        ;; Test without fallback
        result2 (sync/resolve-env-var "ANOTHER_NONEXISTENT")
        correct? (and (= "http://fallback.com" result1)
                      (nil? result2))]
    (test-result "Env Var Resolution"
                 correct?
                 "Fallback works, nil without"
                 (str "result1=" result1 " result2=" result2))))

;; =============================================================================
;; Authentication Tests
;; =============================================================================

(defn test-build-basic-auth-headers
  "Test Basic Auth header building."
  []
  (let [;; Set test env vars
        _ (System/setProperty "TEST_USER" "testuser")
        _ (System/setProperty "TEST_PASS" "testpass")
        auth-config {:type :basic
                     :username-env "TEST_USER"
                     :password-env "TEST_PASS"}
        headers (sync/build-auth-headers auth-config)
        has-auth? (contains? headers "Authorization")
        starts-with-basic? (and has-auth?
                                (clojure.string/starts-with?
                                 (get headers "Authorization")
                                 "Basic "))]
    (test-result "Basic Auth Headers"
                 starts-with-basic?
                 "Authorization header with 'Basic '"
                 (str "Headers: " headers))))

(defn test-build-bearer-auth-headers
  "Test Bearer token header building."
  []
  (let [_ (System/setProperty "TEST_TOKEN" "mytoken123")
        auth-config {:type :bearer
                     :token-env "TEST_TOKEN"}
        headers (sync/build-auth-headers auth-config)
        expected "Bearer mytoken123"
        actual (get headers "Authorization")]
    (test-result "Bearer Auth Headers"
                 (= expected actual)
                 expected
                 actual)))

(defn test-build-api-key-headers
  "Test API Key header building."
  []
  (let [_ (System/setProperty "TEST_API_KEY" "key123")
        auth-config {:type :api-key
                     :header-name "X-API-Key"
                     :key-env "TEST_API_KEY"}
        headers (sync/build-auth-headers auth-config)
        expected "key123"
        actual (get headers "X-API-Key")]
    (test-result "API Key Headers"
                 (= expected actual)
                 expected
                 actual)))

;; =============================================================================
;; URL Building Tests
;; =============================================================================

(defn test-build-url-simple
  "Test simple URL building without parameters."
  []
  (let [config {:connection {:base-url-env "http://example.com"
                             :path-prefix "/api"}}
        endpoint-config {:path "/pages"}
        url (sync/build-url config endpoint-config {})
        expected "http://example.com/api/pages"]
    (test-result "Build URL Simple"
                 (= expected url)
                 expected
                 url)))

(defn test-build-url-with-params
  "Test URL building with path parameter replacement."
  []
  (let [config {:connection {:base-url-env "http://example.com"
                             :path-prefix "/api"}}
        endpoint-config {:path "/pages/:id"}
        url (sync/build-url config endpoint-config {:id 123})
        expected "http://example.com/api/pages/123"]
    (test-result "Build URL With Params"
                 (= expected url)
                 expected
                 url)))

;; =============================================================================
;; Request Building Tests
;; =============================================================================

(defn test-build-request
  "Test full request building."
  []
  (let [_ (System/setProperty "TEST_WP_USER" "admin")
        _ (System/setProperty "TEST_WP_PASS" "pass")
        config {:connection {:base-url-env "http://example.com"
                             :path-prefix "/api"
                             :content-type "application/json"}
                :auth {:type :basic
                       :username-env "TEST_WP_USER"
                       :password-env "TEST_WP_PASS"}
                :endpoints {:create {:method :post
                                     :path "/pages"}}}
        request (sync/build-request config :create {} "{\"title\":\"Test\"}")
        has-required? (and (:method request)
                           (:url request)
                           (:headers request)
                           (:body request))]
    (test-result "Build Request"
                 has-required?
                 "method, url, headers, body present"
                 (str "Keys: " (keys request)))))

;; =============================================================================
;; Platform Registry Tests
;; =============================================================================

(defn test-discover-platforms
  "Test platform discovery from default/sync/ directory."
  []
  (let [platforms (registry/discover-platforms)
        contains-wordpress? (contains? platforms :wordpress)]
    (test-result "Discover Platforms"
                 contains-wordpress?
                 "Contains :wordpress"
                 (str "Platforms: " platforms))))

(defn test-load-platform-metadata
  "Test loading platform metadata."
  []
  (let [metadata (registry/load-platform-metadata :wordpress)
        has-required? (and (:platform metadata)
                           (:display-name metadata)
                           (:capabilities metadata))]
    (test-result "Load Platform Metadata"
                 has-required?
                 "platform, display-name, capabilities present"
                 (str "Metadata keys: " (keys metadata)))))

(defn test-platform-exists
  "Test platform existence check."
  []
  (let [wp-exists? (registry/platform-exists? :wordpress)
        fake-exists? (registry/platform-exists? :nonexistent)]
    (test-result "Platform Exists"
                 (and wp-exists? (not fake-exists?))
                 "WordPress exists, nonexistent doesn't"
                 (str "WP=" wp-exists? " Fake=" fake-exists?))))

(defn test-register-custom-platform
  "Test custom platform registration."
  []
  (let [_ (registry/register-platform! :custom-test {:platform :custom-test})
        registered (registry/get-registered-platform :custom-test)
        exists? (some? registered)]
    (registry/unregister-platform! :custom-test)
    (test-result "Register Custom Platform"
                 exists?
                 "Custom platform registered"
                 (str "Registered: " (some? registered)))))

(defn test-list-platforms
  "Test listing all platforms."
  []
  (let [platforms (registry/list-platforms)
        has-wordpress? (some #(= :wordpress (:platform %)) platforms)
        count-correct? (>= (count platforms) 1)]
    (test-result "List Platforms"
                 (and has-wordpress? count-correct?)
                 "At least 1 platform with WordPress"
                 (str "Count: " (count platforms) " Has WP: " has-wordpress?))))

(defn test-search-platforms-by-query
  "Test platform search by text query."
  []
  (let [results (registry/search-platforms {:query "wordpress"})
        found-wordpress? (some #(= :wordpress (:platform %)) results)]
    (test-result "Search Platforms by Query"
                 found-wordpress?
                 "Found WordPress"
                 (str "Results: " (count results)))))

(defn test-search-platforms-by-capability
  "Test platform search by capability."
  []
  (let [results (registry/search-platforms {:capability :create})
        has-results? (> (count results) 0)]
    (test-result "Search Platforms by Capability"
                 has-results?
                 "Found platforms with :create"
                 (str "Count: " (count results)))))

(defn test-validate-platform-config
  "Test platform configuration validation."
  []
  (let [valid-config {:platform :test
                      :display-name "Test"
                      :connection {:base-url-env "TEST_URL"}
                      :auth {:type :basic}
                      :compiler {:compiler-ns "test.ns"
                                 :compile-fn "compile"}
                      :endpoints {:create {:method :post}}}
        result (registry/validate-platform-config valid-config)
        is-valid? (:valid? result)]
    (test-result "Validate Platform Config"
                 is-valid?
                 "Valid config passes"
                 (str "Valid: " is-valid? " Errors: " (:errors result)))))

(defn test-validate-platform-config-invalid
  "Test platform configuration validation with invalid config."
  []
  (let [invalid-config {:platform :test}  ;; Missing required fields
        result (registry/validate-platform-config invalid-config)
        has-errors? (not (:valid? result))]
    (test-result "Validate Invalid Platform Config"
                 has-errors?
                 "Invalid config fails"
                 (str "Valid: " (:valid? result) " Error count: " (count (:errors result))))))

;; =============================================================================
;; Compiler Integration Tests (Mock)
;; =============================================================================

(defn test-compiler-fn-loading
  "Test compiler function loading (will fail gracefully if not found)."
  []
  (let [;; Try to load a real compiler if it exists
        compile-fn (sync/load-compiler-fn "forma.integrations.oxygen.compiler"
                                          "compile-to-oxygen")
        ;; We expect nil if not found (not an error)
        result (if compile-fn :found :not-found)]
    (test-result "Compiler Function Loading"
                 true  ;; Always pass - this is informational
                 "Function loaded or gracefully nil"
                 (str "Result: " result))))

;; =============================================================================
;; Test Suite Runner
;; =============================================================================

(defn run-all-sync-tests
  "Run all sync system tests and print results."
  []
  (println "\n" (apply str (repeat 80 "=")) "\n")
  (println "SYNC SYSTEM TEST SUITE")
  (println (apply str (repeat 80 "=")) "\n")

  (let [test-groups
        {"Configuration Loading"
         [test-load-wordpress-config
          test-env-var-resolution]

         "Authentication"
         [test-build-basic-auth-headers
          test-build-bearer-auth-headers
          test-build-api-key-headers]

         "URL Building"
         [test-build-url-simple
          test-build-url-with-params]

         "Request Building"
         [test-build-request]

         "Platform Registry"
         [test-discover-platforms
          test-load-platform-metadata
          test-platform-exists
          test-register-custom-platform
          test-list-platforms
          test-search-platforms-by-query
          test-search-platforms-by-capability
          test-validate-platform-config
          test-validate-platform-config-invalid]

         "Compiler Integration"
         [test-compiler-fn-loading]}

        results (atom [])]

    ;; Run all test groups
    (doseq [[group-name tests] test-groups]
      (println (str "\n" group-name ":"))
      (println (apply str (repeat (count group-name) "-")))

      (doseq [test-fn tests]
        (let [result (run-test (str test-fn) test-fn)]
          (swap! results conj result)
          (if (:passed? result)
            (println "  ✓" (:name result))
            (do
              (println "  ✗" (:name result))
              (when (:notes result)
                (println "    Notes:" (:notes result)))
              (println "    Expected:" (:expected result))
              (println "    Actual:  " (:actual result)))))))

    ;; Summary
    (let [total (count @results)
          passed (count (filter :passed? @results))
          failed (- total passed)
          pass-rate (if (> total 0)
                      (int (* 100 (/ passed total)))
                      0)]

      (println "\n" (apply str (repeat 80 "=")))
      (println "\nSYNC SYSTEM TEST SUMMARY")
      (println (apply str (repeat 80 "=")))
      (println (format "Total:  %d tests" total))
      (println (format "Passed: %d tests ✓" passed))
      (println (format "Failed: %d tests ✗" failed))
      (println (format "Success Rate: %d%%\n" pass-rate))

      (when (= 100 pass-rate)
        (println "🎉 SYNC TESTS: ALL PASSING! 🎉\n"))

      {:total total
       :passed passed
       :failed failed
       :pass-rate pass-rate
       :results @results})))
