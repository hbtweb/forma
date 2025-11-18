(ns forma.project-config
  "Project-specific configuration loading and management
  
   Supports per-project config.edn files that override or extend default configuration.
   Project configs are loaded from projects/{project-name}/config.edn"
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [forma.compiler :as compiler]))

(defn load-project-config
  "Load project-specific config.edn from projects/{project-name}/config.edn
   
   Args:
     project-name - Name of the project
   
   Returns:
     Project config map, or empty map if not found"
  [project-name]
  (if (nil? project-name)
    {}
    (try
      (let [config-path (str "projects/" project-name "/config.edn")
            config-file (io/file config-path)]
        (if (.exists config-file)
          (edn/read-string (slurp config-file))
          {}))
      (catch Exception e
        (log/warn (str "Could not load project config for " project-name ": " (.getMessage e)))
        {}))))

(defn merge-project-config
  "Merge project config with base config
   
   Project config can override:
   - :platform-stack
   - :styling-system
   - :resolution-order (project-specific resolution)
   - :paths (project-specific paths)
   - :features (project-specific feature flags)
   
   Args:
     base-config - Base configuration from forma-config
     project-name - Name of the project
   
   Returns:
     Merged configuration with project overrides"
  [base-config project-name]
  (if (nil? project-name)
    base-config
    (let [project-config (load-project-config project-name)]
      (if (empty? project-config)
        base-config
        (merge-with
         (fn [base-val project-val]
           (if (map? base-val)
             (merge base-val project-val)
             project-val))
         base-config
         project-config)))))

(defn get-project-platform-stack
  "Get platform stack for a project (with project-specific override if present)
   
   Args:
     project-name - Name of the project
   
   Returns:
     Platform stack vector (e.g., [:html :css :htmx])"
  [project-name]
  (let [base-config (compiler/load-config)
        merged-config (merge-project-config base-config project-name)]
    (get-in merged-config [:defaults :platform-stack] 
            (get-in base-config [:defaults :platform-stack] [:html :css :htmx]))))

(defn get-project-styling-system
  "Get styling system for a project (with project-specific override if present)
   
   Args:
     project-name - Name of the project
   
   Returns:
     Styling system keyword (e.g., :shadcn-ui)"
  [project-name]
  (let [base-config (compiler/load-config)
        merged-config (merge-project-config base-config project-name)]
    (get-in merged-config [:defaults :styling-system]
            (get-in base-config [:defaults :styling-system] :shadcn-ui))))

(defn get-project-resolution-order
  "Get resolution order for a project (with project-specific override if present)
   
   Args:
     project-name - Name of the project
   
   Returns:
     Resolution order vector (e.g., [:project :library :default])"
  [project-name]
  (let [base-config (compiler/load-config)
        merged-config (merge-project-config base-config project-name)]
    (get-in merged-config [:resolution-order]
            (get base-config :resolution-order [:project :library :default]))))

(defn build-project-context
  "Build context with project-specific configuration merged in
   
   This is a convenience function that merges project config into build-context
   
   Args:
     data - Context data
     project-name - Name of the project
     options - Additional options
   
   Returns:
     Full context with project config merged"
  [data project-name & [options]]
  (let [base-config (compiler/load-config)
        project-config (load-project-config project-name)
        merged-config (merge-project-config base-config project-name)
        project-platform-stack (get-project-platform-stack project-name)
        project-styling-system (get-project-styling-system project-name)]
    (compiler/build-context
     (merge data options)
     {:project-name project-name
      :platform-stack project-platform-stack
      :styling-system project-styling-system})))

