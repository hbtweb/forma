(ns forma.server.auth
  "Authentication stub for standalone UI library development.
   
   Provides minimal auth functions that always allow access in dev mode.
   When integrated with parent project, this namespace will be replaced
   by the actual auth from corebase.server.auth")

(defn wrap-require-any-role
  "Middleware that requires user to have any of the specified roles.
   In dev mode, always allows access."
  [handler roles]
  (fn [request]
    ;; In dev mode, always allow - grant all roles to request
    (let [request-with-roles (assoc-in request [:identity :roles] roles)]
      (handler request-with-roles))))

(defn wrap-auth
  "Auth middleware stub - in dev mode, always allows access."
  [handler]
  (fn [request]
    (handler (assoc request :identity {:roles [:role/admin :role/devops :role/superadmin]}))))

