(ns forma.platforms.html
  "HTML platform-specific compilation utilities.

   Note: HTML minification is now handled by forma.minification.core
   using EDN-driven operations defined in forma/default/platforms/html.edn"
  (:require [hiccup.core :as h]))

(defn to-html-string
  "Convert Hiccup data structure to HTML string"
  [hiccup-data]
  (if (= (count hiccup-data) 1)
    (h/html (first hiccup-data))
    (h/html hiccup-data)))

