(ns forma.cache.compiler
  "Cache-aware compiler integration for Forma.

  Wraps forma.compiler with intelligent caching:
  - Caches resolved contexts (hierarchy + tokens)
  - Caches compiled elements
  - Caches platform configurations
  - Tracks dependencies automatically
  - Provides incremental compilation API

  Phase 5.3 implementation."
  (:require [forma.compiler :as compiler]
            [forma.cache.core :as cache]
            [forma.cache.dependencies :as deps]
            [forma.cache.invalidation :as invalidation]
            [forma.cache.incremental :as incremental]
            [clojure.string :as str]))

;; ============================================================================
;; GLOBAL CACHE AND DEPENDENCY GRAPH
;; ============================================================================

;; Global cache instance for compilation artifacts
(defonce ^:private global-cache
  (atom nil))

;; Global dependency graph for tracking file dependencies
(defonce ^:private global-dependency-graph
  (atom (deps/create-dependency-graph)))

(defn initialize-cache!
  "Initialize the global cache system.

  Options:
    :max-size - Maximum memory cache entries (default: 1000)
    :ttl-ms - Time-to-live for cache entries (default: 3600000 = 1 hour)
    :disk-enabled? - Enable disk cache (default: true)
    :cache-dir - Disk cache directory (default: '.forma-cache')

  Returns:
    Cache instance"
  [& {:keys [max-size ttl-ms disk-enabled? cache-dir]
      :or {max-size 1000
           ttl-ms 3600000
           disk-enabled? true
           cache-dir ".forma-cache"}}]
  (let [new-cache (cache/create-layered-cache
                   :max-size max-size
                   :ttl-ms ttl-ms
                   :disk-enabled? disk-enabled?
                   :cache-dir cache-dir)]
    (reset! global-cache new-cache)
    new-cache))

(defn get-cache
  "Get the global cache instance (initializes if needed)."
  []
  (when-not @global-cache
    (initialize-cache!))
  @global-cache)

(defn get-dependency-graph
  "Get the global dependency graph atom."
  []
  global-dependency-graph)

(defn clear-cache!
  "Clear all cache entries and reset dependency graph.

  Returns:
    Number of entries cleared"
  []
  (let [count (cache/clear! (get-cache))]
    (reset! global-dependency-graph (deps/create-dependency-graph))
    count))

;; ============================================================================
;; CACHED COMPILATION FUNCTIONS
;; ============================================================================

(defn compile-element-cached
  "Compile an element with caching.

  Checks cache first, compiles only if needed, tracks dependencies.

  Args:
    element - Forma element EDN
    context - Compilation context
    options - Additional options:
              :force-recompile - Skip cache and force recompilation
              :track-dependencies - Track file dependencies (default: true)

  Returns:
    Compiled output (string, Hiccup, etc.)"
  [element context & [options]]
  (let [opts (merge {:force-recompile false
                     :track-dependencies true}
                    options)
        cache-key (cache/element-cache-key element context)
        cache-inst (get-cache)]

    (if (and (not (:force-recompile opts))
             (cache/get-cached cache-inst cache-key))
      ;; Cache hit
      (cache/get-cached cache-inst cache-key)

      ;; Cache miss - compile and cache
      (let [result (compiler/compile-to-html [element] context)]

        ;; Cache the result
        (cache/put-cached! cache-inst cache-key result)

        ;; Track dependencies if enabled
        (when (:track-dependencies opts)
          (let [graph (get-dependency-graph)
                element-id (or (:id element)
                              (keyword (str "element-" (hash element))))
                element-node-id (str "element:" (name element-id))]

            ;; Track element node
            (deps/add-node! graph element-node-id
                           {:type :element
                            :element element
                            :cache-key cache-key})

            ;; Track token dependencies
            (when-let [token-refs (deps/extract-token-references element)]
              (deps/track-token-usage! graph element-node-id token-refs))

            ;; Track component dependency if this is a component reference
            (when-let [component-name (:component element)]
              (deps/add-edge! graph element-node-id
                            (deps/component-node-id component-name)))))

        result))))

