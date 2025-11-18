(ns forma.util.style-merge
  "Helpers for merging inline style declarations without duplicate semicolons."
  (:require [clojure.string :as str]))

(defn- trim-trailing-semicolon
  [s]
  (when s
    (-> s
        str
        str/trim
        (str/replace #";\s*$" ""))))

(defn merge-styles
  "Merge explicit and resolved style strings.
  Handles nil/blank values and ensures there is at most one trailing semicolon."
  [explicit resolved]
  (let [explicit* (trim-trailing-semicolon explicit)
        resolved* (trim-trailing-semicolon resolved)
        parts (->> [explicit* resolved*]
                   (remove str/blank?))
        merged (str/join "; " parts)]
    (when-not (str/blank? merged)
      (str merged ";"))))
