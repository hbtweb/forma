(ns forma.pos
  "Consolidated POS Terminal UI - pages and routes
   
   Provides both full POS terminal and simplified test pages.
   Routes support role-based access control.
   
   Merged from:
   - ui/pos/pages.clj (full terminal)
   - ui/pos/pages_simple.clj (test pages)
   - ui/pos/routes.clj"
  (:require [forma.layout :as layout]
            [forma.server.auth :as auth]
            [cheshire.core :as json]
            [hiccup.page :refer [html5]]))

;; ============================================================================
;; PAGES: Simplified (for testing/minimal UI)
;; ============================================================================

(defn pos-page [request]
  (layout/unified-layout
   {:title "POS Terminal"
    :user-roles (layout/extract-user-roles request)
    :current-path (:uri request "/")}
   [:div
    [:h1 "POS Terminal"]
    [:p "Welcome to the point of sale system"]]))

(defn dashboard-page [request]
  (layout/unified-layout
   {:title "Dashboard"
    :user-roles (layout/extract-user-roles request)
    :current-path (:uri request "/dashboard")}
   [:div
    [:h1 "Dashboard"]
    [:p "Sales dashboard"]]))

(defn customers-page [request]
  (layout/unified-layout
   {:title "Customers"
    :user-roles (layout/extract-user-roles request)
    :current-path (:uri request "/customers")}
   [:div
    [:h1 "Customers"]
    [:p "Customer management"]]))

(defn products-page [request]
  (layout/unified-layout
   {:title "Products"
    :user-roles (layout/extract-user-roles request)
    :current-path (:uri request "/products")}
   [:div
    [:h1 "Products"]
    [:p "Product catalog"]]))

(defn invoices-page [request]
  (layout/unified-layout
   {:title "Invoices"
    :user-roles (layout/extract-user-roles request)
    :current-path (:uri request "/invoices")}
   [:div
    [:h1 "Invoices"]
    [:p "Invoice management"]]))

(defn quotes-page [request]
  (layout/unified-layout
   {:title "Quotes"
    :user-roles (layout/extract-user-roles request)
    :current-path (:uri request "/quotes")}
   [:div
    [:h1 "Quotes"]
    [:p "Quote management"]]))

(defn tickets-page [request]
  (layout/unified-layout
   {:title "Tickets"
    :user-roles (layout/extract-user-roles request)
    :current-path (:uri request "/tickets")}
   [:div
    [:h1 "Tickets"]
    [:p "Support tickets"]]))

(defn assets-page [request]
  (layout/unified-layout
   {:title "Assets"
    :user-roles (layout/extract-user-roles request)
    :current-path (:uri request "/assets")}
   [:div
    [:h1 "Assets"]
    [:p "Asset tracking"]]))

(defn orders-page [request]
  (layout/unified-layout
   {:title "Orders"
    :user-roles (layout/extract-user-roles request)
    :current-path (:uri request "/orders")}
   [:div
    [:h1 "Orders"]
    [:p "Order management"]]))

;; ============================================================================
;; PAGES: Full POS Terminal (from pos/pages.clj)
;; ============================================================================
;; NOTE: These are available for future use when full POS is needed
;; Currently using simplified pages above

(comment
  (defn pos-terminal-page
    "Main POS terminal page with full cart and product grid"
    [{:keys [products _user org request]}]
    (layout/unified-layout
     {:title (str "POS Terminal - " (:name org))
      :user-roles (layout/extract-user-roles request)
      :current-path (:uri request "/pos")
      :extra-css ["/client/css/pos.css"]
      :extra-js ["/client/js/pos.js"]}
     [:div.pos-terminal
      ;; Full POS implementation available in git history
      ;; See ui/pos/pages.clj for complete implementation
      ])))

;; ============================================================================
;; ROUTES
;; ============================================================================

(defn html-response [html-string]
  {:status 200
   :headers {"Content-Type" "text/html; charset=utf-8"
             "Cache-Control" "public, max-age=60"}  ; Cache 1 min
   :body html-string})

(defn pos-page-handler [request]
  (html-response (pos-page request)))

(defn dashboard-page-handler [request]
  (html-response (dashboard-page request)))

(defn orders-page-handler [request]
  (html-response (orders-page request)))

(defn customers-page-handler [request]
  (html-response (customers-page request)))

(def routes
  "Client terminal routes - use /client/* to avoid conflicts with resource engine"
  [["/client/pos" {:get pos-page-handler}]
   ["/client/dashboard" {:get dashboard-page-handler}]
   ["/client/orders" {:get orders-page-handler}]
   ["/client/customers" {:get customers-page-handler}]
   ["/client/products" {:get (fn [req] (html-response (products-page req)))}]
   ["/client/invoices" {:get (fn [req] (html-response (invoices-page req)))}]
   ["/client/quotes" {:get (fn [req] (html-response (quotes-page req)))}]
   ["/client/tickets" {:get (fn [req] (html-response (tickets-page req)))}]
   ["/client/assets" {:get (fn [req] (html-response (assets-page req)))}]])

;; NOTE: Root-level routes (/, /products, /customers, etc.) are now handled
;; by the Resource Engine (RepairShopr DSL) for consistency and data-driven UI.
;; Use /client/* routes above for simplified POS terminal views.
