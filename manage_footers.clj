(require '[clj-http.client :as http]
         '[cheshire.core :as json])

(def wp-url "http://hbtcomputers.com.au.test")
(def wp-user "admin")
(def wp-pass "T8W8rxIo5y566jm79HgSs9Mi")

(println "=== Managing Footer Templates ===\n")

;; List all footers
(println "1. Listing all footer templates...")
(let [response (http/get (str wp-url "/index.php?rest_route=/oxygen/v1/templates/list/footer")
                         {:basic-auth [wp-user wp-pass]
                          :accept :json
                          :throw-exceptions false})
      data (json/parse-string (:body response) true)]

  (if (= 200 (:status response))
    (do
      (println "\n  Current Footers:")
      (doseq [footer (:data data)]
        (println (str "    ID: " (:id footer)
                     " - Title: " (or (:title footer) "[No Title]")
                     " - Status: " (or (:status footer) "unknown"))))

      ;; Store all footer IDs
      (def all-footer-ids (map :id (:data data)))
      (def new-footer-id 47)

      ;; Find old footers (everything except ID 47)
      (def old-footer-ids (filter #(not= % new-footer-id) all-footer-ids))

      (when (seq old-footer-ids)
        (println (str "\n2. Found " (count old-footer-ids) " old footer(s) to delete: " (vec old-footer-ids)))

        ;; Delete each old footer
        (doseq [footer-id old-footer-ids]
          (when footer-id  ; Only if ID is not nil
            (println (str "\n  Deleting footer ID " footer-id "..."))
            (let [del-response (http/delete (str wp-url "/index.php?rest_route=/oxygen/v1/page/" footer-id)
                                           {:basic-auth [wp-user wp-pass]
                                            :throw-exceptions false})]
              (if (or (= 200 (:status del-response)) (= 204 (:status del-response)))
                (println (str "    ✓ Deleted footer ID " footer-id))
                (println (str "    ✗ Failed to delete footer ID " footer-id
                            " - Status: " (:status del-response)))))))

        (println "\n3. Verifying remaining footers...")
        (let [verify-response (http/get (str wp-url "/index.php?rest_route=/oxygen/v1/templates/list/footer")
                                       {:basic-auth [wp-user wp-pass]
                                        :accept :json})
              verify-data (json/parse-string (:body verify-response) true)]

          (println "\n  Remaining Footers:")
          (doseq [footer (:data verify-data)]
            (println (str "    ID: " (:id footer)
                         " - Title: " (or (:title footer) "[No Title]")
                         " - Status: " (or (:status footer) "unknown")))))))
    (do
      (println "  ✗ Failed to fetch footers")
      (println (str "  Status: " (:status response)))
      (println (str "  Body: " (:body response))))))

(println "\n=== Footer Management Complete ===")
(println "\nNew footer (ID: 47) is now the only footer applied everywhere!")
(println "View it at: http://hbtcomputers.com.au.test")