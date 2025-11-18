(ns forma.build.core
  "Phase 5.4-5.6: Build Pipeline - Core build orchestration and API

  Provides:
  - Build modes (development vs production)
  - Build task execution (EDN-driven)
  - Build API (programmatic interface)
  - Build configuration management
  - Policy enforcement (Phase 5.6)"
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [forma.compiler :as compiler]
            [forma.cache.compiler :as cache]
            [forma.minification.core :as minify]
            [forma.optimization.core :as optimize]
            [forma.build.assets :as assets]
            [forma.policy.core :as policy]
            [forma.policy.reporting :as policy-reporting]))

;; =============================================================================
;; Build Configuration
;; =============================================================================

(def ^:private default-build-config
  "Default build configuration"
  {:mode :production
   :project-name nil
   :source-dirs ["default/" "library/" "projects/"]
   :output-dir "build/"
   :clean-output? true
   :tasks [:compile :optimize :minify :bundle]
   :cache {:enabled true
           :strategy :content-hash}
   :optimization {:enabled true
                  :dead-code-elimination? true
                  :css-deduplication? true
                  :inline-tokens? true
                  :inline-threshold 5}
   :minification {:enabled true
                  :html {:enabled true
                         :remove-whitespace true
                         :remove-comments true
                         :boolean-attributes true
                         :quote-removal true}
                  :css {:enabled true
                        :remove-whitespace true
                        :remove-comments true
                        :hex-shortening true
                        :value-compression true}}
   :assets {:copy-static? true
            :static-dirs ["assets/" "public/"]
            :optimize-images? false}
   :bundle {:enabled true
            :split-css? true
            :inline-critical-css? false}
   :watch {:enabled false
           :debounce-ms 100}
   :on-progress nil
   :on-complete nil
   :on-error nil})

(def ^:private build-modes
  "Predefined build mode configurations"
  {:development {:mode :development
                 :optimization {:enabled false}
                 :minification {:enabled false}
                 :cache {:enabled true
                         :strategy :timestamp}
                 :bundle {:split-css? false}
                 :watch {:enabled true}}

   :production {:mode :production
                :optimization {:enabled true
                               :dead-code-elimination? true
                               :css-deduplication? true
                               :inline-tokens? true}
                :minification {:enabled true
                               :html {:enabled true
                                      :remove-whitespace true
                                      :remove-comments true}
                               :css {:enabled true
                                     :remove-whitespace true
                                     :remove-comments true}}
                :bundle {:split-css? true
                         :inline-critical-css? true}
                :watch {:enabled false}}

   :test {:mode :test
          :optimization {:enabled false}
          :minification {:enabled false}
          :cache {:enabled false}
          :clean-output? false
          :watch {:enabled false}}})

(defn load-build-config
  "Load build configuration from file or use defaults

  Parameters:
  - config-path: Path to build.edn file (optional)

  Returns build configuration map"
  ([]
   (load-build-config nil))
  ([config-path]
   (if (and config-path (.exists (io/file config-path)))
     (merge default-build-config (edn/read-string (slurp config-path)))
     default-build-config)))

(defn with-build-mode
  "Apply build mode preset to configuration

  Parameters:
  - config: Build configuration map
  - mode: Build mode keyword (:development, :production, :test)

  Returns updated configuration"
  [config mode]
  (if-let [mode-config (get build-modes mode)]
    (merge config mode-config)
    config))

;; =============================================================================
;; Build State Management
;; =============================================================================

(defrecord BuildState
  [config          ; Build configuration
   start-time      ; Build start timestamp
   end-time        ; Build end timestamp
   tasks-completed ; Vector of completed task names
   tasks-failed    ; Vector of failed task names
   files-processed ; Set of processed file paths
   errors          ; Vector of error maps
   stats           ; Build statistics map
   artifacts])     ; Map of output artifacts

(defn create-build-state
  "Create initial build state from configuration"
  [config]
  (map->BuildState
    {:config config
     :start-time (System/currentTimeMillis)
     :end-time nil
     :tasks-completed []
     :tasks-failed []
     :files-processed #{}
     :errors []
     :stats {:files-compiled 0
             :files-optimized 0
             :files-minified 0
             :files-bundled 0
             :bytes-in 0
             :bytes-out 0
             :cache-hits 0
             :cache-misses 0}
     :artifacts {}}))

