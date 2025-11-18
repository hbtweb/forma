(ns forma.components.timeline
  "Timeline component for RepairShopr-style activity tracking"
  (:require [hiccup.core :as h]
            [clojure.string :as str]))

(defn render-timeline-item
  "Render a single timeline item"
  [item resource-key request]
  (let [item-id (:id item)
        detail-url (str "/" (name resource-key) "/" item-id)]
    [:div {:class "timeline-item"
           :data-id item-id}
     [:div {:class "timeline-marker"
            :data-type (:type item)}]
     [:div {:class "timeline-content"}
      [:div {:class "timeline-header"}
       [:h4 {:class "timeline-title"} (:title item)]
       [:span {:class "timeline-time"} (:timestamp item)]]
      [:div {:class "timeline-body"}
       [:p {:class "timeline-description"} (:description item)]
       (when (:details item)
         [:div {:class "timeline-details"}
          [:button {:class "btn btn-sm btn-secondary"
                    :hx-get detail-url
                    :hx-trigger "click"
                    :hx-target "body"
                    :hx-swap "outerHTML"}
           "View Details"]])]
      [:div {:class "timeline-footer"}
       [:span {:class "timeline-user"} (:user item)]
       [:span {:class "timeline-type"} (str/capitalize (name (:type item)))]]]]))

(defn render-timeline
  "Render a complete timeline with HTMX lazy loading"
  [resource-key request data props]
  (let [items (:data props)
        filters (:filters props)
        load-more-url (str "/" (name resource-key) "/timeline?offset=" (count items))
        has-more (:has-more props)]
    
    [:div {:class "timeline-container"}
     [:div {:class "timeline-header"}
      [:h2 {:class "timeline-title"} (str (str/capitalize (name resource-key)) " Timeline")]
      [:div {:class "timeline-filters"}
       [:select {:class "timeline-filter"
                 :hx-get (str "/" (name resource-key) "/timeline")
                 :hx-trigger "change"
                 :hx-target "#timeline-items"
                 :hx-swap "outerHTML"
                 :name "type"}
        [:option {:value ""} "All Types"]
        [:option {:value "created"} "Created"]
        [:option {:value "updated"} "Updated"]
        [:option {:value "commented"} "Commented"]
        [:option {:value "assigned"} "Assigned"]]
       [:select {:class "timeline-filter"
                 :hx-get (str "/" (name resource-key) "/timeline")
                 :hx-trigger "change"
                 :hx-target "#timeline-items"
                 :hx-swap "outerHTML"
                 :name "user"}
        [:option {:value ""} "All Users"]
        [:option {:value "john"} "John Doe"]
        [:option {:value "jane"} "Jane Smith"]]]]
     
     [:div {:class "timeline-items"
            :id "timeline-items"}
      (if (seq items)
        (for [item items]
          (render-timeline-item item resource-key request))
        [:div {:class "timeline-empty-state"}
         [:p "No timeline events found"]])]
     
     (when has-more
       [:div {:class "timeline-load-more"}
        [:button {:class "btn btn-primary"
                  :hx-get load-more-url
                  :hx-trigger "click"
                  :hx-target "#timeline-items"
                  :hx-swap "beforeend"
                  :hx-indicator "#loading-spinner"}
         "Load More"]
        [:div {:id "loading-spinner"
               :class "htmx-indicator"
               :style "display: none;"}
         [:span "Loading..."]]])]))
