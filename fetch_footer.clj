(require '[clj-http.client :as http]
         '[cheshire.core :as json])

(println "Fetching current footer templates...")

(let [response (http/get "http://hbtcomputers.com.au.test/index.php?rest_route=/oxygen/v1/templates/list/footer"
                         {:basic-auth ["admin" "password"]
                          :accept :json})
      data (json/parse-string (:body response) true)]

  (println "\n=== Current Footers ===")
  (doseq [footer (:data data)]
    (println (str "ID: " (:id footer) " - " (:title footer))))

  ;; Fetch the full tree for Footer ID 26 (from docs)
  (println "\n=== Fetching Footer 26 Details ===")
  (let [footer-response (http/get "http://hbtcomputers.com.au.test/index.php?rest_route=/oxygen/v1/page/26"
                                  {:basic-auth ["admin" "password"]
                                   :accept :json})
        footer-data (json/parse-string (:body footer-response) true)]
    (println "\nFooter 26 Structure:")
    (println (json/generate-string footer-data {:pretty true}))))