(defn complete-build
  "Mark build as complete and finalize state"
  [state]
  (assoc state :end-time (System/currentTimeMillis)))

(defn build-duration
  "Calculate build duration in milliseconds"
  [state]
  (when (and (:start-time state) (:end-time state))
    (- (:end-time state) (:start-time state))))

(defn build-success?
  "Check if build completed successfully"
  [state]
  (and (:end-time state)
       (empty? (:tasks-failed state))
       (empty? (:errors state))))

;; =============================================================================
;; Build Task Protocol
;; =============================================================================

(defprotocol BuildTask
  "Protocol for build tasks"
  (task-name [this]
    "Return task name as keyword")
  (task-enabled? [this config]
    "Check if task is enabled in configuration")
  (execute-task [this state]
    "Execute task and return updated build state"))

;; =============================================================================
;; Build Task Implementations
;; =============================================================================

(defrecord CompileTask []
  BuildTask
  (task-name [_] :compile)
  (task-enabled? [_ config]
    (contains? (set (:tasks config)) :compile))
  (execute-task [_ state]
    (let [config (:config state)
          project-name (:project-name config)
          context {:project-name project-name
                   :environment (:mode config)
                   :platform-stack (get-in config [:compiler :platform-stack] [:html :css :htmx])}]

      (try
        ;; Use cached compilation if enabled
        (let [result (if (get-in config [:cache :enabled])
                       (cache/compile-project-incremental project-name context
                         {:strategy (get-in config [:cache :strategy] :content-hash)})
                       ;; Fallback to regular compilation
                       {:compiled [] :artifacts {}})]

          (-> state
              (update :tasks-completed conj :compile)
              (update-in [:stats :files-compiled] + (count (:compiled result)))
              (update :files-processed into (:compiled result))
              (assoc-in [:artifacts :compiled] (:artifacts result))))

        (catch Exception e
          (-> state
              (update :tasks-failed conj :compile)
              (update :errors conj {:task :compile
                                    :error (.getMessage e)
                                    :exception e})))))))

(defrecord OptimizeTask []
  BuildTask
  (task-name [_] :optimize)
  (task-enabled? [_ config]
    (and (contains? (set (:tasks config)) :optimize)
         (get-in config [:optimization :enabled])))
  (execute-task [_ state]
    (let [config (:config state)
          opt-config (:optimization config)
          compiled-artifacts (get-in state [:artifacts :compiled])]

      (try
        ;; Optimize compiled artifacts
        (let [optimized (atom [])
              results (reduce-kv
                        (fn [acc file-path artifact]
                          (let [optimized-artifact (optimize/optimize-compilation
                                                     artifact
                                                     {}  ; token-registry
                                                     opt-config)]
                            (swap! optimized conj file-path)
                            (assoc acc file-path optimized-artifact)))
                        {}
                        compiled-artifacts)]

          (-> state
              (update :tasks-completed conj :optimize)
              (update-in [:stats :files-optimized] + (count @optimized))
              (assoc-in [:artifacts :optimized] results)))

        (catch Exception e
          (-> state
              (update :tasks-failed conj :optimize)
              (update :errors conj {:task :optimize
                                    :error (.getMessage e)
                                    :exception e})))))))

(defrecord MinifyTask []
  BuildTask
  (task-name [_] :minify)
  (task-enabled? [_ config]
    (and (contains? (set (:tasks config)) :minify)
         (get-in config [:minification :enabled])))
  (execute-task [_ state]
    (let [config (:config state)
          minify-config (:minification config)
          artifacts (or (get-in state [:artifacts :optimized])
                       (get-in state [:artifacts :compiled]))]

      (try
        ;; Minify artifacts
        (let [minified (atom [])
              results (reduce-kv
                        (fn [acc file-path artifact]
                          ;; Apply minification based on artifact type
                          (let [minified-artifact (cond
                                                    (string? artifact)
                                                    (minify/minify-html artifact
                                                      (get minify-config :html {}))

                                                    (map? artifact)
                                                    artifact  ; Already processed

                                                    :else
                                                    artifact)]
                            (swap! minified conj file-path)
                            (assoc acc file-path minified-artifact)))
                        {}
                        artifacts)]

          (-> state
              (update :tasks-completed conj :minify)
              (update-in [:stats :files-minified] + (count @minified))
              (assoc-in [:artifacts :minified] results)))

        (catch Exception e
          (-> state
              (update :tasks-failed conj :minify)
              (update :errors conj {:task :minify
                                    :error (.getMessage e)
                                    :exception e})))))))

