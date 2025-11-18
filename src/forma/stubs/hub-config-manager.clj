(ns forma.server.hub.config-manager
  "Hub config manager stub for standalone UI library development.
   
   Provides minimal implementations for hub management.
   When integrated with parent project, this namespace will be replaced
   by the actual hub config manager.")

(defn get-online-hubs
  "Get list of online hubs.
   Stub implementation returns empty vector."
  []
  [])

(defn get-registry-stats
  "Get hub registry statistics.
   Stub implementation returns empty stats."
  []
  {:total 0
   :online 0
   :offline 0})

(defn get-config
  "Get configuration for a specific hub.
   Stub implementation returns default config."
  [store-id]
  {:store-id store-id
   :status :offline})

