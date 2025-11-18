(ns fetch-page54-correct
  "Fetch page 54 using correct endpoint"
  (:require [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.java.io :as io]))

(def wordpress-url "http://hbtcomputers.com.au.test")
(def wordpress-user "admin")
(def wordpress-password "T8W8rxIo5y566jm79HgSs9Mi")

(defn fetch-page []
  (let [url (str wordpress-url "/index.php?rest_route=/oxygen/v1/page/54")]
    (println "Fetching page 54 tree...")
    (println "URL:" url)
    (println)
    (try
      (let [response (http/get url
                              {:basic-auth [wordpress-user wordpress-password]
                               :throw-exceptions false
                               :as :string})]
        (if (= 200 (:status response))
          (let [data (json/parse-string (:body response) true)]
            (spit "page54-with-code-elements.json" (json/generate-string data {:pretty true}))
            (println "✅ Success! Saved to page54-with-code-elements.json")
            (println)
            (println "Tree keys:" (keys data))
            data)
          (do
            (println "❌ Failed")
            (println "Status:" (:status response))
            (println "Body:" (:body response))
            nil)))
      (catch Exception e
        (println "❌ Exception:" (.getMessage e))
        (.printStackTrace e)
        nil))))

(fetch-page)