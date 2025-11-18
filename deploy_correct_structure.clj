(ns deploy-correct-structure
  (:require [clj-http.client :as http]
            [cheshire.core :as json]))

(def wordpress-url "http://hbtcomputers.com.au.test")
(def wordpress-user "admin")
(def wordpress-password "T8W8rxIo5y566jm79HgSs9Mi")

(defn deploy-correct-page
  "Deploy a page with CORRECT Oxygen structure"
  []
  (println "\n=== Deploying with CORRECT Oxygen Structure ===\n")

  (let [;; CORRECT Oxygen tree structure (matching working pages)
        tree {:_nextNodeId 200  ; ✅ Required! Must be number
              :status "exported"  ; ✅ Required for builder to open!
              :root {:id 1  ; ✅ Number ID
                     :data {:type "root"  ; ✅ Object with type
                            :properties []}  ; ✅ Array
                     :children [{:id 100  ; ✅ Number IDs
                                :data {:type "EssentialElements\\Section"
                                       :properties {:design {:background "#1a202c"
                                                            :padding "80px 20px"
                                                            :layout_v2 {:layout "vertical"}}}}
                                :children [{:id 101
                                           :data {:type "EssentialElements\\Heading"
                                                  :properties {:content {:content {:text "Hello from Forma!"
                                                                                  :tags "h1"}}}}
                                           :children []
                                           :_parentId 100}
                                          {:id 102
                                           :data {:type "EssentialElements\\Text"
                                                  :properties {:content {:text "This page was deployed programmatically with CORRECT structure!"}}}
                                           :children []
                                           :_parentId 100}]
                                :_parentId 1}]}}

        ;; Request body
        body {:title "Forma Test Page - Correct Structure"
              :post_type "page"
              :status "draft"
              :tree tree}

        ;; API endpoint
        url (str wordpress-url "/index.php?rest_route=/oxygen/v1/save")]

    (println "Deploying page with CORRECT structure:")
    (println "- root.id = number (1)")
    (println "- root.data = {type: 'root', properties: []}")
    (println "- All child IDs are numbers")
    (println "- Content nested: content.content.text")
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
            (println "🎉 Try opening in Oxygen Builder - should work now!")
            (println "  " (:edit_url data))
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
  ;; Deploy with correct structure
  (deploy-correct-page)
)
