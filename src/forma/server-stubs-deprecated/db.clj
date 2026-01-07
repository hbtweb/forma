(ns forma.server.db
  "Database stub for standalone UI library development.
   
   Provides minimal implementations that return empty results.
   When integrated with parent project, this namespace will be replaced
   by the actual db from corebase.server.db")

(defn query
  "Execute a query and return results.
   Stub implementation returns empty vector."
  [query & args]
  [])

(defn query-one
  "Execute a query and return first result.
   Stub implementation returns nil."
  [query & args]
  nil)

