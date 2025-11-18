(ns forma.fragments
  "HTML fragments for HTMX - reusable components
   
   Returns HTML snippets (not full pages) for HTMX partial updates.
   Uses section renderers from dynamic_pages.clj"
  (:require [forma.server.resource-engine :as engine]
            [clojure.string :as str]))

;; ============================================================================
;; Fragment Renderers
;; ============================================================================

(defn render-table-fragment
  "Render data table fragment for HTMX"
  [resource-key data request]
  (let [metadata (engine/get-metadata resource-key)
        columns (engine/get-list-columns resource-key)
        items (:items data)]
    [:div.table-container
     [:table.data-table
      [:thead [:tr (for [col columns] [:th {:style {:width (:width col)}} (:header col)])]]
      [:tbody
       (if (empty? items)
         [:tr [:td {:colSpan (count columns) :style {:padding "48px" :text-align "center"}}
               [:p "No " (:list-title metadata) " found"]]]
         (for [item items]
           [:tr
            (for [col columns]
              [:td (case (:type col)
                     :money (str "$" (/ (get item (:field col) 0) 100.0))
                     :badge [:span.badge {:class (str "badge-" (name (get item (:field col))))}
                             (str/capitalize (name (get item (:field col) "")))]
                     :boolean (if (get item (:field col)) [:i.fa.fa-check] [:i.fa.fa-xmark])
                     :actions [:div.action-buttons
                               [:a.btn.btn-small {:href (str (:uri request) "/" (:id item))} [:i.fa.fa-eye]]
                               [:a.btn.btn-small {:href (str (:uri request) "/" (:id item) "/edit")} [:i.fa.fa-pencil]]]
                     (str (get item (:field col) "")))])]))]]]))

(defn render-form-fragment
  "Render form fragment for HTMX"
  [resource-key mode data request]
  (let [fields (engine/get-form-fields resource-key)
        entity (:entity data)]
    [:div.form-card
     [:form {:method "POST" :action (:uri request)
             :hx-post (str "/api/v1" (:uri request))
             :hx-target "this"
             :hx-swap "outerHTML"}
      (when (= mode :edit) [:input {:type "hidden" :name "_method" :value "PUT"}])
      [:div.form-fields
       (for [field fields]
         [:div.form-group
          [:label (:label field)]
          [:input.form-control {:name (name (:name field))
                                :type (name (:type field))
                                :value (get entity (:name field))
                                :required (:required field)}]])]
      [:div.form-actions
       [:button.btn.btn-primary {:type "submit"} "Save"]
       [:a.btn.btn-secondary {:href (str "/" (name resource-key))} "Cancel"]]]]]))

(defn render-detail-fragment
  "Render detail card fragment for HTMX"
  [resource-key data _request]
  (let [fields (engine/get-form-fields resource-key)
        entity (:entity data)]
    [:div.detail-card
     [:div.details-grid
      (for [field (take 12 fields)]
        [:div.detail-field
         [:label.detail-label (:label field)]
         [:div.detail-value (str (get entity (:name field) "-"))]])]]))

;; ============================================================================
;; Fragment Dispatcher
;; ============================================================================

(defn render-fragment
  "Render HTML fragment based on action (for HTMX)"
  [resource-key action data request]
  (case action
    :list (render-table-fragment resource-key data request)
    :detail (render-detail-fragment resource-key data request)
    :new (render-form-fragment resource-key :new data request)
    :edit (render-form-fragment resource-key :edit data request)
    ;; Default
    [:div.fragment
     [:p "Fragment for " (name resource-key) " - " (name action)]
     [:pre (pr-str data)]]))

