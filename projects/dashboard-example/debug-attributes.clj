(require '[forma.compiler :as compiler])
(require '[kora.core.compiler :as core-compiler])

(println "=== Debug Attributes ===\n")

(def button-element
  [:button {:class "btn" :hx-get "/api/refresh" :hx-target ".main-content" :hx-swap "outerHTML"}
   "Refresh Data"])

(def context (compiler/build-context {} {:project-name "dashboard-example" :platform-stack [:html :css :htmx]}))

(println "1. Parsing element...")
(def parsed (core-compiler/parse-element compiler/forma-compiler button-element))
(println "   Parsed props:" (:props parsed))

(println "\n2. Loading platform configs...")
(def platform-configs (compiler/load-platform-stack-memo [:html :css :htmx] "dashboard-example"))
(println "   Platform count:" (count platform-configs))
(println "   Platforms:" (map :platform platform-configs))

(println "\n3. Checking HTMX extractor...")
(def htmx-config (first (filter #(= (:platform %) :htmx) platform-configs)))
(println "   HTMX config:" (pr-str (select-keys htmx-config [:platform :compiler])))

(println "\n4. Testing attribute extraction...")
(def props {:class "btn" :hx-get "/api/refresh" :hx-target ".main-content" :hx-swap "outerHTML"})
(def extracted (compiler/extract-by-extractor-config props platform-configs :attributes))
(println "   Extracted attributes:" extracted)

(println "\n5. Testing element-styles...")
(def props-for-styles (dissoc props :class))
(def styled (compiler/element-styles props-for-styles platform-configs))
(println "   Styled attrs:" styled)

