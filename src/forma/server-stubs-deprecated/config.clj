(ns forma.server.config
  "Configuration stub for standalone UI library development.

   When integrated with parent project, this namespace will be
   replaced by the actual config from corebase.core.config")

(defn mode
  "Returns current environment mode (dev, prod, etc.)"
  []
  :dev)

(defn server-port
  "Returns server port number"
  []
  3000)

