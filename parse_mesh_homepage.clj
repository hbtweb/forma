(ns parse-mesh-homepage
  "Parse Mesh homepage and deploy to Oxygen"
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clj-http.client :as http]
            [cheshire.core :as json]))

(def wordpress-url "http://hbtcomputers.com.au.test")
(def wordpress-user "admin")
(def wordpress-password "T8W8rxIo5y566jm79HgSs9Mi")

;; Simplified Mesh homepage structure (without interactive JS logic)
;; We'll focus on the static layout and styling

(defn create-hero-section
  "Create a hero section similar to Mesh"
  [start-id]
  {:id start-id
   :data {:type "EssentialElements\\Section"
          :properties {:design {:background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
                               :padding "120px 20px"
                               :layout_v2 {:layout "vertical"
                                          :align-items "center"
                                          :justify-content "center"}}}}
   :children [{:id (+ start-id 1)
              :data {:type "EssentialElements\\Heading"
                     :properties {:content {:content {:text "Welcome to HBT Computers"
                                                     :tags "h1"}}
                                 :design {:typography {:size {:number 48 :unit "px"}
                                                      :weight "700"
                                                      :color "#ffffff"
                                                      :text-align "center"}}}}
              :children []
              :_parentId start-id}
             {:id (+ start-id 2)
              :data {:type "EssentialElements\\Text"
                     :properties {:content {:text "Premium tech products and services for Western Australia"}
                                 :design {:typography {:size {:number 18 :unit "px"}
                                                      :color "rgba(255,255,255,0.9)"
                                                      :text-align "center"}
                                         :spacing {:margin {:top {:number 16 :unit "px"}}}}}}
              :children []
              :_parentId start-id}
             {:id (+ start-id 3)
              :data {:type "EssentialElements\\Button"
                     :properties {:content {:text "Shop Now"}
                                 :design {:typography {:size {:number 16 :unit "px"}
                                                      :weight "600"}
                                         :background "#ffffff"
                                         :color "#667eea"
                                         :padding "14px 32px"
                                         :border-radius {:number 8 :unit "px"}
                                         :spacing {:margin {:top {:number 24 :unit "px"}}}
                                         :hover {:background "#f0f0f0"}}}}
              :children []
              :_parentId start-id}]
   :_parentId 1})

