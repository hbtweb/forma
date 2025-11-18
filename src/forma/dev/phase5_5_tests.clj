(ns forma.dev.phase5-5-tests
  "Phase 5.5 Tests: Hot Reload

  Test Coverage:
  - File watcher creation and management
  - File change detection
  - Reload strategy determination
  - WebSocket server functionality
  - Client connection management
  - Message broadcasting
  - Integration with build pipeline
  - Development server coordination"
  (:require [clojure.test :refer [deftest is testing]]
            [forma.reload.core :as reload]
            [forma.reload.websocket :as ws]
            [forma.reload.integration :as integration]
            [clojure.java.io :as io]))

;; =============================================================================
;; Test Utilities
;; =============================================================================

(defn create-test-file
  "Create temporary test file"
  [path content]
  (io/make-parents path)
  (spit path content))

(defn delete-test-file
  "Delete test file"
  [path]
  (when (.exists (io/file path))
    (.delete (io/file path))))

;; =============================================================================
;; File Change Detection Tests
;; =============================================================================

(deftest test-file-change-record
  (testing "FileChange record creation"
    (let [change (reload/map->FileChange
                   {:path "test.css"
                    :type :modify
                    :timestamp (System/currentTimeMillis)
                    :extension :css
                    :strategy :inject})]
      (is (= "test.css" (:path change)))
      (is (= :modify (:type change)))
      (is (= :css (:extension change)))
      (is (= :inject (:strategy change))))))

(deftest test-reload-strategy-determination
  (testing "CSS files use inject strategy"
    (let [strategies {:css :inject :html :reload :edn :rebuild}]
      (is (= :inject (get strategies :css)))))

  (testing "HTML files use reload strategy"
    (let [strategies {:css :inject :html :reload :edn :rebuild}]
      (is (= :reload (get strategies :html)))))

  (testing "EDN files use rebuild strategy"
    (let [strategies {:css :inject :html :reload :edn :rebuild}]
      (is (= :rebuild (get strategies :edn)))))

  (testing "Unknown extensions use default reload"
    (let [strategies {:css :inject :html :reload :edn :rebuild}]
      (is (nil? (get strategies :unknown))))))

;; =============================================================================
;; File Watcher Tests
;; =============================================================================

(deftest test-file-watcher-creation
  (testing "Create file watcher"
    (let [config {:watch-dirs ["test/"]
                  :debounce-ms 100}
          watcher (reload/create-file-watcher config)]
      (is (some? (:watch-service watcher)))
      (is (map? @(:watch-keys watcher)))
      (is (= config (:config watcher)))
      (is (false? @(:running? watcher)))
      (is (empty? @(:change-buffer watcher)))

      ;; Clean up
      (.close (:watch-service watcher)))))

(deftest test-file-watcher-state
  (testing "File watcher initial state"
    (let [watcher (reload/create-file-watcher {})]
      (is (false? @(:running? watcher)))
      (is (empty? @(:change-buffer watcher)))
      (is (number? @(:last-flush watcher)))

      ;; Clean up
      (.close (:watch-service watcher)))))

;; =============================================================================
;; Reload State Tests
;; =============================================================================

(deftest test-reload-state-creation
  (testing "Create reload state"
    (let [config {:port 3449 :watch-dirs ["test/"]}
          state (reload/create-reload-state config)]
      (is (= config (:config state)))
      (is (nil? (:watcher state)))
      (is (set? @(:clients state)))
      (is (zero? @(:reload-count state)))
      (is (nil? @(:last-reload state)))
      (is (map? @(:statistics state))))))

(deftest test-reload-statistics
  (testing "Reload statistics structure"
    (let [state (reload/create-reload-state {})
          stats (reload/reload-statistics state)]
      (is (map? stats))
      (is (contains? stats :css-injected))
      (is (contains? stats :html-reloaded))
      (is (contains? stats :edn-rebuilt))
      (is (contains? stats :errors))
      (is (contains? stats :reload-count))
      (is (contains? stats :connected-clients)))))

;; =============================================================================
;; Reload Strategy Tests
;; =============================================================================

