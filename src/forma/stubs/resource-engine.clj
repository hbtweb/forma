(ns forma.server.resource-engine
  "Resource engine stub - alias for forma.server.engine.
   
   This namespace re-exports functions from engine stub to maintain
   compatibility with code that uses resource-engine namespace.")

(require '[forma.server.engine :as engine])

(def get-metadata engine/get-metadata)
(def get-list-columns engine/get-list-columns)
(def get-form-fields engine/get-form-fields)
(def get-sections engine/get-sections)
(def get-all-resources engine/get-all-resources)
(def get-conventions engine/get-conventions)

