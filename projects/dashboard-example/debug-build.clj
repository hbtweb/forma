(require '[forma.compiler :as compiler])
(require '[kora.core.compiler :as core-compiler])

(println "=== Debug Build ===\n")

;; Test with a simple nested structure
(def test-element
  [:div {}
   [:header {:class "header"}
    [:h1 {} "Dashboard"]
    [:p {} "Welcome back!"]]
   [:div {:class "container"}
    [:div {:class "test"} "Container content"]]])

(def context (compiler/build-context {} {:project-name "dashboard-example" :platform-stack [:html :css :htmx]}))

(println "Parsing element...")
(def parsed (core-compiler/parse-element compiler/forma-compiler test-element))
(println "Parsed:" parsed)
(println "Children count:" (count (:children parsed)))
(println "Children:" (:children parsed))

(println "\nCompiling element...")
(def compiled (core-compiler/compile-with-pipeline compiler/forma-compiler test-element context compiler/forma-hierarchy-levels))
(println "Compiled:" compiled)
(println "Compiled children count:" (count (nthrest compiled 2)))

