(require '[clojure.edn :as edn])
(require '[clojure.java.io :as io])

(println "=== Debug HTML Config Direct Load ===\n")

;; Load HTML config directly
(def html-config-path "forma/resources/forma/platforms/html.edn")
(def html-config (edn/read-string (slurp html-config-path)))

(println "1. HTML config loaded:")
(println "   Platform:" (:platform html-config))
(println "   Elements count:" (count (:elements html-config)))

(println "\n2. Checking for table elements:")
(def table-elements [:header :table :thead :tbody :tr :th :td])
(doseq [elem table-elements]
  (let [config (get-in html-config [:elements elem])]
    (if config
      (println "   ✓" elem "→" (get config :element "div"))
      (println "   ✗" elem "→ NOT FOUND"))))

(println "\n3. All element keys (first 20):")
(println "   " (take 20 (keys (:elements html-config))))

(println "\n4. Searching for 'header' in keys:")
(println "   " (filter #(str/includes? (str %) "header") (keys (:elements html-config))))

(println "\n5. Searching for 'table' in keys:")
(println "   " (filter #(str/includes? (str %) "table") (keys (:elements html-config))))

