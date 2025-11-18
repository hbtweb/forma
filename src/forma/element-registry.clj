(ns forma.element-registry
  "Element registry for Oxygen DSL elements
  
   Loads and manages element definitions from EDN files.
   Provides element metadata, validation, and rendering hints."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]))

(defn load-element-file
  "Load element definitions from a single EDN file"
  [filename]
  (try
    (-> (str "forma/oxygen/elements/" filename)
        io/resource
        slurp
        edn/read-string)
    (catch Exception e
      (log/warn (str "Could not load element file " filename ": " (.getMessage e)))
      {})))

(defn load-all-elements
  "Load all element definitions from EDN files"
  []
  (let [element-files ["layout.edn" "interactive.edn" "typography.edn" 
                      "forms.edn" "ui.edn" "woocommerce.edn"
                      "blog.edn" "builders.edn" "lists.edn" "navigation.edn"
                      "sliders.edn" "social.edn" "repairshopr.edn"]]
    (reduce
     (fn [registry filename]
       (let [elements (load-element-file filename)]
         (merge registry (get elements :elements {}))))
     {}
     element-files)))

(defn get-element-definition
  "Get element definition by type"
  [element-type]
  (let [registry (load-all-elements)]
    (get registry element-type)))

(defn get-element-categories
  "Get all element categories and their elements"
  []
  (let [element-files ["layout.edn" "interactive.edn" "typography.edn" 
                      "forms.edn" "ui.edn" "woocommerce.edn"
                      "blog.edn" "builders.edn" "lists.edn" "navigation.edn"
                      "sliders.edn" "social.edn" "repairshopr.edn"]]
    (reduce
     (fn [categories filename]
       (let [elements (load-element-file filename)
             category-name (keyword (str/replace filename #"\.edn$" ""))]
         (assoc categories category-name (get elements :elements {}))))
     {}
     element-files)))

(defn list-all-elements
  "Get list of all available element types"
  []
  (keys (load-all-elements)))

(defn validate-element-props
  "Validate element properties against definition"
  [element-type _props]
  (let [element-def (get-element-definition element-type)]
    (if element-def
      ;; TODO: Add property validation logic
      {:valid? true :errors []}
      {:valid? false :errors [(str "Unknown element type: " element-type)]})))

