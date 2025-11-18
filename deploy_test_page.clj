(ns deploy-test-page
  (:require [clj-http.client :as http]
            [cheshire.core :as json]))

(def wordpress-url "http://hbtcomputers.com.au.test")
(def wordpress-user "admin")
(def wordpress-password "T8W8rxIo5y566jm79HgSs9Mi")

(defn deploy-simple-page
  "Deploy a simple test page to WordPress/Oxygen"
  []
  (println "\n=== Deploying Test Page to WordPress/Oxygen ===\n")

  (let [;; Simple Oxygen tree structure
        tree {:_nextNodeId 100
              :root {:id "root"
                     :data {}
                     :children [{:id 101
                                :data {:type "EssentialElements\\Section"
                                       :properties {:design {:background "#1a202c"
                                                            :padding "80px 20px"
                                                            :textAlign "center"}}}
                                :children [{:id 102
                                           :data {:type "EssentialElements\\Heading"
                                                  :properties {:content {:text "Hello from Forma!"}
                                                              :level 1
                                                              :design {:color "#ffffff"
                                                                      :fontSize "48px"}}}
                                           :children []}
                                          {:id 103
                                           :data {:type "EssentialElements\\Text"
                                                  :properties {:text "This page was deployed programmatically from Clojure!"
                                                              :design {:color "#e2e8f0"
                                                                      :fontSize "20px"}}}
                                           :children []}]}]}}

        ;; Request body
        body {:title "Forma Test Page"
              :post_type "page"
              :status "draft"
              :tree tree}

        ;; API endpoint
        url (str wordpress-url "/index.php?rest_route=/oxygen/v1/save")]

    (println "URL:" url)
    (println "Title:" (:title body))
    (println "Status:" (:status body))
    (println)

    (try
      (let [response (http/post url
                               {:basic-auth [wordpress-user wordpress-password]
                                :content-type :json
                                :body (json/generate-string body)
                                :throw-exceptions false
                                :as :string})]

        (println "Response Status:" (:status response))
        (println)

        (if (= 200 (:status response))
          (let [data (json/parse-string (:body response) true)]
            (println "✅ Page deployed successfully!")
            (println)
            (println "Page ID:" (:id data))
            (println "Page URL:" (:url data))
            (println "Edit URL:" (:edit_url data))
            (println)
            (println "🎉 Open in browser:")
            (println "  " (:url data))
            (println)
            {:success true :data data})
          (do
            (println "❌ Deployment failed")
            (println "Body:" (:body response))
            (println)
            {:success false :status (:status response) :body (:body response)})))

      (catch Exception e
        (println "❌ Error:" (.getMessage e))
        (.printStackTrace e)
        {:success false :error (.getMessage e)}))))

(comment
  ;; Deploy test page
  (deploy-simple-page)
)
