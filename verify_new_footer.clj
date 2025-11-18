(require '[clj-http.client :as http]
         '[cheshire.core :as json]
         '[clojure.java.io :as io])

(def wp-url "http://hbtcomputers.com.au.test")
(def wp-user "admin")
(def wp-pass "T8W8rxIo5y566jm79HgSs9Mi")
(def footer-id 47)

(println "=== Verifying New Footer (ID: 47) ===\n")

;; Fetch the footer details
(println "Fetching footer details...")
(let [response (http/get (str wp-url "/index.php?rest_route=/oxygen/v1/page/" footer-id)
                         {:basic-auth [wp-user wp-pass]
                          :accept :json})
      data (json/parse-string (:body response) true)]

  (println "\n✓ Footer Details:")
  (println (str "  ID: " (:id data)))
  (println (str "  Title: " (:title data)))
  (println (str "  Type: " (:post_type data)))
  (println (str "  Status: " (:status data)))
  (println (str "  URL: " (:url data)))

  ;; Save tree to file for inspection
  (println "\nSaving tree structure to footer-47-tree.json...")
  (spit "footer-47-tree.json"
        (json/generate-string (:tree data) {:pretty true}))
  (println "✓ Tree saved!")

  ;; Print some stats
  (defn count-nodes [node]
    (if (:children node)
      (+ 1 (reduce + (map count-nodes (:children node))))
      1))

  (let [node-count (count-nodes (get-in data [:tree :root]))]
    (println (str "\n✓ Tree Statistics:"))
    (println (str "  Total nodes: " node-count))
    (println (str "  Next node ID: " (get-in data [:tree :_nextNodeId])))
    (println (str "  Status: " (get-in data [:tree :status])))))

(println "\n=== Verification Complete ===")
(println "\nYou can now:")
(println "1. Visit: http://hbtcomputers.com.au.test")
(println "2. Check footer-47-tree.json for the tree structure")
(println "3. Edit in Oxygen builder: http://hbtcomputers.com.au.test/wp-admin/admin.php?page=oxygen_vsb_sign_shortcodes&action=edit&post=47")