(ns fetch-page54-alt
  "Fetch page 54 using the save endpoint"
  (:require [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.java.io :as io]))

(def wordpress-url "http://hbtcomputers.com.au.test")
(def wordpress-user "admin")
(def wordpress-password "T8W8rxIo5y566jm79HgSs9Mi")

(defn fetch-page []
  (let [url (str wordpress-url "/index.php?rest_route=/oxygen/v1/pages/54")]
    (println "Trying URL:" url)
    (try
      (let [response (http/get url
                              {:basic-auth [wordpress-user wordpress-password]
                               :throw-exceptions false
                               :as :string})]
        (println "Status:" (:status response))
        (if (= 200 (:status response))
          (let [data (json/parse-string (:body response) true)]
            (spit "page54-with-code-elements.json" (json/generate-string data {:pretty true}))
            (println "✅ Saved to page54-with-code-elements.json")
            data)
          (println "Error:" (:body response))))
      (catch Exception e
        (println "Exception:" (.getMessage e))
        nil))))

(fetch-page)