(defn create-category-card
  "Create a single category card"
  [id parent-id category]
  {:id id
   :data {:type "EssentialElements\\Div"
          :properties {:design {:background "#ffffff"
                               :border "1px solid #e5e7eb"
                               :border-radius {:number 12 :unit "px"}
                               :padding "24px"
                               :layout_v2 {:layout "vertical"
                                          :align-items "center"}
                               :hover {:border "1px solid #667eea"
                                      :box-shadow "0 10px 30px rgba(0,0,0,0.1)"
                                      :transform "translateY(-4px)"}
                               :transition "all 0.3s ease"}}}
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

(defn create-categories-section
  "Create categories grid section"
  [start-id]
  (let [categories [{:name "Laptops & Computers"
                    :icon "💻"
                    :description "Gaming laptops, ultrabooks, desktops"}
                   {:name "Mobile & Tablets"
                    :icon "📱"
                    :description "Smartphones, tablets, accessories"}
                   {:name "Audio & Video"
                    :icon "🎧"
                    :description "Headphones, speakers, microphones"}
                   {:name "Gaming"
                    :icon "🎮"
                    :description "Gaming laptops, consoles, peripherals"}
                   {:name "Accessories"
                    :icon "🔌"
                    :description "Cables, adapters, storage drives"}
                   {:name "Networking"
                    :icon "🌐"
                    :description "Routers, switches, cables"}]
        grid-id (+ start-id 1)
        card-ids (range (+ start-id 10) (+ start-id 10 (* (count categories) 4)) 4)]

    {:id start-id
     :data {:type "EssentialElements\\Section"
            :properties {:design {:padding "80px 20px"
                                 :background "#f9fafb"}}}
     :children [{:id grid-id
                :data {:type "EssentialElements\\Div"
                       :properties {:design {:max-width "1400px"
                                            :margin "0 auto"
                                            :layout_v2 {:layout "vertical"}}}}
                :children (cons
                           ;; Section heading
                           {:id (+ start-id 2)
                            :data {:type "EssentialElements\\Heading"
                                   :properties {:content {:content {:text "Shop by Category" :tags "h2"}}
                                               :design {:typography {:size {:number 36 :unit "px"}
                                                                    :weight "700"
                                                                    :text-align "center"}
                                                       :spacing {:margin {:bottom {:number 48 :unit "px"}}}}}}
                            :children []
                            :_parentId grid-id}
                           ;; Category grid
                           [{:id (+ start-id 3)
                            :data {:type "EssentialElements\\Div"
                                   :properties {:design {:layout_v2 {:display "grid"
                                                                    :grid-template-columns "repeat(3, 1fr)"
                                                                    :gap {:number 24 :unit "px"}}}}}
                            :children (mapv (fn [category id]
                                             (create-category-card id (+ start-id 3) category))
                                           categories
                                           card-ids)
                            :_parentId grid-id}])
                :_parentId start-id}]
     :_parentId 1}))

(defn create-product-card
  "Create a single product card (simplified)"
  [id parent-id product]
  {:id id
   :data {:type "EssentialElements\\Div"
          :properties {:design {:background "#ffffff"
                               :border "1px solid #e5e7eb"
                               :border-radius {:number 8 :unit "px"}
                               :padding "16px"
                               :layout_v2 {:layout "vertical"}
                               :hover {:border "1px solid #667eea"
                                      :box-shadow "0 4px 12px rgba(0,0,0,0.1)"}
                               :transition "all 0.2s ease"}}}
   :children [{:id (+ id 1)
              :data {:type "EssentialElements\\Text"
                     :properties {:content {:text (:image product)}
                                 :design {:typography {:size {:number 64 :unit "px"}}
                                         :text-align "center"
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

(defn create-products-section
  "Create featured products section"
  [start-id]
  (let [products [{:name "Pro Wireless Headphones" :price 299.99 :image "🎧"}
                 {:name "Laptop Ultra" :price 1999.99 :image "💻"}
                 {:name "Smartwatch Pro" :price 399.99 :image "⌚"}
                 {:name "4K Monitor" :price 599.99 :image "🖥️"}]
        grid-id (+ start-id 2)
        card-ids (range (+ start-id 10) (+ start-id 10 (* (count products) 4)) 4)]

    {:id start-id
     :data {:type "EssentialElements\\Section"
            :properties {:design {:padding "80px 20px"
                                 :background "#ffffff"}}}
     :children [{:id (+ start-id 1)
                :data {:type "EssentialElements\\Div"
                       :properties {:design {:max-width "1400px"
                                            :margin "0 auto"}}}
                :children [{:id grid-id
                           :data {:type "EssentialElements\\Heading"
                                  :properties {:content {:content {:text "Featured Products" :tags "h2"}}
                                              :design {:typography {:size {:number 36 :unit "px"}
                                                                   :weight "700"}
                                                      :spacing {:margin {:bottom {:number 48 :unit "px"}}}}}}
                           :children []
                           :_parentId (+ start-id 1)}
                          {:id (+ start-id 3)
                           :data {:type "EssentialElements\\Div"
                                  :properties {:design {:layout_v2 {:display "grid"
                                                                   :grid-template-columns "repeat(4, 1fr)"
                                                                   :gap {:number 24 :unit "px"}}}}}
                           :children (mapv (fn [product id]
                                            (create-product-card id (+ start-id 3) product))
                                          products
                                          card-ids)
                           :_parentId (+ start-id 1)}]
                :_parentId start-id}]
     :_parentId 1}))

(defn build-mesh-homepage-tree
  "Build complete Mesh homepage tree structure"
  []
  (let [hero (create-hero-section 1000)
        categories (create-categories-section 2000)
        products (create-products-section 3000)]

    {:_nextNodeId 10000
     :status "exported"
     :root {:id 1
            :data {:type "root" :properties []}
            :children [hero categories products]}}))

(defn deploy-mesh-homepage
  "Deploy Mesh homepage to Oxygen"
  []
  (println "\n=== Deploying Mesh Homepage to Oxygen ===\n")

  (let [tree (build-mesh-homepage-tree)
        body {:title "Mesh Homepage - Full Demo"
              :post_type "page"
              :status "draft"
              :tree tree}
        url (str wordpress-url "/index.php?rest_route=/oxygen/v1/save")]

    (println "Building homepage with:")
    (println "  - Hero section (gradient background, CTA)")
    (println "  - 6 category cards (grid layout)")
    (println "  - 4 featured products (grid layout)")
    (println "  - Full Mesh styling (colors, spacing, hover effects)")
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
            (println "✅ Mesh homepage deployed successfully!")
            (println)
            (println "Page ID:" (:id data))
            (println "Preview URL:" (:url data))
            (println "Edit URL:" (:edit_url data))
            (println)
            (println "🎨 This page includes:")
            (println "  ✅ Gradient hero section")
            (println "  ✅ Grid layouts (3-column categories, 4-column products)")
            (println "  ✅ Hover effects and transitions")
            (println "  ✅ Responsive spacing")
            (println "  ✅ Mesh color scheme")
            (println)
            (println "🚀 Open in browser to see the full Mesh experience!")
            {:success true :data data})
          (do
            (println "❌ Deployment failed")
            (println "Status:" (:status response))
            (println "Body:" (:body response))
            {:success false :status (:status response) :body (:body response)})))

      (catch Exception e
        (println "❌ Error:" (.getMessage e))
        (.printStackTrace e)
        {:success false :error (.getMessage e)}))))

(comment
  ;; Deploy the Mesh homepage
  (deploy-mesh-homepage)

  ;; Test tree structure
  (def tree (build-mesh-homepage-tree))
  (println "Root children count:" (count (get-in tree [:root :children])))
  (println "Total elements:" (:_nextNodeId tree))
  )