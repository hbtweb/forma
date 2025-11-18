(require '[forma.compiler :as compiler])
(require '[kora.core.compiler :as core-compiler])

(println "=== Debug Full Pipeline ===\n")

(def button-element
  [:button {:class "btn" :hx-get "/api/refresh" :hx-target ".main-content" :hx-swap "outerHTML"}
   "Refresh Data"])

(def context (compiler/build-context {} {:project-name "dashboard-example" :platform-stack [:html :css :htmx]}))

(println "1. Full pipeline compilation...")
(def compiled (core-compiler/compile-with-pipeline compiler/forma-compiler button-element context compiler/forma-hierarchy-levels))
(println "   Final compiled:" compiled)
(println "   Attrs:" (second compiled))

(println "\n2. Step by step...")
(def parsed (core-compiler/parse-element compiler/forma-compiler button-element))
(println "   Parsed:" parsed)

(def expanded (update parsed :props #(core-compiler/expand-properties compiler/forma-compiler %)))
(println "   Expanded:" expanded)

(def resolved-props (core-compiler/resolve-context expanded context compiler/forma-hierarchy-levels))
(println "   Resolved props:" resolved-props)

(def resolved-element (assoc expanded :props resolved-props))
(println "   Resolved element:" resolved-element)

(def styled-props (core-compiler/apply-styling compiler/forma-compiler resolved-element resolved-props context))
(println "   Styled props:" styled-props)

(def styled-element (assoc resolved-element :props styled-props))
(println "   Styled element:" styled-element)

(def final-compiled (core-compiler/compile-element compiler/forma-compiler styled-element context))
(println "   Final compiled:" final-compiled)

