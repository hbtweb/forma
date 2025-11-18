(ns forma.sync.client
  "Generic HTTP sync client for platform-agnostic bidirectional synchronization.

  This namespace provides a universal HTTP client that reads EDN-based platform
  configurations and handles:
  - HTTP requests (GET, POST, PUT, DELETE)
  - Authentication (Basic, Bearer, OAuth, API Key, JWT)
  - Request/response transformation
  - Retry logic with exponential backoff
  - Rate limiting
  - Compiler integration (EDN ↔ Platform format)

  Configuration is loaded via three-tier resolution:
  Project → Library → Default

  Example usage:
    (publish :wordpress \"My Page\" [[:section ...]])
    (pull :wordpress 684)
    (list-all :wordpress {:post_type \"page\"})"
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [cheshire.core :as json]))

;; =============================================================================
;; Configuration Loading
;; =============================================================================

(defn load-sync-config
  "Load sync configuration for a platform using three-tier resolution.

  Resolution order:
  1. projects/{project}/sync/{platform}.edn
  2. library/sync/{platform}.edn
  3. sync/{platform}.edn (from default/ on classpath)

  Returns the configuration map or nil if not found."
  [platform project-name]
  (let [platform-str (name platform)
        ;; Since default/, library/, projects/ are on classpath,
        ;; resources are accessed without those prefixes
        paths [(str project-name "/sync/" platform-str ".edn")
               (str "sync/" platform-str ".edn")
               (str "sync/" platform-str ".edn")]]  ;; Fallback to default
    (some (fn [path]
            (when-let [resource (io/resource path)]
              (try
                (-> resource slurp edn/read-string)
                (catch Exception e
                  (println "Warning: Failed to load sync config:" path (.getMessage e))
                  nil))))
          paths)))

(defn merge-configs
  "Deep merge multiple sync configurations.
  Project config overrides library, library overrides default."
  [& configs]
  (apply merge-with
         (fn [v1 v2]
           (if (and (map? v1) (map? v2))
             (merge-with merge v1 v2)
             v2))
         configs))

(defn get-merged-config
  "Load and merge all available configs for a platform."
  [platform project-name]
  (let [default-config (load-sync-config platform "default")
        library-config (load-sync-config platform "library")
        project-config (load-sync-config platform project-name)]
    (merge-configs default-config library-config project-config)))

;; =============================================================================
;; Environment Variable Resolution
;; =============================================================================

(defn resolve-env-var
  "Resolve environment variable reference.

  Input: 'WORDPRESS_URL' or 'WORDPRESS_URL || http://localhost'
  Output: value from environment or fallback

  If the input looks like a direct value (starts with http://, https://, etc.),
  return it directly without env var lookup."
  [env-ref]
  (if-let [env-ref (and env-ref (str/trim env-ref))]
    (if (or (str/starts-with? env-ref "http://")
            (str/starts-with? env-ref "https://")
            (str/starts-with? env-ref "/"))
      ;; Direct value - return as-is
      env-ref
      ;; Environment variable reference
      (let [[var-name fallback] (map str/trim (str/split env-ref #"\|\|" 2))
            ;; Check both environment variables and system properties
            env-value (or (System/getenv var-name)
                          (System/getProperty var-name))]
        (or env-value fallback)))
    nil))

(defn resolve-config-env-vars
  "Recursively resolve all environment variable references in config."
  [config]
  (cond
    (string? config)
    (if (str/starts-with? config "$")
      (resolve-env-var (subs config 1))
      config)

    (map? config)
    (into {} (map (fn [[k v]] [k (resolve-config-env-vars v)]) config))

    (sequential? config)
    (mapv resolve-config-env-vars config)

    :else
    config))

;; =============================================================================
;; Authentication
;; =============================================================================

(defn build-auth-headers
  "Build authentication headers based on config.

  Supported types:
  - :basic - HTTP Basic Auth
  - :bearer - Bearer token
  - :api-key - API key in header
  - :oauth - OAuth 2.0 token
  - :jwt - JWT token"
  [auth-config]
  (let [auth-type (:type auth-config)]
    (case auth-type
      :basic
      (let [username (resolve-env-var (:username-env auth-config))
            password (resolve-env-var (:password-env auth-config))
            credentials (str username ":" password)
            encoder (java.util.Base64/getEncoder)
            encoded (.encodeToString encoder (.getBytes credentials))]
        {"Authorization" (str "Basic " encoded)})

      :bearer
      (let [token (resolve-env-var (:token-env auth-config))]
        {"Authorization" (str "Bearer " token)})

      :api-key
      (let [key-name (:header-name auth-config "X-API-Key")
            key-value (resolve-env-var (:key-env auth-config))]
        {key-name key-value})

      :oauth
      (let [token (resolve-env-var (:token-env auth-config))]
        {"Authorization" (str "Bearer " token)})

      :jwt
      (let [token (resolve-env-var (:token-env auth-config))]
        {"Authorization" (str "Bearer " token)})

      ;; No auth
      {})))

;; =============================================================================
;; HTTP Request Building
;; =============================================================================

(defn build-url
  "Build full URL from config and endpoint.

  Replaces path parameters like :id with actual values."
  [config endpoint-config params]
  (let [base-url (resolve-env-var (get-in config [:connection :base-url-env]))
        path-prefix (get-in config [:connection :path-prefix] "")
        endpoint-path (:path endpoint-config)
        ;; Replace path parameters
        final-path (reduce (fn [path [k v]]
                             (str/replace path (str ":" (name k)) (str v)))
                           endpoint-path
                           params)]
    (str base-url path-prefix final-path)))

(defn build-request
  "Build HTTP request map.

  Returns:
  {:method :get
   :url \"http://...\"
   :headers {...}
   :body \"...\"}"
  [config endpoint-key params body]
  (let [endpoint-config (get-in config [:endpoints endpoint-key])
        method (:method endpoint-config)
        url (build-url config endpoint-config params)
        auth-headers (build-auth-headers (:auth config))
        content-type (get-in config [:connection :content-type] "application/json")
        headers (merge auth-headers
                       {"Content-Type" content-type}
                       (:headers params {}))]
    {:method method
     :url url
     :headers headers
     :body body}))

;; =============================================================================
;; HTTP Execution (Placeholder - requires clj-http dependency)
;; =============================================================================

(defn execute-request
  "Execute HTTP request using clj-http.

  Args:
    request-map - HTTP request map with :method, :url, :headers, :body

  Returns:
    HTTP response map with :status, :headers, :body"
  [request-map]
  (require '[clj-http.client :as http])
  (let [http-request (ns-resolve 'clj-http.client 'request)
        ;; Convert method keyword and merge request options
        http-options (merge
                      {:method (:method request-map)
                       :url (:url request-map)
                       :headers (:headers request-map)
                       :throw-exceptions false  ; Return response even on errors
                       :as :string              ; Return body as string
                       :content-type :json}
                      (when (:body request-map)
                        {:body (:body request-map)}))]
    (http-request http-options)))

(defn parse-response
  "Parse HTTP response body based on content type."
  [response]
  (let [content-type (get-in response [:headers "content-type"] "application/json")
        body (:body response)]
    (cond
      (str/includes? content-type "application/json")
      (try
        (json/parse-string body true)
        (catch Exception e
          {:error "Failed to parse JSON" :message (.getMessage e) :body body}))

      :else
      body)))

;; =============================================================================
;; Retry Logic
;; =============================================================================

(defn exponential-backoff
  "Calculate exponential backoff delay in milliseconds."
  [attempt max-delay]
  (min (* 1000 (Math/pow 2 attempt)) max-delay))

(defn execute-with-retry
  "Execute request with exponential backoff retry logic."
  [request-map retry-config]
  (let [max-retries (get retry-config :max-retries 3)
        max-delay (get retry-config :max-delay 10000)]
    (loop [attempt 0]
      (let [response (execute-request request-map)
            status (:status response)]
        (if (and (>= status 500) (< attempt max-retries))
          (do
            (Thread/sleep (exponential-backoff attempt max-delay))
            (recur (inc attempt)))
          response)))))

;; =============================================================================
;; Compiler Integration
;; =============================================================================

(defn load-compiler-fn
  "Load compiler function from namespace.

  Returns the function or nil if not found."
  [compiler-ns fn-name]
  (try
    (require (symbol compiler-ns))
    (ns-resolve (symbol compiler-ns) (symbol fn-name))
    (catch Exception e
      (println "Warning: Failed to load compiler function:" compiler-ns fn-name (.getMessage e))
      nil)))

(defn compile-to-platform
  "Compile Forma EDN to platform-specific format using configured compiler."
  [forma-edn config]
  (if-let [compiler-config (:compiler config)]
    (let [compiler-ns (str (:compiler-ns compiler-config))
          compile-fn-name (str (:compile-fn compiler-config))
          compile-fn (load-compiler-fn compiler-ns compile-fn-name)]
      (if compile-fn
        (compile-fn forma-edn)
        {:error "Compiler function not found"
         :compiler-ns compiler-ns
         :function compile-fn-name}))
    {:error "No compiler configuration found"}))

(defn compile-from-platform
  "Compile platform-specific format back to Forma EDN (reverse compilation)."
  [platform-data config]
  (if-let [compiler-config (:compiler config)]
    (let [compiler-ns (str (:compiler-ns compiler-config))
          reverse-fn-name (str (:reverse-fn compiler-config))
          reverse-fn (load-compiler-fn compiler-ns reverse-fn-name)]
      (if reverse-fn
        (reverse-fn platform-data)
        {:error "Reverse compiler function not found"
         :compiler-ns compiler-ns
         :function reverse-fn-name}))
    {:error "No compiler configuration found"}))

;; =============================================================================
;; Public API
;; =============================================================================

(defn publish
  "Publish Forma EDN content to a platform.

  Args:
    platform - Platform keyword (:wordpress, :ghost, etc.)
    title - Content title
    forma-edn - Forma EDN structure
    opts - Options map:
      :project-name - Project name for config resolution
      :params - Additional request parameters
      :metadata - Platform-specific metadata

  Returns:
    {:success true/false
     :data {...}
     :error \"...\"}"
  [platform title forma-edn opts]
  (let [project-name (get opts :project-name "default")
        config (get-merged-config platform project-name)
        config (resolve-config-env-vars config)

        ;; Compile to platform format
        platform-data (compile-to-platform forma-edn config)

        ;; Build request body
        body-data (merge
                   {:title title
                    :content platform-data}
                   (:metadata opts))
        body (json/generate-string body-data)

        ;; Execute request
        request (build-request config :create (:params opts) body)
        response (execute-with-retry request (:retry config))]

    (if (= 200 (:status response))
      {:success true
       :data (parse-response response)}
      {:success false
       :error (str "HTTP " (:status response))
       :response response})))

(defn pull
  "Pull content from platform and convert to Forma EDN.

  Args:
    platform - Platform keyword
    id - Content ID
    opts - Options map

  Returns:
    {:success true/false
     :data {:forma-edn [...]}
     :error \"...\"}"
  [platform id opts]
  (let [project-name (get opts :project-name "default")
        config (get-merged-config platform project-name)
        config (resolve-config-env-vars config)

        ;; Execute request
        request (build-request config :read {:id id} nil)
        response (execute-with-retry request (:retry config))]

    (if (= 200 (:status response))
      (let [platform-data (parse-response response)
            forma-edn (compile-from-platform platform-data config)]
        {:success true
         :data {:forma-edn forma-edn
                :original platform-data}})
      {:success false
       :error (str "HTTP " (:status response))
       :response response})))

(defn update-content
  "Update existing content on platform.

  Args:
    platform - Platform keyword
    id - Content ID
    title - Updated title
    forma-edn - Updated Forma EDN
    opts - Options map

  Returns:
    {:success true/false
     :data {...}
     :error \"...\"}"
  [platform id title forma-edn opts]
  (let [project-name (get opts :project-name "default")
        config (get-merged-config platform project-name)
        config (resolve-config-env-vars config)

        ;; Compile to platform format
        platform-data (compile-to-platform forma-edn config)

        ;; Build request body
        body-data (merge
                   {:id id
                    :title title
                    :content platform-data}
                   (:metadata opts))
        body (json/generate-string body-data)

        ;; Execute request
        request (build-request config :update {:id id} body)
        response (execute-with-retry request (:retry config))]

    (if (= 200 (:status response))
      {:success true
       :data (parse-response response)}
      {:success false
       :error (str "HTTP " (:status response))
       :response response})))

(defn delete-content
  "Delete content from platform.

  Args:
    platform - Platform keyword
    id - Content ID
    opts - Options map

  Returns:
    {:success true/false
     :data {...}
     :error \"...\"}"
  [platform id opts]
  (let [project-name (get opts :project-name "default")
        config (get-merged-config platform project-name)
        config (resolve-config-env-vars config)

        ;; Execute request
        request (build-request config :delete {:id id} nil)
        response (execute-with-retry request (:retry config))]

    (if (= 200 (:status response))
      {:success true
       :data (parse-response response)}
      {:success false
       :error (str "HTTP " (:status response))
       :response response})))

(defn list-all
  "List all content from platform.

  Args:
    platform - Platform keyword
    opts - Options map:
      :params - Query parameters
      :project-name - Project name

  Returns:
    {:success true/false
     :data [{...}]
     :error \"...\"}"
  [platform opts]
  (let [project-name (get opts :project-name "default")
        config (get-merged-config platform project-name)
        config (resolve-config-env-vars config)

        ;; Execute request
        request (build-request config :list (:params opts) nil)
        response (execute-with-retry request (:retry config))]

    (if (= 200 (:status response))
      {:success true
       :data (parse-response response)}
      {:success false
       :error (str "HTTP " (:status response))
       :response response})))
