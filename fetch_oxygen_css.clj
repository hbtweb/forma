(ns fetch-oxygen-css
  "Fetch the actual CSS that Oxygen generated to see what's missing"
  (:require [clj-http.client :as http]))

(defn fetch-page-css
  "Fetch the generated CSS file for a page"
  [page-id]
  (let [css-url (str "http://hbtcomputers.com.au.test/wp-content/uploads/oxygen/css/post-" page-id ".css")]
    (println (str "\n=== Fetching CSS for Page " page-id " ==="))
    (println "URL:" css-url)
    (println)

    (try
      (let [response (http/get css-url {:as :string})]
        (println "✅ CSS file found!")
        (println "Size:" (count (:body response)) "characters")
        (println)
        (println "CSS Content:")
        (println "---")
        (println (:body response))
        (println "---")
        (:body response))
      (catch Exception e
        (println "❌ Error:" (.getMessage e))
        nil))))

(defn analyze-css
  "Analyze what CSS properties are actually present"
  [css-content]
  (println "\n=== CSS Analysis ===")
  (println)

  (let [patterns {:gradients #"linear-gradient|radial-gradient"
                  :grid-layout #"display:\s*grid|grid-template"
                  :flexbox #"display:\s*flex|flex-direction"
                  :colors #"background|color|border-color"
                  :spacing #"padding|margin|gap"
                  :typography #"font-size|font-weight|line-height"
                  :hover #":hover"
                  :transforms #"transform|translate"}]

    (doseq [[name pattern] patterns]
      (let [matches (re-seq pattern (or css-content ""))]
        (println (str name ": ")
                (if (seq matches)
                  (str "✅ Found (" (count matches) " instances)")
                  "❌ Not found"))))))

(comment
  ;; Fetch and analyze CSS for page 50
  (def css (fetch-page-css 50))
  (analyze-css css)

  ;; Also check page 49 (styling test)
  (def css-49 (fetch-page-css 49))
  (analyze-css css-49)
  )
