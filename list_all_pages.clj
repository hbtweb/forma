(ns list-all-pages
  (:require [clj-http.client :as http]
            [cheshire.core :as json]))

(def wordpress-url "http://hbtcomputers.com.au.test")
(def wordpress-user "admin")
(def wordpress-password "T8W8rxIo5y566jm79HgSs9Mi")

(defn list-pages
  "List all WordPress pages"
  []
  (println "\n=== WordPress Pages ===\n")

  (try
    (let [response (http/get (str wordpress-url "/index.php?rest_route=/wp/v2/pages&per_page=100")
                            {:basic-auth [wordpress-user wordpress-password]
                             :throw-exceptions false
                             :as :string})
          pages (json/parse-string (:body response) true)]

      (println "Found" (count pages) "pages:\n")

      (doseq [page pages]
        (println (format "ID: %-4d | %-10s | %s"
                        (:id page)
                        (:status page)
                        (get-in page [:title :rendered])))
        (println (format "       URL: %s" (:link page)))
        (println))

      {:success true :count (count pages) :pages pages})

    (catch Exception e
      (println "Error:" (.getMessage e))
      {:success false :error (.getMessage e)})))

(defn list-oxygen-templates
  "List Oxygen templates via REST API"
  []
  (println "\n=== Oxygen Templates ===\n")

  (try
    (let [response (http/get (str wordpress-url "/index.php?rest_route=/oxygen/v1/templates/list/template")
                            {:basic-auth [wordpress-user wordpress-password]
                             :throw-exceptions false
                             :as :string})
          data (json/parse-string (:body response) true)
          templates (get-in data [:data :templates])]

      (if templates
        (do
          (println "Found" (count templates) "templates:\n")
          (doseq [tmpl templates]
            (println (format "ID: %-4d | %-10s | %s"
                            (:id tmpl)
                            (:status tmpl)
                            (:title tmpl)))
            (println))
          {:success true :count (count templates) :templates templates})
        (do
          (println "No templates found or endpoint returned unexpected format")
          (println "Response:" data)
          {:success false :message "No templates found"})))

    (catch Exception e
      (println "Error:" (.getMessage e))
      {:success false :error (.getMessage e)})))

(defn list-all
  "List all pages and templates"
  []
  (list-pages)
  (list-oxygen-templates))

(comment
  (list-pages)
  (list-oxygen-templates)
  (list-all)
)
