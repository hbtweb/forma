(require '[forma.compiler :as compiler])
(require '[clojure.java.io :as io])
(require '[kora.core.compiler :as core-compiler])

(println "=== Building Dashboard Example (Fixed) ===\n")

;; Define as a SINGLE root element (not a collection)
;; The compiler will recursively compile all nested children
(def dashboard-element
  [:div {}
   ;; Header
   [:header {:class "header"}
    [:h1 {} "Dashboard"]
    [:p {} "Welcome back! Here's what's happening today."]]
  
   ;; Container
   [:div {:class "container"}
    ;; Stats Grid
    [:div {:class "stats-grid"}
     [:div {:class "stat-card"}
      [:h3 {} "Total Users"]
      [:div {:class "value"} "12,543"]]
     [:div {:class "stat-card"}
      [:h3 {} "Revenue"]
      [:div {:class "value"} "$45,231"]]
     [:div {:class "stat-card"}
      [:h3 {} "Orders"]
      [:div {:class "value"} "1,234"]]
     [:div {:class "stat-card"}
      [:h3 {} "Growth"]
      [:div {:class "value"} "+12.5%"]]
     ]
    
    ;; Content Grid
    [:div {:class "content-grid"}
     ;; Main Content
     [:div {:class "main-content"}
      [:h2 {} "Recent Activity"]
      [:table {:class "table"}
       [:thead {}
        [:tr {}
         [:th {} "User"]
         [:th {} "Action"]
         [:th {} "Status"]
         [:th {} "Time"]]]
       [:tbody {}
        [:tr {}
         [:td {} "John Doe"]
         [:td {} "Order #1234"]
         [:td {} [:span {:class "badge badge-success"} "Completed"]]
         [:td {} "2 min ago"]]
        [:tr {}
         [:td {} "Jane Smith"]
         [:td {} "Order #1235"]
         [:td {} [:span {:class "badge badge-warning"} "Pending"]]
         [:td {} "5 min ago"]]
        [:tr {}
         [:td {} "Bob Johnson"]
         [:td {} "Order #1236"]
         [:td {} [:span {:class "badge badge-danger"} "Failed"]]
         [:td {} "10 min ago"]]]]
      [:button {:class "btn" :hx-get "/api/refresh" :hx-target ".main-content" :hx-swap "outerHTML"}
       "Refresh Data"]]
     
     ;; Sidebar
     [:div {:class "sidebar"}
      [:h2 {} "Quick Actions"]
      [:button {:class "btn" :style "width: 100%; margin-bottom: 0.5rem;"
                :hx-post "/api/create" :hx-target "body" :hx-swap "beforeend"}
       "Create New"]
      [:button {:class "btn btn-secondary" :style "width: 100%; margin-bottom: 0.5rem;"
                :hx-get "/api/export"}
       "Export Data"]
      [:button {:class "btn btn-secondary" :style "width: 100%;"
                :hx-delete "/api/clear" :hx-confirm "Are you sure?"}
       "Clear Cache"]]]]])

(println "Building context with project 'dashboard-example'...")
(def context (compiler/build-context {} {:project-name "dashboard-example" :platform-stack [:html :css :htmx]}))

(println "Compiling single root element (children will be compiled recursively)...")
;; Compile as a single element - the compiler will recursively compile all nested children
(def compiled (core-compiler/compile-with-pipeline compiler/forma-compiler dashboard-element context compiler/forma-hierarchy-levels))

(println "\n=== Compiled Structure (first 1000 chars) ===")
(def compiled-str (pr-str compiled))
(println (subs compiled-str 0 (min 1000 (count compiled-str))))
(println "...")

;; Convert to HTML string
(require '[hiccup.core :as h])
(def compiled-html (h/html compiled))

(println "\n=== Compiled HTML (first 1000 chars) ===")
(println (subs compiled-html 0 (min 1000 (count compiled-html))))
(println "...")

;; Check for HTMX attributes
(if (clojure.string/includes? compiled-html "hx-get")
  (println "\n✓ HTMX attributes found in compiled output")
  (println "\n⚠ HTMX attributes NOT found in compiled output"))

;; Check for key content
(def has-stats (clojure.string/includes? compiled-html "Total Users"))
(def has-table (clojure.string/includes? compiled-html "Recent Activity"))
(def has-buttons (clojure.string/includes? compiled-html "Refresh Data"))

(println "\n=== Content Check ===")
(println (str "Stats grid: " (if has-stats "✓" "✗")))
(println (str "Table: " (if has-table "✓" "✗")))
(println (str "Buttons: " (if has-buttons "✓" "✗")))

;; Write full HTML with styles
(def full-html (str "<!DOCTYPE html>
<html lang=\"en\">
<head>
    <meta charset=\"UTF-8\">
    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">
    <title>Dashboard Example - Forma Compiled</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: #f5f5f5;
            color: #333;
        }
        .header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 2rem;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        .header h1 { font-size: 2rem; margin-bottom: 0.5rem; }
        .header p { opacity: 0.9; }
        .container {
            max-width: 1200px;
            margin: 2rem auto;
            padding: 0 1rem;
        }
        .stats-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
            gap: 1.5rem;
            margin-bottom: 2rem;
        }
        .stat-card {
            background: white;
            padding: 1.5rem;
            border-radius: 8px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
            transition: transform 0.2s;
        }
        .stat-card:hover {
            transform: translateY(-4px);
            box-shadow: 0 4px 12px rgba(0,0,0,0.15);
        }
        .stat-card h3 {
            font-size: 0.875rem;
            color: #666;
            text-transform: uppercase;
            margin-bottom: 0.5rem;
        }
        .stat-card .value {
            font-size: 2rem;
            font-weight: bold;
            color: #667eea;
        }
        .content-grid {
            display: grid;
            grid-template-columns: 2fr 1fr;
            gap: 1.5rem;
        }
        .main-content {
            background: white;
            padding: 1.5rem;
            border-radius: 8px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
        }
        .sidebar {
            background: white;
            padding: 1.5rem;
            border-radius: 8px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
        }
        .btn {
            background: #667eea;
            color: white;
            border: none;
            padding: 0.75rem 1.5rem;
            border-radius: 6px;
            cursor: pointer;
            font-size: 1rem;
            transition: background 0.2s;
        }
        .btn:hover {
            background: #5568d3;
        }
        .btn-secondary {
            background: #6c757d;
        }
        .btn-secondary:hover {
            background: #5a6268;
        }
        .table {
            width: 100%;
            border-collapse: collapse;
            margin-top: 1rem;
        }
        .table th,
        .table td {
            padding: 0.75rem;
            text-align: left;
            border-bottom: 1px solid #e9ecef;
        }
        .table th {
            background: #f8f9fa;
            font-weight: 600;
        }
        .badge {
            display: inline-block;
            padding: 0.25rem 0.75rem;
            border-radius: 12px;
            font-size: 0.875rem;
            font-weight: 500;
        }
        .badge-success {
            background: #d4edda;
            color: #155724;
        }
        .badge-warning {
            background: #fff3cd;
            color: #856404;
        }
        .badge-danger {
            background: #f8d7da;
            color: #721c24;
        }
    </style>
</head>
<body>
" compiled-html "
</body>
</html>"))

(spit "projects/dashboard-example/compiled.html" full-html)
(println "\n✓ Compiled HTML written to projects/dashboard-example/compiled.html")
(println (str "✓ Total compiled size: " (count compiled-html) " characters"))
(println (str "✓ Full HTML size: " (count full-html) " characters"))

