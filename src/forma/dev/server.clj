(ns forma.dev.server
  "Minimal dev server for UI library development
  
   Keeps UI library pure - just takes request args and returns HTML.
   Server is optional dependency, only for dev/testing.
   
   Usage:
   (start-server demo-routes {:port 3000})
   
   Or run as main:
   clojure -M -m forma.dev.server"
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.resource :as resource]
            [ring.middleware.content-type :as content-type]
            [ring.util.response :as response]
            [hiccup.core :as h]
            [forma.layout :as layout]
            [forma.pos :as pos]
            [forma.admin :as admin]))

(defn html-response [html-string]
  {:status 200
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body html-string})

(defn demo-home-page [request]
  (html-response
   (layout/unified-layout
    {:title "UI Library Demo"
     :user-roles [:role/admin :role/devops]
     :current-path (:uri request)}
    [:div
     [:h1 "Corebase UI Library"]
     [:p "Welcome to the UI library development server."]
     [:div {:style {:margin-top "24px"}}
      [:h2 "Demo Pages"]
      [:ul {:style {:list-style "none" :padding 0}}
       [:li {:style {:margin "8px 0"}}
        [:a.btn.btn-primary {:href "/pos"} "POS Terminal"]]
       [:li {:style {:margin "8px 0"}}
        [:a.btn.btn-primary {:href "/admin"} "Admin Dashboard"]]
       [:li {:style {:margin "8px 0"}}
        [:a.btn.btn-primary {:href "/admin/status"} "DevOps Status"]]]]])))

(def demo-routes
  "Demo routes showing UI library capabilities"
  [["/" {:get (fn [req] (demo-home-page req))}]
   ["/pos" {:get (fn [req] (html-response (pos/pos-page req)))}]
   ["/admin" {:get (fn [req] (html-response (admin/dashboard req)))}]
   ["/admin/status" {:get (fn [req] (html-response (admin/dashboard req)))}]
   ["/admin/hubs" {:get (fn [req] (html-response (admin/hub-management-page req)))}]])

(defn wrap-routes
  "Wrap handler with route matching"
  [routes]
  (fn [request]
    (let [path (:uri request)
          method (:request-method request)]
      (if-let [route (first (filter (fn [[route-path route-handlers]]
                                      (and (= route-path path)
                                           (contains? route-handlers method)))
                                    routes))]
        (let [[_ route-handlers] route
              handler (get route-handlers method)]
          (handler request))
        {:status 404
         :headers {"Content-Type" "text/html"}
         :body "<h1>404 Not Found</h1><p>The requested page does not exist.</p>"})))))

(defonce server-instance (atom nil))

(defn start-server
  "Start Ring server with routes and options.
   
   Args:
     routes - Vector of [path {:get handler :post handler ...}]
     opts - Map with:
       :port - Server port (default: 3000)
       :join? - Block thread? (default: true)"
  ([routes] (start-server routes {}))
  ([routes opts]
   (when @server-instance
     (println "Server already running. Use (stop) first.")
     @server-instance)
   (let [port (or (:port opts) 3000)
         join? (get opts :join? true)
         handler (-> (wrap-routes routes)
                     (resource/wrap-resource "public")
                     (content-type/wrap-content-type))
         server (jetty/run-jetty handler {:port port :join? join?})]
     (reset! server-instance server)
     (println (str "UI Library Dev Server started on http://localhost:" port))
     (println "Press Ctrl+C to stop (or call (stop) in REPL)")
     server)))

(defn -main
  "Entry point - starts dev server with demo routes"
  [& args]
  (let [port (if-let [p (first args)]
               (Integer/parseInt p)
               3000)]
    (start-server demo-routes {:port port})))

