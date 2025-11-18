(ns forma.components.data-table
  "Data table component for RepairShopr-style tables"
  (:require [hiccup.core :as h]
            [clojure.string :as str]))

(defn render-table-body
  "Render only the table body for HTMX partial updates"
  [resource-key request items props]
  (let [columns (:columns props)]
    [:tbody {:id "table-body"}
     (if (seq items)
       (for [item items]
         [:tr {:class "table-row" :data-id (:id item)}
          (for [column columns]
            [:td {:class (str "column-" (name (:field column)))}
             (let [value (get item (:field column))]
               (case (:type column)
                 :actions [:div {:class "row-actions"}
                           [:button {:class "btn btn-sm btn-primary"} "View"]
                           [:button {:class "btn btn-sm btn-secondary"} "Edit"]]
                 :currency (str "$" (format "%.2f" (or value 0)))
                 :date (if value (str value) "-")
                 :boolean (if value "Yes" "No")
                 :status [:span {:class (str "status status-" (name value))} 
                          (str/capitalize (name value))]
                 (str (or value "-"))))])])
       [:tr
        [:td {:colspan (count columns)} 
         [:div {:class "empty-state"}
          [:i {:class "fa fa-inbox"}]
          [:p "No data found"]]]])]))

(defn render-table
  "Render a data table with RepairShopr functionality and HTMX support"
  [resource-key request data props]
  (let [columns (:columns props)
        items (:data props)
        sortable? (:sortable? props)
        filterable? (:filterable? props)
        bulk-actions? (:bulk-actions? props)
        ;; Extract query params for HTMX
        query-params (:query-params request)
        search-q (get query-params "q" "")
        sort-field (get query-params "sort" "")
        sort-dir (get query-params "dir" "asc")
        page (get query-params "page" "1")
        per-page (get query-params "per" "25")
        ;; Build HTMX URLs
        base-url (str "/" (name resource-key))
        search-url (str base-url "?q=" search-q "&sort=" sort-field "&dir=" sort-dir "&page=" page "&per=" per-page)
        bulk-url (str base-url "/bulk")]
    
    [:div {:class "data-table-container"}
     ;; Table header with filters and actions
     (when (or filterable? bulk-actions?)
       [:div {:class "table-controls"}
        (when filterable?
          [:div {:class "table-filters"}
           [:input {:type "text" 
                    :placeholder "Search..." 
                    :class "table-search"
                    :value search-q
                    :hx-get base-url
                    :hx-trigger "keyup changed delay:300ms"
                    :hx-target "#table-body"
                    :hx-swap "outerHTML"
                    :name "q"}]
           [:select {:class "table-filter"
                     :hx-get base-url
                     :hx-trigger "change"
                     :hx-target "#table-body"
                     :hx-swap "outerHTML"
                     :name "status"}
            [:option {:value ""} "All"]
            [:option {:value "active"} "Active"]
            [:option {:value "inactive"} "Inactive"]]])
        (when bulk-actions?
          [:div {:class "bulk-actions"}
           [:button {:class "btn btn-sm btn-secondary"
                     :hx-get base-url
                     :hx-trigger "click"
                     :hx-target "#table-body"
                     :hx-swap "outerHTML"
                     :hx-vals "selectAll=true"} "Select All"]
           [:button {:class "btn btn-sm btn-danger"
                     :hx-delete bulk-url
                     :hx-trigger "click"
                     :hx-target "#table-body"
                     :hx-swap "outerHTML"
                     :hx-confirm "Delete selected items?"
                     :hx-vals "js:{ids: getSelectedIds()}"} "Delete Selected"]])])
     
     ;; Main table
     [:table {:class "data-table"}
      [:thead
       [:tr
        (when bulk-actions?
          [:th {:class "bulk-select"}
           [:input {:type "checkbox" :class "select-all"}]])
        (for [column columns]
          [:th {:class (str "column-" (name (:field column)))
                :data-sortable (when sortable? "true")
                :hx-get (when sortable? base-url)
                :hx-trigger (when sortable? "click")
                :hx-target (when sortable? "#table-body")
                :hx-swap (when sortable? "outerHTML")
                :hx-vals (when sortable? (str "sort=" (name (:field column)) "&dir=" (if (= sort-field (name (:field column))) (if (= sort-dir "asc") "desc" "asc") "asc")))}
           (:header column)
           (when sortable?
             [:span {:class "sort-indicator"} 
              (if (= sort-field (name (:field column)))
                (if (= sort-dir "asc") "↑" "↓")
                "↕")])])]]
      
      [:tbody {:id "table-body"}
       (if (seq items)
         (for [item items]
           [:tr {:class "table-row" :data-id (:id item)}
            (when bulk-actions?
              [:td {:class "bulk-select"}
               [:input {:type "checkbox" :class "row-select" :value (:id item)}]])
            (for [column columns]
              [:td {:class (str "column-" (name (:field column)))}
               (let [value (get item (:field column))]
                 (case (:type column)
                   :actions [:div {:class "row-actions"}
                             [:button {:class "btn btn-sm btn-primary"
                                       :hx-get (str base-url "/" (:id item))
                                       :hx-target "body"
                                       :hx-swap "outerHTML"} "View"]
                             [:button {:class "btn btn-sm btn-secondary"
                                       :hx-get (str base-url "/" (:id item) "/edit")
                                       :hx-target "body"
                                       :hx-swap "outerHTML"} "Edit"]]
                   :currency (str "$" (format "%.2f" (or value 0)))
                   :date (if value (str value) "-")
                   :boolean (if value "Yes" "No")
                   :status [:span {:class (str "status status-" (name value))} 
            (str/capitalize (name value))]
                   (str (or value "-"))))])])
         [:tr
          [:td {:colspan (count columns)} 
           [:div {:class "empty-state"}
            [:i {:class "fa fa-inbox"}]
            [:p "No data found"]]]])]
     
     ;; Table footer with pagination
     [:div {:class "table-footer"}
      [:div {:class "table-info"}
       [:span (str "Showing " (count items) " of " (count items) " entries")]]
      [:div {:class "table-pagination"}
       [:button {:class "btn btn-sm btn-secondary"
                 :disabled (= page "1")
                 :hx-get base-url
                 :hx-trigger "click"
                 :hx-target "#table-body"
                 :hx-swap "outerHTML"
                 :hx-vals (str "page=" (max 1 (dec (Integer/parseInt page))))} "Previous"]
       [:span {:class "page-info"} (str "Page " page " of 1")]
       [:button {:class "btn btn-sm btn-secondary"
                 :disabled true
                 :hx-get base-url
                 :hx-trigger "click"
       :hx-target "#table-body"
       :hx-swap "outerHTML"
                 :hx-vals (str "page=" (inc (Integer/parseInt page)))} "Next"]]]]]))