(defrecord AssetsTask []
  BuildTask
  (task-name [_] :assets)
  (task-enabled? [_ config]
    (and (contains? (set (:tasks config)) :assets)
         (get-in config [:assets :copy-static?])))
  (execute-task [_ state]
    (let [config (:config state)
          asset-config (:assets config)]

      (try
        ;; Process static assets
        (let [result (assets/copy-static-assets
                       (merge asset-config
                              {:output-dir (str (:output-dir config) "assets/")}))]

          (-> state
              (update :tasks-completed conj :assets)
              (update-in [:stats :assets-copied] + (:total result))
              (update-in [:stats :bytes-in] + (:bytes result))
              (assoc-in [:artifacts :assets] result)))

        (catch Exception e
          (-> state
              (update :tasks-failed conj :assets)
              (update :errors conj {:task :assets
                                    :error (.getMessage e)
                                    :exception e})))))))

(defrecord PolicyCheckTask []
  BuildTask
  (task-name [_] :policy-check)
  (task-enabled? [_ config]
    (and (contains? (set (:tasks config)) :policy-check)
         (get-in config [:policies :enabled] true)))
  (execute-task [_ state]
    (let [config (:config state)
          policy-config (:policies config)
          artifacts (or (get-in state [:artifacts :minified])
                       (get-in state [:artifacts :optimized])
                       (get-in state [:artifacts :compiled]))
          elements (get state :elements [])
          environment (get config :mode :production)]

      (try
        ;; Check policies on all elements
        (let [policy-context {:environment environment
                             :policies policy-config
                             :project-name (:project-name config)
                             :compiled-output artifacts
                             :all-elements elements}

              all-violations (reduce (fn [acc element]
                                      (concat acc (policy/check-policies element policy-context)))
                                    []
                                    elements)

              violation-counts (policy/violation-count all-violations)
              on-violation (get policy-config :on-violation :warn)]

          ;; Report violations
          (when (seq all-violations)
            (println "\n")  ; Blank line before report
            (policy-reporting/report-violations all-violations
                                               {:colorize? true
                                                :show-summary? true}))

          ;; Handle based on severity
          (case on-violation
            :error (when (policy/has-errors? all-violations)
                    (throw (ex-info "Policy violations detected - build failed"
                                   {:violations all-violations
                                    :counts violation-counts})))
            :warn (when (seq all-violations)
                   (println (str "\n⚠️  Build completed with "
                                (:errors violation-counts) " errors, "
                                (:warnings violation-counts) " warnings")))
            :ignore nil)

          (-> state
              (update :tasks-completed conj :policy-check)
              (assoc-in [:artifacts :policy-violations] all-violations)
              (assoc-in [:stats :policy-violations] violation-counts)))

        (catch Exception e
          (if (= (get policy-config :on-violation :warn) :error)
            ;; Re-throw if policy errors should fail build
            (throw e)
            ;; Otherwise record and continue
            (-> state
                (update :tasks-failed conj :policy-check)
                (update :errors conj {:task :policy-check
                                      :error (.getMessage e)
                                      :exception e}))))))))

(defrecord BundleTask []
  BuildTask
  (task-name [_] :bundle)
  (task-enabled? [_ config]
    (and (contains? (set (:tasks config)) :bundle)
         (get-in config [:bundle :enabled])))
  (execute-task [_ state]
    (let [config (:config state)
          bundle-config (:bundle config)
          artifacts (or (get-in state [:artifacts :minified])
                       (get-in state [:artifacts :optimized])
                       (get-in state [:artifacts :compiled]))]

      (try
        ;; Bundle artifacts (write to output directory)
        (let [output-dir (:output-dir config)
              bundled (atom [])]

          ;; Create output directory
          (io/make-parents (str output-dir "/.keep"))

          ;; Write artifacts to disk
          (doseq [[file-path artifact] artifacts]
            (let [output-path (str output-dir "/" file-path)]
              (io/make-parents output-path)
              (spit output-path (str artifact))
              (swap! bundled conj file-path)))

          (-> state
              (update :tasks-completed conj :bundle)
              (update-in [:stats :files-bundled] + (count @bundled))
              (assoc-in [:artifacts :bundled] {:output-dir output-dir
                                               :files @bundled})))

        (catch Exception e
          (-> state
              (update :tasks-failed conj :bundle)
              (update :errors conj {:task :bundle
                                    :error (.getMessage e)
                                    :exception e})))))))

