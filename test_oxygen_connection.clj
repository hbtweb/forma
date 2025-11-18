(ns test-oxygen-connection
  "Quick connection test for WordPress/Oxygen REST API"
  (:require [forma.sync.client :as sync]
            [cheshire.core :as json]))

;; =============================================================================
;; Connection Test
;; =============================================================================

(defn test-connection
  "Test connection to WordPress REST API.

   Make sure to set environment variables first:
     (System/setProperty \"WORDPRESS_URL\" \"https://hbtcomputers.com.au\")
     (System/setProperty \"WORDPRESS_USER\" \"your-username\")
     (System/setProperty \"WORDPRESS_APP_PASSWORD\" \"xxxx xxxx xxxx\")

   Then run:
     (test-connection)

   Should return:
     {:success true
      :message \"Connected successfully\"
      :oxygen-available true}"
  []
  (println "\n=== Testing WordPress/Oxygen Connection ===\n")

  ;; Check environment variables
  (let [wp-url (or (System/getenv "WORDPRESS_URL")
                   (System/getProperty "WORDPRESS_URL"))
        wp-user (or (System/getenv "WORDPRESS_USER")
                    (System/getProperty "WORDPRESS_USER"))
        wp-pass (or (System/getenv "WORDPRESS_APP_PASSWORD")
                    (System/getProperty "WORDPRESS_APP_PASSWORD"))]

    (println "WordPress URL:" (or wp-url "[NOT SET]"))
    (println "WordPress User:" (or wp-user "[NOT SET]"))
    (println "WordPress Password:" (if wp-pass "[SET]" "[NOT SET]"))
    (println)

    (if (and wp-url wp-user wp-pass)
      (do
        (println "Testing connection to" wp-url "...\n")

        ;; Test by loading config and making a request
        (try
          (let [config (sync/get-merged-config :wordpress "hbt-computers")
                resolved-config (sync/resolve-config-env-vars config)]

            (println "✓ Config loaded successfully")
            (println "  Base URL:" (get-in resolved-config [:connection :base-url-env]))
            (println "  Path prefix:" (get-in resolved-config [:connection :path-prefix]))
            (println)

            ;; Try to fetch info endpoint
            (let [info-url (str wp-url "/wp-json/oxygen/v1/info")
                  _ (println "Testing endpoint:" info-url)
                  response ((requiring-resolve 'clj-http.client/get)
                           info-url
                           {:basic-auth [wp-user wp-pass]
                            :throw-exceptions false
                            :as :string})]

              (println "  Response status:" (:status response))

              (if (= 200 (:status response))
                (do
                  (println "✓ Connection successful!")
                  (println "\nOxygen REST Bridge Info:")
                  (println (:body response))
                  {:success true
                   :message "Connected successfully"
                   :oxygen-available true})
                (do
                  (println "✗ Connection failed")
                  (println "  Status:" (:status response))
                  (println "  Body:" (:body response))
                  {:success false
                   :error "Connection failed"
                   :status (:status response)
                   :body (:body response)}))))

          (catch Exception e
            (println "✗ Error:" (.getMessage e))
            {:success false
             :error (.getMessage e)})))

      (do
        (println "✗ Environment variables not set!")
        (println "\nPlease set:")
        (println "  (System/setProperty \"WORDPRESS_URL\" \"https://hbtcomputers.com.au\")")
        (println "  (System/setProperty \"WORDPRESS_USER\" \"your-username\")")
        (println "  (System/setProperty \"WORDPRESS_APP_PASSWORD\" \"xxxx xxxx xxxx\")")
        {:success false
         :error "Environment variables not set"}))))

(comment
  ;; USAGE:

  ;; 1. Set credentials
  (System/setProperty "WORDPRESS_URL" "https://hbtcomputers.com.au")
  (System/setProperty "WORDPRESS_USER" "your-username")
  (System/setProperty "WORDPRESS_APP_PASSWORD" "xxxx xxxx xxxx xxxx xxxx xxxx")

  ;; 2. Test connection
  (test-connection)

  ;; 3. If successful, you should see:
  ;; ✓ Config loaded successfully
  ;; ✓ Connection successful!
  ;; Oxygen REST Bridge Info: {...}

  ;; 4. Then you're ready to deploy!
  (require '[forma.oxygen-deploy-demo :as demo])
  (demo/deploy-hero-section)
)
