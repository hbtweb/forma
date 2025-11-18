(ns forma.components.tabbed-detail
  "Tabbed detail component for RepairShopr-style detail views"
  (:require [hiccup.core :as h]
            [clojure.string :as str]))

(defn render-tab-content
  "Render content for a single tab"
  [tab resource-key request]
  (let [tab-id (:id tab)
        content-url (str "/" (name resource-key) "/tabs/" tab-id)]
    [:div {:class "tab-content"
           :id (str "tab-" tab-id)
           :data-tab tab-id}
     [:div {:class "tab-content-body"
            :hx-get content-url
            :hx-trigger "load"
            :hx-target (str "#tab-" tab-id " .tab-content-body")
            :hx-swap "outerHTML"}
      [:div {:class "tab-loading"}
       [:span "Loading content..."]]]]))

(defn render-tabbed-detail
  "Render a complete tabbed detail view with HTMX lazy loading"
  [resource-key request data props]
  (let [tabs (:tabs props)
        active-tab (or (:active-tab props) (first tabs))
        tab-switch-url (str "/" (name resource-key) "/tabs")]
    
    [:div {:class "tabbed-detail-container"}
     [:div {:class "tabbed-detail-header"}
      [:h2 {:class "tabbed-title"} (str (str/capitalize (name resource-key)) " Details")]
      [:div {:class "tabbed-actions"}
       [:button {:class "btn btn-primary"
                 :hx-get (str "/" (name resource-key) "/edit")
                 :hx-trigger "click"
                 :hx-target "body"
                 :hx-swap "outerHTML"}
        "Edit"]
       [:button {:class "btn btn-secondary"
                 :hx-get (str "/" (name resource-key))
                 :hx-trigger "click"
                 :hx-target "body"
                 :hx-swap "outerHTML"}
        "Back to List"]]]
     
     [:div {:class "tabbed-navigation"}
      [:nav {:class "tab-nav"}
       (for [tab tabs]
         [:button {:class (str "tab-btn" (when (= (:id tab) (:id active-tab)) " active"))
                   :data-tab (:id tab)
                   :hx-get tab-switch-url
                   :hx-trigger "click"
                   :hx-target "#tabbed-content"
                   :hx-swap "outerHTML"
                   :hx-vals (str "tab=" (:id tab))}
          [:i {:class (str "fa " (:icon tab))}]
          (:label tab)])]]
     
     [:div {:class "tabbed-content"
            :id "tabbed-content"}
      (if (seq tabs)
        (for [tab tabs]
          (render-tab-content tab resource-key request))
        [:div {:class "tabbed-empty-state"}
         [:p "No tabs available"]])]]))
