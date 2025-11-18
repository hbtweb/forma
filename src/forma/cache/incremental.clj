(ns forma.cache.incremental
  "Incremental compilation support for fast rebuilds.

  Enables compiling only changed files and their dependencies instead of
  full project recompilation.

  Features:
  - Change detection (content-based or timestamp-based)
  - Dependency-aware recompilation
  - Compilation state tracking
  - Smart build ordering
  - Progress reporting

  Phase 5.3 implementation."
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [forma.cache.core :as cache]
            [forma.cache.dependencies :as deps]
            [forma.cache.invalidation :as invalidation]))

;; ============================================================================
;; COMPILATION STATE
;; ============================================================================

(defn create-compilation-state
  "Create a new compilation state tracker.

  Structure:
    {:compiled #{node-ids...}     ; Successfully compiled nodes
     :failed #{node-ids...}       ; Failed compilations
     :pending #{node-ids...}      ; Waiting to compile
     :in-progress #{node-ids...}  ; Currently compiling
     :skipped #{node-ids...}      ; Skipped (cached)
     :timestamp 1234567890}       ; Build timestamp

  Returns:
    Empty compilation state"
  []
  {:compiled #{}
   :failed #{}
   :pending #{}
   :in-progress #{}
   :skipped #{}
   :timestamp (cache/now-millis)})

(defn mark-compiled!
  "Mark a node as successfully compiled.

  Args:
    state-atom - Atom containing compilation state
    node-id - Node identifier

  Returns:
    Updated state"
  [state-atom node-id]
  (swap! state-atom
         (fn [state]
           (-> state
               (update :compiled conj node-id)
               (update :in-progress disj node-id)
               (update :pending disj node-id))))
  @state-atom)

(defn mark-failed!
  "Mark a node compilation as failed.

  Args:
    state-atom - Atom containing compilation state
    node-id - Node identifier
    error - Error information

  Returns:
    Updated state"
  [state-atom node-id error]
  (swap! state-atom
         (fn [state]
           (-> state
               (update :failed conj node-id)
               (update :in-progress disj node-id)
               (update :pending disj node-id)
               (assoc-in [:errors node-id] error))))
  @state-atom)

(defn mark-skipped!
  "Mark a node as skipped (using cached result).

  Args:
    state-atom - Atom containing compilation state
    node-id - Node identifier

  Returns:
    Updated state"
  [state-atom node-id]
  (swap! state-atom
         (fn [state]
           (-> state
               (update :skipped conj node-id)
               (update :pending disj node-id))))
  @state-atom)

(defn mark-in-progress!
  "Mark a node as currently compiling.

  Args:
    state-atom - Atom containing compilation state
    node-id - Node identifier

  Returns:
    Updated state"
  [state-atom node-id]
  (swap! state-atom
         (fn [state]
           (-> state
               (update :in-progress conj node-id)
               (update :pending disj node-id))))
  @state-atom)

;; ============================================================================
;; CHANGE DETECTION
;; ============================================================================