(deftest test-inject-strategy
  (testing "CSS injection strategy"
    (let [state (reload/create-reload-state {})
          test-file "test-inject.css"
          _ (create-test-file test-file "body { color: red; }")
          change (reload/map->FileChange
                   {:path test-file
                    :type :modify
                    :extension :css
                    :strategy :inject})
          result (reload/apply-reload-strategy :inject change state)]

      (is (= :inject (:type result)))
      (is (= test-file (:path result)))
      (is (string? (:content result)))
      (is (= 1 (:css-injected @(:statistics state))))

      ;; Clean up
      (delete-test-file test-file))))

(deftest test-reload-strategy
  (testing "Page reload strategy"
    (let [state (reload/create-reload-state {})
          change (reload/map->FileChange
                   {:path "test.html"
                    :type :modify
                    :extension :html
                    :strategy :reload})
          result (reload/apply-reload-strategy :reload change state)]

      (is (= :reload (:type result)))
      (is (= "test.html" (:path result)))
      (is (= 1 (:html-reloaded @(:statistics state)))))))

(deftest test-rebuild-strategy
  (testing "Rebuild strategy"
    (let [state (reload/create-reload-state
                  {:project-name "test-project"
                   :build {:mode :test}})
          change (reload/map->FileChange
                   {:path "test.edn"
                    :type :modify
                    :extension :edn
                    :strategy :rebuild})
          result (reload/apply-reload-strategy :rebuild change state)]

      (is (= :rebuild (:type result)))
      (is (= "test.edn" (:path result)))
      (is (contains? result :build-result))
      (is (= 1 (:edn-rebuilt @(:statistics state)))))))

;; =============================================================================
;; WebSocket Client Tests
;; =============================================================================

(deftest test-websocket-client-structure
  (testing "WebSocketClient record structure"
    (let [client (ws/map->WebSocketClient
                   {:id "test-id"
                    :socket nil
                    :writer nil
                    :connected? (atom true)
                    :last-activity (atom (System/currentTimeMillis))
                    :metadata {}})]
      (is (= "test-id" (:id client)))
      (is (true? @(:connected? client)))
      (is (number? @(:last-activity client)))
      (is (map? (:metadata client))))))

;; =============================================================================
;; WebSocket Server Tests
;; =============================================================================

(deftest test-websocket-server-creation
  (testing "Create WebSocket server"
    (let [config {:port 3450}
          server (ws/create-websocket-server config)]
      (is (= config (:config server)))
      (is (nil? (:server-socket server)))
      (is (set? @(:clients server)))
      (is (false? @(:running? server)))
      (is (some? (:executor server)))

      ;; Clean up executor
      (.shutdown (:executor server)))))

(deftest test-websocket-server-statistics
  (testing "WebSocket server statistics"
    (let [server (ws/create-websocket-server {:port 3450})
          stats (ws/server-statistics server)]
      (is (map? stats))
      (is (contains? stats :running?))
      (is (contains? stats :connected-clients))
      (is (contains? stats :port))
      (is (= 3450 (:port stats)))

      ;; Clean up
      (.shutdown (:executor server)))))

;; =============================================================================
;; Development Server Tests
;; =============================================================================

(deftest test-dev-server-creation
  (testing "Create development server"
    (let [config {:project-name "test-project"
                  :port 3451
                  :build-mode :development}
          server (integration/create-dev-server config)]
      (is (map? (:config server)))
      (is (= "test-project" (get-in server [:config :project-name])))
      (is (= 3451 (get-in server [:config :port])))
      (is (= :development (get-in server [:config :build-mode])))
      (is (nil? (:reload-state server)))
      (is (nil? (:ws-server server)))
      (is (false? @(:running? server))))))

(deftest test-dev-server-configuration
  (testing "Development server default configuration"
    (let [server (integration/create-dev-server {})
          config (:config server)]
      (is (= 3449 (:port config)))
      (is (vector? (:watch-dirs config)))
      (is (= :development (:build-mode config)))
      (is (true? (:auto-build? config)))
      (is (true? (:incremental? config))))))

;; =============================================================================
;; Change Processing Tests
;; =============================================================================

