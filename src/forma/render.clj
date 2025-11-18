(ns forma.render
  "Unified rendering - consolidates pages.clj + fragments.clj"
  (:require [forma.layout :as layout]
            [forma.server.engine :as engine]
            [forma.components.data-table :as data-table]
            [forma.components.form-ssr :as form]
            [clojure.string :as str]))

;; Re-export for compatibility
(def get-metadata engine/get-metadata)
(def get-list-columns engine/get-list-columns)
(def get-form-fields engine/get-form-fields)

;; Simplified rendering - pages.clj content moved here
(defn dynamic-list-page [resource-key request items & [opts]]
  ;; Uses engine/get-metadata and renders list page with data table
  (let [table-params (data-table/extract-table-params request)
        total-count (or (:total opts) (count items))]
    (layout/unified-layout
      {:title (str (:list-title (engine/get-metadata resource-key)) " - List")
       :user-roles (layout/extract-user-roles request)
       :current-path (:uri request)}
      (data-table/render-table resource-key request items
                               (merge table-params
                                      {:total total-count
                                       :bulk-actions? true})))))

(defn dynamic-detail-page [resource-key request entity & [opts]]
  (layout/unified-layout
    {:title (str (:detail-title (engine/get-metadata resource-key)))
     :user-roles (layout/extract-user-roles request)
     :current-path (:uri request)}
    [:div "Detail page for " (name resource-key)]))

(defn dynamic-form-page [resource-key request mode & [opts]]
  ;; Uses engine/get-form-fields and renders form page
  (let [entity (:entity opts)
        errors (:errors opts)]
    (layout/unified-layout
      {:title (str (if (= mode :new) "New" "Edit") " " (name resource-key))
       :user-roles (layout/extract-user-roles request)
       :current-path (:uri request)}
      (form/render-form resource-key request entity mode
                        (merge opts {:errors errors})))))

