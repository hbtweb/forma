(ns forma.sync.registry
  "Platform discovery and registry for sync configurations.

  This namespace provides discovery and registration of available sync platforms,
  making it easy to enumerate supported platforms and access their metadata."
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as str]))

;; =============================================================================
;; Platform Discovery
;; =============================================================================

(defn discover-platforms
  "Discover all available sync platforms by scanning the sync/ directory.

  Returns a set of platform keywords."
  []
  (try
    (let [sync-dir-url (io/resource "sync")]
      (if sync-dir-url
        ;; Handle both file: and jar: URLs
        (let [path (.getPath sync-dir-url)
              file (io/file path)]
          (if (.exists file)
            ;; File system access (development)
            (->> (file-seq file)
                 (filter #(.isFile %))
                 (filter #(str/ends-with? (.getName %) ".edn"))
                 (map #(-> (.getName %)
                           (str/replace #"\.edn$" "")
                           keyword))
                 set)
            ;; JAR access or resource not a directory - try alternative approach
            ;; For now, just check for known platforms
            (let [known-platforms [:wordpress]]
              (->> known-platforms
                   (filter #(some? (io/resource (str "sync/" (name %) ".edn"))))
                   set))))
        #{}))
    (catch Exception e
      (println "Warning: Failed to discover platforms:" (.getMessage e))
      #{})))

(defn load-platform-metadata
  "Load metadata for a specific platform.

  Returns metadata map with:
  - :platform - Platform keyword
  - :display-name - Human-readable name
  - :description - Platform description
  - :connection - Connection info (URL patterns)
  - :auth - Authentication type
  - :compiler - Compiler namespace info
  - :capabilities - Supported operations"
  [platform]
  (try
    (when-let [resource (io/resource (str "sync/" (name platform) ".edn"))]
      (let [config (-> resource slurp edn/read-string)]
        {:platform (:platform config)
         :display-name (:display-name config)
         :description (:description config)
         :connection (select-keys (:connection config) [:base-url-env :path-prefix])
         :auth (select-keys (:auth config) [:type])
         :compiler (select-keys (:compiler config) [:compiler-ns :input-format :output-format])
         :capabilities (-> config :endpoints keys set)}))
    (catch Exception e
      (println "Warning: Failed to load platform metadata:" platform (.getMessage e))
      nil)))

(defn list-platforms
  "List all available platforms with their metadata.

  Returns:
  [{:platform :wordpress
    :display-name \"WordPress\"
    :description \"...\"
    :capabilities #{:create :read :update :delete}}
   ...]"
  []
  (let [platforms (discover-platforms)]
    (->> platforms
         (map load-platform-metadata)
         (filter some?)
         (sort-by :platform))))

;; =============================================================================
;; Platform Registry (In-Memory)
;; =============================================================================

(defonce ^:private platform-registry (atom {}))

(defn register-platform!
  "Register a custom platform configuration.

  Useful for adding platforms at runtime without creating EDN files."
  [platform config]
  (swap! platform-registry assoc platform config))

(defn unregister-platform!
  "Remove a platform from the registry."
  [platform]
  (swap! platform-registry dissoc platform))

(defn get-registered-platform
  "Get a registered platform configuration.

  Returns nil if not found."
  [platform]
  (get @platform-registry platform))

(defn list-registered-platforms
  "List all custom registered platforms."
  []
  (keys @platform-registry))

;; =============================================================================
;; Platform Validation
;; =============================================================================

(defn validate-platform-config
  "Validate that a platform configuration has all required fields.

  Returns:
  {:valid? true/false
   :errors [...]}"
  [config]
  (let [errors (atom [])]

    ;; Required top-level keys
    (when-not (:platform config)
      (swap! errors conj "Missing :platform key"))

    (when-not (:display-name config)
      (swap! errors conj "Missing :display-name key"))

    ;; Connection validation
    (when-not (get-in config [:connection :base-url-env])
      (swap! errors conj "Missing :connection :base-url-env"))

    ;; Auth validation
    (when-not (get-in config [:auth :type])
      (swap! errors conj "Missing :auth :type"))

    ;; Compiler validation
    (when-not (get-in config [:compiler :compiler-ns])
      (swap! errors conj "Missing :compiler :compiler-ns"))

    (when-not (get-in config [:compiler :compile-fn])
      (swap! errors conj "Missing :compiler :compile-fn"))

    ;; Endpoints validation
    (when-not (:endpoints config)
      (swap! errors conj "Missing :endpoints key"))

    {:valid? (empty? @errors)
     :errors @errors}))

(defn platform-exists?
  "Check if a platform configuration exists (either in files or registry)."
  [platform]
  (or (contains? (discover-platforms) platform)
      (contains? @platform-registry platform)))

;; =============================================================================
;; Public API
;; =============================================================================

(defn get-platform-info
  "Get comprehensive information about a platform.

  Checks registry first, then falls back to file-based discovery."
  [platform]
  (or (get-registered-platform platform)
      (load-platform-metadata platform)))

(defn search-platforms
  "Search for platforms by name or capability.

  Args:
    query - Search string (matches display-name or description)
    capability - Capability keyword (e.g., :create, :read)

  Returns:
  [{:platform :wordpress ...} ...]"
  [{:keys [query capability]}]
  (let [platforms (list-platforms)
        query-lower (when query (str/lower-case query))]

    (->> platforms
         ;; Filter by text query
         (filter (fn [p]
                   (or (nil? query)
                       (str/includes? (str/lower-case (str (:display-name p))) query-lower)
                       (str/includes? (str/lower-case (str (:description p ""))) query-lower))))
         ;; Filter by capability
         (filter (fn [p]
                   (or (nil? capability)
                       (contains? (:capabilities p) capability)))))))

(defn print-platform-list
  "Pretty-print list of available platforms."
  []
  (let [platforms (list-platforms)]
    (println "\n=== Available Sync Platforms ===\n")
    (doseq [p platforms]
      (println (format "%-20s %s" (str (:platform p)) (:display-name p)))
      (when (:description p)
        (println (format "%-20s %s" "" (:description p))))
      (println (format "%-20s Capabilities: %s" ""
                       (str/join ", " (map name (:capabilities p)))))
      (println))
    (println (format "Total: %d platforms\n" (count platforms)))))