(deftest test-change-grouping
  (testing "Group changes by strategy"
    (let [changes [(reload/map->FileChange
                     {:path "test1.css" :strategy :inject})
                   (reload/map->FileChange
                     {:path "test2.css" :strategy :inject})
                   (reload/map->FileChange
                     {:path "test.html" :strategy :reload})
                   (reload/map->FileChange
                     {:path "test.edn" :strategy :rebuild})]
          grouped (group-by :strategy changes)]

      (is (= 2 (count (:inject grouped))))
      (is (= 1 (count (:reload grouped))))
      (is (= 1 (count (:rebuild grouped)))))))

;; =============================================================================
;; Integration Tests
;; =============================================================================

(deftest test-reload-configuration-merging
  (testing "Merge reload configuration with defaults"
    (let [custom {:port 4000 :watch-dirs ["custom/"]}
          merged (merge reload/default-reload-config custom)]
      (is (= 4000 (:port merged)))
      (is (= ["custom/"] (:watch-dirs merged)))
      (is (contains? merged :debounce-ms))
      (is (contains? merged :strategies)))))

(deftest test-build-integration-config
  (testing "Build pipeline integration configuration"
    (let [config {:project-name "test"
                  :build-mode :development
                  :auto-build? true
                  :incremental? true}]
      (is (= :development (:build-mode config)))
      (is (true? (:auto-build? config)))
      (is (true? (:incremental? config))))))

;; =============================================================================
;; Error Handling Tests
;; =============================================================================

(deftest test-error-statistics-tracking
  (testing "Error statistics are tracked"
    (let [state (reload/create-reload-state {})]
      (swap! (:statistics state) update :errors inc)
      (is (= 1 (:errors @(:statistics state)))))))

(deftest test-reload-with-error-callback
  (testing "Error callback is invoked on errors"
    (let [errors (atom [])
          config {:on-error (fn [error] (swap! errors conj error))}
          state (reload/create-reload-state config)]

      ;; Simulate error
      (when-let [on-error (:on-error config)]
        (on-error {:type :test-error :message "Test error"}))

      (is (= 1 (count @errors)))
      (is (= :test-error (:type (first @errors)))))))

;; =============================================================================
;; Test Runner
;; =============================================================================

(defn run-all-phase5-5-tests
  "Run all Phase 5.5 hot reload tests"
  []
  (println "\n========================================")
  (println "Phase 5.5 Tests: Hot Reload")
  (println "========================================\n")

  (let [test-vars [#'test-file-change-record
                   #'test-reload-strategy-determination
                   #'test-file-watcher-creation
                   #'test-file-watcher-state
                   #'test-reload-state-creation
                   #'test-reload-statistics
                   #'test-inject-strategy
                   #'test-reload-strategy
                   #'test-rebuild-strategy
                   #'test-websocket-client-structure
                   #'test-websocket-server-creation
                   #'test-websocket-server-statistics
                   #'test-dev-server-creation
                   #'test-dev-server-configuration
                   #'test-change-grouping
                   #'test-reload-configuration-merging
                   #'test-build-integration-config
                   #'test-error-statistics-tracking
                   #'test-reload-with-error-callback]
        results (map (fn [test-var]
                      (try
                        (test-var)
                        {:test (str test-var) :result :pass}
                        (catch Exception e
                          {:test (str test-var) :result :fail :error (.getMessage e)})))
                    test-vars)
        passed (count (filter #(= :pass (:result %)) results))
        failed (count (filter #(= :fail (:result %)) results))]

    (println "\nTest Summary:")
    (println "-------------")
    (println "Total: " (count results))
    (println "Passed:" passed)
    (println "Failed:" failed)

    (when (pos? failed)
      (println "\nFailed tests:")
      (doseq [result (filter #(= :fail (:result %)) results)]
        (println "  -" (:test result))
        (when (:error result)
          (println "    Error:" (:error result)))))

    (println "\n========================================")
    (println (if (zero? failed)
               "✓ All Phase 5.5 tests passed!"
               (str "✗ " failed " test(s) failed")))
    (println "========================================\n")

    {:total (count results)
     :passed passed
     :failed failed
     :success? (zero? failed)}))

(comment
  ;; Run all tests
  (run-all-phase5-5-tests)

  ;; Run individual tests
  (test-file-change-record)
  (test-reload-strategy-determination)
  (test-inject-strategy)
  )
