(ns forma.cache.invalidation
  "Cache invalidation strategies for intelligent cache management.

  Provides multiple invalidation strategies:
  - Content-based: Invalidate when content hash changes
  - Timestamp-based: Invalidate when modification time changes
  - Dependency-based: Invalidate dependents when dependency changes
  - TTL-based: Invalidate after time-to-live expires
  - Pattern-based: Invalidate entries matching a pattern
  - Global: Invalidate all cache entries

  Phase 5.3 implementation."
  (:require [clojure.string :as str]
            [forma.cache.core :as cache]
            [forma.cache.dependencies :as deps]))

;; ============================================================================
;; INVALIDATION STRATEGIES
;; ============================================================================

(defmulti invalidate-by-strategy
  "Invalidate cache entries using a specific strategy.

  Dispatch on :strategy key in options map.

  Args:
    cache - Cache instance
    graph-atom - Dependency graph atom
    targets - Strategy-specific targets (files, patterns, etc.)
    options - Strategy-specific options

  Returns:
    {:invalidated #{...} :strategy :... :metadata {...}}"
  (fn [_cache _graph-atom _targets options]
    (:strategy options)))

(defmethod invalidate-by-strategy :content-hash
  [cache graph-atom file-paths options]
  (let [result (deps/invalidate-changed-files!
                cache graph-atom file-paths
                :strategy :content-hash)]
    (merge result
           {:strategy :content-hash
            :metadata {:files-checked (count file-paths)
                       :files-changed (count (:changed result))}})))

(defmethod invalidate-by-strategy :timestamp
  [cache graph-atom file-paths options]
  (let [result (deps/invalidate-changed-files!
                cache graph-atom file-paths
                :strategy :timestamp)]
    (merge result
           {:strategy :timestamp
            :metadata {:files-checked (count file-paths)
                       :files-changed (count (:changed result))}})))

(defmethod invalidate-by-strategy :dependency-based
  [cache graph-atom node-ids options]
  (let [invalidated (atom #{})]
    (doseq [node-id node-ids]
      (let [nodes (deps/invalidate-transitive! cache graph-atom node-id)]
        (swap! invalidated into nodes)))
    {:invalidated @invalidated
     :strategy :dependency-based
     :metadata {:nodes-affected (count @invalidated)
                :source-nodes (count node-ids)}}))

(defmethod invalidate-by-strategy :pattern
  [cache graph-atom patterns options]
  (let [graph @graph-atom
        matching-nodes (filter
                        (fn [[node-id node-data]]
                          (some (fn [pattern]
                                  (or (str/includes? node-id pattern)
                                      (when-let [path (:path node-data)]
                                        (str/includes? path pattern))
                                      (when-let [ref (:ref node-data)]
                                        (str/includes? ref pattern))))
                                patterns))
                        (:nodes graph))
        node-ids (map first matching-nodes)]

    ;; Invalidate matching nodes
    (doseq [node-id node-ids]
      (deps/invalidate-cache-for-node! cache graph node-id))

    {:invalidated (set node-ids)
     :strategy :pattern
     :metadata {:patterns patterns
                :matches-found (count node-ids)}}))

(defmethod invalidate-by-strategy :global
  [cache graph-atom _ options]
  (let [cleared-count (cache/clear! cache)]
    {:invalidated :all
     :strategy :global
     :metadata {:entries-cleared cleared-count}}))

(defmethod invalidate-by-strategy :selective
  [cache graph-atom targets options]
  ;; Selective invalidation based on change type
  (let [{:keys [changed-files changed-tokens changed-components]} targets
        invalidated (atom #{})]

    ;; Invalidate changed files and their dependents
    (when (seq changed-files)
      (let [result (deps/invalidate-changed-files!
                    cache graph-atom changed-files
                    :strategy (:file-strategy options :content-hash))]
        (swap! invalidated into (:invalidated result))))

    ;; Invalidate changed tokens and their dependents
    (when (seq changed-tokens)
      (doseq [token-ref changed-tokens]
        (let [node-id (deps/token-node-id token-ref)
              nodes (deps/invalidate-transitive! cache graph-atom node-id)]
          (swap! invalidated into nodes))))

    ;; Invalidate changed components and their dependents
    (when (seq changed-components)
      (doseq [component-name changed-components]
        (let [node-id (deps/component-node-id component-name)
              nodes (deps/invalidate-transitive! cache graph-atom node-id)]
          (swap! invalidated into nodes))))

    {:invalidated @invalidated
     :strategy :selective
     :metadata {:files-changed (count changed-files)
                :tokens-changed (count changed-tokens)
                :components-changed (count changed-components)
                :total-invalidated (count @invalidated)}}))

(defmethod invalidate-by-strategy :default
  [cache graph-atom targets options]
  (throw (ex-info "Unknown invalidation strategy"
                  {:strategy (:strategy options)
                   :valid-strategies [:content-hash :timestamp :dependency-based
                                      :pattern :global :selective]})))

;; ============================================================================
;; HIGH-LEVEL INVALIDATION API
;; ============================================================================

(defn invalidate!
  "High-level invalidation function with automatic strategy selection.

  Args:
    cache - Cache instance
    graph-atom - Dependency graph atom
    what - What to invalidate:
           - File path(s) - String or collection
           - :all - Invalidate everything
           - Node ID(s) - String or collection
           - Pattern - String with wildcards
    options - Optional configuration:
              :strategy - Force specific strategy
              :file-strategy - Strategy for file changes (:content-hash or :timestamp)

  Returns:
    Invalidation result map

  Examples:
    (invalidate! cache graph 'components/button.edn')
    (invalidate! cache graph ['components/button.edn' 'global/defaults.edn'])
    (invalidate! cache graph :all)
    (invalidate! cache graph 'token:$colors.*' {:strategy :pattern})
    (invalidate! cache graph 'file:components/*' {:strategy :pattern})"
  [cache graph-atom what & [options]]
  (let [opts (or options {})
        strategy (or (:strategy opts)
                     (cond
                       (= what :all) :global
                       (string? what) (if (str/includes? what "*")
                                        :pattern
                                        (if (str/starts-with? what "file:")
                                          :dependency-based
                                          :content-hash))
                       (coll? what) :content-hash
                       :else :global))]

    (invalidate-by-strategy
     cache
     graph-atom
     (if (= what :all) nil what)
     (assoc opts :strategy strategy))))

;; ============================================================================
;; SMART INVALIDATION
;; ============================================================================

(defn smart-invalidate!
  "Intelligently invalidate cache based on file system changes.

  Automatically detects:
  - Changed files (content hash or timestamp)
  - Affected tokens
  - Affected components
  - Transitive dependencies

  Args:
    cache - Cache instance
    graph-atom - Dependency graph atom
    watch-dirs - Directories to watch for changes
    options - Configuration:
              :strategy - :content-hash (default) or :timestamp
              :patterns - File patterns to watch (e.g., ['*.edn'])
              :exclude - Patterns to exclude

  Returns:
    Invalidation result map"
  [cache graph-atom watch-dirs & [options]]
  (let [opts (merge {:strategy :content-hash
                     :patterns ["*.edn"]
                     :exclude []}
                    options)
        graph @graph-atom
        file-nodes (filter #(= :file (:type (val %))) (:nodes graph))
        file-paths (map #(get-in % [1 :path]) file-nodes)

        ;; Filter by watch directories
        watched-files (filter
                       (fn [path]
                         (some #(str/starts-with? path %) watch-dirs))
                       file-paths)

        ;; Filter by patterns
        pattern-matched (if (seq (:patterns opts))
                         (filter
                          (fn [path]
                            (some #(or (str/ends-with? path %)
                                       (= % "*"))
                                  (:patterns opts)))
                          watched-files)
                         watched-files)

        ;; Exclude patterns
        final-files (if (seq (:exclude opts))
                     (remove
                      (fn [path]
                        (some #(str/includes? path %) (:exclude opts)))
                      pattern-matched)
                     pattern-matched)]

    (invalidate-by-strategy
     cache
     graph-atom
     final-files
     {:strategy (:strategy opts)})))

;; ============================================================================
;; BATCH INVALIDATION
;; ============================================================================

(defn batch-invalidate!
  "Invalidate multiple targets in a single batch operation.

  More efficient than multiple individual invalidations as it:
  - Deduplicates overlapping dependencies
  - Processes transitive dependencies once
  - Generates single invalidation report

  Args:
    cache - Cache instance
    graph-atom - Dependency graph atom
    targets - Collection of invalidation targets:
              {:files [...] :tokens [...] :components [...] :patterns [...]}
    options - Configuration

  Returns:
    Combined invalidation result"
  [cache graph-atom targets & [options]]
  (let [opts (or options {})
        {:keys [files tokens components patterns]} targets
        all-invalidated (atom #{})
        results (atom [])]

    ;; Invalidate files
    (when (seq files)
      (let [result (invalidate-by-strategy
                    cache graph-atom files
                    (assoc opts :strategy (or (:file-strategy opts) :content-hash)))]
        (swap! all-invalidated into (:invalidated result))
        (swap! results conj result)))

    ;; Invalidate tokens
    (when (seq tokens)
      (let [node-ids (map deps/token-node-id tokens)
            result (invalidate-by-strategy
                    cache graph-atom node-ids
                    (assoc opts :strategy :dependency-based))]
        (swap! all-invalidated into (:invalidated result))
        (swap! results conj result)))

    ;; Invalidate components
    (when (seq components)
      (let [node-ids (map deps/component-node-id components)
            result (invalidate-by-strategy
                    cache graph-atom node-ids
                    (assoc opts :strategy :dependency-based))]
        (swap! all-invalidated into (:invalidated result))
        (swap! results conj result)))

    ;; Invalidate patterns
    (when (seq patterns)
      (let [result (invalidate-by-strategy
                    cache graph-atom patterns
                    (assoc opts :strategy :pattern))]
        (swap! all-invalidated into (:invalidated result))
        (swap! results conj result)))

    {:invalidated @all-invalidated
     :strategy :batch
     :metadata {:total-invalidated (count @all-invalidated)
                :operations (count @results)
                :details @results}}))

;; ============================================================================
;; INVALIDATION POLICIES
;; ============================================================================

(defn create-invalidation-policy
  "Create an invalidation policy configuration.

  Policies define when and how cache should be invalidated.

  Options:
    :strategy - Default invalidation strategy
    :auto-invalidate? - Automatically invalidate on changes
    :watch-dirs - Directories to watch for changes
    :watch-patterns - File patterns to watch
    :ttl-ms - Time-to-live for cache entries
    :max-age-ms - Maximum age before forced invalidation

  Returns:
    Policy configuration map"
  [& {:keys [strategy auto-invalidate? watch-dirs watch-patterns
             ttl-ms max-age-ms]
      :or {strategy :content-hash
           auto-invalidate? false
           watch-dirs ["default/" "library/" "projects/"]
           watch-patterns ["*.edn"]
           ttl-ms nil
           max-age-ms nil}}]
  {:strategy strategy
   :auto-invalidate? auto-invalidate?
   :watch-dirs watch-dirs
   :watch-patterns watch-patterns
   :ttl-ms ttl-ms
   :max-age-ms max-age-ms})

(defn apply-policy!
  "Apply invalidation policy to cache.

  Args:
    cache - Cache instance
    graph-atom - Dependency graph atom
    policy - Policy configuration

  Returns:
    Invalidation result"
  [cache graph-atom policy]
  (when (:auto-invalidate? policy)
    (smart-invalidate!
     cache
     graph-atom
     (:watch-dirs policy)
     {:strategy (:strategy policy)
      :patterns (:watch-patterns policy)})))

;; ============================================================================
;; REPORTING
;; ============================================================================

(defn invalidation-summary
  "Generate a summary report of invalidation operations.

  Args:
    results - Sequence of invalidation results

  Returns:
    Summary map with aggregated statistics"
  [results]
  (let [total-invalidated (reduce + (map #(count (:invalidated %)) results))
        by-strategy (group-by :strategy results)]
    {:total-operations (count results)
     :total-invalidated total-invalidated
     :by-strategy (into {}
                        (map (fn [[k v]]
                               [k {:operations (count v)
                                   :invalidated (reduce + (map #(count (:invalidated %)) v))}])
                             by-strategy))
     :operations results}))