(defn render-table-styles
  "CSS styles for data table component"
  []
  "
  .data-table-container {
    background: white;
    border-radius: 8px;
    box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    overflow: hidden;
  }
  
  .table-controls {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 16px;
    border-bottom: 1px solid #e5e7eb;
    background: #f9fafb;
  }
  
  .table-filters {
    display: flex;
    gap: 12px;
  }
  
  .table-search {
    padding: 8px 12px;
    border: 1px solid #d1d5db;
    border-radius: 4px;
    font-size: 14px;
  }
  
  .table-filter {
    padding: 8px 12px;
    border: 1px solid #d1d5db;
    border-radius: 4px;
    font-size: 14px;
  }
  
  .bulk-actions {
    display: flex;
    gap: 8px;
  }
  
  .data-table {
    width: 100%;
    border-collapse: collapse;
  }
  
  .data-table th {
    background: #f3f4f6;
    padding: 12px 16px;
    text-align: left;
    font-weight: 600;
    color: #374151;
    border-bottom: 1px solid #e5e7eb;
  }
  
  .data-table th[data-sortable='true'] {
    cursor: pointer;
    user-select: none;
  }
  
  .data-table th[data-sortable='true']:hover {
    background: #e5e7eb;
  }
  
  .sort-indicator {
    margin-left: 8px;
    opacity: 0.5;
  }
  
  .data-table td {
    padding: 12px 16px;
    border-bottom: 1px solid #f3f4f6;
  }
  
  .table-row:hover {
    background: #f9fafb;
  }
  
  .row-actions {
    display: flex;
    gap: 4px;
  }
  
  .status {
    padding: 4px 8px;
    border-radius: 4px;
    font-size: 12px;
    font-weight: 500;
  }
  
  .status-active {
    background: #dcfce7;
    color: #166534;
  }
  
  .status-inactive {
    background: #fee2e2;
    color: #991b1b;
  }
  
  .empty-state {
    text-align: center;
    padding: 40px;
    color: #6b7280;
  }
  
  .empty-state i {
    font-size: 48px;
    margin-bottom: 16px;
    opacity: 0.5;
  }
  
  .table-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 16px;
    border-top: 1px solid #e5e7eb;
    background: #f9fafb;
  }
  
  .table-info {
    color: #6b7280;
    font-size: 14px;
  }
  
  .table-pagination {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  
  .page-info {
    font-size: 14px;
    color: #6b7280;
  }
  
  .btn {
    padding: 6px 12px;
    border: 1px solid #d1d5db;
    border-radius: 4px;
    background: white;
    color: #374151;
    font-size: 14px;
    cursor: pointer;
    text-decoration: none;
    display: inline-block;
  }
  
  .btn:hover {
    background: #f9fafb;
  }
  
  .btn:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
  
  .btn-sm {
    padding: 4px 8px;
    font-size: 12px;
  }
  
  .btn-primary {
    background: #3b82f6;
    color: white;
    border-color: #3b82f6;
  }
  
  .btn-primary:hover {
    background: #2563eb;
  }
  
  .btn-secondary {
    background: #6b7280;
    color: white;
    border-color: #6b7280;
  }
  
  .btn-secondary:hover {
    background: #4b5563;
  }
  
  .btn-danger {
    background: #ef4444;
    color: white;
    border-color: #ef4444;
  }
  
  .btn-danger:hover {
    background: #dc2626;
  }
  ")