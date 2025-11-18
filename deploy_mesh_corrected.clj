(ns deploy-mesh-corrected
  "Deploy Mesh homepage with CORRECT Oxygen properties + Tailwind CSS injection"
  (:require [clojure.string :as str]
            [clj-http.client :as http]
            [cheshire.core :as json]))

(def wordpress-url "http://hbtcomputers.com.au.test")
(def wordpress-user "admin")
(def wordpress-password "T8W8rxIo5y566jm79HgSs9Mi")

;; =============================================================================
;; Tailwind CSS Injection Element
;; =============================================================================

(defn create-tailwind-inject
  "Inject Tailwind CSS + Mesh design tokens at the top of the page"
  [id]
  {:id id
   :data {:type "EssentialElements\\Code"
          :properties {:content {:code (str
"<link href=\"https://cdn.jsdelivr.net/npm/tailwindcss@3.4/dist/tailwind.min.css\" rel=\"stylesheet\">
<style>
/* Mesh Design Tokens */
:root {
  --primary: 221 83% 53%;           /* #667eea - Purple */
  --primary-foreground: 0 0% 100%;  /* #ffffff */
  --foreground: 222 47% 11%;        /* #1f2937 - Dark Gray */
  --muted-foreground: 215 16% 47%;  /* #6b7280 - Medium Gray */
  --background: 0 0% 100%;          /* #ffffff */
  --card: 0 0% 100%;                /* #ffffff */
  --border: 220 13% 91%;            /* #e5e7eb - Light Gray */
  --muted: 210 40% 96%;             /* #f9fafb */
}

/* Utility classes for gradients (Oxygen properties don't support gradients) */
.gradient-hero {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.gradient-text {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

/* Hover transitions */
.card-hover {
  transition: all 0.3s ease;
}

.card-hover:hover {
  transform: translateY(-4px);
  box-shadow: 0 10px 30px rgba(0,0,0,0.15);
  border-color: #667eea !important;
}

/* Mesh button styles */
.btn-primary {
  background: white;
  color: #667eea;
  padding: 14px 32px;
  border-radius: 8px;
  font-weight: 600;
  transition: all 0.2s ease;
  display: inline-block;
  text-decoration: none;
}

.btn-primary:hover {
  background: #f0f0f0;
  transform: scale(1.05);
}
</style>")
                       :language "html"}}}
   :children []
   :_parentId 1})

;; =============================================================================
;; Hero Section with CORRECT Oxygen Properties
;; =============================================================================

(defn create-hero-section
  "Hero section with proper Oxygen property schema"
  [start-id]
  {:id start-id
   :data {:type "EssentialElements\\Section"
          :properties {:design {:sizing {:minHeight {:number 60 :unit "vh"}}
                               :spacing {:padding {:top {:number 120 :unit "px"}
                                                  :bottom {:number 120 :unit "px"}
                                                  :left {:number 20 :unit "px"}
                                                  :right {:number 20 :unit "px"}}}
                               :layout_v2 {:layout "vertical"
                                          :align-items "center"
                                          :justify-content "center"}}
                      :attributes {:class "gradient-hero"}}}  ; Use CSS class for gradient
   :children [{:id (+ start-id 1)
              :data {:type "EssentialElements\\Heading"
                     :properties {:content {:content {:text "Welcome to HBT Computers"
                                                     :tags "h1"}}
                                 :design {:typography {:size {:number 48 :unit "px"}
                                                      :weight "700"
                                                      :color "#ffffff"
                                                      :text-align "center"}
                                         :spacing {:margin {:bottom {:number 16 :unit "px"}}}}}}
              :children []
              :_parentId start-id}
             {:id (+ start-id 2)
              :data {:type "EssentialElements\\Text"
                     :properties {:content {:text "Premium tech products and services for Western Australia"}
                                 :design {:typography {:size {:number 18 :unit "px"}
                                                      :color "rgba(255,255,255,0.9)"
                                                      :text-align "center"}
                                         :spacing {:margin {:bottom {:number 32 :unit "px"}}}}}}
              :children []
              :_parentId start-id}
             {:id (+ start-id 3)
              :data {:type "EssentialElements\\Link"
                     :properties {:content {:text "Shop Now"
                                           :url "#products"}
                                 :design {:typography {:size {:number 16 :unit "px"}
                                                      :weight "600"}
                                         :spacing {:padding {:top {:number 14 :unit "px"}
                                                            :bottom {:number 14 :unit "px"}
                                                            :left {:number 32 :unit "px"}
                                                            :right {:number 32 :unit "px"}}}}
                                 :attributes {:class "btn-primary"}}}
              :children []
              :_parentId start-id}]
   :_parentId 1})

;; =============================================================================
;; Category Card with Proper Oxygen Properties
;; =============================================================================

(defn create-category-card
  "Category card with correct Oxygen schema + hover class"
  [id parent-id category]
  {:id id
   :data {:type "EssentialElements\\Div"
          :properties {:design {:background {:color "#ffffff"}
                               :borders {:border {:top {:width {:number 1 :unit "px"}
                                                       :style "solid"
                                                       :color "#e5e7eb"}}
                                        :radius {:topLeft {:number 12 :unit "px"}
                                                :topRight {:number 12 :unit "px"}
                                                :bottomLeft {:number 12 :unit "px"}
                                                :bottomRight {:number 12 :unit "px"}}}
                               :spacing {:padding {:top {:number 24 :unit "px"}
                                                  :bottom {:number 24 :unit "px"}
                                                  :left {:number 24 :unit "px"}
                                                  :right {:number 24 :unit "px"}}}
                               :layout_v2 {:layout "vertical"
                                          :align-items "center"}}
                      :attributes {:class "card-hover"}}}  ; CSS class for hover
   :children [{:id (+ id 1)
              :data {:type "EssentialElements\\Text"
                     :properties {:content {:text (:icon category)}
                                 :design {:typography {:size {:number 48 :unit "px"}}
                                         :spacing {:margin {:bottom {:number 12 :unit "px"}}}}}}
              :children []
              :_parentId id}
             {:id (+ id 2)
              :data {:type "EssentialElements\\Heading"
                     :properties {:content {:content {:text (:name category) :tags "h3"}}
                                 :design {:typography {:size {:number 18 :unit "px"}
                                                      :weight "600"
                                                      :color "#1f2937"}
                                         :spacing {:margin {:bottom {:number 8 :unit "px"}}}}}}
              :children []
              :_parentId id}
             {:id (+ id 3)
              :data {:type "EssentialElements\\Text"
                     :properties {:content {:text (:description category)}
                                 :design {:typography {:size {:number 14 :unit "px"}
                                                      :color "#6b7280"
                                                      :text-align "center"}}}}
              :children []
              :_parentId id}]
   :_parentId parent-id})

;; =============================================================================
;; Categories Section with GRID
;; =============================================================================

(defn create-categories-section
  "Categories with proper Oxygen grid layout"
  [start-id]
  (let [categories [{:name "Laptops & Computers" :icon "💻" :description "Gaming laptops, ultrabooks, desktops"}
                   {:name "Mobile & Tablets" :icon "📱" :description "Smartphones, tablets, accessories"}
                   {:name "Audio & Video" :icon "🎧" :description "Headphones, speakers, microphones"}
                   {:name "Gaming" :icon "🎮" :description "Gaming laptops, consoles, peripherals"}
                   {:name "Accessories" :icon "🔌" :description "Cables, adapters, storage drives"}
                   {:name "Networking" :icon "🌐" :description "Routers, switches, cables"}]
        grid-id (+ start-id 3)
        card-ids (range (+ start-id 10) (+ start-id 100) 4)]

    {:id start-id
     :data {:type "EssentialElements\\Section"
            :properties {:design {:spacing {:padding {:top {:number 80 :unit "px"}
                                                     :bottom {:number 80 :unit "px"}
                                                     :left {:number 20 :unit "px"}
                                                     :right {:number 20 :unit "px"}}}
                                 :background {:color "#f9fafb"}}}}
     :children [{:id (+ start-id 1)
                :data {:type "EssentialElements\\Div"
                       :properties {:design {:sizing {:maxWidth {:number 1400 :unit "px"}}
                                            :spacing {:margin {:left "auto" :right "auto"}}
                                            :layout_v2 {:layout "vertical"}}}}
                :children [{:id (+ start-id 2)
                           :data {:type "EssentialElements\\Heading"
                                  :properties {:content {:content {:text "Shop by Category" :tags "h2"}}
                                              :design {:typography {:size {:number 36 :unit "px"}
                                                                   :weight "700"
                                                                   :text-align "center"}
                                                      :spacing {:margin {:bottom {:number 48 :unit "px"}}}}}}
                           :children []
                           :_parentId (+ start-id 1)}
                          {:id grid-id
                           :data {:type "EssentialElements\\Div"
                                  :properties {:design {:layout_v2 {:layout "grid"
                                                                   :grid {:columnCount 3
                                                                         :columnGap {:number 24 :unit "px"}
                                                                         :rowGap {:number 24 :unit "px"}}}}}}
                           :children (mapv (fn [category id]
                                            (create-category-card id grid-id category))
                                          categories
                                          card-ids)
                           :_parentId (+ start-id 1)}]
                :_parentId start-id}]
     :_parentId 1}))

;; =============================================================================
;; Product Card
;; =============================================================================

(defn create-product-card
  "Product card with proper Oxygen properties"
  [id parent-id product]
  {:id id
   :data {:type "EssentialElements\\Div"
          :properties {:design {:background {:color "#ffffff"}
                               :borders {:border {:top {:width {:number 1 :unit "px"}
                                                       :style "solid"
                                                       :color "#e5e7eb"}}
                                        :radius {:topLeft {:number 8 :unit "px"}
                                                :topRight {:number 8 :unit "px"}
                                                :bottomLeft {:number 8 :unit "px"}
                                                :bottomRight {:number 8 :unit "px"}}}
                               :spacing {:padding {:top {:number 16 :unit "px"}
                                                  :bottom {:number 16 :unit "px"}
                                                  :left {:number 16 :unit "px"}
                                                  :right {:number 16 :unit "px"}}}
                               :layout_v2 {:layout "vertical"}}
                      :attributes {:class "card-hover"}}}
   :children [{:id (+ id 1)
              :data {:type "EssentialElements\\Text"
                     :properties {:content {:text (:image product)}
                                 :design {:typography {:size {:number 64 :unit "px"}}
                                         :spacing {:margin {:bottom {:number 12 :unit "px"}}}}}}
              :children []
              :_parentId id}
             {:id (+ id 2)
              :data {:type "EssentialElements\\Heading"
                     :properties {:content {:content {:text (:name product) :tags "h3"}}
                                 :design {:typography {:size {:number 14 :unit "px"}
                                                      :weight "600"}
                                         :spacing {:margin {:bottom {:number 8 :unit "px"}}}}}}
              :children []
              :_parentId id}
             {:id (+ id 3)
              :data {:type "EssentialElements\\Text"
                     :properties {:content {:text (str "$" (:price product))}
                                 :design {:typography {:size {:number 20 :unit "px"}
                                                      :weight "700"
                                                      :color "#667eea"}}}}
              :children []
              :_parentId id}]
   :_parentId parent-id})

;; =============================================================================
;; Products Section
;; =============================================================================

(defn create-products-section
  "Products with 4-column grid"
  [start-id]
  (let [products [{:name "Pro Wireless Headphones" :price 299.99 :image "🎧"}
                 {:name "Laptop Ultra" :price 1999.99 :image "💻"}
                 {:name "Smartwatch Pro" :price 399.99 :image "⌚"}
                 {:name "4K Monitor" :price 599.99 :image "🖥️"}]
        grid-id (+ start-id 3)
        card-ids (range (+ start-id 10) (+ start-id 100) 4)]

    {:id start-id
     :data {:type "EssentialElements\\Section"
            :properties {:design {:spacing {:padding {:top {:number 80 :unit "px"}
                                                     :bottom {:number 80 :unit "px"}
                                                     :left {:number 20 :unit "px"}
                                                     :right {:number 20 :unit "px"}}}
                                 :background {:color "#ffffff"}}}}
     :children [{:id (+ start-id 1)
                :data {:type "EssentialElements\\Div"
                       :properties {:design {:sizing {:maxWidth {:number 1400 :unit "px"}}
                                            :spacing {:margin {:left "auto" :right "auto"}}
                                            :layout_v2 {:layout "vertical"}}}}
                :children [{:id (+ start-id 2)
                           :data {:type "EssentialElements\\Heading"
                                  :properties {:content {:content {:text "Featured Products" :tags "h2"}}
                                              :design {:typography {:size {:number 36 :unit "px"}
                                                                   :weight "700"}
                                                      :spacing {:margin {:bottom {:number 48 :unit "px"}}}}}}
                           :children []
                           :_parentId (+ start-id 1)}
                          {:id grid-id
                           :data {:type "EssentialElements\\Div"
                                  :properties {:design {:layout_v2 {:layout "grid"
                                                                   :grid {:columnCount 4
                                                                         :columnGap {:number 24 :unit "px"}
                                                                         :rowGap {:number 24 :unit "px"}}}}}}
                           :children (mapv (fn [product id]
                                            (create-product-card id grid-id product))
                                          products
                                          card-ids)
                           :_parentId (+ start-id 1)}]
                :_parentId start-id}]
     :_parentId 1}))

;; =============================================================================
;; Build Complete Homepage Tree
;; =============================================================================

(defn build-corrected-homepage
  "Build homepage with CORRECT Oxygen properties + Tailwind injection"
  []
  (let [tailwind-inject (create-tailwind-inject 10)
        hero (create-hero-section 1000)
        categories (create-categories-section 2000)
        products (create-products-section 3000)]

    {:_nextNodeId 10000
     :status "exported"
     :root {:id 1
            :data {:type "root" :properties []}
            :children [tailwind-inject hero categories products]}}))

;; =============================================================================
;; Deploy
;; =============================================================================

(defn deploy-corrected-homepage
  "Deploy Mesh homepage with CORRECT styling!"
  []
  (println "\n=== Deploying Mesh Homepage (CORRECTED with Tailwind) ===\n")

  (let [tree (build-corrected-homepage)
        body {:title "Mesh Homepage - Corrected Styling"
              :post_type "page"
              :status "draft"
              :tree tree}
        url (str wordpress-url "/index.php?rest_route=/oxygen/v1/save")]

    (println "✅ Using CORRECT Oxygen property schema:")
    (println "  • Layout: {:layout \"grid\" :grid {:columnCount 4}}")
    (println "  • Spacing: Individual side objects with {:number N :unit \"px\"}")
    (println "  • Typography: Proper size/weight/color objects")
    (println "  • Borders: Full border schema with radius")
    (println)
    (println "✅ Injecting Tailwind CSS + Mesh tokens via Code element")
    (println "  • Tailwind 3.4 CDN")
    (println "  • CSS custom properties (--primary, --foreground, etc.)")
    (println "  • Utility classes (.gradient-hero, .card-hover, .btn-primary)")
    (println)

    (try
      (let [response (http/post url
                               {:basic-auth [wordpress-user wordpress-password]
                                :content-type :json
                                :body (json/generate-string body)
                                :throw-exceptions false
                                :as :string})]

        (if (= 200 (:status response))
          (let [data (json/parse-string (:body response) true)]
            (println "🎉 CORRECTED Mesh homepage deployed!")
            (println)
            (println "Page ID:" (:id data))
            (println "Preview URL:" (:url data))
            (println "Edit URL:" (:edit_url data))
            (println)
            (println "🎨 This version includes:")
            (println "  ✅ Proper Oxygen property schema")
            (println "  ✅ Tailwind CSS injected")
            (println "  ✅ Mesh design tokens")
            (println "  ✅ 3-column category grid")
            (println "  ✅ 4-column product grid")
            (println "  ✅ Gradient hero background")
            (println "  ✅ Hover effects on cards")
            (println "  ✅ Proper spacing and typography")
            (println)
            (println "🌐 Open in browser - styles should render correctly now!")
            {:success true :data data})
          (do
            (println "❌ Deployment failed")
            (println "Status:" (:status response))
            (println "Body:" (:body response))
            {:success false})))

      (catch Exception e
        (println "❌ Error:" (.getMessage e))
        (.printStackTrace e)
        {:success false}))))

(comment
  ;; Deploy the corrected homepage
  (deploy-corrected-homepage)
  )