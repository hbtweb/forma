(require '[forma.compiler :as compiler])
(require '[kora.core.compiler :as core-compiler])

(println "=== Debug Element Config Lookup ===\n")

(def context (compiler/build-context {} {:project-name "dashboard-example" :platform-stack [:html :css :htmx]}))

(def platform-stack (get context :platform-stack [:html :css :htmx]))
(def platform-configs (compiler/load-platform-stack-memo platform-stack "dashboard-example"))

(println "1. Platform configs loaded:")
(doseq [config platform-configs]
  (println "   Platform:" (:platform config))
  (println "   Elements count:" (count (:elements config)))
  (println "   Sample elements:" (take 10 (keys (:elements config)))))

(println "\n2. Testing element lookups:")
(def test-elements [:header :table :thead :tbody :tr :th :td :button :div])

(doseq [element-type test-elements]
  (let [html-config (first (filter #(= (:platform %) :html) platform-configs))
        element-config (get-in html-config [:elements element-type])]
    (if element-config
      (println "   ✓" element-type "→" (get element-config :element "div"))
      (println "   ✗" element-type "→ NOT FOUND"))))

(println "\n3. Testing actual compilation:")
(def header-element [:header {:class "header"} [:h1 {} "Test"]])
(def parsed (core-compiler/parse-element compiler/forma-compiler header-element))
(println "   Parsed:" parsed)
(println "   Type:" (:type parsed))

(def html-config (first (filter #(= (:platform %) :html) platform-configs)))
(def element-config (get-in html-config [:elements (:type parsed)]))
(println "   Element config found:" (some? element-config))
(if element-config
  (println "   Element config:" (select-keys element-config [:element :class-attr :content-source])))

