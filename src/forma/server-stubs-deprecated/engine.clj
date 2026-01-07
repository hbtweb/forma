(ns forma.server.engine
  "Engine stub for standalone UI library development.
   
   Provides minimal implementations of metadata and resource functions.
   When integrated with parent project, this namespace will be replaced
   by the actual engine from corebase.server.engine")

(defn get-metadata
  "Get metadata for a resource key.
   Returns map with :list-title, :detail-title, :nav-label, etc."
  [resource-key]
  {:list-title (str "List " (name resource-key))
   :detail-title (str "Detail " (name resource-key))
   :nav-label (str (name resource-key))
   :title (str (name resource-key))
   :icon "fa-circle"})

(defn get-list-columns
  "Get column definitions for list view.
   Returns vector of column maps."
  [resource-key]
  [])

(defn get-form-fields
  "Get field definitions for form view.
   Returns vector of field maps."
  [resource-key]
  [])

(defn get-sections
  "Get section definitions for detail view.
   Returns vector of section maps."
  [resource-key]
  [])

(defn get-all-resources
  "Get all resource definitions from registry.
   Returns map of resource-key -> resource-definition."
  []
  {})

(defn get-conventions
  "Get resource conventions and configuration.
   Returns map with :nav-auto-groups and other convention settings."
  []
  {:nav-auto-groups {}})

