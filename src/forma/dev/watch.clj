(ns forma.dev.watch
  "File watching for Hot Module Replacement (HMR)
  
   Provides file system watching infrastructure to detect changes
   in EDN files and trigger recompilation."
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]))

(defn setup-watch-mode
  "Watch EDN files and trigger recompilation
  
   Args:
     project-name - Name of the project to watch
     callback - Function to call when files change (takes changed-file path)
   
   Returns:
     Watch service (can be used to stop watching)
   
   Note: This is a basic implementation. For production use,
   consider using a library like hawk or chime for more robust
   file watching."
  [project-name callback]
  (let [watch-paths [(str "projects/" project-name "/pages")
                     (str "projects/" project-name "/templates")
                     (str "projects/" project-name "/sections")
                     (str "projects/" project-name "/components")
                     (str "projects/" project-name "/config.edn")
                     "default/components"
                     "default/styles"
                     "default/platforms"
                     "default/global"]
        watch-service (java.nio.file.FileSystems/getDefault (.newWatchService))]
    (doseq [path watch-paths]
      (let [dir (io/file path)]
        (when (.exists dir)
          (try
            (.register (.toPath dir)
                      watch-service
                      (into-array [java.nio.file.StandardWatchEventKinds/ENTRY_CREATE
                                  java.nio.file.StandardWatchEventKinds/ENTRY_MODIFY
                                  java.nio.file.StandardWatchEventKinds/ENTRY_DELETE]))
            (catch Exception e
              (log/warn (str "Could not watch " path ": " (.getMessage e))))))))
    ;; Start watching in background thread
    (future
      (loop []
        (try
          (let [watch-key (.take watch-service)]
            (doseq [event (.pollEvents watch-key)]
              (let [_kind (.kind event)
                    file-name (str (.context event))
                    changed-file (str (.watchable watch-key) "/" file-name)]
                (when (.endsWith file-name ".edn")
                  (callback changed-file))))
            (.reset watch-key)
            (recur))
          (catch InterruptedException _
            (log/info "File watching stopped"))
          (catch Exception e
            (log/error e "Error in file watcher")
            (recur)))))
    {:watch-service watch-service
     :stop (fn [] (.close watch-service))}))

(defn stop-watching
  "Stop file watching
  
   Args:
     watch-result - Result from setup-watch-mode"
  [watch-result]
  ((:stop watch-result)))

