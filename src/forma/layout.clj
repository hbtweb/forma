(ns forma.layout
  "Unified UI layout for all applications (POS, Admin, Status, Designer)
   
   Provides shared shell with role-aware navigation, minimal JS.
   Now with RepairShopr-style UI and dynamic navigation!"
  (:require [hiccup.page :refer [html5]]
            [forma.stubs.config :as config]
            [forma.navigation :as nav]))

;; ============================================================================
;; Shared Styles
;; ============================================================================

(def shared-styles
  "Global CSS variables and base styles - RepairShopr-inspired styling"
  "
    /* ========================================================================
       REPAIRSHOPR-STYLE UI
       ======================================================================== */
    
    /* Reset & Base */
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { 
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', system-ui, sans-serif;
      font-size: 14px; 
      color: #1f2937; 
      background: #f3f4f6;
      line-height: 1.5;
      overflow-x: hidden;
    }
    
    /* Main Layout Structure */
    .unified-layout { 
      display: flex; 
      flex-direction: column; 
      min-height: 100vh; 
    }
    
    /* ========================================================================
       TOP NAVBAR (Dark Header - RepairShopr style)
       ======================================================================== */
    .unified-nav { 
      background: #2c3e50; 
      color: white; 
      box-shadow: 0 2px 4px rgba(0,0,0,0.15);
      position: sticky;
      top: 0;
      z-index: 1000;
    }
    .unified-nav-inner { 
      max-width: 100%;
      margin: 0 auto; 
      padding: 0 20px;
      display: flex; 
      align-items: center; 
      gap: 24px;
      height: 56px;
    }
    .unified-logo { 
      font-size: 18px; 
      font-weight: 700; 
      color: white; 
      text-decoration: none;
      white-space: nowrap;
    }
    .unified-logo:hover { color: #60a5fa; }
    
    /* ========================================================================
       SUB-NAVBAR (Function Navigation - RepairShopr style)
       ======================================================================== */
    .unified-subnav {
      background: #34495e;
      border-top: 1px solid rgba(255,255,255,0.1);
      box-shadow: 0 2px 4px rgba(0,0,0,0.1);
    }
    .unified-subnav-inner {
      max-width: 100%;
      margin: 0 auto;
      padding: 0 20px;
      display: flex;
      align-items: center;
      gap: 8px;
      height: 48px;
      overflow-x: auto;
      overflow-y: hidden;
    }
    .unified-subnav::-webkit-scrollbar { height: 4px; }
    .unified-subnav::-webkit-scrollbar-track { background: rgba(0,0,0,0.1); }
    .unified-subnav::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.3); border-radius: 2px; }
    
    .unified-nav-link { 
      color: #bdc3c7; 
      text-decoration: none; 
      padding: 10px 16px; 
      border-radius: 4px;
      font-weight: 500;
      font-size: 13px;
      transition: all 0.2s;
      display: flex;
      align-items: center;
      gap: 8px;
      white-space: nowrap;
    }
    .unified-nav-link i { font-size: 14px; }
    .unified-nav-link:hover { 
      background: rgba(255,255,255,0.1); 
      color: white; 
    }
    .unified-nav-link.active { 
      background: rgba(52,152,219,0.3); 
      color: #3498db; 
      font-weight: 600;
    }
    .unified-nav-link.active-child { 
      background: rgba(52,152,219,0.15); 
      color: #ecf0f1;
    }
    
    /* Dropdown Menu */
    .nav-dropdown { position: relative; }
    .nav-dropdown-toggle {
      color: #bdc3c7;
      background: none;
      border: none;
      padding: 10px 16px;
      font-weight: 500;
      font-size: 13px;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 8px;
      white-space: nowrap;
    }
    .nav-dropdown-toggle:hover { background: rgba(255,255,255,0.1); color: white; }
    .nav-dropdown-toggle.active-child { 
      background: rgba(52,152,219,0.15); 
      color: #ecf0f1; 
    }
    .nav-dropdown-menu {
      display: none;
      position: absolute;
      top: 100%;
      left: 0;
      background: white;
      border-radius: 6px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.15);
      min-width: 200px;
      margin-top: 4px;
      z-index: 1001;
    }
    .nav-dropdown:hover .nav-dropdown-menu { display: block; }
    .nav-dropdown-item {
      display: block;
      padding: 10px 16px;
      color: #374151;
      text-decoration: none;
      font-size: 13px;
      border-bottom: 1px solid #f3f4f6;
    }
    .nav-dropdown-item:hover { background: #f9fafb; }
    .nav-dropdown-item:last-child { border-bottom: none; }
    
    /* User Menu (right side) */
    .unified-nav-meta { 
      display: flex; 
      gap: 12px; 
      align-items: center;
      font-size: 12px;
      color: #95a5a6;
      margin-left: auto;
    }
    .unified-nav-meta span { display: flex; gap: 4px; align-items: center; }
    .unified-nav-meta .label { color: #7f8c8d; }
    .unified-nav-meta i { font-size: 11px; }
    
    /* ========================================================================
       MAIN CONTENT AREA (White background)
       ======================================================================== */
    .unified-main { 
      flex: 1; 
      max-width: 100%; 
      width: 100%; 
      margin: 0 auto; 
      padding: 24px 20px;
      background: #f3f4f6;
    }
    .unified-content-wrapper {
      max-width: 1400px;
      margin: 0 auto;
    }
    
    /* ========================================================================
       FOOTER (RepairShopr style)
       ======================================================================== */
    .unified-footer {
      background: #2c3e50;
      color: #95a5a6;
      padding: 16px 20px;
      text-align: center;
      font-size: 12px;
      border-top: 1px solid rgba(0,0,0,0.1);
    }
    .unified-footer a {
      color: #3498db;
      text-decoration: none;
    }
    .unified-footer a:hover {
      text-decoration: underline;
    }
    
    /* ========================================================================
       UTILITY CLASSES (RepairShopr-inspired)
       ======================================================================== */
    .card { 
      background: white; 
      padding: 24px; 
      border-radius: 6px; 
      box-shadow: 0 1px 3px rgba(0,0,0,0.08);
      margin-bottom: 20px;
      border: 1px solid #e5e7eb;
    }
    .card h2 { 
      font-size: 16px; 
      font-weight: 600; 
      color: #1f2937; 
      margin-bottom: 16px;
      border-bottom: 1px solid #f3f4f6;
      padding-bottom: 12px;
    }
    
    /* Buttons */
    .btn { 
      padding: 8px 16px; 
      border-radius: 4px; 
      font-size: 13px; 
      font-weight: 600; 
      cursor: pointer; 
      border: 1px solid transparent; 
      transition: all 0.2s;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      text-decoration: none;
      line-height: 1.5;
    }
    .btn i { font-size: 12px; }
    .btn-primary { background: #3498db; color: white; border-color: #3498db; }
    .btn-primary:hover { background: #2980b9; border-color: #2980b9; }
    .btn-secondary { background: #ecf0f1; color: #2c3e50; border-color: #bdc3c7; }
    .btn-secondary:hover { background: #bdc3c7; }
    .btn-success { background: #27ae60; color: white; border-color: #27ae60; }
    .btn-success:hover { background: #229954; }
    .btn-danger { background: #e74c3c; color: white; border-color: #e74c3c; }
    .btn-danger:hover { background: #c0392b; }
    .btn-warning { background: #f39c12; color: white; border-color: #f39c12; }
    .btn-warning:hover { background: #e67e22; }
    .btn-sm { padding: 6px 12px; font-size: 12px; }
    .btn-xs { padding: 4px 8px; font-size: 11px; }
    .btn-block { width: 100%; justify-content: center; }
    
    /* FontAwesome Icons (CDN loaded separately) */
    .fa, .fas, .far, .fab, .fal, .fad { margin-right: 4px; }
    
    /* Responsive */
    @media (max-width: 768px) {
      .unified-nav-inner { padding: 0 12px; gap: 12px; }
      .unified-subnav-inner { padding: 0 12px; }
      .unified-main { padding: 16px 12px; }
      .card { padding: 16px; }
    }
  ")

;; ============================================================================
;; Navigation Builders
;; ============================================================================

(defn build-nav-links
  "Build navigation links using new navigation system
   
   DEPRECATED: Use (nav/build-navigation user-roles current-path) directly
   Kept for backwards compatibility"
  [user-roles current-path]
  (nav/build-navigation user-roles current-path))

;; ============================================================================
;; Layout Components
;; ============================================================================

(defn render-nav-item
  "Render a single navigation item (handles regular items and dropdowns)"
  [item]
  (if (:dropdown item)
    ;; Dropdown navigation item
    [:div.nav-dropdown {:key (:id item)}
     [:button.nav-dropdown-toggle {:class (when (:active-child? item) "active-child")}
      (when (:icon item) [:i {:class (str "fa " (:icon item))}])
      (:label item)
      [:i.fa.fa-chevron-down {:style {:font-size "10px" :margin-left "4px"}}]]
     [:div.nav-dropdown-menu
      (for [child (:children item)]
        [:a.nav-dropdown-item {:href (:path child)
                               :key (:id child)}
         (:label child)])]]
    
    ;; Regular navigation item
    [:a.unified-nav-link {:href (:path item)
                          :key (:id item)
                          :class (cond
                                   (:active? item) "active"
                                   (:active-child? item) "active-child"
                                   :else nil)}
     (when (:icon item) [:i {:class (str "fa " (:icon item))}])
     (:label item)]))

(defn unified-navbar
  "Render unified navigation bar with RepairShopr-style layout
   
   Two-tier navigation:
   - Top navbar: Logo + user menu + meta
   - Sub navbar: Function navigation (Customers, Tickets, etc.)"
  [{:keys [user-roles current-path]}]
  (let [nav-items (build-nav-links user-roles current-path)]
    [:div
     ;; Top Navbar (Brand + User Menu)
     [:nav.unified-nav
      [:div.unified-nav-inner
       [:a.unified-logo {:href "/"} "Corebase"]
       [:div.unified-nav-meta
        [:span [:i.fa.fa-server] [:span.label "env:"] [:span (or (System/getenv "ENV") "dev")]]
        [:span [:i.fa.fa-cog] [:span.label "mode:"] [:span (name (config/mode))]]
        [:span [:i.fa.fa-plug] [:span.label "port:"] [:span (str (config/server-port))]]]]]
     
     ;; Sub Navbar (Function Navigation)
     (when (seq nav-items)
       [:div.unified-subnav
        [:div.unified-subnav-inner
         (for [item nav-items]
           (render-nav-item item))]])]))

(defn unified-footer
  "Render unified footer with RepairShopr-style"
  []
  [:footer.unified-footer
   [:div
    "Corebase " [:span {:style {:color "#7f8c8d"}} "·"]
    " Built with " [:i.fa.fa-heart {:style {:color "#e74c3c"}}]
    " using Clojure & Datomic " [:span {:style {:color "#7f8c8d"}} "·"]
    " " [:a {:href "/admin" :target "_blank"} "Admin"]
    " " [:a {:href "/admin/status" :target "_blank"} "Status"]]])

(defn unified-layout
  "Main unified layout wrapper with RepairShopr-style UI
   
   Args:
     opts - Map with:
       :title - Page title
       :user-roles - Vector of role keywords (e.g. [:role/pos :role/admin])
       :current-path - Current request path for active nav highlighting
       :extra-css - Optional vector of CSS file paths
       :extra-js - Optional vector of JS file paths
       :show-footer - Show footer? (default: true)
     body - Page body content (Hiccup)"
  [{:keys [title user-roles current-path extra-css extra-js show-footer]
    :or {show-footer true}} & body]
  (html5
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    [:title (str title " - Corebase")]
    ;; FontAwesome 6 (for icons)
    [:link {:rel "stylesheet" 
            :href "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css"
            :integrity "sha512-iecdLmaskl7CVkqkXNQ/ZH/XLlvWZOJyj7Yy7tcenmpD1ypASozpmT/E0iPtmFIB46ZmdtAc9eNBvH0H/ZpiBw=="
            :crossorigin "anonymous"
            :referrerpolicy "no-referrer"}]
    ;; Shared styles
    [:style shared-styles]
    ;; Component CSS
    [:link {:rel "stylesheet" :href "/css/data-table.css"}]
    [:link {:rel "stylesheet" :href "/css/forms.css"}]
    ;; Load extra CSS
    (for [css-path extra-css]
      [:link {:rel "stylesheet" :href css-path :key css-path}])
    ;; HTMX for progressive enhancement
    [:script {:src "https://unpkg.com/htmx.org@1.9.10" :defer false}]]
   [:body
    [:div.unified-layout
     (unified-navbar {:user-roles user-roles :current-path current-path})
     [:main.unified-main
      [:div.unified-content-wrapper
       body]]
     (when show-footer
       (unified-footer))]
    ;; Component JS
    [:script {:src "/js/htmx-table.js" :defer true}]
    [:script {:src "/js/htmx-forms.js" :defer true}]
    ;; Load extra JS
    (for [js-path extra-js]
      [:script {:src js-path :defer true :key js-path}])]))

;; ============================================================================
;; Helper: Extract User Roles from Request
;; ============================================================================

(defn extract-user-roles
  "Extract user roles from request identity.
   For now, returns all roles for dev (no auth yet).
   
   TODO: Wire real auth and extract from JWT/session."
  [request]
  (if-let [identity (:identity request)]
    (if-let [role (:role identity)]
      [role]
      ;; Dev fallback: grant all roles
      [:role/pos :role/admin :role/devops :role/designer :role/superadmin])
    ;; No identity: grant all roles for dev
    [:role/pos :role/admin :role/devops :role/designer :role/superadmin]))

