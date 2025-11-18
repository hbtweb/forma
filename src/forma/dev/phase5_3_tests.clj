(ns forma.dev.phase5-3-tests
  "Test suite for Phase 5.3 - Intelligent Caching.

  Tests:
  - LRU cache implementation
  - Disk cache implementation
  - Layered cache (memory + disk)
  - Dependency tracking
  - Cache invalidation strategies
  - Incremental compilation
  - Cache integration with compiler

  Run all tests: (run-all-phase5-3-tests)"
  (:require [forma.cache.core :as cache]
            [forma.cache.dependencies :as deps]
            [forma.cache.invalidation :as invalidation]
            [forma.cache.incremental :as incremental]
            [forma.cache.compiler :as cache-compiler]
            [forma.compiler :as compiler]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; ============================================================================
;; TEST UTILITIES
;; ============================================================================

(def ^:dynamic *test-results* (atom {:passed 0 :failed 0 :errors []}))

(defmacro deftest [name & body]
  `(defn ~name []
     (try
       ~@body
       (swap! *test-results* update :passed inc)
       (println "✓" (str '~name))
       true
       (catch Throwable e#
         (swap! *test-results* update :failed inc)
         (swap! *test-results* update :errors conj
                {:test '~name
                 :error (.getMessage e#)
                 :stacktrace (vec (take 5 (.getStackTrace e#)))})
         (println "✗" (str '~name) "-" (.getMessage e#))
         false))))

(defn assert-true [condition message]
  (when-not condition
    (throw (ex-info message {:condition condition}))))

(defn assert-equals [expected actual message]
  (when-not (= expected actual)
    (throw (ex-info message {:expected expected :actual actual}))))

(defn assert-contains [coll item message]
  (when-not (some #{item} coll)
    (throw (ex-info message {:collection coll :item item}))))

(defn assert-not-nil [value message]
  (when (nil? value)
    (throw (ex-info message {:value value}))))

;; ============================================================================
;; LRU CACHE TESTS
;; ============================================================================

(deftest test-lru-cache-basic
  (let [cache (cache/create-lru-cache :max-size 3)]

    ;; Test put and get
    (cache/put-cached! cache "key1" "value1")
    (assert-equals "value1" (cache/get-cached cache "key1")
                   "Should retrieve cached value")

    ;; Test cache miss
    (assert-equals nil (cache/get-cached cache "nonexistent")
                   "Should return nil for cache miss")

    ;; Test stats
    (let [stats (cache/stats cache)]
      (assert-equals 1 (:size stats) "Should have 1 entry")
      (assert-equals 1 (:hits stats) "Should have 1 hit")
      (assert-equals 0 (:evictions stats) "Should have 0 evictions"))))

(deftest test-lru-cache-eviction
  (let [cache (cache/create-lru-cache :max-size 2)]

    ;; Fill cache
    (cache/put-cached! cache "key1" "value1")
    (cache/put-cached! cache "key2" "value2")

    ;; Add third item - should evict key1
    (cache/put-cached! cache "key3" "value3")

    (assert-equals nil (cache/get-cached cache "key1")
                   "key1 should be evicted")
    (assert-equals "value2" (cache/get-cached cache "key2")
                   "key2 should still exist")
    (assert-equals "value3" (cache/get-cached cache "key3")
                   "key3 should exist")

    (let [stats (cache/stats cache)]
      (assert-equals 1 (:evictions stats) "Should have 1 eviction"))))

(deftest test-lru-cache-access-order
  (let [cache (cache/create-lru-cache :max-size 2)]

    ;; Fill cache
    (cache/put-cached! cache "key1" "value1")
    (cache/put-cached! cache "key2" "value2")

    ;; Access key1 to make it most recently used
    (cache/get-cached cache "key1")

    ;; Add key3 - should evict key2 (least recently used)
    (cache/put-cached! cache "key3" "value3")

    (assert-equals "value1" (cache/get-cached cache "key1")
                   "key1 should still exist (recently accessed)")
    (assert-equals nil (cache/get-cached cache "key2")
                   "key2 should be evicted")
    (assert-equals "value3" (cache/get-cached cache "key3")
                   "key3 should exist")))

(deftest test-lru-cache-ttl
  (let [cache (cache/create-lru-cache :max-size 10 :ttl-ms 50)]

    ;; Add entry
    (cache/put-cached! cache "key1" "value1")

    ;; Should exist immediately
    (assert-equals "value1" (cache/get-cached cache "key1")
                   "Should exist before TTL expires")

    ;; Wait for TTL to expire (double the TTL to be safe)
    (Thread/sleep 120)

    ;; Should be expired
    (assert-equals nil (cache/get-cached cache "key1")
                   "Should be expired after TTL")))

(deftest test-lru-cache-invalidate
  (let [cache (cache/create-lru-cache :max-size 10)]

    (cache/put-cached! cache "key1" "value1")
    (cache/put-cached! cache "key2" "value2")

    ;; Invalidate specific key
    (cache/invalidate! cache "key1")

    (assert-equals nil (cache/get-cached cache "key1")
                   "key1 should be invalidated")
    (assert-equals "value2" (cache/get-cached cache "key2")
                   "key2 should still exist")))

(deftest test-lru-cache-clear
  (let [cache (cache/create-lru-cache :max-size 10)]

    (cache/put-cached! cache "key1" "value1")
    (cache/put-cached! cache "key2" "value2")
    (cache/put-cached! cache "key3" "value3")

    ;; Clear all
    (let [cleared (cache/clear! cache)]
      (assert-equals 3 cleared "Should clear 3 entries"))

    (assert-equals 0 (:size (cache/stats cache))
                   "Cache should be empty")))

;; ============================================================================
;; DISK CACHE TESTS
;; ============================================================================

(deftest test-disk-cache-basic
  (let [test-dir ".test-cache"
        cache (cache/create-disk-cache :cache-dir test-dir)]

    (try
      ;; Test put and get
      (cache/put-cached! cache "key1" {:data "value1"})
      (assert-equals {:data "value1"} (cache/get-cached cache "key1")
                     "Should retrieve cached value from disk")

      ;; Test cache miss
      (assert-equals nil (cache/get-cached cache "nonexistent")
                     "Should return nil for cache miss")

      (finally
        ;; Cleanup
        (cache/clear! cache)
        (when (.exists (io/file test-dir))
          (.delete (io/file test-dir)))))))

(deftest test-disk-cache-persistence
  (let [test-dir ".test-cache-persist"]

    (try
      ;; Create cache and add entry
      (let [cache1 (cache/create-disk-cache :cache-dir test-dir)]
        (cache/put-cached! cache1 "key1" {:data "value1"}))

      ;; Create new cache instance (simulating restart)
      (let [cache2 (cache/create-disk-cache :cache-dir test-dir)]
        (assert-equals {:data "value1"} (cache/get-cached cache2 "key1")
                       "Should retrieve value after restart"))

      (finally
        ;; Cleanup
        (let [cache (cache/create-disk-cache :cache-dir test-dir)]
          (cache/clear! cache))
        (when (.exists (io/file test-dir))
          (.delete (io/file test-dir)))))))

;; ============================================================================
;; LAYERED CACHE TESTS
;; ============================================================================

(deftest test-layered-cache-promotion
  (let [test-dir ".test-cache-layered"
        cache (cache/create-layered-cache
               :max-size 10
               :disk-enabled? true
               :cache-dir test-dir)]

    (try
      ;; Add to cache (goes to both layers)
      (cache/put-cached! cache "key1" "value1")

      ;; Get from cache (memory hit)
      (assert-equals "value1" (cache/get-cached cache "key1")
                     "Should get from memory")

      ;; Clear memory layer only
      (cache/clear! (:memory-cache cache))

      ;; Get again (should promote from disk to memory)
      (assert-equals "value1" (cache/get-cached cache "key1")
                     "Should promote from disk to memory")

      ;; Verify it's now in memory
      (assert-not-nil (cache/get-cached (:memory-cache cache) "key1")
                      "Should be in memory after promotion")

      (finally
        ;; Cleanup
        (cache/clear! cache)
        (when (.exists (io/file test-dir))
          (.delete (io/file test-dir)))))))

;; ============================================================================
;; DEPENDENCY TRACKING TESTS
;; ============================================================================

(deftest test-dependency-graph-basic
  (let [graph (atom (deps/create-dependency-graph))]

    ;; Add nodes
    (deps/add-node! graph "node1" {:type :file :path "file1.edn"})
    (deps/add-node! graph "node2" {:type :file :path "file2.edn"})

    ;; Add edge (node2 depends on node1)
    (deps/add-edge! graph "node2" "node1")

    ;; Check dependencies
    (let [dependencies (deps/get-dependencies @graph "node2")]
      (assert-contains dependencies "node1"
                       "node2 should depend on node1"))

    ;; Check dependents
    (let [dependents (deps/get-dependents @graph "node1")]
      (assert-contains dependents "node2"
                       "node1 should have node2 as dependent"))))

(deftest test-dependency-transitive
  (let [graph (atom (deps/create-dependency-graph))]

    ;; Create chain: C -> B -> A
    (deps/add-node! graph "A" {:type :file :path "a.edn"})
    (deps/add-node! graph "B" {:type :file :path "b.edn"})
    (deps/add-node! graph "C" {:type :file :path "c.edn"})

    (deps/add-edge! graph "B" "A")  ; B depends on A
    (deps/add-edge! graph "C" "B")  ; C depends on B

    ;; Get transitive dependents of A
    (let [dependents (deps/get-transitive-dependents @graph "A")]
      (assert-contains dependents "B"
                       "B should be transitive dependent of A")
      (assert-contains dependents "C"
                       "C should be transitive dependent of A"))))

(deftest test-file-tracking
  (let [graph (atom (deps/create-dependency-graph))
        test-file "test-tracking.edn"]

    (try
      ;; Create test file
      (spit test-file "{:test true}")

      ;; Track file
      (deps/track-file! graph test-file)

      ;; Verify node exists
      (let [node-id (deps/file-node-id test-file)
            node (get-in @graph [:nodes node-id])]
        (assert-not-nil node "File node should exist")
        (assert-equals :file (:type node) "Node should be of type :file"))

      ;; Check file changed (should be false initially)
      (assert-equals false (deps/file-changed? @graph test-file)
                     "File should not be marked as changed")

      ;; Modify file
      (Thread/sleep 10)  ; Ensure timestamp changes
      (spit test-file "{:test false}")

      ;; Check file changed (should be true now)
      (assert-equals true (deps/file-changed? @graph test-file)
                     "File should be marked as changed after modification")

      (finally
        ;; Cleanup
        (io/delete-file test-file true)))))

(deftest test-token-tracking
  (let [graph (atom (deps/create-dependency-graph))]

    ;; Track token
    (deps/track-token! graph "$colors.primary" "#4f46e5" "global/defaults.edn")

    ;; Verify node exists
    (let [node-id (deps/token-node-id "$colors.primary")
          node (get-in @graph [:nodes node-id])]
      (assert-not-nil node "Token node should exist")
      (assert-equals :token (:type node) "Node should be of type :token")
      (assert-equals "$colors.primary" (:ref node) "Token ref should match"))))

;; ============================================================================
;; INVALIDATION TESTS
;; ============================================================================

(deftest test-invalidation-content-hash
  (let [cache (cache/create-lru-cache :max-size 10)
        graph (atom (deps/create-dependency-graph))
        test-file "test-invalidation.edn"]

    (try
      ;; Create and track file
      (spit test-file "{:version 1}")
      (deps/track-file! graph test-file)

      ;; Cache something
      (cache/put-cached! cache "file-cache:test-invalidation.edn" "cached-v1")

      ;; Modify file
      (Thread/sleep 10)
      (spit test-file "{:version 2}")

      ;; Invalidate changed files
      (let [result (invalidation/invalidate-by-strategy
                    cache graph [test-file]
                    {:strategy :content-hash})]
        (assert-true (seq (:changed result))
                     "Should detect changed file"))

      (finally
        ;; Cleanup
        (io/delete-file test-file true)))))

(deftest test-invalidation-dependency-based
  (let [cache (cache/create-lru-cache :max-size 10)
        graph (atom (deps/create-dependency-graph))]

    ;; Create dependency chain
    (deps/add-node! graph "A" {:type :file})
    (deps/add-node! graph "B" {:type :file})
    (deps/add-edge! graph "B" "A")  ; B depends on A

    ;; Cache both
    (cache/put-cached! cache "file:A" "value-A")
    (cache/put-cached! cache "file:B" "value-B")

    ;; Invalidate A (should also invalidate B)
    (let [result (invalidation/invalidate-by-strategy
                  cache graph ["A"]
                  {:strategy :dependency-based})]
      (assert-contains (:invalidated result) "A"
                       "A should be invalidated")
      (assert-contains (:invalidated result) "B"
                       "B should be invalidated (dependent)"))))

(deftest test-invalidation-pattern
  (let [cache (cache/create-lru-cache :max-size 10)
        graph (atom (deps/create-dependency-graph))]

    ;; Add nodes
    (deps/add-node! graph "file:components/button.edn" {:type :file :path "components/button.edn"})
    (deps/add-node! graph "file:components/card.edn" {:type :file :path "components/card.edn"})
    (deps/add-node! graph "file:global/defaults.edn" {:type :file :path "global/defaults.edn"})

    ;; Invalidate by pattern
    (let [result (invalidation/invalidate-by-strategy
                  cache graph ["components"]
                  {:strategy :pattern})]
      (assert-equals 2 (count (:invalidated result))
                     "Should invalidate 2 component files"))))

;; ============================================================================
;; INCREMENTAL COMPILATION TESTS
;; ============================================================================

(deftest test-change-detection
  (let [graph (atom (deps/create-dependency-graph))
        test-file1 "test-change-1.edn"
        test-file2 "test-change-2.edn"]

    (try
      ;; Create files
      (spit test-file1 "{:version 1}")
      (spit test-file2 "{:version 1}")

      ;; Track files
      (deps/track-file! graph test-file1)
      (deps/track-file! graph test-file2)

      ;; Modify one file
      (Thread/sleep 10)
      (spit test-file1 "{:version 2}")

      ;; Detect changes
      (let [result (incremental/detect-changes @graph [test-file1 test-file2] :content-hash)]
        (assert-contains (:changed result) test-file1
                         "test-file1 should be changed")
        (assert-contains (:unchanged result) test-file2
                         "test-file2 should be unchanged"))

      (finally
        ;; Cleanup
        (io/delete-file test-file1 true)
        (io/delete-file test-file2 true)))))

(deftest test-topological-sort
  (let [graph (atom (deps/create-dependency-graph))]

    ;; Create dependency chain: C -> B -> A
    (deps/add-node! graph "A" {:type :file})
    (deps/add-node! graph "B" {:type :file})
    (deps/add-node! graph "C" {:type :file})

    (deps/add-edge! graph "B" "A")
    (deps/add-edge! graph "C" "B")

    ;; Sort
    (let [sorted (incremental/topological-sort @graph #{"A" "B" "C"})]
      ;; A should come before B, B should come before C
      (assert-true (< (.indexOf sorted "A") (.indexOf sorted "B"))
                   "A should come before B")
      (assert-true (< (.indexOf sorted "B") (.indexOf sorted "C"))
                   "B should come before C"))))

(deftest test-compilation-state
  (let [state (atom (incremental/create-compilation-state))]

    ;; Mark as pending
    (swap! state assoc :pending #{"node1" "node2"})

    ;; Mark as in-progress
    (incremental/mark-in-progress! state "node1")
    (assert-contains (:in-progress @state) "node1"
                     "node1 should be in-progress")

    ;; Mark as compiled
    (incremental/mark-compiled! state "node1")
    (assert-contains (:compiled @state) "node1"
                     "node1 should be compiled")
    (assert-true (not (contains? (:in-progress @state) "node1"))
                 "node1 should not be in-progress")))

;; ============================================================================
;; CACHE INTEGRATION TESTS
;; ============================================================================

(deftest test-cache-initialization
  (cache-compiler/clear-cache!)
  (cache-compiler/initialize-cache! :max-size 100 :disk-enabled? false)

  (let [cache (cache-compiler/get-cache)]
    (assert-not-nil cache "Cache should be initialized")

    (let [stats (cache/stats cache)]
      (assert-equals 0 (:size (:memory stats))
                     "Cache should be empty initially"))))

(deftest test-compile-element-cached
  (cache-compiler/clear-cache!)
  (cache-compiler/initialize-cache! :max-size 100 :disk-enabled? false)

  (let [element [:div {:class "test"} "Hello"]
        context {:platform-stack [:html :css]}

        ;; First compilation (cache miss)
        result1 (cache-compiler/compile-element-cached element context)

        ;; Second compilation (cache hit)
        result2 (cache-compiler/compile-element-cached element context)]

    (assert-equals result1 result2
                   "Cached result should match original")

    (let [stats (cache-compiler/cache-stats)]
      (assert-true (> (get-in stats [:memory :hits]) 0)
                   "Should have cache hits"))))

;; ============================================================================
;; TEST RUNNER
;; ============================================================================

(defn run-all-phase5-3-tests []
  (println "\n=== Phase 5.3: Intelligent Caching Tests ===\n")

  (reset! *test-results* {:passed 0 :failed 0 :errors []})

  (println "\n--- LRU Cache Tests ---")
  (test-lru-cache-basic)
  (test-lru-cache-eviction)
  (test-lru-cache-access-order)
  (test-lru-cache-ttl)
  (test-lru-cache-invalidate)
  (test-lru-cache-clear)

  (println "\n--- Disk Cache Tests ---")
  (test-disk-cache-basic)
  (test-disk-cache-persistence)

  (println "\n--- Layered Cache Tests ---")
  (test-layered-cache-promotion)

  (println "\n--- Dependency Tracking Tests ---")
  (test-dependency-graph-basic)
  (test-dependency-transitive)
  (test-file-tracking)
  (test-token-tracking)

  (println "\n--- Invalidation Tests ---")
  (test-invalidation-content-hash)
  (test-invalidation-dependency-based)
  (test-invalidation-pattern)

  (println "\n--- Incremental Compilation Tests ---")
  (test-change-detection)
  (test-topological-sort)
  (test-compilation-state)

  (println "\n--- Cache Integration Tests ---")
  (test-cache-initialization)
  (test-compile-element-cached)

  (let [{:keys [passed failed errors]} @*test-results*
        total (+ passed failed)]
    (println "\n=== Test Results ===")
    (println "Total:" total)
    (println "Passed:" passed)
    (println "Failed:" failed)

    (when (seq errors)
      (println "\n=== Errors ===")
      (doseq [{:keys [test error]} errors]
        (println "Test:" test)
        (println "Error:" error)
        (println)))

    (if (zero? failed)
      (println "\n✅ All Phase 5.3 tests passed!")
      (println "\n❌ Some Phase 5.3 tests failed."))

    {:passed passed :failed failed :total total :success? (zero? failed)}))
