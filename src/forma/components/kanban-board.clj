(ns forma.components.kanban-board
  "Kanban board component for RepairShopr-style task management"
  (:require [hiccup.core :as h]
            [clojure.string :as str]))

(defn render-kanban-column
  "Render a single kanban column"
  [column-name items resource-key request]
  (let [column-id (str "column-" (str/lower-case (str/replace column-name #" " "-")))
        add-url (str "/" (name resource-key) "/new?status=" (str/lower-case column-name))
        move-url (str "/" (name resource-key) "/move")]
    [:div {:class "kanban-column"
           :id column-id
           :data-column column-name}
     [:div {:class "kanban-column-header"}
      [:h3 {:class "kanban-column-title"} column-name]
      [:span {:class "kanban-count"} (count items)]
      [:button {:class "btn btn-sm btn-primary"
                :hx-get add-url
                :hx-trigger "click"
                :hx-target "#kanban-board"
                :hx-swap "outerHTML"}
       "+ Add"]]
     
     [:div {:class "kanban-cards"
            :hx-post move-url
            :hx-trigger "drop"
            :hx-target "#kanban-board"
            :hx-swap "outerHTML"
            :hx-vals (str "js:{column: '" column-name "'}")}
      (if (seq items)
        (for [item items]
          [:div {:class "kanban-card"
                 :draggable "true"
                 :data-id (:id item)
                 :hx-get (str "/" (name resource-key) "/" (:id item))
                 :hx-trigger "click"
                 :hx-target "body"
                 :hx-swap "outerHTML"}
           [:div {:class "kanban-card-header"}
            [:h4 {:class "kanban-card-title"} (:title item)]
            [:span {:class "kanban-priority" 
                    :data-priority (:priority item)}
             (str/capitalize (name (:priority item)))]]
           [:div {:class "kanban-card-body"}
            [:p {:class "kanban-card-description"} (:description item)]]
           [:div {:class "kanban-card-footer"}
            [:span {:class "kanban-assignee"} (:assignee item)]
            [:span {:class "kanban-due-date"} (:due-date item)]]])
        [:div {:class "kanban-empty-state"}
         [:p "No items in this column"]])]]))

(defn render-kanban-board
  "Render a complete kanban board with HTMX drag/drop support"
  [resource-key request data props]
  (let [columns (:columns props)
        items (:data props)
        ;; Group items by status/column
        grouped-items (group-by :status items)
        ;; Default columns if not provided
        default-columns ["To Do" "In Progress" "Review" "Done"]
        column-names (or columns default-columns)]
    
    [:div {:class "kanban-board-container"
           :id "kanban-board"}
     [:div {:class "kanban-board-header"}
      [:h2 {:class "kanban-board-title"} (str (str/capitalize (name resource-key)) " Board")]
      [:div {:class "kanban-board-actions"}
       [:button {:class "btn btn-primary"
                 :hx-get (str "/" (name resource-key) "/new")
                 :hx-trigger "click"
                 :hx-target "body"
                 :hx-swap "outerHTML"}
        "New Item"]
       [:button {:class "btn btn-secondary"
                 :hx-get (str "/" (name resource-key))
                 :hx-trigger "click"
                 :hx-target "body"
                 :hx-swap "outerHTML"}
        "List View"]]]
     
     [:div {:class "kanban-columns"}
      (for [column-name column-names]
        (render-kanban-column column-name 
                             (get grouped-items (str/lower-case column-name) [])
                             resource-key 
                             request))]]))

(defn render-kanban-styles
  "CSS styles for kanban board component"
  []
  "
  .kanban-board-container {
    background: white;
    border-radius: 8px;
    box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    padding: 24px;
    overflow-x: auto;
  }
  
  .kanban-board-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 24px;
    padding-bottom: 16px;
    border-bottom: 1px solid #e5e7eb;
  }
  
  .kanban-board-title {
    margin: 0;
    color: #374151;
    font-size: 24px;
    font-weight: 600;
  }
  
  .kanban-board-actions {
    display: flex;
    gap: 12px;
  }
  
  .kanban-columns {
    display: flex;
    gap: 16px;
    min-height: 500px;
  }
  
  .kanban-column {
    flex: 1;
    min-width: 280px;
    background: #f9fafb;
    border-radius: 8px;
    padding: 16px;
    border: 1px solid #e5e7eb;
  }
  
  .kanban-column-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
    padding-bottom: 12px;
    border-bottom: 1px solid #d1d5db;
  }
  
  .kanban-column-title {
    margin: 0;
    color: #374151;
    font-size: 16px;
    font-weight: 600;
  }
  
  .kanban-count {
    background: #e5e7eb;
    color: #6b7280;
    padding: 4px 8px;
    border-radius: 12px;
    font-size: 12px;
    font-weight: 500;
  }
  
  .kanban-cards {
    min-height: 400px;
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
  
  .kanban-card {
    background: white;
    border-radius: 6px;
    padding: 16px;
    box-shadow: 0 1px 3px rgba(0,0,0,0.1);
    cursor: pointer;
    transition: all 0.2s;
    border: 1px solid #e5e7eb;
  }
  
  .kanban-card:hover {
    box-shadow: 0 4px 6px rgba(0,0,0,0.1);
    transform: translateY(-1px);
  }
  
  .kanban-card[draggable='true'] {
    cursor: grab;
  }
  
  .kanban-card[draggable='true']:active {
    cursor: grabbing;
  }
  
  .kanban-card-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 8px;
  }
  
  .kanban-card-title {
    margin: 0;
    color: #374151;
    font-size: 14px;
    font-weight: 600;
    line-height: 1.4;
  }
  
  .kanban-priority {
    padding: 2px 6px;
    border-radius: 4px;
    font-size: 10px;
    font-weight: 500;
    text-transform: uppercase;
  }
  
  .kanban-priority[data-priority='high'] {
    background: #fee2e2;
    color: #991b1b;
  }
  
  .kanban-priority[data-priority='medium'] {
    background: #fef3c7;
    color: #92400e;
  }
  
  .kanban-priority[data-priority='low'] {
    background: #d1fae5;
    color: #065f46;
  }
  
  .kanban-card-body {
    margin-bottom: 12px;
  }
  
  .kanban-card-description {
    margin: 0;
    color: #6b7280;
    font-size: 13px;
    line-height: 1.4;
    display: -webkit-box;
    -webkit-line-clamp: 3;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  
  .kanban-card-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 12px;
    color: #9ca3af;
  }
  
  .kanban-assignee {
    font-weight: 500;
  }
  
  .kanban-due-date {
    color: #6b7280;
  }
  
  .kanban-empty-state {
    text-align: center;
    padding: 40px 20px;
    color: #9ca3af;
  }
  
  .kanban-empty-state p {
    margin: 0;
    font-size: 14px;
  }
  
  /* Drag and drop states */
  .kanban-column.drag-over {
    background: #f0f9ff;
    border-color: #3b82f6;
  }
  
  .kanban-card.dragging {
    opacity: 0.5;
    transform: rotate(5deg);
  }
  
  /* Responsive */
  @media (max-width: 768px) {
    .kanban-columns {
      flex-direction: column;
    }
    
    .kanban-column {
      min-width: auto;
    }
  }
  
  .btn {
    padding: 8px 16px;
    border: 1px solid #d1d5db;
    border-radius: 6px;
    background: white;
    color: #374151;
    font-size: 14px;
    font-weight: 500;
    cursor: pointer;
    text-decoration: none;
    display: inline-block;
    transition: all 0.2s;
  }
  
  .btn:hover {
    background: #f9fafb;
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
  
  .btn-sm {
    padding: 4px 8px;
    font-size: 12px;
  }
  ")