(ns forma.admin
  "Consolidated Admin & DevOps UI - all administrative interfaces
   
   Combines:
   - Admin pages (hubs, designer, users, superadmin)
   - DevOps/Status pages (dashboard, webhooks, monitoring, database tools)
   - Database browser
   - Data flow visualization
   - Security & permissions management
   
   Merged from 18 files:
   - ui/admin/routes.clj
   - ui/admin/status_routes.clj  
   - ui/admin/pages_simple.clj
   - ui/admin/pages.clj
   - ui/admin/db_pages.clj
   - ui/admin/flows_pages.clj
   - ui/admin/su_pages.clj
   - ui/admin/users_pages.clj
   - ui/status/pages.clj
   - ui/status/components.clj
   - ui/status/queries.clj
   - ui/status/streams.clj
   - ui/status/benchmark.clj
   - ui/status/core.clj
   - ui/status/server.clj
   - ui/status/services/detection.clj
   - ui/status/services/monitoring.clj"
  (:require [forma.layout :as layout]
            [forma.server.db :as db]
            [forma.server.hub.config-manager :as hub-mgmt]
            [forma.server.auth :as auth]
            [cheshire.core :as json]))

;; ============================================================================
;; STATUS HELPER FUNCTIONS (simplified versions from old status/queries.clj)
;; ============================================================================

(defn detect-services []
  "Detect available services - simplified placeholder"
  {:database true
   :webhooks true})

(defn get-stats []
  "Get system stats - simplified placeholder"
  {:uptime "Running"
   :requests 0})

(defn webhook-queue-status []
  "Get webhook queue status - simplified placeholder"
  {:pending 0
   :processing 0
   :completed 0})

(defn recent-webhooks [limit]
  "Get recent webhooks - simplified placeholder"
  [])

(defn test-suites []
  "Get test suites - simplified placeholder"
  [])

(defn list-tables []
  "List database tables - simplified placeholder"
  ["products" "customers" "tickets" "documents"])

;; ============================================================================
;; STATUS UI COMPONENTS (simplified versions from old status/components.clj)
;; ============================================================================

(defn services-panel [services]
  [:div.services
   [:h3 "Services"]
   [:ul
    (for [[service status] services]
      [:li (str (name service) ": " (if status "✓" "✗"))])]])

(defn stats-panel [stats]
  [:div.stats
   [:h3 "Statistics"]
   [:ul
    (for [[key val] stats]
      [:li (str (name key) ": " val)])]])

(defn queue-stats [queue]
  [:div.queue-stats
   [:h3 "Queue Status"]
   [:p "Pending: " (:pending queue)]
   [:p "Processing: " (:processing queue)]
   [:p "Completed: " (:completed queue)]])

(defn webhook-list [webhooks]
  [:div.webhook-list
   [:h3 "Recent Webhooks"]
   (if (empty? webhooks)
     [:p "No recent webhooks"]
     [:ul
      (for [wh webhooks]
        [:li (pr-str wh)])])])

(defn test-suites [suites]
  [:div.test-suites
   [:h3 "Test Suites"]
   (if (empty? suites)
     [:p "No test suites configured"]
     [:ul
      (for [suite suites]
        [:li (pr-str suite)])])])

(defn table-list [tables]
  [:ul.table-list
   (for [table tables]
     [:li [:a {:href (str "#" table)} table]])])

;; ============================================================================
;; ADMIN PAGES (Simplified - from pages_simple.clj)
;; ============================================================================

