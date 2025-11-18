(ns forma.components.line-items
  "Line items component for RepairShopr-style invoice/quote management"
  (:require [hiccup.core :as h]
            [clojure.string :as str]))

(defn calculate-line-total
  "Calculate total for a line item"
  [item]
  (let [quantity (or (:quantity item) 0)
        price (or (:price item) 0)
        discount (or (:discount item) 0)]
    (- (* quantity price) discount)))

(defn render-line-item
  "Render a single line item row"
  [item index resource-key request]
  (let [item-id (:id item)
        update-url (str "/" (name resource-key) "/line-items/" item-id)
        delete-url (str "/" (name resource-key) "/line-items/" item-id)]
    [:tr {:class "line-item-row"
          :data-id item-id
          :data-index index}
     [:td {:class "line-item-description"}
      [:input {:type "text"
               :value (:description item)
               :placeholder "Description"
               :class "form-input"
               :name "description"
               :hx-patch update-url
               :hx-trigger "change delay:500ms"
               :hx-target "#line-items-total"
               :hx-swap "outerHTML"}]]
     [:td {:class "line-item-quantity"}
      [:input {:type "number"
               :value (:quantity item)
               :placeholder "0"
               :min "0"
               :step "0.01"
               :class "form-input"
               :name "quantity"
               :hx-patch update-url
               :hx-trigger "change delay:500ms"
               :hx-target "#line-items-total"
               :hx-swap "outerHTML"}]]
     [:td {:class "line-item-price"}
      [:input {:type "number"
               :value (:price item)
               :placeholder "0.00"
               :min "0"
               :step "0.01"
               :class "form-input"
               :name "price"
               :hx-patch update-url
               :hx-trigger "change delay:500ms"
               :hx-target "#line-items-total"
               :hx-swap "outerHTML"}]]
     [:td {:class "line-item-discount"}
      [:input {:type "number"
               :value (:discount item)
               :placeholder "0.00"
               :min "0"
               :step "0.01"
               :class "form-input"
               :name "discount"
               :hx-patch update-url
               :hx-trigger "change delay:500ms"
               :hx-target "#line-items-total"
               :hx-swap "outerHTML"}]]
     [:td {:class "line-item-total"}
      [:span {:class "line-total-amount"}
       (str "$" (format "%.2f" (calculate-line-total item)))]]
     [:td {:class "line-item-actions"}
      [:button {:class "btn btn-sm btn-danger"
                :hx-delete delete-url
                :hx-trigger "click"
                :hx-target "#line-items-total"
                :hx-swap "outerHTML"
                :hx-confirm "Remove this line item?"}
       "Remove"]]]))

(defn render-line-items
  "Render a complete line items editor with HTMX calculations"
  [resource-key request data props]
  (let [items (:data props)
        add-url (str "/" (name resource-key) "/line-items")
        calculate-url (str "/" (name resource-key) "/calculate")
        subtotal (reduce + (map calculate-line-total items))
        tax-rate (or (:tax-rate props) 0.08)
        tax-amount (* subtotal tax-rate)
        total (+ subtotal tax-amount)]
    
    [:div {:class "line-items-container"}
     [:div {:class "line-items-header"}
      [:h3 {:class "line-items-title"} "Line Items"]
      [:button {:class "btn btn-primary"
                :hx-post add-url
                :hx-trigger "click"
                :hx-target "#line-items-table tbody"
                :hx-swap "beforeend"}
       "+ Add Item"]]
     
     [:div {:class "line-items-table-container"}
      [:table {:class "line-items-table"
               :id "line-items-table"}
       [:thead
        [:tr
         [:th {:class "line-item-description"} "Description"]
         [:th {:class "line-item-quantity"} "Qty"]
         [:th {:class "line-item-price"} "Price"]
         [:th {:class "line-item-discount"} "Discount"]
         [:th {:class "line-item-total"} "Total"]
         [:th {:class "line-item-actions"} "Actions"]]]
       [:tbody
        (if (seq items)
          (for [[index item] (map-indexed vector items)]
            (render-line-item item index resource-key request))
          [:tr {:class "line-items-empty"}
           [:td {:colspan 6 :class "empty-state"}
            [:p "No line items added yet"]]])]]]
     
     [:div {:class "line-items-totals"
            :id "line-items-total"}
      [:div {:class "totals-row"}
       [:span {:class "totals-label"} "Subtotal:"]
       [:span {:class "totals-amount"} (str "$" (format "%.2f" subtotal))]]
      [:div {:class "totals-row"}
       [:span {:class "totals-label"} "Tax (" (str (* tax-rate 100)) "%):"]
       [:span {:class "totals-amount"} (str "$" (format "%.2f" tax-amount))]]
      [:div {:class "totals-row totals-total"}
       [:span {:class "totals-label"} "Total:"]
       [:span {:class "totals-amount"} (str "$" (format "%.2f" total))]]]]))