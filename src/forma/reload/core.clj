(ns forma.reload.core
  "Phase 5.5: Hot Reload - Core reload orchestration

  Provides:
  - File watching with change detection
  - WebSocket server for live updates
  - Reload coordination and messaging
  - Integration with build pipeline and cache system"
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [forma.build.core :as build]
            [forma.cache.compiler :as cache])
  (:import [java.nio.file FileSystems
                          Path
                          Paths
                          WatchService
                          WatchKey
                          WatchEvent
                          StandardWatchEventKinds]
           [java.nio.file.attribute FileTime]
           [java.util.concurrent TimeUnit]))

;; =============================================================================
;; Hot Reload Configuration
;; =============================================================================

(def ^:private default-reload-config
  "Default hot reload configuration"
  {:enabled true
   :port 3449
   :watch-dirs ["default/" "library/" "projects/"]
   :ignore-patterns [#"^\." #"\.tmp$" #"node_modules" #"build/" #"\.git"]
   :debounce-ms 100
   :strategies {:css :inject
                :html :reload
                :edn :rebuild}
   :websocket {:enabled true
               :heartbeat-ms 30000
               :reconnect-delay-ms 1000}
   :build {:mode :development
           :incremental? true}
   :on-change nil
   :on-reload nil
   :on-error nil})

;; =============================================================================
;; File Change Detection
;; =============================================================================

(defrecord FileChange
  [path          ; File path
   type          ; Change type (:create, :modify, :delete)
   timestamp     ; Change timestamp
   extension     ; File extension
   strategy])    ; Reload strategy (:inject, :reload, :rebuild)

(defn- file-extension
  "Get file extension without dot"
  [path]
  (let [name (.toString path)
        idx (.lastIndexOf name ".")]
    (when (pos? idx)
      (keyword (subs name (inc idx))))))

(defn- should-ignore?
  "Check if file path should be ignored"
  [path ignore-patterns]
  (let [path-str (.toString path)]
    (some #(re-find % path-str) ignore-patterns)))

(defn- determine-reload-strategy
  "Determine reload strategy based on file extension"
  [extension strategies]
  (get strategies extension :reload))

(defn- create-file-change
  "Create FileChange record from watch event"
  [event path config]
  (let [ext (file-extension path)
        strategy (determine-reload-strategy ext (:strategies config))]
    (map->FileChange
      {:path (.toString path)
       :type (condp = (.kind event)
               StandardWatchEventKinds/ENTRY_CREATE :create
               StandardWatchEventKinds/ENTRY_MODIFY :modify
               StandardWatchEventKinds/ENTRY_DELETE :delete
               :unknown)
       :timestamp (System/currentTimeMillis)
       :extension ext
       :strategy strategy})))

;; =============================================================================
;; File Watcher
;; =============================================================================

(defrecord FileWatcher
  [watch-service  ; Java WatchService
   watch-keys     ; Map of WatchKey -> Path
   config         ; Watcher configuration
   running?       ; Atom - is watcher running?
   change-buffer  ; Atom - buffered changes for debouncing
   last-flush])   ; Atom - last buffer flush timestamp

(defn create-file-watcher
  "Create file watcher for hot reload

  Parameters:
  - config: Watcher configuration map

  Returns FileWatcher instance"
  [config]
  (let [watch-service (.newWatchService (FileSystems/getDefault))]
    (map->FileWatcher
      {:watch-service watch-service
       :watch-keys (atom {})
       :config config
       :running? (atom false)
       :change-buffer (atom [])
       :last-flush (atom (System/currentTimeMillis))})))

(defn- register-directory
  "Register directory with watch service"
  [watcher dir-path]
  (try
    (let [path (Paths/get dir-path (into-array String []))
          watch-key (.register path
                               (:watch-service watcher)
                               (into-array
                                 [StandardWatchEventKinds/ENTRY_CREATE
                                  StandardWatchEventKinds/ENTRY_MODIFY
                                  StandardWatchEventKinds/ENTRY_DELETE]))]
      (swap! (:watch-keys watcher) assoc watch-key path)
      {:success true :path dir-path})
    (catch Exception e
      {:success false :path dir-path :error (.getMessage e)})))

(defn start-file-watcher
  "Start watching directories for changes

  Parameters:
  - watcher: FileWatcher instance

  Returns watcher with running flag set"
  [watcher]
  (let [config (:config watcher)
        watch-dirs (:watch-dirs config)]

    ;; Register all watch directories
    (doseq [dir watch-dirs]
      (when (.exists (io/file dir))
        (register-directory watcher dir)))

    ;; Start watching in background thread
    (future
      (reset! (:running? watcher) true)
      (try
        (while @(:running? watcher)
          (when-let [watch-key (.poll (:watch-service watcher)
                                      (:debounce-ms config)
                                      TimeUnit/MILLISECONDS)]
            (let [dir-path (get @(:watch-keys watcher) watch-key)]
              (doseq [event (.pollEvents watch-key)]
                (let [relative-path (.context event)
                      absolute-path (.resolve dir-path relative-path)]

                  ;; Check ignore patterns
                  (when-not (should-ignore? absolute-path (:ignore-patterns config))
                    ;; Buffer change
                    (let [change (create-file-change event absolute-path config)]
                      (swap! (:change-buffer watcher) conj change)))))

              ;; Reset watch key
              (.reset watch-key))))
        (catch Exception e
          (when-let [on-error (:on-error config)]
            (on-error {:type :watcher-error :error (.getMessage e)})))))

    ;; Start debounce flush thread
    (future
      (while @(:running? watcher)
        (Thread/sleep (:debounce-ms config))
        (let [now (System/currentTimeMillis)
              last-flush @(:last-flush watcher)
              debounce-ms (:debounce-ms config)]

          ;; Flush if debounce period has passed
          (when (and (seq @(:change-buffer watcher))
                     (>= (- now last-flush) debounce-ms))
            (let [changes @(:change-buffer watcher)]
              (reset! (:change-buffer watcher) [])
              (reset! (:last-flush watcher) now)

              ;; Call on-change callback
              (when-let [on-change (:on-change config)]
                (on-change changes)))))))

    watcher))

(defn stop-file-watcher
  "Stop file watcher

  Parameters:
  - watcher: FileWatcher instance

  Returns stopped watcher"
  [watcher]
  (reset! (:running? watcher) false)
  (.close (:watch-service watcher))
  watcher)

;; =============================================================================
;; Hot Reload State
;; =============================================================================

(defrecord ReloadState
  [config         ; Reload configuration
   watcher        ; FileWatcher instance
   clients        ; Atom - connected WebSocket clients
   reload-count   ; Atom - total reload count
   last-reload    ; Atom - last reload timestamp
   statistics])   ; Atom - reload statistics

(defn create-reload-state
  "Create hot reload state

  Parameters:
  - config: Reload configuration map

  Returns ReloadState instance"
  [config]
  (map->ReloadState
    {:config config
     :watcher nil
     :clients (atom #{})
     :reload-count (atom 0)
     :last-reload (atom nil)
     :statistics (atom {:css-injected 0
                        :html-reloaded 0
                        :edn-rebuilt 0
                        :errors 0})}))

;; =============================================================================
;; Reload Strategies
;; =============================================================================

(defmulti apply-reload-strategy
  "Apply reload strategy based on change type"
  (fn [strategy change state] strategy))

(defmethod apply-reload-strategy :inject
  [_ change state]
  ;; CSS injection - hot swap without full reload
  (swap! (:statistics state) update :css-injected inc)
  {:type :inject
   :path (:path change)
   :content (slurp (:path change))})

(defmethod apply-reload-strategy :reload
  [_ change state]
  ;; Full page reload
  (swap! (:statistics state) update :html-reloaded inc)
  {:type :reload
   :path (:path change)})

(defmethod apply-reload-strategy :rebuild
  [_ change state]
  ;; Rebuild from source (EDN changes)
  (swap! (:statistics state) update :edn-rebuilt inc)

  ;; Invalidate cache for changed file
  (cache/invalidate-cache! (:path change))

  ;; Trigger incremental rebuild
  (let [build-config (get-in state [:config :build])
        result (build/build (:mode build-config)
                           :project-name (get-in state [:config :project-name])
                           :tasks [:compile])]
    {:type :rebuild
     :path (:path change)
     :build-result result}))

(defmethod apply-reload-strategy :default
  [_ change state]
  ;; Default: full reload
  {:type :reload
   :path (:path change)})

;; =============================================================================
;; Change Processing
;; =============================================================================

(defn process-file-changes
  "Process file changes and apply reload strategies

  Parameters:
  - changes: Vector of FileChange records
  - state: ReloadState instance

  Returns vector of reload actions"
  [changes state]
  (let [grouped (group-by :strategy changes)
        actions (atom [])]

    ;; Process each strategy group
    (doseq [[strategy change-group] grouped]
      (doseq [change change-group]
        (try
          (let [action (apply-reload-strategy strategy change state)]
            (swap! actions conj action))
          (catch Exception e
            (swap! (:statistics state) update :errors inc)
            (when-let [on-error (get-in state [:config :on-error])]
              (on-error {:type :strategy-error
                        :change change
                        :error (.getMessage e)}))))))

    @actions))

;; =============================================================================
;; Public API
;; =============================================================================

(defn start-hot-reload
  "Start hot reload system

  Parameters:
  - config: Reload configuration map

  Returns reload state"
  [config]
  (let [merged-config (merge default-reload-config config)
        state (create-reload-state merged-config)
        watcher (create-file-watcher
                  (assoc merged-config
                    :on-change (fn [changes]
                                 ;; Process changes and broadcast
                                 (let [actions (process-file-changes changes state)]
                                   (swap! (:reload-count state) inc)
                                   (reset! (:last-reload state) (System/currentTimeMillis))

                                   ;; Call on-reload callback
                                   (when-let [on-reload (:on-reload merged-config)]
                                     (on-reload actions))

                                   ;; Broadcast to clients (WebSocket)
                                   ;; Implementation in reload.websocket ns
                                   actions))))]

    ;; Start file watcher
    (assoc state :watcher (start-file-watcher watcher))))

(defn stop-hot-reload
  "Stop hot reload system

  Parameters:
  - state: ReloadState instance

  Returns stopped state"
  [state]
  (when-let [watcher (:watcher state)]
    (stop-file-watcher watcher))
  (assoc state :watcher nil))

(defn reload-statistics
  "Get hot reload statistics

  Parameters:
  - state: ReloadState instance

  Returns statistics map"
  [state]
  (merge
    @(:statistics state)
    {:reload-count @(:reload-count state)
     :last-reload @(:last-reload state)
     :connected-clients (count @(:clients state))}))

(comment
  ;; Start hot reload
  (def reload-state
    (start-hot-reload
      {:watch-dirs ["forma/default/" "forma/projects/"]
       :on-reload (fn [actions]
                    (println "Reload actions:" (count actions))
                    (doseq [action actions]
                      (println "  -" (:type action) (:path action))))}))

  ;; Check statistics
  (reload-statistics reload-state)

  ;; Stop hot reload
  (stop-hot-reload reload-state)
  )