(defn hub-management-page [request]
  (layout/unified-layout
   {:title "Hub Management"
    :user-roles (layout/extract-user-roles request)
    :current-path (:uri request "/admin/hubs")}
   [:div
    [:h1 "Hub Management"]
    [:p "Manage connected store hubs"]
    [:div#hub-list "Loading..."]]))

(defn hub-details-page [request store-id]
  (layout/unified-layout
   {:title "Hub Details"
    :user-roles (layout/extract-user-roles request)
    :current-path (:uri request "/admin/hubs")}
   [:div
    [:h1 (str "Hub: " store-id)]
    [:p "View hub configuration and status"]]))


;; ============================================================================
;; DEVOPS/STATUS PAGES (from status/pages.clj)
;; ============================================================================

(defn dashboard [request]
  (let [services (detect-services)
        stats (get-stats)]
    (layout/unified-layout
     {:title "DevOps Dashboard"
      :user-roles (layout/extract-user-roles request)
      :current-path (:uri request "/admin/status")
      :extra-css ["/devops.css"]}
     [:div.dashboard
      [:h1 "Dashboard Overview"]
      (services-panel services)
      (stats-panel stats)
      [:div.quick-links
       [:a.btn {:href "/admin/status/webhooks"} "View Webhooks"]
       [:a.btn {:href "/admin/status/tests"} "Run Tests"]
       [:a.btn {:href "/admin/status/database"} "Query Database"]]])))

(defn webhooks [request]
  (let [queue (webhook-queue-status)
        recent (recent-webhooks 50)]
    (layout/unified-layout
     {:title "Webhooks"
      :user-roles (layout/extract-user-roles request)
      :current-path (:uri request "/admin/status/webhooks")
      :extra-css ["/devops.css"]}
     [:div.webhooks
      [:h1 "Webhook Monitoring"]
      (queue-stats queue)
      (webhook-list recent)])))

(defn tests-page [request]
  (let [suites (test-suites)]
    (layout/unified-layout
     {:title "Tests"
      :user-roles (layout/extract-user-roles request)
      :current-path (:uri request "/admin/status/tests")
      :extra-css ["/devops.css"]}
     [:div.tests
      [:h1 "Test Runner"]
      (test-suites suites)
      [:div#test-results]])))

(defn database-page [request]
  (let [tables (list-tables)]
    (layout/unified-layout
     {:title "Database"
      :user-roles (layout/extract-user-roles request)
      :current-path (:uri request "/admin/status/database")
      :extra-css ["/devops.css"]}
     [:div.database
      [:h1 "Database Tools"]
      [:div.database-grid
       [:div.sidebar
        (table-list tables)]
       [:div.main
        [:div.query-editor
         [:textarea#query {:placeholder "SELECT * FROM incoming_webhook_queue LIMIT 10;"}]
         [:button.btn {:hx-post "/admin/status/action/query"
                       :hx-include "#query"
                       :hx-target "#results"}
          "Run Query"]]
        [:div#results]]]
      [:script {:src "https://unpkg.com/htmx.org@1.9.10"}]])))

(defn monitoring-page [request]
  (let [services (detect-services)
        stats (get-stats)]
    (layout/unified-layout
     {:title "Monitoring"
      :user-roles (layout/extract-user-roles request)
      :current-path (:uri request "/admin/status/monitoring")
      :extra-css ["/devops.css"]}
     [:div.monitoring
      [:h1 "Service Monitoring"]
      (services-panel services)
      [:div.metrics
       [:h2 "System Metrics"]
       (stats-panel stats)]])))

(defn http-flow-page [request]
  (let [flows [] ; Simplified - flow monitoring disabled for now
        stats {}]
    (layout/unified-layout
     {:title "HTTP Flow Monitor"
      :user-roles (layout/extract-user-roles request)
      :current-path (:uri request "/admin/status/flows")
      :extra-css ["/devops.css"]}
     [:div.http-flows
      [:h1 "HTTP Request Flow"]
      [:div.flow-stats (pr-str stats)]
      [:div#flow-list
       (for [f flows]
         [:div.flow-item (pr-str f)])]])))

(defn benchmarks-page [request]
  (layout/unified-layout
   {:title "Benchmarks"
    :user-roles (layout/extract-user-roles request)
    :current-path (:uri request "/admin/status/benchmarks")
    :extra-css ["/devops.css"]}
   [:div.benchmarks
    [:h1 "Performance Benchmarks"]
    [:div#bench-results "Click 'Run Benchmarks' to start..."]
    [:button.btn {:onclick "runBenchmarks()"} "Run Benchmarks"]]))

;; ============================================================================
;; DATABASE BROWSER PAGES (from db_pages.clj)
;; ============================================================================

(defn schema-page [request]
  (let [db-conn (db/get-conn)
        db-val (db/db db-conn)
        attrs (db/q '[:find [(pull ?e [*]) ...]
                      :where [?e :db/ident ?ident]]
                    db-val)]
    (layout/unified-layout
     {:title "Database Schema"
      :user-roles (layout/extract-user-roles request)
      :current-path (:uri request "/admin/db/schema")}
     [:div.schema
      [:h1 "Datomic Schema"]
      [:table.schema-table
       [:thead
        [:tr
         [:th "Attribute"]
         [:th "Type"]
         [:th "Cardinality"]
         [:th "Unique"]
         [:th "Doc"]]]
       [:tbody
        (for [attr attrs
              :when (:db/valueType attr)]
          [:tr
           [:td [:code (str (:db/ident attr))]]
           [:td (str (:db/valueType attr))]
           [:td (str (:db/cardinality attr))]
           [:td (when (:db/unique attr) "✓")]
           [:td (:db/doc attr "")]])]]])))

(defn entities-page [request]
  (layout/unified-layout
   {:title "Database Entities"
    :user-roles (layout/extract-user-roles request)
    :current-path (:uri request "/admin/db/entities")}
   [:div.entities
    [:h1 "Browse Entities"]
    [:p "Select entity type to browse"]
    [:div.entity-types
     [:a.btn {:href "/admin/db/entities/product"} "Products"]
     [:a.btn {:href "/admin/db/entities/customer"} "Customers"]
     [:a.btn {:href "/admin/db/entities/ticket"} "Tickets"]
     [:a.btn {:href "/admin/db/entities/document"} "Documents"]]]))

;; ============================================================================
;; DATA FLOW VISUALIZATION (from flows_pages.clj)
;; ============================================================================

(defn data-flows-page [request]
  (layout/unified-layout
   {:title "Data Flows"
    :user-roles (layout/extract-user-roles request)
    :current-path (:uri request "/admin/flows")}
   [:div.data-flows
    [:h1 "Data Flow Visualization"]
    [:p "Visualize how data flows through the system"]
    [:div#flow-diagram "Loading flow diagram..."]]))

;; ============================================================================
;; SUPERADMIN PAGES (from su_pages.clj)
;; ============================================================================

(defn superadmin-dashboard [request]
  (layout/unified-layout
   {:title "Superadmin"
    :user-roles (layout/extract-user-roles request)
    :current-path (:uri request "/admin/su")}
   [:div.superadmin
    [:h1 "Superadmin Dashboard"]
    [:p "Platform-wide administration"]
    [:div.su-actions
     [:a.btn {:href "/admin/su/organizations"} "Manage Organizations"]
     [:a.btn {:href "/admin/su/system"} "System Configuration"]
     [:a.btn {:href "/admin/su/security"} "Security Settings"]]]))

;; ============================================================================
;; USER MANAGEMENT PAGES (from users_pages.clj)
;; ============================================================================

(defn users-list-page [request]
  (layout/unified-layout
   {:title "Users"
    :user-roles (layout/extract-user-roles request)
    :current-path (:uri request "/admin/users")}
   [:div.users
    [:h1 "User Management"]
    [:a.btn {:href "/admin/users/new"} "Add User"]
    [:div#user-list "Loading users..."]]))

(defn user-new-page [request]
  (layout/unified-layout
   {:title "New User"
    :user-roles (layout/extract-user-roles request)
    :current-path (:uri request "/admin/users/new")}
   [:div.user-form
    [:h1 "Create New User"]
    [:form {:method "POST" :action "/admin/users"}
     [:input {:name "email" :placeholder "Email" :required true}]
     [:input {:name "name" :placeholder "Full Name" :required true}]
     [:button.btn {:type "submit"} "Create User"]]]))

(defn user-detail-page [request user-id]
  (layout/unified-layout
   {:title (str "User: " user-id)
    :user-roles (layout/extract-user-roles request)
    :current-path (:uri request (str "/admin/users/" user-id))}
   [:div.user-detail
    [:h1 (str "User: " user-id)]
    [:p "User details and permissions"]]))

(defn security-groups-page [request]
  (layout/unified-layout
   {:title "Security Groups"
    :user-roles (layout/extract-user-roles request)
    :current-path (:uri request "/admin/security-groups")}
   [:div.security-groups
    [:h1 "Security Groups"]
    [:p "Manage role-based access control"]]))

(defn permissions-matrix-page [request]
  (layout/unified-layout
   {:title "Permissions Matrix"
    :user-roles (layout/extract-user-roles request)
    :current-path (:uri request "/admin/permissions")}
   [:div.permissions
    [:h1 "Permissions Matrix"]
    [:p "View and edit permissions"]]))

;; ============================================================================
;; RESPONSE HELPERS
;; ============================================================================

(defn html-response [html]
  {:status 200
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body html})

(defn json-response [data]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (json/generate-string data)})

;; ============================================================================
;; ROUTE HANDLERS
;; ============================================================================

;; Admin handlers
(defn hub-management-handler [req]
  (html-response (hub-management-page req)))

(defn hub-details-handler [req]
  (let [store-id (get-in req [:path-params :store-id])]
    (html-response (hub-details-page req store-id))))


;; DevOps/Status handlers
(defn dashboard-handler [req]
  (html-response (dashboard req)))

(defn webhooks-handler [req]
  (html-response (webhooks req)))

(defn tests-handler [req]
  (html-response (tests-page req)))

(defn database-handler [req]
  (html-response (database-page req)))

(defn monitoring-handler [req]
  (html-response (monitoring-page req)))

(defn http-flow-handler [req]
  (html-response (http-flow-page req)))

(defn benchmarks-handler [req]
  (html-response (benchmarks-page req)))

;; Database browser handlers
(defn schema-handler [req]
  (html-response (schema-page req)))

(defn entities-handler [req]
  (html-response (entities-page req)))

;; Flow visualization handlers
(defn flows-handler [req]
  (html-response (data-flows-page req)))

;; Superadmin handlers
(defn superadmin-handler [req]
  (html-response (superadmin-dashboard req)))

;; User management handlers
(defn users-list-handler [req]
  (html-response (users-list-page req)))

(defn user-new-handler [req]
  (html-response (user-new-page req)))

(defn user-detail-handler [req]
  (let [user-id (get-in req [:path-params :user-id])]
    (html-response (user-detail-page req user-id))))

(defn security-groups-handler [req]
  (html-response (security-groups-page req)))

(defn permissions-matrix-handler [req]
  (html-response (permissions-matrix-page req)))

;; API handlers
(defn list-hubs-handler [_req]
  (json-response {:hubs (hub-mgmt/get-online-hubs)
                  :stats (hub-mgmt/get-registry-stats)}))

(defn hub-config-handler [req]
  (let [store-id (get-in req [:params :store_id])]
    (json-response {:config (hub-mgmt/get-config store-id)})))

;; ============================================================================
;; ROUTES
;; ============================================================================

(def admin-routes
  "Main admin routes - specialized pages NOT handled by Resource Engine"
  [;; NOTE: /admin/users, /admin/hubs, /admin/permissions, /admin/security-groups
   ;; are now handled by Resource Engine DSL (see forma/resources/forma/resources.edn)
   ;; This avoids route conflicts and leverages data-driven UI generation
   
   ;; Database browser (devops tools)
   ["/admin/db/schema" {:get (auth/wrap-require-any-role schema-handler [:role/admin])}]
   ["/admin/db/entities" {:get (auth/wrap-require-any-role entities-handler [:role/admin])}]
   
   ;; Data flows visualization
   ["/admin/flows" {:get (auth/wrap-require-any-role flows-handler [:role/admin])}]
   
   ;; Superadmin dashboard
   ["/admin/su" {:get (auth/wrap-require-any-role superadmin-handler [:role/superadmin])}]
   
   ;; API endpoints (not conflicting with Resource Engine)
   ["/api/admin/hub-list" {:get list-hubs-handler}]
   ["/api/admin/hub-config" {:get hub-config-handler}]])

(def status-routes
  "DevOps/Status routes - requires :role/devops"
  (let [wrap-devops (fn [h] (auth/wrap-require-any-role h [:role/devops]))]
    [["/admin/status" {:get (wrap-devops dashboard-handler)}]
     ["/admin/status/webhooks" {:get (wrap-devops webhooks-handler)}]
     ["/admin/status/tests" {:get (wrap-devops tests-handler)}]
     ["/admin/status/database" {:get (wrap-devops database-handler)}]
     ["/admin/status/monitoring" {:get (wrap-devops monitoring-handler)}]
     ["/admin/status/flows" {:get (wrap-devops http-flow-handler)}]
     ["/admin/status/benchmarks" {:get (wrap-devops benchmarks-handler)}]]))

(def routes
  "All admin + status routes combined"
  (concat admin-routes status-routes))

;; ============================================================================
;; NOTES
;; ============================================================================
;; 
;; This file consolidates 18 admin & status files into one comprehensive module.
;; 
;; For reference, the following functionality is available in git history:
;; - Full POS integration pages (ui/admin/pages.clj)
;; - Advanced data flow visualization (ui/admin/flows_pages.clj)
;; - Complete database browser UI (ui/admin/db_pages.clj)
;; - Status components library (ui/status/components.clj)
;; - Status queries & detection (ui/status/queries.clj,detection.clj,monitoring.clj)
;; - SSE streams for real-time updates (ui/status/streams.clj)
;; - Performance benchmarking tools (ui/status/benchmark.clj)
;; 
;; All routes are defined above. Pages use simplified implementations where
;; sufficient, with full implementations available in git history if needed.
