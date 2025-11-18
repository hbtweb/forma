(require '[forma.compiler :as compiler])
(require '[kora.core.compiler :as core-compiler])

(println "=== Debug Full Compilation ===\n")

(def button-element
  [:button {:class "btn" :hx-get "/api/refresh" :hx-target ".main-content" :hx-swap "outerHTML"}
   "Refresh Data"])

(def context (compiler/build-context {} {:project-name "dashboard-example" :platform-stack [:html :css :htmx]}))

(println "1. Parsing...")
(def parsed (core-compiler/parse-element compiler/forma-compiler button-element))
(println "   Parsed:" parsed)

(println "\n2. Expanding properties...")
(def expanded (core-compiler/expand-properties compiler/forma-compiler (:props parsed)))
(println "   Expanded:" expanded)

(println "\n3. Resolving context...")
(def resolved (kora.core.compiler/resolve-context parsed context compiler/forma-hierarchy-levels))
(println "   Resolved props:" (:props resolved))

(println "\n4. Loading platform configs...")
(def platform-stack (get context :platform-stack [:html :css :htmx]))
(def platform-configs (compiler/load-platform-stack-memo platform-stack "dashboard-example"))
(println "   Platforms:" (map :platform platform-configs))

(println "\n5. Testing apply-platform-compilation for HTML...")
(def html-config (first (filter #(= (:platform %) :html) platform-configs)))
(def element-config (get-in html-config [:elements (:type resolved)]))
(println "   Element config:" (select-keys element-config [:element :class-attr :content-source :children-handling]))

(def resolved-props (:props resolved))
(def classes (get resolved-props :class))
(println "   Classes:" classes)

(def props-for-styles (dissoc resolved-props :class))
(println "   Props for styles:" props-for-styles)

(def styled-attrs (compiler/element-styles props-for-styles platform-configs))
(println "   Styled attrs:" styled-attrs)

(def attrs (merge (when classes {:class classes}) styled-attrs))
(println "   Final attrs:" attrs)