(defn compile-to-html-cached
  "Wrapper for forma.compiler/compile-to-html with caching.

  Args:
    elements - Forma element(s) - can be single element or collection
    context - Compilation context
    options - Additional options:
              :force-recompile - Skip cache
              :track-dependencies - Track dependencies

  Returns:
    Compiled HTML string"
  [elements context & [options]]
  (let [opts (or options {})
        elements-vec (if (vector? (first elements))
                      elements
                      [elements])
        cache-key (cache/content-hash [elements-vec context])
        cache-inst (get-cache)]

    (if (and (not (:force-recompile opts))
             (cache/get-cached cache-inst cache-key))
      ;; Cache hit
      (cache/get-cached cache-inst cache-key)

      ;; Cache miss - compile
      (let [result (compiler/compile-to-html elements context)]

        ;; Cache result
        (cache/put-cached! cache-inst cache-key result)

        ;; Track dependencies for each element
        (when (:track-dependencies opts true)
          (doseq [element elements-vec]
            (compile-element-cached element context
                                   (assoc opts :force-recompile true))))

        result))))

(defn load-platform-config-cached
  "Load platform configuration with caching.

  Args:
    platform-name - Platform name (e.g., :html, :css)
    project-name - Optional project name

  Returns:
    Platform configuration map"
  [platform-name & [project-name]]
  (let [cache-key (str "platform:" (name platform-name)
                      (when project-name (str ":" project-name)))
        cache-inst (get-cache)]

    (if-let [cached (cache/get-cached cache-inst cache-key)]
      cached

      ;; Load and cache
      (let [config (if project-name
                    (compiler/load-platform-config platform-name project-name)
                    (compiler/load-platform-config platform-name))
            file-path (str "platforms/" (name platform-name) ".edn")]

        ;; Cache result
        (cache/put-cached! cache-inst cache-key config)

        ;; Track file dependency
        (let [graph (get-dependency-graph)]
          (deps/track-file! graph file-path
                           {:platform platform-name
                            :project project-name}))

        config))))

;; ============================================================================
;; INCREMENTAL COMPILATION API
;; ============================================================================

