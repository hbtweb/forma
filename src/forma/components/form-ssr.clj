(ns forma.components.form-ssr
  "Server-side rendered form component for RepairShopr"
  (:require [hiccup.core :as h]
            [clojure.string :as str]))

(defn render-field
  "Render a single form field with HTMX validation support"
  [field value errors resource-key mode]
  (let [field-name (:name field)
        field-type (:type field)
        field-label (:label field)
        field-placeholder (:placeholder field)
        field-required (:required field)
        field-options (:options field)
        field-value (or value "")
        field-error (get errors field-name)
        validation-url (str "/" (name resource-key) "/validate")
        auto-save-url (when (= mode :edit)
                        (str "/" (name resource-key) "/" (:id value) "/autosave"))]
    
    [:div {:class "form-field"}
     (when field-label
       [:label {:for field-name :class "form-label"}
        field-label
        (when field-required [:span {:class "required"} " *"])])
     
     (case field-type
       :text [:input {:type "text"
                      :id field-name
                      :name field-name
                      :value field-value
                      :placeholder field-placeholder
                      :required field-required
                      :class (str "form-input" (when field-error " error"))
                      :hx-post validation-url
                      :hx-trigger "blur"
                      :hx-target (str "#err-" field-name)
                      :hx-swap "outerHTML"
                      :hx-vals (str "field=" field-name "&value=" field-value)
                      :hx-patch (when auto-save-url auto-save-url)
                      :hx-trigger (when auto-save-url "change delay:1s")}]
       
       :email [:input {:type "email"
                       :id field-name
                       :name field-name
                       :value field-value
                       :placeholder field-placeholder
                       :required field-required
                       :class (str "form-input" (when field-error " error"))
                       :hx-post validation-url
                       :hx-trigger "blur"
                       :hx-target (str "#err-" field-name)
                       :hx-swap "outerHTML"
                       :hx-vals (str "field=" field-name "&value=" field-value)
                       :hx-patch (when auto-save-url auto-save-url)
                       :hx-trigger (when auto-save-url "change delay:1s")}]
       
       :password [:input {:type "password"
                          :id field-name
                          :name field-name
                          :placeholder field-placeholder
                          :required field-required
                          :class (str "form-input" (when field-error " error"))}]
       
       :number [:input {:type "number"
                        :id field-name
                        :name field-name
                        :value field-value
                        :placeholder field-placeholder
                        :required field-required
                        :class (str "form-input" (when field-error " error"))
                        :hx-post validation-url
                        :hx-trigger "blur"
                        :hx-target (str "#err-" field-name)
                        :hx-swap "outerHTML"
                        :hx-vals (str "field=" field-name "&value=" field-value)
                        :hx-patch (when auto-save-url auto-save-url)
                        :hx-trigger (when auto-save-url "change delay:1s")}]
       
       :textarea [:textarea {:id field-name
                             :name field-name
                             :placeholder field-placeholder
                             :required field-required
                             :class (str "form-textarea" (when field-error " error"))
                             :rows 4
                             :hx-post validation-url
                             :hx-trigger "blur"
                             :hx-target (str "#err-" field-name)
                             :hx-swap "outerHTML"
                             :hx-vals (str "field=" field-name "&value=" field-value)
                             :hx-patch (when auto-save-url auto-save-url)
                             :hx-trigger (when auto-save-url "change delay:1s")}
                  field-value]
       
       :select [:select {:id field-name
                         :name field-name
                         :required field-required
                         :class (str "form-select" (when field-error " error"))
                         :hx-post validation-url
                         :hx-trigger "change"
                         :hx-target (str "#err-" field-name)
                         :hx-swap "outerHTML"
                         :hx-vals (str "field=" field-name "&value=" field-value)
                         :hx-patch (when auto-save-url auto-save-url)
                         :hx-trigger (when auto-save-url "change delay:1s")}
                [:option {:value ""} (str "Select " (or field-label "option"))]
                (for [option field-options]
                  [:option {:value (:value option)
                           :selected (= (:value option) field-value)}
                   (:label option)])]
       
       :checkbox [:div {:class "form-checkbox"}
                  [:input {:type "checkbox"
                           :id field-name
                           :name field-name
                           :checked (boolean field-value)
                           :class (str "form-checkbox-input" (when field-error " error"))
                           :hx-patch (when auto-save-url auto-save-url)
                           :hx-trigger (when auto-save-url "change delay:1s")}]
                  [:label {:for field-name :class "form-checkbox-label"}
                   field-label]]
       
       :radio [:div {:class "form-radio-group"}
               (for [option field-options]
                 [:div {:class "form-radio"}
                  [:input {:type "radio"
                           :id (str field-name "-" (:value option))
                           :name field-name
                           :value (:value option)
                           :checked (= (:value option) field-value)
                           :class (str "form-radio-input" (when field-error " error"))
                           :hx-patch (when auto-save-url auto-save-url)
                           :hx-trigger (when auto-save-url "change delay:1s")}]
                  [:label {:for (str field-name "-" (:value option)) :class "form-radio-label"}
                   (:label option)]])]
       
       :date [:input {:type "date"
                      :id field-name
                      :name field-name
                      :value field-value
                      :required field-required
                      :class (str "form-input" (when field-error " error"))
                      :hx-post validation-url
                      :hx-trigger "blur"
                      :hx-target (str "#err-" field-name)
                      :hx-swap "outerHTML"
                      :hx-vals (str "field=" field-name "&value=" field-value)
                      :hx-patch (when auto-save-url auto-save-url)
                      :hx-trigger (when auto-save-url "change delay:1s")}]
       
       :file [:input {:type "file"
                      :id field-name
                      :name field-name
                      :required field-required
                      :class (str "form-input" (when field-error " error"))}]
       
       ;; Default to text input
       [:input {:type "text"
                :id field-name
                :name field-name
                :value field-value
                :placeholder field-placeholder
                :required field-required
                :class (str "form-input" (when field-error " error"))
                :hx-post validation-url
                :hx-trigger "blur"
                :hx-target (str "#err-" field-name)
                :hx-swap "outerHTML"
                :hx-vals (str "field=" field-name "&value=" field-value)
                :hx-patch (when auto-save-url auto-save-url)
                :hx-trigger (when auto-save-url "change delay:1s")}])
     
     [:div {:id (str "err-" field-name)}
      (when field-error
        [:div {:class "form-error"} field-error])]
     
     (when (:help-text field)
       [:div {:class "form-help"} (:help-text field)])]))

