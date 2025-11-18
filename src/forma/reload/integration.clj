(ns forma.reload.integration
  "Phase 5.5: Hot Reload Integration with Build Pipeline

  Provides:
  - Integration between hot reload and build system
  - Automatic rebuild on file changes
  - WebSocket message broadcasting
  - Development server coordination"
  (:require [forma.reload.core :as reload]
            [forma.reload.websocket :as ws]
            [forma.build.core :as build]
            [forma.cache.compiler :as cache]))

;; =============================================================================
;; Integrated Development Server
;; =============================================================================

(defrecord DevServer
  [config          ; Server configuration
   reload-state    ; Hot reload state
   ws-server       ; WebSocket server
   build-config    ; Build configuration
   running?])      ; Atom - is server running?

(defn create-dev-server
  "Create integrated development server

  Parameters:
  - config: Server configuration map

  Options:
  - :port - WebSocket server port (default 3449)
  - :watch-dirs - Directories to watch for changes
  - :project-name - Project name for builds
  - :build-mode - Build mode (:development, :production)
  - :auto-build? - Automatically rebuild on changes (default true)
  - :on-change - Custom change handler
  - :on-build - Custom build handler
  - :on-error - Custom error handler

  Returns DevServer instance"
  [config]
  (let [default-config {:port 3449
                        :watch-dirs ["default/" "library/" "projects/"]
                        :project-name nil
                        :build-mode :development
                        :auto-build? true
                        :incremental? true}
        merged-config (merge default-config config)]

    (map->DevServer
      {:config merged-config
       :reload-state nil
       :ws-server nil
       :build-config nil
       :running? (atom false)})))

(defn- handle-file-changes
  "Handle file changes with automatic rebuild

  Parameters:
  - server: DevServer instance
  - changes: Vector of file changes

  Returns vector of reload actions"
  [server changes]
  (let [config (:config server)
        ws-server (:ws-server server)
        auto-build? (:auto-build? config)
        incremental? (:incremental? config)]

    ;; Group changes by strategy
    (let [grouped (group-by :strategy changes)
          actions (atom [])]

      ;; Handle CSS changes (inject without rebuild)
      (when-let [css-changes (get grouped :inject)]
        (doseq [change css-changes]
          (let [action {:type :inject
                       :path (:path change)
                       :content (slurp (:path change))}]
            (swap! actions conj action)
            (ws/broadcast-message ws-server action))))

      ;; Handle HTML changes (reload without rebuild)
      (when-let [html-changes (get grouped :reload)]
        (doseq [change html-changes]
          (let [action {:type :reload
                       :path (:path change)}]
            (swap! actions conj action)
            (ws/broadcast-message ws-server action))))

      ;; Handle EDN changes (rebuild + reload)
      (when-let [edn-changes (get grouped :rebuild)]
        (when auto-build?
          (try
            ;; Invalidate cache for changed files
            (doseq [change edn-changes]
              (cache/invalidate-cache! (:path change)))

            ;; Trigger rebuild
            (let [build-result (if incremental?
                                ;; Incremental build
                                (cache/compile-project-incremental
                                  (:project-name config)
                                  {:environment (:build-mode config)
                                   :platform-stack [:html :css :htmx]}
                                  {:strategy :content-hash})
                                ;; Full build
                                (build/build (:build-mode config)
                                           :project-name (:project-name config)
                                           :tasks [:compile]))
                  success? (or (empty? (:errors build-result))
                              (zero? (:failed build-result 0)))]

              ;; Broadcast rebuild result
              (doseq [change edn-changes]
                (let [action {:type :rebuild
                             :path (:path change)
                             :success success?
                             :build-result build-result}]
                  (swap! actions conj action)
                  (ws/broadcast-message ws-server action)))

              ;; Call on-build callback
              (when-let [on-build (:on-build config)]
                (on-build build-result)))

            (catch Exception e
              (let [error-action {:type :error
                                 :message (.getMessage e)
                                 :paths (map :path edn-changes)}]
                (swap! actions conj error-action)
                (ws/broadcast-message ws-server error-action)
                (when-let [on-error (:on-error config)]
                  (on-error {:type :build-error :error e})))))))

      ;; Call custom on-change handler
      (when-let [on-change (:on-change config)]
        (on-change changes @actions))

      @actions)))

(defn start-dev-server
  "Start integrated development server

  Parameters:
  - server: DevServer instance

  Returns server with services started"
  [server]
  (let [config (:config server)]

    ;; Start WebSocket server
    (let [ws-server (-> (ws/create-websocket-server
                          {:port (:port config)
                           :on-error (:on-error config)})
                        (ws/start-websocket-server))]

      ;; Start hot reload
      (let [reload-state (reload/start-hot-reload
                           (assoc config
                             :on-reload (fn [actions]
                                         ;; Actions already broadcasted
                                         ;; in handle-file-changes
                                         actions)
                             :on-change (fn [changes]
                                         (handle-file-changes
                                           (assoc server :ws-server ws-server)
                                           changes))))]

        (reset! (:running? server) true)

        (-> server
            (assoc :ws-server ws-server)
            (assoc :reload-state reload-state))))))

(defn stop-dev-server
  "Stop integrated development server

  Parameters:
  - server: DevServer instance

  Returns stopped server"
  [server]
  (reset! (:running? server) false)

  ;; Stop hot reload
  (when-let [reload-state (:reload-state server)]
    (reload/stop-hot-reload reload-state))

  ;; Stop WebSocket server
  (when-let [ws-server (:ws-server server)]
    (ws/stop-websocket-server ws-server))

  (-> server
      (assoc :reload-state nil)
      (assoc :ws-server nil)))

(defn dev-server-status
  "Get development server status

  Parameters:
  - server: DevServer instance

  Returns status map"
  [server]
  (let [reload-stats (when-let [state (:reload-state server)]
                      (reload/reload-statistics state))
        ws-stats (when-let [ws (:ws-server server)]
                  (ws/server-statistics ws))]
    {:running? @(:running? server)
     :config (:config server)
     :reload-statistics reload-stats
     :websocket-statistics ws-stats}))

;; =============================================================================
;; Public API
;; =============================================================================

(defn start
  "Start development server with hot reload

  Parameters:
  - config: Server configuration map

  Options:
  - :port - WebSocket port (default 3449)
  - :watch-dirs - Directories to watch
  - :project-name - Project name
  - :build-mode - Build mode (:development)
  - :on-change - Change callback
  - :on-build - Build callback
  - :on-error - Error callback

  Returns running server instance"
  [config]
  (-> (create-dev-server config)
      (start-dev-server)))

(defn stop
  "Stop development server

  Parameters:
  - server: Running server instance

  Returns stopped server"
  [server]
  (stop-dev-server server))

(defn status
  "Get server status

  Parameters:
  - server: Server instance

  Returns status map"
  [server]
  (dev-server-status server))

(comment
  ;; Start development server
  (def dev-server
    (start {:project-name "dashboard-example"
            :port 3449
            :watch-dirs ["forma/default/"
                        "forma/library/"
                        "forma/projects/dashboard-example/"]
            :on-change (fn [changes actions]
                        (println "Changes detected:" (count changes))
                        (println "Actions taken:" (count actions)))
            :on-build (fn [result]
                       (println "Build completed:"
                               (if (:success? result)
                                 "SUCCESS"
                                 "FAILED")))
            :on-error (fn [error]
                       (println "Error:" (:type error) (:error error)))}))

  ;; Check status
  (status dev-server)

  ;; Stop server
  (stop dev-server)
  )
