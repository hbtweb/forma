(ns forma.cache.core
  "Multi-layer intelligent caching system for Forma compilation.

  Provides four cache layers:
  1. Token Cache - Resolved token values
  2. Hierarchy Cache - Resolved inheritance chains
  3. Element Cache - Compiled element output
  4. File Cache - Generated file content

  Features:
  - Configurable cache strategies (content-hash, timestamp, dependency-based)
  - LRU eviction for memory management
  - Disk persistence for faster cold starts
  - Dependency tracking for intelligent invalidation
  - TTL (time-to-live) support
  - Statistics and monitoring

  Phase 5.3 implementation."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.security MessageDigest]
           [java.time Instant]))

;; ============================================================================
;; CACHE PROTOCOL
;; ============================================================================

(defprotocol ICache
  "Protocol for cache implementations"
  (get-cached [this key] "Get value from cache, returns nil if not found")
  (put-cached! [this key value] "Store value in cache")
  (invalidate! [this key] "Remove specific key from cache")
  (clear! [this] "Clear all cache entries")
  (stats [this] "Get cache statistics"))

;; ============================================================================
;; UTILITIES
;; ============================================================================

(defn content-hash
  "Generate SHA-256 hash of content for cache key generation.

  Args:
    content - Any Clojure data structure or string

  Returns:
    Hex string representation of hash (e.g., 'a3f2...')"
  [content]
  (let [digest (MessageDigest/getInstance "SHA-256")
        bytes (.digest digest (.getBytes (pr-str content) "UTF-8"))]
    (apply str (map #(format "%02x" %) bytes))))

(defn now-millis
  "Get current timestamp in milliseconds"
  []
  (.toEpochMilli (Instant/now)))

(defn expired?
  "Check if cache entry has expired based on TTL.

  Args:
    entry - Cache entry map with :timestamp
    ttl-ms - Time-to-live in milliseconds

  Returns:
    true if expired, false otherwise"
  [{:keys [timestamp]} ttl-ms]
  (when (and timestamp ttl-ms)
    (> (- (now-millis) timestamp) ttl-ms)))

;; ============================================================================
;; LRU CACHE IMPLEMENTATION
;; ============================================================================

(defrecord LRUCache [state config]
  ICache
  (get-cached [this key]
    (let [{:keys [entries access-order ttl-ms]} @state
          entry (get entries key)]
      (when entry
        ;; Check TTL expiration
        (if (expired? entry ttl-ms)
          (do
            ;; Remove expired entry
            (swap! state update :entries dissoc key)
            (swap! state update :access-order (fn [order] (remove #{key} order)))
            nil)
          (do
            ;; Update access time (move to end of LRU list)
            (swap! state update :access-order
                   (fn [order]
                     (vec (concat (remove #{key} order) [key]))))
            (swap! state update-in [:stats :hits] (fnil inc 0))
            (:value entry))))))

  (put-cached! [this key value]
    (let [{:keys [max-size]} (:config @state)
          entry {:value value
                 :timestamp (now-millis)}]
      (swap! state
             (fn [{:keys [entries access-order] :as s}]
               (let [new-entries (assoc entries key entry)
                     new-order (vec (concat (remove #{key} access-order) [key]))]
                 ;; Evict oldest entry if over max-size
                 (if (> (count new-entries) max-size)
                   (let [evict-key (first new-order)
                         evicted-entries (dissoc new-entries evict-key)
                         evicted-order (vec (rest new-order))]
                     (-> s
                         (assoc :entries evicted-entries)
                         (assoc :access-order evicted-order)
                         (update-in [:stats :evictions] (fnil inc 0))))
                   (-> s
                       (assoc :entries new-entries)
                       (assoc :access-order new-order))))))
      (swap! state update-in [:stats :puts] (fnil inc 0))
      value))

  (invalidate! [this key]
    (swap! state update :entries dissoc key)
    (swap! state update :access-order (fn [order] (vec (remove #{key} order))))
    (swap! state update-in [:stats :invalidations] (fnil inc 0))
    nil)

  (clear! [this]
    (let [count (count (:entries @state))]
      (swap! state assoc :entries {} :access-order [])
      (swap! state update-in [:stats :clears] (fnil inc 0))
      count))

  (stats [this]
    (let [{:keys [entries stats config]} @state
          {:keys [hits puts evictions invalidations clears]} stats
          total-requests (+ (or hits 0) (or puts 0))]
      {:size (count entries)
       :max-size (:max-size config)
       :hits (or hits 0)
       :misses (- total-requests (or hits 0))
       :puts (or puts 0)
       :evictions (or evictions 0)
       :invalidations (or invalidations 0)
       :clears (or clears 0)
       :hit-rate (if (pos? total-requests)
                   (double (/ (or hits 0) total-requests))
                   0.0)})))

(defn create-lru-cache
  "Create a new LRU cache.

  Options:
    :max-size - Maximum number of entries (default: 1000)
    :ttl-ms - Time-to-live in milliseconds (default: nil = no expiration)

  Returns:
    LRUCache instance"
  [& {:keys [max-size ttl-ms]
      :or {max-size 1000
           ttl-ms nil}}]
  (->LRUCache
   (atom {:entries {}
          :access-order []
          :stats {}
          :ttl-ms ttl-ms
          :config {:max-size max-size
                   :ttl-ms ttl-ms}})
   {:max-size max-size
    :ttl-ms ttl-ms}))

;; ============================================================================
;; DISK CACHE IMPLEMENTATION
;; ============================================================================

(defn- cache-file-path
  "Generate file path for cache entry.

  Args:
    cache-dir - Base cache directory
    key - Cache key (will be hashed)

  Returns:
    File path string"
  [cache-dir key]
  (let [key-hash (content-hash key)]
    (str cache-dir "/" (subs key-hash 0 2) "/" key-hash ".edn")))

(defn- ensure-cache-dir!
  "Ensure cache directory exists.

  Args:
    path - Directory path

  Returns:
    File object for directory"
  [path]
  (let [dir (io/file path)]
    (when-not (.exists dir)
      (.mkdirs dir))
    dir))

(defrecord DiskCache [cache-dir state]
  ICache
  (get-cached [this key]
    (try
      (let [file-path (cache-file-path cache-dir key)
            file (io/file file-path)]
        (when (.exists file)
          (let [entry (edn/read-string (slurp file))
                {:keys [ttl-ms]} @state]
            ;; Check TTL expiration
            (if (expired? entry ttl-ms)
              (do
                ;; Remove expired file
                (.delete file)
                (swap! state update-in [:stats :expirations] (fnil inc 0))
                nil)
              (do
                (swap! state update-in [:stats :hits] (fnil inc 0))
                (:value entry))))))
      (catch Exception e
        (swap! state update-in [:stats :errors] (fnil inc 0))
        nil)))

  (put-cached! [this key value]
    (try
      (let [file-path (cache-file-path cache-dir key)
            file (io/file file-path)
            entry {:value value
                   :timestamp (now-millis)
                   :key key}]
        ;; Ensure parent directory exists
        (ensure-cache-dir! (.getParent file))
        ;; Write entry
        (spit file (pr-str entry))
        (swap! state update-in [:stats :puts] (fnil inc 0))
        value)
      (catch Exception e
        (swap! state update-in [:stats :errors] (fnil inc 0))
        nil)))

  (invalidate! [this key]
    (try
      (let [file-path (cache-file-path cache-dir key)
            file (io/file file-path)]
        (when (.exists file)
          (.delete file)
          (swap! state update-in [:stats :invalidations] (fnil inc 0))))
      (catch Exception e
        (swap! state update-in [:stats :errors] (fnil inc 0))))
    nil)

  (clear! [this]
    (try
      (let [dir (io/file cache-dir)
            files (file-seq dir)
            count (count (filter #(.isFile %) files))]
        (doseq [file files]
          (when (.isFile file)
            (.delete file)))
        (swap! state update-in [:stats :clears] (fnil inc 0))
        count)
      (catch Exception e
        (swap! state update-in [:stats :errors] (fnil inc 0))
        0)))

  (stats [this]
    (let [{:keys [hits puts invalidations clears errors expirations]} (:stats @state)
          total-requests (+ (or hits 0) (or puts 0))]
      {:hits (or hits 0)
       :misses (- total-requests (or hits 0))
       :puts (or puts 0)
       :invalidations (or invalidations 0)
       :clears (or clears 0)
       :errors (or errors 0)
       :expirations (or expirations 0)
       :hit-rate (if (pos? total-requests)
                   (double (/ (or hits 0) total-requests))
                   0.0)})))

(defn create-disk-cache
  "Create a new disk-based cache.

  Options:
    :cache-dir - Directory for cache files (default: '.forma-cache')
    :ttl-ms - Time-to-live in milliseconds (default: nil = no expiration)

  Returns:
    DiskCache instance"
  [& {:keys [cache-dir ttl-ms]
      :or {cache-dir ".forma-cache"
           ttl-ms nil}}]
  (ensure-cache-dir! cache-dir)
  (->DiskCache
   cache-dir
   (atom {:stats {}
          :ttl-ms ttl-ms})))

;; ============================================================================
;; LAYERED CACHE (MEMORY + DISK)
;; ============================================================================

(defrecord LayeredCache [memory-cache disk-cache]
  ICache
  (get-cached [this key]
    ;; Try memory first
    (if-let [value (get-cached memory-cache key)]
      value
      ;; Try disk second
      (when disk-cache
        (when-let [value (get-cached disk-cache key)]
          ;; Promote to memory cache
          (put-cached! memory-cache key value)
          value))))

  (put-cached! [this key value]
    ;; Write to both layers
    (put-cached! memory-cache key value)
    (when disk-cache
      (put-cached! disk-cache key value))
    value)

  (invalidate! [this key]
    (invalidate! memory-cache key)
    (when disk-cache
      (invalidate! disk-cache key))
    nil)

  (clear! [this]
    (let [mem-count (clear! memory-cache)
          disk-count (when disk-cache (clear! disk-cache))]
      (+ mem-count (or disk-count 0))))

  (stats [this]
    {:memory (stats memory-cache)
     :disk (when disk-cache (stats disk-cache))}))

(defn create-layered-cache
  "Create a layered cache with memory (L1) and optional disk (L2).

  Options:
    :max-size - Maximum memory cache size (default: 1000)
    :ttl-ms - Time-to-live in milliseconds (default: 3600000 = 1 hour)
    :disk-enabled? - Enable disk cache (default: true)
    :cache-dir - Disk cache directory (default: '.forma-cache')

  Returns:
    LayeredCache instance"
  [& {:keys [max-size ttl-ms disk-enabled? cache-dir]
      :or {max-size 1000
           ttl-ms 3600000  ; 1 hour default
           disk-enabled? true
           cache-dir ".forma-cache"}}]
  (let [memory-cache (create-lru-cache :max-size max-size :ttl-ms ttl-ms)
        disk-cache (when disk-enabled?
                    (create-disk-cache :cache-dir cache-dir :ttl-ms ttl-ms))]
    (->LayeredCache memory-cache disk-cache)))

;; ============================================================================
;; CACHE KEY GENERATION
;; ============================================================================

(defn element-cache-key
  "Generate cache key for compiled element.

  Args:
    element - Element EDN
    context - Compilation context

  Returns:
    Cache key string"
  [element context]
  (let [relevant-context (select-keys context [:hierarchy-levels :tokens :platform-stack :styling-stack])]
    (content-hash [element relevant-context])))

(defn hierarchy-cache-key
  "Generate cache key for resolved hierarchy.

  Args:
    element-id - Element identifier (e.g., :button)
    level - Hierarchy level (e.g., :components)
    context - Context with hierarchy data

  Returns:
    Cache key string"
  [element-id level context]
  (let [relevant-context (select-keys context [:project-name :hierarchy-levels])]
    (content-hash [element-id level relevant-context])))

(defn token-cache-key
  "Generate cache key for resolved token.

  Args:
    token-ref - Token reference string (e.g., '$colors.primary')
    context - Context with token registry

  Returns:
    Cache key string"
  [token-ref context]
  (let [token-registry (:tokens context)]
    (content-hash [token-ref token-registry])))

(defn file-cache-key
  "Generate cache key for generated file.

  Args:
    file-path - Relative file path
    content - File content (for hash-based caching)

  Returns:
    Cache key string"
  [file-path content]
  (content-hash [file-path content]))