(defn compile-project-incremental
  "Incrementally compile a Forma project.

  Only recompiles changed files and their dependencies.

  Args:
    project-name - Project name
    context - Base compilation context
    options - Configuration:
              :strategy - Change detection strategy (:content-hash or :timestamp)
              :force-rebuild - Force rebuild specific files
              :on-progress - Progress callback function
              :watch-dirs - Directories to watch (default: from project)

  Returns:
    Build result with statistics"
  [project-name context & [options]]
  (let [opts (merge {:strategy :content-hash
                     :watch-dirs [(str "projects/" project-name "/")
                                 "default/"
                                 "library/"]
                     :force-rebuild #{}}
                    options)

        cache-inst (get-cache)
        graph (get-dependency-graph)

        ;; Find all project files
        file-paths (-> (clojure.java.io/file (first (:watch-dirs opts)))
                      file-seq
                      (->> (filter #(.isFile %))
                           (map #(.getPath %))
                           (filter #(str/ends-with? % ".edn"))))

        ;; Compilation function
        compile-fn (fn [node-id node-data]
                    (case (:type node-data)
                      :file
                      (let [file-path (:path node-data)
                            edn-data (clojure.edn/read-string (slurp file-path))]
                        (compile-to-html-cached [edn-data] context opts))

                      :component
                      (let [component-name (:name node-data)
                            component-edn (:component node-data)]
                        (compile-element-cached component-edn context opts))

                      ;; Default: return node data
                      node-data))]

    ;; Execute incremental build
    (incremental/incremental-compile!
     cache-inst
     graph
     file-paths
     compile-fn
     opts)))

;; ============================================================================
;; CACHE MANAGEMENT API
;; ============================================================================

(defn invalidate-cache!
  "Invalidate cache entries.

  Args:
    what - What to invalidate:
           - :all - Clear entire cache
           - File path(s) - String or collection
           - Node ID(s) - String or collection
           - Pattern - String with wildcards
    options - Invalidation options

  Returns:
    Invalidation result"
  [what & [options]]
  (let [cache-inst (get-cache)
        graph (get-dependency-graph)]
    (invalidation/invalidate! cache-inst graph what options)))

(defn cache-stats
  "Get cache statistics.

  Returns:
    Statistics map with hit rates, sizes, etc."
  []
  (cache/stats (get-cache)))

(defn dependency-stats
  "Get dependency graph statistics.

  Returns:
    Statistics map with node/edge counts, etc."
  []
  (deps/dependency-report @(get-dependency-graph)))

;; ============================================================================
;; CONFIGURATION
;; ============================================================================

(defn configure-cache!
  "Configure cache behavior.

  Options:
    :enabled - Enable/disable caching (default: true)
    :max-size - Maximum memory cache size
    :ttl-ms - Cache entry time-to-live
    :disk-enabled? - Enable disk cache
    :cache-dir - Disk cache directory
    :strategy - Default invalidation strategy

  Returns:
    Updated configuration"
  [& {:keys [enabled max-size ttl-ms disk-enabled? cache-dir strategy]
      :or {enabled true}}]

  (when-not enabled
    (clear-cache!)
    (reset! global-cache nil))

  (when (and enabled (not @global-cache))
    (initialize-cache!
     :max-size (or max-size 1000)
     :ttl-ms (or ttl-ms 3600000)
     :disk-enabled? (if (nil? disk-enabled?) true disk-enabled?)
     :cache-dir (or cache-dir ".forma-cache")))

  {:enabled enabled
   :max-size (or max-size 1000)
   :ttl-ms (or ttl-ms 3600000)
   :disk-enabled? (if (nil? disk-enabled?) true disk-enabled?)
   :cache-dir (or cache-dir ".forma-cache")
   :strategy (or strategy :content-hash)})

;; ============================================================================
;; UTILITIES
;; ============================================================================

(defn warm-cache!
  "Pre-warm cache by loading commonly used configurations.

  Args:
    project-name - Optional project name
    platforms - Platforms to load (default: [:html :css :htmx])

  Returns:
    Number of items cached"
  [& {:keys [project-name platforms]
      :or {platforms [:html :css :htmx]}}]
  (let [count (atom 0)]

    ;; Load platform configs
    (doseq [platform platforms]
      (load-platform-config-cached platform project-name)
      (swap! count inc))

    @count))

(defn export-cache-stats
  "Export cache and dependency statistics to a map.

  Useful for monitoring and debugging.

  Returns:
    Map with cache and dependency statistics"
  []
  {:cache (cache-stats)
   :dependencies (dependency-stats)
   :timestamp (cache/now-millis)})

(defn format-cache-report
  "Format cache statistics as a human-readable string.

  Returns:
    Formatted report string"
  []
  (let [stats (export-cache-stats)
        cache-stats (get-in stats [:cache :memory])
        dep-stats (:dependencies stats)]

    (str "=== Forma Cache Report ===\n"
         "\n"
         "Memory Cache:\n"
         "  Size: " (:size cache-stats) " / " (:max-size cache-stats) "\n"
         "  Hit Rate: " (format "%.1f%%" (* 100 (:hit-rate cache-stats))) "\n"
         "  Hits: " (:hits cache-stats) "\n"
         "  Misses: " (:misses cache-stats) "\n"
         "  Evictions: " (:evictions cache-stats) "\n"
         "\n"
         "Dependency Graph:\n"
         "  Nodes: " (:node-count dep-stats) "\n"
         "  Edges: " (:edge-count dep-stats) "\n"
         "  By Type: " (:nodes-by-type dep-stats) "\n"
         "\n"
         "Generated: " (:timestamp stats) "\n")))
