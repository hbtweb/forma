(ns forma.cache.dependencies
  "Dependency tracking system for intelligent cache invalidation.

  Tracks dependencies between:
  - Files (EDN configs, components, platforms)
  - Tokens (references and definitions)
  - Components (imports and usage)
  - Hierarchy levels (inheritance chains)

  Enables incremental compilation by invalidating only affected cache entries
  when a dependency changes.

  Phase 5.3 implementation."
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.walk :as walk]
            [forma.cache.core :as cache]))

;; ============================================================================
;; DEPENDENCY GRAPH
;; ============================================================================

(defn create-dependency-graph
  "Create an empty dependency graph.

  Structure:
    {:nodes {node-id {:type :file :path 'components/button.edn' :hash 'abc123'}}
     :edges {node-id #{dependent-id1 dependent-id2}}
     :reverse-edges {dependent-id #{node-id}}}

  Returns:
    Empty dependency graph"
  []
  {:nodes {}
   :edges {}
   :reverse-edges {}})

(defn add-node!
  "Add or update a node in the dependency graph.

  Args:
    graph-atom - Atom containing dependency graph
    node-id - Unique node identifier (e.g., 'file:components/button.edn')
    node-data - Node metadata {:type :file :path '...' :hash '...' :timestamp ...}

  Returns:
    Updated node-data"
  [graph-atom node-id node-data]
  (swap! graph-atom assoc-in [:nodes node-id] node-data)
  node-data)

(defn add-edge!
  "Add a dependency edge from source to target.

  'source depends on target' means:
  - If target changes, source must be recompiled

  Args:
    graph-atom - Atom containing dependency graph
    source-id - Source node ID
    target-id - Target node ID (dependency)

  Returns:
    nil"
  [graph-atom source-id target-id]
  (swap! graph-atom update-in [:edges source-id] (fnil conj #{}) target-id)
  (swap! graph-atom update-in [:reverse-edges target-id] (fnil conj #{}) source-id)
  nil)

(defn remove-node!
  "Remove a node and all its edges from the dependency graph.

  Args:
    graph-atom - Atom containing dependency graph
    node-id - Node identifier to remove

  Returns:
    Removed node-data or nil"
  [graph-atom node-id]
  (let [graph @graph-atom
        node-data (get-in graph [:nodes node-id])
        dependencies (get-in graph [:edges node-id] #{})
        dependents (get-in graph [:reverse-edges node-id] #{})]

    ;; Remove edges
    (doseq [dep-id dependencies]
      (swap! graph-atom update-in [:reverse-edges dep-id] disj node-id))

    (doseq [dependent-id dependents]
      (swap! graph-atom update-in [:edges dependent-id] disj node-id))

    ;; Remove node
    (swap! graph-atom update :nodes dissoc node-id)
    (swap! graph-atom update :edges dissoc node-id)
    (swap! graph-atom update :reverse-edges dissoc node-id)

    node-data))

(defn get-dependencies
  "Get all direct dependencies of a node.

  Args:
    graph - Dependency graph
    node-id - Node identifier

  Returns:
    Set of dependency node IDs"
  [graph node-id]
  (get-in graph [:edges node-id] #{}))

(defn get-dependents
  "Get all direct dependents of a node (reverse dependencies).

  Args:
    graph - Dependency graph
    node-id - Node identifier

  Returns:
    Set of dependent node IDs"
  [graph node-id]
  (get-in graph [:reverse-edges node-id] #{}))

(defn get-transitive-dependents
  "Get all transitive dependents of a node (full dependency tree).

  Example:
    A depends on B, B depends on C
    get-transitive-dependents(C) => #{B A}

  Args:
    graph - Dependency graph
    node-id - Node identifier

  Returns:
    Set of all transitive dependent node IDs"
  [graph node-id]
  (loop [to-visit #{node-id}
         visited #{}
         dependents #{}]
    (if (empty? to-visit)
      dependents
      (let [current (first to-visit)
            direct-deps (get-dependents graph current)
            new-deps (set/difference direct-deps visited)]
        (recur (set/union (disj to-visit current) new-deps)
               (conj visited current)
               (set/union dependents new-deps))))))

;; ============================================================================
;; FILE TRACKING
;; ============================================================================

(defn file-node-id
  "Generate node ID for a file.

  Args:
    file-path - Relative file path

  Returns:
    Node ID string (e.g., 'file:components/button.edn')"
  [file-path]
  (str "file:" file-path))

(defn file-hash
  "Generate hash of file content for change detection.

  Args:
    file-path - Absolute or relative file path

  Returns:
    Content hash string or nil if file doesn't exist"
  [file-path]
  (try
    (when (.exists (io/file file-path))
      (cache/content-hash (slurp file-path)))
    (catch Exception _ nil)))

(defn file-timestamp
  "Get file modification timestamp.

  Args:
    file-path - Absolute or relative file path

  Returns:
    Timestamp in milliseconds or nil if file doesn't exist"
  [file-path]
  (try
    (when (.exists (io/file file-path))
      (.lastModified (io/file file-path)))
    (catch Exception _ nil)))

(defn track-file!
  "Track a file in the dependency graph.

  Args:
    graph-atom - Atom containing dependency graph
    file-path - Relative file path
    metadata - Optional additional metadata

  Returns:
    Node data"
  [graph-atom file-path & [metadata]]
  (let [node-id (file-node-id file-path)
        node-data (merge {:type :file
                          :path file-path
                          :hash (file-hash file-path)
                          :timestamp (file-timestamp file-path)}
                         metadata)]
    (add-node! graph-atom node-id node-data)))

(defn file-changed?
  "Check if a file has changed since last tracking.

  Strategy can be:
    :content-hash - Compare content hashes (default)
    :timestamp - Compare modification timestamps

  Args:
    graph - Dependency graph
    file-path - Relative file path
    strategy - :content-hash or :timestamp (default: :content-hash)

  Returns:
    true if file changed, false otherwise"
  [graph file-path & {:keys [strategy] :or {strategy :content-hash}}]
  (let [node-id (file-node-id file-path)
        old-node (get-in graph [:nodes node-id])]
    (if-not old-node
      true  ; New file = changed
      (case strategy
        :content-hash
        (let [old-hash (:hash old-node)
              new-hash (file-hash file-path)]
          (not= old-hash new-hash))

        :timestamp
        (let [old-timestamp (:timestamp old-node)
              new-timestamp (file-timestamp file-path)]
          (not= old-timestamp new-timestamp))))))

;; ============================================================================
;; TOKEN TRACKING
;; ============================================================================

(defn extract-token-references
  "Extract all token references from EDN data.

  Args:
    edn-data - Any Clojure data structure

  Returns:
    Set of token reference strings (e.g., #{'$colors.primary' '$spacing.md'})"
  [edn-data]
  (let [refs (atom #{})]
    (walk/postwalk
     (fn [node]
       (when (and (string? node)
                  (str/starts-with? node "$"))
         (swap! refs conj node))
       node)
     edn-data)
    @refs))

(defn token-node-id
  "Generate node ID for a token.

  Args:
    token-ref - Token reference string (e.g., '$colors.primary')

  Returns:
    Node ID string (e.g., 'token:$colors.primary')"
  [token-ref]
  (str "token:" token-ref))

(defn track-token!
  "Track a token definition in the dependency graph.

  Args:
    graph-atom - Atom containing dependency graph
    token-ref - Token reference string (e.g., '$colors.primary')
    value - Token value
    source-file - File where token is defined

  Returns:
    Node data"
  [graph-atom token-ref value source-file]
  (let [node-id (token-node-id token-ref)
        node-data {:type :token
                   :ref token-ref
                   :value value
                   :source-file source-file}]
    (add-node! graph-atom node-id node-data)
    ;; Track dependency on source file
    (when source-file
      (add-edge! graph-atom node-id (file-node-id source-file)))
    node-data))

(defn track-token-usage!
  "Track token usage (create dependency from element to token).

  Args:
    graph-atom - Atom containing dependency graph
    element-id - Element identifier using the token
    token-refs - Set or sequence of token references

  Returns:
    nil"
  [graph-atom element-id token-refs]
  (doseq [token-ref token-refs]
    (add-edge! graph-atom element-id (token-node-id token-ref)))
  nil)

;; ============================================================================
;; COMPONENT TRACKING
;; ============================================================================

(defn component-node-id
  "Generate node ID for a component.

  Args:
    component-name - Component name (keyword or string)

  Returns:
    Node ID string (e.g., 'component:button')"
  [component-name]
  (str "component:" (name component-name)))

(defn track-component!
  "Track a component in the dependency graph.

  Args:
    graph-atom - Atom containing dependency graph
    component-name - Component name
    component-edn - Component EDN definition
    source-file - File where component is defined

  Returns:
    Node data"
  [graph-atom component-name component-edn source-file]
  (let [node-id (component-node-id component-name)
        token-refs (extract-token-references component-edn)
        node-data {:type :component
                   :name component-name
                   :source-file source-file
                   :token-refs token-refs}]

    (add-node! graph-atom node-id node-data)

    ;; Track dependency on source file
    (when source-file
      (add-edge! graph-atom node-id (file-node-id source-file)))

    ;; Track token dependencies
    (track-token-usage! graph-atom node-id token-refs)

    node-data))

;; ============================================================================
;; INVALIDATION
;; ============================================================================

(defn invalidate-cache-for-node!
  "Invalidate cache entries for a specific node.

  Args:
    cache - Cache instance (ICache)
    graph - Dependency graph
    node-id - Node identifier

  Returns:
    Number of cache entries invalidated"
  [cache graph node-id]
  (let [node (get-in graph [:nodes node-id])]
    (when node
      ;; Invalidate based on node type
      (case (:type node)
        :file
        (do
          ;; Invalidate file cache
          (forma.cache.core/invalidate! cache (str "file-cache:" (:path node)))
          1)

        :token
        (do
          ;; Invalidate token cache
          (forma.cache.core/invalidate! cache (str "token-cache:" (:ref node)))
          1)

        :component
        (do
          ;; Invalidate component cache
          (forma.cache.core/invalidate! cache (str "component-cache:" (:name node)))
          1)

        0))))

(defn invalidate-transitive!
  "Invalidate cache for a node and all its transitive dependents.

  Args:
    cache - Cache instance (ICache)
    graph-atom - Atom containing dependency graph
    node-id - Node identifier

  Returns:
    Set of invalidated node IDs"
  [cache graph-atom node-id]
  (let [graph @graph-atom
        dependents (get-transitive-dependents graph node-id)
        all-nodes (conj dependents node-id)]

    ;; Invalidate all affected nodes
    (doseq [id all-nodes]
      (invalidate-cache-for-node! cache graph id))

    all-nodes))

(defn invalidate-changed-files!
  "Scan for changed files and invalidate affected cache entries.

  Args:
    cache - Cache instance
    graph-atom - Atom containing dependency graph
    file-paths - Sequence of file paths to check
    strategy - :content-hash or :timestamp (default: :content-hash)

  Returns:
    {:changed #{file-paths...}
     :invalidated #{node-ids...}}"
  [cache graph-atom file-paths & {:keys [strategy] :or {strategy :content-hash}}]
  (let [graph @graph-atom
        changed-files (filter #(file-changed? graph % :strategy strategy) file-paths)
        invalidated (atom #{})]

    (doseq [file-path changed-files]
      (let [node-id (file-node-id file-path)
            nodes (invalidate-transitive! cache graph-atom node-id)]
        (swap! invalidated set/union nodes)
        ;; Update file tracking
        (track-file! graph-atom file-path)))

    {:changed (set changed-files)
     :invalidated @invalidated}))

;; ============================================================================
;; UTILITIES
;; ============================================================================

(defn dependency-report
  "Generate a dependency report for analysis.

  Args:
    graph - Dependency graph

  Returns:
    Map with statistics and details"
  [graph]
  (let [nodes (:nodes graph)
        edges (:edges graph)
        node-count (count nodes)
        edge-count (reduce + (map count (vals edges)))
        nodes-by-type (group-by :type (vals nodes))]

    {:node-count node-count
     :edge-count edge-count
     :nodes-by-type (into {}
                          (map (fn [[k v]] [k (count v)])
                               nodes-by-type))
     :most-depended-on
     (->> (:reverse-edges graph)
          (sort-by (comp count val) >)
          (take 10)
          (map (fn [[node-id deps]]
                 {:node-id node-id
                  :dependent-count (count deps)
                  :node-data (get nodes node-id)})))}))

(defn visualize-dependencies
  "Generate a simple text visualization of dependencies.

  Args:
    graph - Dependency graph
    node-id - Starting node ID
    max-depth - Maximum depth to traverse (default: 3)

  Returns:
    String representation of dependency tree"
  [graph node-id & {:keys [max-depth] :or {max-depth 3}}]
  (letfn [(visualize-node [id depth]
            (let [node (get-in graph [:nodes id])
                  indent (str/join (repeat (* depth 2) " "))
                  deps (get-dependencies graph id)]
              (str indent "- " (:type node) ": " (or (:path node) (:ref node) (:name node)) "\n"
                   (when (and (seq deps) (< depth max-depth))
                     (str/join (map #(visualize-node % (inc depth)) deps))))))]
    (visualize-node node-id 0)))