;; =============================================================================
;; Build Pipeline Execution
;; =============================================================================

(def ^:private task-registry
  "Registry of available build tasks"
  {:compile (->CompileTask)
   :optimize (->OptimizeTask)
   :minify (->MinifyTask)
   :assets (->AssetsTask)
   :policy-check (->PolicyCheckTask)
   :bundle (->BundleTask)})

(defn execute-build-pipeline
  "Execute build pipeline with given configuration

  Parameters:
  - config: Build configuration map

  Returns completed build state"
  [config]
  (let [initial-state (create-build-state config)
        tasks (map #(get task-registry %) (:tasks config))]

    ;; Call on-progress callback
    (when-let [on-progress (:on-progress config)]
      (on-progress initial-state))

    ;; Execute tasks sequentially
    (let [final-state (reduce
                        (fn [state task]
                          (if (task-enabled? task config)
                            (let [updated-state (execute-task task state)]
                              ;; Call on-progress callback
                              (when-let [on-progress (:on-progress config)]
                                (on-progress updated-state))
                              updated-state)
                            state))
                        initial-state
                        tasks)
          completed-state (complete-build final-state)]

      ;; Call completion callbacks
      (if (build-success? completed-state)
        (when-let [on-complete (:on-complete config)]
          (on-complete completed-state))
        (when-let [on-error (:on-error config)]
          (on-error completed-state)))

      completed-state)))

;; =============================================================================
;; Public API
;; =============================================================================

(defn build
  "Execute build with configuration

  Parameters:
  - config-or-mode: Build configuration map or mode keyword

  Options:
  - :project-name - Project name
  - :mode - Build mode (:development, :production, :test)
  - :config-path - Path to build.edn file
  - :output-dir - Output directory path
  - :clean-output? - Clean output directory before build
  - :tasks - Vector of task keywords to execute
  - :on-progress - Progress callback (fn [state])
  - :on-complete - Completion callback (fn [state])
  - :on-error - Error callback (fn [state])

  Returns build state"
  [config-or-mode & {:as options}]
  (let [base-config (if (keyword? config-or-mode)
                      (with-build-mode default-build-config config-or-mode)
                      config-or-mode)
        config (merge base-config options)]

    ;; Clean output directory if requested
    (when (and (:clean-output? config)
               (.exists (io/file (:output-dir config))))
      (doseq [file (file-seq (io/file (:output-dir config)))]
        (when (.isFile file)
          (.delete file))))

    ;; Execute build pipeline
    (execute-build-pipeline config)))

(defn build-report
  "Generate human-readable build report

  Parameters:
  - state: Build state

  Returns report string"
  [state]
  (let [duration (build-duration state)
        success? (build-success? state)
        stats (:stats state)]
    (str "Build " (if success? "SUCCEEDED" "FAILED") "\n"
         "Duration: " duration "ms\n"
         "Tasks completed: " (count (:tasks-completed state)) "\n"
         "Tasks failed: " (count (:tasks-failed state)) "\n"
         "Files compiled: " (:files-compiled stats) "\n"
         "Files optimized: " (:files-optimized stats) "\n"
         "Files minified: " (:files-minified stats) "\n"
         "Files bundled: " (:files-bundled stats) "\n"
         (when (seq (:errors state))
           (str "\nErrors:\n"
                (clojure.string/join "\n"
                  (map #(str "  - " (:task %) ": " (:error %))
                       (:errors state))))))))

(comment
  ;; Development build
  (def dev-build
    (build :development
           :project-name "dashboard-example"
           :output-dir "build/dev/"))

  (build-report dev-build)

  ;; Production build
  (def prod-build
    (build :production
           :project-name "dashboard-example"
           :output-dir "build/prod/"
           :on-progress (fn [state]
                          (println "Progress:"
                                   (count (:tasks-completed state))
                                   "tasks completed"))))

  (build-report prod-build)

  ;; Custom build configuration
  (def custom-build
    (build {:mode :production
            :project-name "my-project"
            :output-dir "dist/"
            :tasks [:compile :minify :bundle]
            :minification {:enabled true
                           :html {:enabled true}
                           :css {:enabled true}}}))

  (build-report custom-build))
