(ns forma.dev.user
  "Development utilities for REPL-driven development
  
   Load this namespace to get helper functions for rapid iteration.
   
   Usage:
   (require '[forma.dev.user :as dev])
   (dev/reload-ns 'forma.layout)
   (dev/reload-all)")
  
(defn reload-ns
  "Reload a single namespace"
  [ns-sym]
  (require ns-sym :reload)
  (println (str "Reloaded: " ns-sym)))

(defn reload-all
  "Reload all UI library namespaces"
  []
  (require '[forma.layout] :reload-all
           '[forma.pages] :reload-all
           '[forma.render] :reload-all
           '[forma.compiler] :reload-all
           '[forma.navigation] :reload-all
           '[forma.admin] :reload-all
           '[forma.pos] :reload-all)
  (println "Reloaded all UI namespaces"))

(defn test-layout
  "Test the unified layout function"
  []
  (require '[forma.layout :as layout])
  (layout/unified-layout
   {:title "Test Page"
    :user-roles [:role/admin]
    :current-path "/test"}
   [:div.card
    [:h1 "Layout Test"]
    [:p "This is a test of the unified layout"]]))

(defn help []
  "Show available development commands"
  (println "
Available commands:
  (reload-ns 'forma.layout)  - Reload single namespace
  (reload-all)                     - Reload all UI namespaces
  (test-layout)                    - Test layout rendering
  (help)                           - Show this message

Example usage:
  (require '[forma.dev.user :as dev])
  (dev/reload-all)
  (dev/test-layout)
"))

;; Load this namespace in REPL:
;; (require '[forma.dev.user :as dev])
;; (dev/help)

