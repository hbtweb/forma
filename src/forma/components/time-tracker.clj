(ns forma.components.time-tracker
  "Time tracker component for RepairShopr-style time tracking"
  (:require [hiccup.core :as h]
            [clojure.string :as str]))

(defn format-time
  "Format seconds into HH:MM:SS format"
  [seconds]
  (let [hours (quot seconds 3600)
        minutes (quot (mod seconds 3600) 60)
        secs (mod seconds 60)]
    (str (format "%02d:%02d:%02d" hours minutes secs))))

(defn render-time-tracker
  "Render a complete time tracker with HTMX controls"
  [resource-key request data props]
  (let [current-task (:current-task props)
        is-running (:is-running props)
        elapsed-time (:elapsed-time props)
        start-url (str "/" (name resource-key) "/time-tracker/start")
        stop-url (str "/" (name resource-key) "/time-tracker/stop")
        reset-url (str "/" (name resource-key) "/time-tracker/reset")
        update-url (str "/" (name resource-key) "/time-tracker/update")]
    
    [:div {:class "time-tracker-container"}
     [:div {:class "time-tracker-header"}
      [:h3 {:class "time-tracker-title"} "Time Tracker"]
      [:div {:class "time-tracker-status"}
       [:span {:class (str "status-indicator " (if is-running "running" "stopped"))}
        (if is-running "Running" "Stopped")]]]
     
     [:div {:class "time-tracker-display"}
      [:div {:class "timer-display"}
       [:span {:class "timer-time"
               :id "timer-display"
               :hx-get update-url
               :hx-trigger (when is-running "every 1s")
               :hx-target "#timer-display"
               :hx-swap "outerHTML"}
        (format-time elapsed-time)]]
      [:div {:class "timer-task"}
       [:input {:type "text"
                :value (:description current-task)
                :placeholder "What are you working on?"
                :class "form-input"
                :name "description"
                :hx-patch update-url
                :hx-trigger "change delay:500ms"}]]
      [:div {:class "timer-project"}
       [:select {:class "form-select"
                 :name "project"
                 :hx-patch update-url
                 :hx-trigger "change"}
        [:option {:value ""} "Select Project"]
        [:option {:value "project-1"} "Project Alpha"]
        [:option {:value "project-2"} "Project Beta"]
        [:option {:value "project-3"} "Project Gamma"]]]]
     
     [:div {:class "time-tracker-controls"}
      (if is-running
        [:button {:class "btn btn-danger"
                  :hx-post stop-url
                  :hx-trigger "click"
                  :hx-target "#time-tracker-container"
                  :hx-swap "outerHTML"}
         [:i {:class "fa fa-stop"}]
         " Stop"]
        [:button {:class "btn btn-success"
                  :hx-post start-url
                  :hx-trigger "click"
                  :hx-target "#time-tracker-container"
                  :hx-swap "outerHTML"}
         [:i {:class "fa fa-play"}]
         " Start"])
      [:button {:class "btn btn-secondary"
                :hx-post reset-url
                :hx-trigger "click"
                :hx-target "#time-tracker-container"
                :hx-swap "outerHTML"}
       [:i {:class "fa fa-refresh"}]
       " Reset"]]
     
     [:div {:class "time-tracker-history"}
      [:h4 {:class "history-title"} "Recent Sessions"]
      [:div {:class "history-list"
             :id "history-list"
             :hx-get (str "/" (name resource-key) "/time-tracker/history")
             :hx-trigger "load"
             :hx-target "#history-list"
             :hx-swap "outerHTML"}
       [:div {:class "history-loading"}
        [:span "Loading history..."]]]]]))
