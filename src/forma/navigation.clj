(ns forma.navigation
  "Navigation management system with auto-discovery from route registry
   
   Features:
   - Load nav config from navigation.edn
   - Auto-discover routes from route registry
   - Role-based filtering
   - Support for dropdowns and nested items
   - Dynamic addition of new resources"
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; ============================================================================
;; Navigation Config Loading
;; ============================================================================

(def ^:private nav-config-cache (atom nil))

(defn load-nav-config
  "Load navigation config from forma/config/navigation.edn
   Caches result for performance"
  []
  (or @nav-config-cache
      (let [config (try
                     (-> (io/resource "forma/config/navigation.edn")
                         slurp
                         edn/read-string)
                     (catch Exception e
                       (println "WARNING: Could not load navigation config, using defaults:" (.getMessage e))
                       {:nav-items []
                        :settings {:auto-discover false}}))]
        (reset! nav-config-cache config)
        config)))

(defn reload-nav-config!
  "Reload navigation config from disk (for development)"
  []
  (reset! nav-config-cache nil)
  (load-nav-config))

;; ============================================================================
;; Auto-Discovery from Route Registry
;; ============================================================================

(defn detect-nav-group
  "Detect nav group from path using auto-group conventions"
  [path auto-groups]
  (or (some (fn [[prefix _]]
              (when (str/starts-with? path prefix)
                (keyword (str/replace prefix #"[/]" ""))))
            auto-groups)
      :main))

(defn get-default-order
  "Get default order for a nav group"
  [group auto-groups]
  (get-in auto-groups [(str "/" (name group) "/") :order] 100))

(defn discover-routes-from-registry
  "Auto-discover navigable routes from DSL with conventions
   
   Returns vector of discovered nav items ready for navigation"
  []
  (try
    (require 'forma.server.resource-engine)
    (let [get-all-fn (resolve 'forma.server.resource-engine/get-all-resources)
          get-conv-fn (resolve 'forma.server.resource-engine/get-conventions)
          all-resources (when get-all-fn (get-all-fn))
          conventions (when get-conv-fn (get-conv-fn))
          auto-groups (:nav-auto-groups conventions)
          hide-modes (set (:nav-hide-modes conventions #{:config :matrix}))]
      
      (->> all-resources
           ;; Filter: only navigable resources
           (filter (fn [[_k resource-def]]
                     (let [metadata (:metadata resource-def)]
                       (and (:path resource-def)
                            (= :standard (:mode resource-def :standard))
                            (not (hide-modes (:mode resource-def)))
                            (not (:nested-only metadata))))))
           
           ;; Transform to nav items
           (map (fn [[k resource-def]]
                  (let [metadata (:metadata resource-def)
                        path (:path resource-def)
                        detected-group (detect-nav-group path auto-groups)]
                    
                    {:id k
                     :label (or (:nav-label metadata) (:list-title metadata))
                     :icon (:icon metadata)
                     :path path
                     :roles (:permissions resource-def)
                     :order (or (:nav-order metadata) (get-default-order detected-group auto-groups))
                     :group detected-group
                     :auto-discovered true})))
           (vec)))
    (catch Exception e
      (println "Warning: Could not auto-discover routes:" (.getMessage e))
      [])))

;; ============================================================================
;; Navigation Filtering - DSL-ified!
;; ============================================================================

(defn has-role-access?
  "Check if user has access based on required roles"
  [user-roles-set required-roles]
  (or (empty? required-roles)
      (some user-roles-set required-roles)))

(defn filter-nav-items
  "Filter nav items by roles (recursive, handles children automatically)"
  [nav-items user-roles]
  (let [user-roles-set (set user-roles)]
    (filterv
     (fn [item]
       (when (has-role-access? user-roles-set (set (:roles item)))
         (if (:children item)
           ;; Recursively filter children
           (assoc item :children (filter-nav-items (:children item) user-roles))
           item)))
     nav-items)))

;; ============================================================================
;; Active Page Detection
;; ============================================================================

(defn mark-active
  "Mark nav items as active based on current path
   
   Sets :active? true on matching items
   Sets :active-child? true on parents of active children"
  [nav-items current-path]
  (mapv
   (fn [item]
     (let [item-path (:path item)
           ;; Check if this item is active
           item-active? (and item-path
                             (or (= current-path item-path)
                                 (and (not= item-path "/")
                                      (str/starts-with? current-path item-path))))
           ;; Check children
           children-marked (when (:children item)
                             (mark-active (:children item) current-path))
           has-active-child? (some :active? children-marked)]
       
       (cond-> item
         item-active? (assoc :active? true)
         has-active-child? (assoc :active-child? true)
         children-marked (assoc :children children-marked))))
   nav-items))

;; ============================================================================
;; Navigation Grouping (Auto-dropdowns from conventions)
;; ============================================================================

(defn build-dropdown-structure
  "Convert grouped items into dropdown structure based on conventions"
  [grouped-items auto-groups]
  (vec
   (mapcat
    (fn [[group items]]
      (let [group-config (get auto-groups (str "/" (name group) "/"))
            sorted-items (sort-by :order items)]
        
        (if (:dropdown group-config)
          ;; Dropdown group (Admin, Settings)
          [{:id group
            :label (:label group-config)
            :icon (:icon group-config)
            :dropdown true
            :roles []  ; Visible to all who can see children
            :order (:order group-config)
            :children (mapv #(dissoc % :group) sorted-items)}]
          
          ;; Top-level items (no grouping)
          (mapv #(dissoc % :group) sorted-items))))
    grouped-items)))

;; ============================================================================
;; Main Navigation Builder
;; ============================================================================

(defn build-navigation
  "Build complete navigation structure for current user
   
   Args:
     user-roles - Vector/set of role keywords
     current-path - Current request path (for active highlighting)
     opts - Optional map:
       :include-discovered - Include auto-discovered routes? (default: true)
       :dev-mode - Show all items in dev mode? (default: false)
   
   Returns: Vector of nav items ready for rendering"
  [user-roles current-path & [{:keys [include-discovered dev-mode]
                                :or {include-discovered true
                                     dev-mode false}}]]
  (let [config (load-nav-config)
        base-items (:nav-items config [])
        
        ;; Auto-discover routes from DSL if enabled
        discovered-items (when (and include-discovered
                                    (or (get-in config [:settings :auto-discover])
                                        (empty? base-items)))  ; Default to auto if no base items
                           (discover-routes-from-registry))
        
        ;; Get conventions for grouping
        auto-groups (try
                      (require 'forma.server.resource-engine)
                      (let [get-conv-fn (resolve 'forma.server.resource-engine/get-conventions)]
                        (:nav-auto-groups (when get-conv-fn (get-conv-fn))))
                      (catch Exception _e {}))
        
        ;; Group discovered items by nav group
        grouped-discovered (when (seq discovered-items)
                            (group-by :group discovered-items))
        
        ;; Build dropdown structure for discovered items
        structured-discovered (when grouped-discovered
                               (build-dropdown-structure grouped-discovered auto-groups))
        
        ;; Merge base + discovered (discovered replaces base if same ID)
        all-items (if (seq structured-discovered)
                   (vec (concat base-items structured-discovered))
                   base-items)
        
        ;; Sort by order
        sorted-items (sort-by :order all-items)
        
        ;; Filter by roles (unless dev mode shows all)
        visible-items (if dev-mode
                        sorted-items
                        (filter-nav-items sorted-items user-roles))
        
        ;; Mark active items
        active-items (mark-active visible-items current-path)]
    
    active-items))

;; ============================================================================
;; Utility: Get Nav Item by ID
;; ============================================================================

(defn get-nav-item
  "Get a specific nav item by ID
   
   Useful for getting metadata about a specific page"
  [item-id]
  (let [config (load-nav-config)
        all-items (:nav-items config)]
    (some #(when (= (:id %) item-id) %) all-items)))

(defn get-breadcrumbs
  "Generate breadcrumbs for current path
   
   Returns vector of {:label :href} maps"
  [_current-path]
  ;; TODO: Implement breadcrumb generation
  ;; For now, return simple home + current
  [{:label "Home" :href "/"}])

;; ============================================================================
;; Dev Helpers
;; ============================================================================

(comment
  ;; Reload config during development
  (reload-nav-config!)
  
  ;; Build nav for admin user
  (build-navigation [:role/admin] "/customers")
  
  ;; Build nav for technician
  (build-navigation [:role/technician] "/tickets/123")
  
  ;; Get specific nav item
  (get-nav-item :customers))
