(require '[forma.compiler :as compiler])
(require '[kora.core.compiler :as core-compiler])
(require '[hiccup.core :as h])

(println "=== Testing Single Button ===\n")

(def button-element
  [:button {:class "btn" :hx-get "/api/refresh" :hx-target ".main-content" :hx-swap "outerHTML"}
   "Refresh Data"])

(def context (compiler/build-context {} {:project-name "dashboard-example" :platform-stack [:html :css :htmx]}))

(println "Compiling button...")
(def compiled (core-compiler/compile-with-pipeline compiler/forma-compiler button-element context compiler/forma-hierarchy-levels))

(println "Compiled:" compiled)
(println "\nHTML:" (h/html compiled))
(println "\nHas hx-get?" (some #(= % :hx-get) (keys (second compiled))))
(println "Has class?" (some #(= % :class) (keys (second compiled))))