(defn detect-changes
  "Detect changes in project files.

  Args:
    graph - Dependency graph
    file-paths - Sequence of file paths to check
    strategy - :content-hash (default) or :timestamp

  Returns:
    {:changed #{file-paths...}
     :unchanged #{file-paths...}
     :new #{file-paths...}
     :deleted #{file-paths...}}"
  [graph file-paths strategy]
  (let [existing-files (set (map #(get-in % [1 :path])
                                 (filter #(= :file (:type (val %)))
                                         (:nodes graph))))
        checked-files (set file-paths)
        new-files (set/difference checked-files existing-files)
        deleted-files (set/difference existing-files checked-files)
        potential-changed (set/intersection checked-files existing-files)

        changed-files (set (filter
                            #(deps/file-changed? graph % :strategy strategy)
                            potential-changed))

        unchanged-files (set/difference potential-changed changed-files)]

    {:changed changed-files
     :unchanged unchanged-files
     :new new-files
     :deleted deleted-files
     :total-checked (count file-paths)}))

(defn compute-affected-nodes
  "Compute all nodes affected by changes.

  Args:
    graph - Dependency graph
    changed-files - Set of changed file paths

  Returns:
    {:direct #{node-ids...}       ; Directly changed nodes
     :transitive #{node-ids...}   ; Transitively affected nodes
     :all #{node-ids...}}         ; All affected nodes"
  [graph changed-files]
  (let [direct-nodes (set (map deps/file-node-id changed-files))
        transitive-nodes (reduce
                          (fn [acc node-id]
                            (set/union acc (deps/get-transitive-dependents graph node-id)))
                          #{}
                          direct-nodes)
        all-nodes (set/union direct-nodes transitive-nodes)]

    {:direct direct-nodes
     :transitive transitive-nodes
     :all all-nodes}))

;; ============================================================================
;; BUILD ORDERING
;; ============================================================================

(defn topological-sort
  "Topologically sort nodes based on dependencies.

  Ensures dependencies are compiled before dependents.

  Args:
    graph - Dependency graph
    node-ids - Set of node IDs to sort

  Returns:
    Ordered sequence of node IDs, or throws on circular dependency

  Example:
    A depends on B, B depends on C
    Result: [C B A]"
  [graph node-ids]
  (let [sub-graph {:nodes (select-keys (:nodes graph) node-ids)
                   :edges (into {}
                               (filter #(node-ids (key %))
                                       (:edges graph)))}]

    (loop [sorted []
           remaining (set node-ids)
           visited #{}]

      (if (empty? remaining)
        sorted

        ;; Find nodes with no unvisited dependencies
        (let [ready (filter
                     (fn [node-id]
                       (let [deps (deps/get-dependencies sub-graph node-id)]
                         (every? visited deps)))
                     remaining)]

          (if (empty? ready)
            ;; Circular dependency detected
            (throw (ex-info "Circular dependency detected"
                            {:remaining remaining
                             :visited visited
                             :sorted sorted}))

            ;; Add ready nodes to sorted list
            (let [next-node (first ready)]
              (recur (conj sorted next-node)
                     (disj remaining next-node)
                     (conj visited next-node)))))))))

;; ============================================================================
;; INCREMENTAL COMPILATION
;; ============================================================================

(defn plan-incremental-build
  "Plan an incremental build based on changes.

  Args:
    cache - Cache instance
    graph-atom - Dependency graph atom
    file-paths - All project file paths
    options - Configuration:
              :strategy - Change detection strategy (:content-hash or :timestamp)
              :force-rebuild - Set of node IDs to force rebuild

  Returns:
    Build plan:
      {:changes {...}              ; Change detection results
       :affected {...}             ; Affected nodes
       :build-order [...]          ; Topologically sorted build order
       :can-skip #{...}            ; Nodes that can use cache
       :must-rebuild #{...}}       ; Nodes that must be rebuilt"
  [cache graph-atom file-paths & [options]]
  (let [opts (merge {:strategy :content-hash
                     :force-rebuild #{}}
                    options)
        graph @graph-atom
        changes (detect-changes graph file-paths (:strategy opts))
        all-changed (set/union (:changed changes) (:new changes))
        affected (compute-affected-nodes graph all-changed)
        must-rebuild (set/union (:all affected) (:force-rebuild opts))

        ;; Nodes that can potentially use cache
        all-nodes (set (keys (:nodes graph)))
        can-skip (set/difference all-nodes must-rebuild)

        ;; Build order for nodes that must be rebuilt
        build-order (topological-sort graph must-rebuild)]

    {:changes changes
     :affected affected
     :build-order build-order
     :can-skip can-skip
     :must-rebuild must-rebuild
     :stats {:total-nodes (count all-nodes)
             :nodes-to-rebuild (count must-rebuild)
             :nodes-to-skip (count can-skip)
             :files-changed (count all-changed)}}))

(defn execute-incremental-build
  "Execute an incremental build based on a build plan.

  Args:
    cache - Cache instance
    graph-atom - Dependency graph atom
    build-plan - Build plan from plan-incremental-build
    compile-fn - Compilation function (node-id, node-data) -> compiled-result
    options - Configuration:
              :parallel? - Compile independent nodes in parallel
              :on-progress - Progress callback (state) -> nil

  Returns:
    Build result:
      {:state {...}                ; Final compilation state
       :duration-ms 1234           ; Build duration
       :stats {...}}               ; Build statistics"
  [cache graph-atom build-plan compile-fn & [options]]
  (let [opts (merge {:parallel? false
                     :on-progress nil}
                    options)
        graph @graph-atom
        state-atom (atom (create-compilation-state))
        start-time (cache/now-millis)

        ;; Initialize pending set
        _ (swap! state-atom assoc :pending (set (:build-order build-plan)))]

    ;; Compile nodes in order
    (doseq [node-id (:build-order build-plan)]
      (mark-in-progress! state-atom node-id)

      ;; Check if we can use cached result
      (if-let [cached (cache/get-cached cache (str "compiled:" node-id))]
        (do
          (mark-skipped! state-atom node-id)
          (when (:on-progress opts)
            ((:on-progress opts) @state-atom)))

        ;; Must compile
        (try
          (let [node-data (get-in graph [:nodes node-id])
                result (compile-fn node-id node-data)]

            ;; Cache the result
            (cache/put-cached! cache (str "compiled:" node-id) result)

            ;; Update dependency graph
            (deps/track-file! graph-atom (:path node-data))

            (mark-compiled! state-atom node-id)
            (when (:on-progress opts)
              ((:on-progress opts) @state-atom)))

          (catch Exception e
            (mark-failed! state-atom node-id
                          {:error (.getMessage e)
                           :type (type e)
                           :stacktrace (vec (.getStackTrace e))})
            (when (:on-progress opts)
              ((:on-progress opts) @state-atom))))))

    (let [end-time (cache/now-millis)
          final-state @state-atom]

      {:state final-state
       :duration-ms (- end-time start-time)
       :stats {:compiled (count (:compiled final-state))
               :skipped (count (:skipped final-state))
               :failed (count (:failed final-state))
               :total (+ (count (:compiled final-state))
                        (count (:skipped final-state))
                        (count (:failed final-state)))
               :success? (empty? (:failed final-state))}})))

;; ============================================================================
;; HIGH-LEVEL API
;; ============================================================================

(defn incremental-compile!
  "High-level incremental compilation function.

  Automatically:
  - Detects changes
  - Plans build
  - Invalidates cache
  - Executes compilation

  Args:
    cache - Cache instance
    graph-atom - Dependency graph atom
    file-paths - All project file paths
    compile-fn - Compilation function (node-id, node-data) -> result
    options - Configuration:
              :strategy - Change detection strategy
              :force-rebuild - Force rebuild specific nodes
              :on-progress - Progress callback

  Returns:
    Build result with statistics"
  [cache graph-atom file-paths compile-fn & [options]]
  (let [opts (or options {})

        ;; Step 1: Plan build
        build-plan (plan-incremental-build cache graph-atom file-paths opts)

        ;; Step 2: Invalidate cache for changed nodes
        _ (when (seq (:must-rebuild build-plan))
            (invalidation/batch-invalidate!
             cache
             graph-atom
             {:files (vec (:changed (:changes build-plan)))}
             opts))

        ;; Step 3: Execute build
        result (execute-incremental-build
                cache graph-atom build-plan compile-fn opts)]

    (merge result {:plan build-plan})))

;; ============================================================================
;; UTILITIES
;; ============================================================================

(defn format-build-stats
  "Format build statistics for display.

  Args:
    result - Build result from incremental-compile!

  Returns:
    Formatted string"
  [result]
  (let [{:keys [state duration-ms stats plan]} result
        {:keys [compiled skipped failed total success?]} stats
        {:keys [changes]} plan
        {:keys [changed new deleted]} changes]

    (str "=== Incremental Build Results ===\n"
         "Status: " (if success? "SUCCESS" "FAILED") "\n"
         "Duration: " duration-ms "ms\n"
         "\n"
         "Changes:\n"
         "  Changed: " (count changed) " files\n"
         "  New: " (count new) " files\n"
         "  Deleted: " (count deleted) " files\n"
         "\n"
         "Compilation:\n"
         "  Compiled: " compiled " nodes\n"
         "  Cached: " skipped " nodes\n"
         "  Failed: " failed " nodes\n"
         "  Total: " total " nodes\n"
         "\n"
         (when-not success?
           (str "Errors:\n"
                (str/join "\n"
                          (map (fn [[node-id error]]
                                 (str "  " node-id ": " (:error error)))
                               (:errors state)))
                "\n")))))

(defn estimate-build-time
  "Estimate build time based on previous builds.

  Args:
    build-plan - Build plan
    previous-builds - Sequence of previous build results
    avg-compile-ms - Average compilation time per node (default: 10ms)

  Returns:
    Estimated duration in milliseconds"
  [build-plan previous-builds & {:keys [avg-compile-ms] :or {avg-compile-ms 10}}]
  (let [nodes-to-compile (count (:must-rebuild build-plan))

        ;; Use actual average from previous builds if available
        actual-avg (when (seq previous-builds)
                    (let [total-duration (reduce + (map :duration-ms previous-builds))
                          total-nodes (reduce + (map #(get-in % [:stats :compiled]) previous-builds))]
                      (when (pos? total-nodes)
                        (/ total-duration total-nodes))))]

    (* nodes-to-compile (or actual-avg avg-compile-ms))))