(defn render-form
  "Render a complete form with RepairShopr functionality"
  [resource-key request entity mode props]
  (let [fields (:fields props)
        resource-key (or resource-key (:resource-key props) :unknown)
        form-action (case mode
                      :new (str "/" (name resource-key))
                      :edit (str "/" (name resource-key) "/" (:id entity))
                      "/")
        form-method (case mode
                      :new "POST"
                      :edit "PUT"
                      "POST")]
    
    [:div {:class "form-container"}
     [:form {:action form-action
             :method form-method
             :class "repairshopr-form"
             :enctype "multipart/form-data"}
      
      ;; Hidden fields for edit mode
      (when (= mode :edit)
        [:input {:type "hidden" :name "_method" :value "PUT"}])
      
      ;; Form fields
      [:div {:class "form-fields"}
       (for [field fields]
         (render-field field (get entity (:name field)) {} resource-key mode))]
      
      ;; Form actions
      [:div {:class "form-actions"}
       [:button {:type "submit" :class "btn btn-primary"}
        (case mode
          :new "Create"
          :edit "Update"
          "Save")]
       [:a {:href (str "/" (name resource-key)) :class "btn btn-secondary"}
        "Cancel"]]]]))

(defn render-form-styles
  "CSS styles for form component"
  []
  "
  .form-container {
    background: white;
    border-radius: 8px;
    box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    padding: 24px;
  }
  
  .repairshopr-form {
    max-width: 600px;
  }
  
  .form-fields {
    display: flex;
    flex-direction: column;
    gap: 20px;
  }
  
  .form-field {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }
  
  .form-label {
    font-weight: 600;
    color: #374151;
    font-size: 14px;
  }
  
  .required {
    color: #ef4444;
  }
  
  .form-input,
  .form-textarea,
  .form-select {
    padding: 12px;
    border: 1px solid #d1d5db;
    border-radius: 6px;
    font-size: 14px;
    transition: border-color 0.2s;
  }
  
  .form-input:focus,
  .form-textarea:focus,
  .form-select:focus {
    outline: none;
    border-color: #3b82f6;
    box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
  }
  
  .form-input.error,
  .form-textarea.error,
  .form-select.error {
    border-color: #ef4444;
  }
  
  .form-textarea {
    resize: vertical;
    min-height: 100px;
  }
  
  .form-checkbox {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  
  .form-checkbox-input {
    width: 16px;
    height: 16px;
  }
  
  .form-checkbox-label {
    font-size: 14px;
    color: #374151;
  }
  
  .form-radio-group {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  
  .form-radio {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  
  .form-radio-input {
    width: 16px;
    height: 16px;
  }
  
  .form-radio-label {
    font-size: 14px;
    color: #374151;
  }
  
  .form-error {
    color: #ef4444;
    font-size: 12px;
    margin-top: 4px;
  }
  
  .form-help {
    color: #6b7280;
    font-size: 12px;
    margin-top: 4px;
  }
  
  .form-actions {
    display: flex;
    gap: 12px;
    margin-top: 24px;
    padding-top: 24px;
    border-top: 1px solid #e5e7eb;
  }
  
  .btn {
    padding: 12px 24px;
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
  ")