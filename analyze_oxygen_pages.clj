(ns analyze-oxygen-pages
  (:require [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.pprint :as pp]))

(def wordpress-url "http://hbtcomputers.com.au.test")
(def wordpress-user "admin")
(def wordpress-password "T8W8rxIo5y566jm79HgSs9Mi")

(defn get-all-pages []
  (let [response (http/get (str wordpress-url "/index.php?rest_route=/wp/v2/pages&per_page=100")
                          {:basic-auth [wordpress-user wordpress-password]
                           :throw-exceptions false
                           :as :string})]
    (json/parse-string (:body response) true)))

(defn get-page-tree [page-id]
  (let [response (http/get (str wordpress-url "/index.php?rest_route=/oxygen/v1/page/" page-id)
                          {:basic-auth [wordpress-user wordpress-password]
                           :throw-exceptions false
                           :as :string})]
    (json/parse-string (:body response) true)))

(defn extract-element-types [node]
  (when node
    (let [type (get-in node [:data :type])
          children (:children node)]
      (concat
        (when type [type])
        (mapcat extract-element-types children)))))

(defn analyze-element [node path]
  (when node
    (let [type (get-in node [:data :type])
          props (get-in node [:data :properties])
          children (:children node)]
      (concat
        (when type
          [{:path path
            :type type
            :properties (keys props)
            :property-structure props}])
        (mapcat #(analyze-element %1 (str path "/" type))
               children)))))

(defn analyze-all-pages []
  (println "\n=== Analyzing All Oxygen Pages ===\n")

  (let [pages (get-all-pages)]
    (println "Found" (count pages) "pages\n")

    (doseq [page pages]
      (let [page-id (:id page)
            title (get-in page [:title :rendered])
            status (:status page)]

        (println (format "--- Page ID %d: %s (%s) ---" page-id title status))

        (try
          (let [tree-data (get-page-tree page-id)
                tree (:tree tree-data)
                root (:root tree)
                next-id (:_nextNodeId tree)]

            (println "  _nextNodeId:" next-id "(type:" (type next-id) ")")
            (println "  root.id:" (:id root) "(type:" (type (:id root)) ")")
            (println "  root.data:" (:data root))
            (println "  Children count:" (count (:children root)))

            ;; Extract all element types
            (let [types (distinct (extract-element-types root))]
              (println "  Element types used:")
              (doseq [t types]
                (println "    -" t)))

            ;; Analyze first few elements in detail
            (println "\n  First 3 elements in detail:")
            (doseq [[idx child] (take 3 (map-indexed vector (:children root)))]
              (println "    Element" (inc idx) ":")
              (println "      ID:" (:id child) "(type:" (type (:id child)) ")")
              (println "      Type:" (get-in child [:data :type]))
              (println "      Properties:" (keys (get-in child [:data :properties])))
              (when (get-in child [:data :properties :content])
                (println "      Content structure:" (keys (get-in child [:data :properties :content]))))
              (when (get-in child [:data :properties :design])
                (println "      Design structure:" (keys (get-in child [:data :properties :design])))))

            (println))

          (catch Exception e
            (println "  Error analyzing page:" (.getMessage e))
            (println)))))

    (println "\n=== Analysis Complete ===\n")))

(defn save-full-tree [page-id filename]
  (let [tree-data (get-page-tree page-id)
        tree (:tree tree-data)]
    (spit filename (json/generate-string tree {:pretty true}))
    (println "Saved tree for page" page-id "to" filename)))

(comment
  ;; Analyze all pages
  (analyze-all-pages)

  ;; Save specific page trees for detailed analysis
  (save-full-tree 21 "page21-tree.json")
  (save-full-tree 12 "page12-tree.json")
  (save-full-tree 10 "template10-tree.json")
)
