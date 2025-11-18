(ns inspect-wordpress-state
  "Inspector tool to check WordPress/Oxygen state and verify deployments"
  (:require [clj-http.client :as http]
            [cheshire.core :as json]))

(def wordpress-url "http://hbtcomputers.com.au.test")
(def wordpress-user "admin")
(def wordpress-password "T8W8rxIo5y566jm79HgSs9Mi")

(defn fetch-page
  "Fetch a page by ID and return its tree structure"
  [page-id]
  (println (str "\n=== Fetching Page " page-id " ===\n"))

  (try
    (let [url (str wordpress-url "/index.php?rest_route=/oxygen/v1/get&post_id=" page-id)
          response (http/get url
                            {:basic-auth [wordpress-user wordpress-password]
                             :as :json})]

      (println "✅ Page fetched successfully!")
      (println)
      (println "Page ID:" (:id (:body response)))
      (println "Title:" (:title (:body response)))
      (println "Status:" (:status (:body response)))
      (println)

      (let [tree (get-in response [:body :tree])]
        (println "Tree Structure:")
        (println "  _nextNodeId:" (:_nextNodeId tree))
        (println "  status:" (:status tree))
        (println "  root.id:" (get-in tree [:root :id]))
        (println "  root.children count:" (count (get-in tree [:root :children])))
        (println)

        ;; Show first few elements
        (println "First 3 elements:")
        (doseq [[idx child] (map-indexed vector (take 3 (get-in tree [:root :children])))]
          (println (str "  Element " (inc idx) ":"))
          (println "    ID:" (:id child))
          (println "    Type:" (get-in child [:data :type]))
          (println "    Children count:" (count (:children child))))
        (println)

        (:body response)))

    (catch Exception e
      (println "❌ Error fetching page:" (.getMessage e))
      (.printStackTrace e)
      nil)))

(defn list-recent-pages
  "List recent pages to find our test deployments"
  []
  (println "\n=== Recent Pages ===\n")

  (try
    (let [url (str wordpress-url "/wp-json/wp/v2/pages?per_page=10&orderby=modified&order=desc")
          response (http/get url
                            {:basic-auth [wordpress-user wordpress-password]
                             :as :json})]

      (println "Recent pages (last 10):\n")
      (doseq [page (:body response)]
        (println (str "ID: " (:id page)
                     " | Title: " (get-in page [:title :rendered])
                     " | Status: " (:status page)
                     " | Modified: " (:modified page))))
      (println)

      (:body response))

    (catch Exception e
      (println "❌ Error listing pages:" (.getMessage e))
      nil)))

(defn inspect-page-html
  "Fetch and inspect the rendered HTML of a page"
  [page-id]
  (println (str "\n=== Inspecting Rendered HTML for Page " page-id " ===\n"))

  (try
    (let [url (str wordpress-url "/?page_id=" page-id)
          response (http/get url {:as :string})]

      (println "✅ Page HTML fetched!")
      (println "Content length:" (count (:body response)) "characters")
      (println)

      ;; Check for common patterns
      (let [html (:body response)]
        (println "CSS Variable Check:")
        (println "  Contains 'var(--primary)':" (boolean (re-find #"var\(--primary\)" html)))
        (println "  Contains 'var(--foreground)':" (boolean (re-find #"var\(--foreground\)" html)))
        (println "  Contains 'var(--background)':" (boolean (re-find #"var\(--background\)" html)))
        (println)

        (println "Layout Check:")
        (println "  Contains 'grid-template-columns':" (boolean (re-find #"grid-template-columns" html)))
        (println "  Contains 'repeat(4, 1fr)':" (boolean (re-find #"repeat\(4,\s*1fr\)" html)))
        (println)

        (println "Content Check:")
        (println "  Contains 'Mesh Styling Test':" (boolean (re-find #"Mesh Styling Test" html)))
        (println "  Contains 'Grid Layout Test':" (boolean (re-find #"Grid Layout Test" html)))
        (println)

        ;; Extract a snippet of the main content
        (when-let [body-match (re-find #"(?s)<body[^>]*>(.*?)</body>" html)]
          (let [body-content (second body-match)
                snippet (subs body-content 0 (min 1000 (count body-content)))]
            (println "Body content preview (first 1000 chars):")
            (println snippet)
            (println "...")
            (println))))

      (:body response))

    (catch Exception e
      (println "❌ Error fetching HTML:" (.getMessage e))
      nil)))

(defn check-oxygen-css
  "Check if Oxygen's CSS is being loaded and if CSS variables are defined"
  [page-id]
  (println (str "\n=== Checking CSS for Page " page-id " ===\n"))

  (try
    (let [url (str wordpress-url "/?page_id=" page-id)
          response (http/get url {:as :string})
          html (:body response)]

      (println "CSS Link Tags:")
      (doseq [css-link (re-seq #"<link[^>]*rel=[\"']stylesheet[\"'][^>]*>" html)]
        (println "  " css-link))
      (println)

      (println "Inline Style Tags:")
      (let [style-tags (re-seq #"(?s)<style[^>]*>(.*?)</style>" html)]
        (println "  Found" (count style-tags) "style tags")
        (when (seq style-tags)
          (println "  First style tag preview:")
          (let [first-style (second (first style-tags))
                preview (subs first-style 0 (min 500 (count first-style)))]
            (println preview)
            (println "..."))))
      (println))

    (catch Exception e
      (println "❌ Error checking CSS:" (.getMessage e))
      nil)))

(comment
  ;; List recent pages to find our test page
  (list-recent-pages)

  ;; Fetch page 49 (our styling test)
  (fetch-page 49)

  ;; Inspect the rendered HTML
  (inspect-page-html 49)

  ;; Check CSS loading
  (check-oxygen-css 49)

  ;; Full inspection
  (do
    (list-recent-pages)
    (fetch-page 49)
    (inspect-page-html 49)
    (check-oxygen-css 49))
  )
