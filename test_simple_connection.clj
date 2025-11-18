(ns test-simple-connection
  (:require [clj-http.client :as http]
            [cheshire.core :as json]))

(defn test-oxygen-api
  "Simple test of Oxygen REST API"
  []
  (println "\n=== Testing Oxygen REST Bridge ===\n")

  (let [url "http://hbtcomputers.com.au.test/index.php?rest_route=/oxygen/v1/info"
        username "admin"
        password "T8W8rxIo5y566jm79HgSs9Mi"]

    (println "URL:" url)
    (println "User:" username)
    (println)

    (try
      (let [response (http/get url
                              {:basic-auth [username password]
                               :throw-exceptions false
                               :as :string})]

        (println "Status Code:" (:status response))
        (println)

        (if (= 200 (:status response))
          (do
            (println "✅ Connection successful!")
            (println)
            (let [data (json/parse-string (:body response) true)]
              (println "Plugin:" (:plugin data))
              (println "Version:" (:version data))
              (println "Oxygen Version:" (:oxygen_version data))
              (println "Endpoints Available:" (count (:endpoints data)))
              (println)
              {:success true :data data}))
          (do
            (println "❌ Connection failed")
            (println "Body:" (:body response))
            (println)
            {:success false :status (:status response) :body (:body response)})))

      (catch Exception e
        (println "❌ Error:" (.getMessage e))
        {:success false :error (.getMessage e)}))))

(comment
  ;; Run this test
  (test-oxygen-api)
)
