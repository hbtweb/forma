(ns forma.components.stats-cards
  "Stats cards component for RepairShopr-style metrics display"
  (:require [hiccup.core :as h]
            [clojure.string :as str]))

(defn render-stat-card
  "Render a single stat card"
  [stat resource-key request]
  (let [card-id (:id stat)
        refresh-url (str "/" (name resource-key) "/stats/" card-id)]
    [:div {:class "stat-card"
           :data-stat card-id}
     [:div {:class "stat-card-header"}
      [:h4 {:class "stat-title"} (:title stat)]
      [:button {:class "btn-refresh"
                :hx-get refresh-url
                :hx-trigger "click"
                :hx-target (str "#stat-value-" card-id)
                :hx-swap "outerHTML"}
       [:i {:class "fa fa-refresh"}]]]
     [:div {:class "stat-card-body"}
      [:div {:class "stat-value"
             :id (str "stat-value-" card-id)
             :hx-get refresh-url
             :hx-trigger (str "every " (or (:refresh-interval stat) 30) "s")
             :hx-target (str "#stat-value-" card-id)
             :hx-swap "outerHTML"}
       [:span {:class "stat-number"} (:value stat)]
       [:span {:class "stat-unit"} (:unit stat)]]
      [:div {:class "stat-change"}
       [:span {:class (str "change-indicator " 
                           (cond
                             (pos? (:change stat)) "positive"
                             (neg? (:change stat)) "negative"
                             :else "neutral"))}
        (when (not= (:change stat) 0)
          (str (if (pos? (:change stat)) "+" "") (:change stat) "%"))]
       [:span {:class "change-period"} (:change-period stat)]]]
     [:div {:class "stat-card-footer"}
      [:p {:class "stat-description"} (:description stat)]]]))

(defn render-stats-cards
  "Render a complete stats cards grid with HTMX live updates"
  [resource-key request data props]
  (let [stats (:data props)
        refresh-all-url (str "/" (name resource-key) "/stats")]
    
    [:div {:class "stats-cards-container"}
     [:div {:class "stats-cards-header"}
      [:h2 {:class "stats-title"} (str (str/capitalize (name resource-key)) " Statistics")]
      [:div {:class "stats-controls"}
       [:button {:class "btn btn-secondary"
                 :hx-get refresh-all-url
                 :hx-trigger "click"
                 :hx-target "#stats-grid"
                 :hx-swap "outerHTML"}
        [:i {:class "fa fa-refresh"}]
        " Refresh All"]
       [:select {:class "stats-filter"
                 :hx-get refresh-all-url
                 :hx-trigger "change"
                 :hx-target "#stats-grid"
                 :hx-swap "outerHTML"
                 :name "period"}
        [:option {:value "today"} "Today"]
        [:option {:value "week"} "This Week"]
        [:option {:value "month"} "This Month"]
        [:option {:value "year"} "This Year"]]]]
     
     [:div {:class "stats-grid"
            :id "stats-grid"}
      (if (seq stats)
        (for [stat stats]
          (render-stat-card stat resource-key request))
        [:div {:class "stats-empty-state"}
         [:p "No statistics available"]])]]))
