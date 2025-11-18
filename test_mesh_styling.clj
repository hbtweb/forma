(ns test-mesh-styling
  "Test comprehensive Mesh styling patterns in Oxygen deployment"
  (:require [clj-http.client :as http]
            [cheshire.core :as json]))

(def wordpress-url "http://hbtcomputers.com.au.test")
(def wordpress-user "admin")
(def wordpress-password "T8W8rxIo5y566jm79HgSs9Mi")

(defn create-styled-test-page
  "Deploy a test page with ALL Mesh design patterns to verify styling works"
  []
  (println "\n=== Testing Mesh Styling Patterns in Oxygen ===\n")

  (let [tree {:_nextNodeId 500
              :status "exported"
              :root {:id 1
                     :data {:type "root" :properties []}
                     :children [
                       ;; Test 1: Background colors + padding (Mesh hero style)
                       {:id 100
                        :data {:type "EssentialElements\\Section"
                               :properties {:design {:background "hsl(var(--primary))"
                                                    :padding "80px 20px"
                                                    :layout_v2 {:layout "vertical"
                                                               :align-items "center"}}}}
                        :children [{:id 101
                                   :data {:type "EssentialElements\\Heading"
                                          :properties {:content {:content {:text "Mesh Styling Test"
                                                                          :tags "h1"}}
                                                      :design {:typography {:size {:number 36 :unit "px"}
                                                                          :weight "700"
                                                                          :color "hsl(var(--primary-foreground))"}}}}
                                   :children []
                                   :_parentId 100}
                                  {:id 102
                                   :data {:type "EssentialElements\\Text"
                                          :properties {:content {:text "Testing all Mesh design patterns: colors, spacing, typography, layouts, hover states"}
                                                      :design {:typography {:size {:number 14 :unit "px"}
                                                                          :color "hsl(var(--muted-foreground))"}}}}
                                   :children []
                                   :_parentId 100}]
                        :_parentId 1}

                       ;; Test 2: Grid layout (Mesh product grid)
                       {:id 200
                        :data {:type "EssentialElements\\Section"
                               :properties {:design {:padding "40px 20px"
                                                    :background "hsl(var(--background))"
                                                    :layout_v2 {:layout "vertical"}}}}
                        :children [{:id 201
                                   :data {:type "EssentialElements\\Heading"
                                          :properties {:content {:content {:text "Grid Layout Test" :tags "h2"}}
                                                      :design {:typography {:size {:number 24 :unit "px"}
                                                                          :weight "600"
                                                                          :color "hsl(var(--foreground))"}}}}
                                   :children []
                                   :_parentId 200}
                                  {:id 202
                                   :data {:type "EssentialElements\\Div"
                                          :properties {:design {:layout_v2 {:display "grid"
                                                                           :grid-template-columns "repeat(4, 1fr)"
                                                                           :gap {:number 16 :unit "px"}}}}}
                                   :children (mapv (fn [i]
                                                    {:id (+ 210 i)
                                                     :data {:type "EssentialElements\\Div"
                                                            :properties {:design {:background "hsl(var(--card))"
                                                                                 :border "1px solid hsl(var(--border))"
                                                                                 :border-radius {:number 8 :unit "px"}
                                                                                 :padding "16px"
                                                                                 :hover {:border "1px solid hsl(var(--primary))"
                                                                                        :box-shadow "0 4px 6px rgba(0,0,0,0.1)"}}}}
                                                     :children [{:id (+ 220 i)
                                                                :data {:type "EssentialElements\\Heading"
                                                                       :properties {:content {:content {:text (str "Card " (inc i)) :tags "h3"}}
                                                                                   :design {:typography {:size {:number 16 :unit "px"}
                                                                                                       :weight "600"
                                                                                                       :color "hsl(var(--foreground))"}}}}
                                                                :children []
                                                                :_parentId (+ 210 i)}
                                                               {:id (+ 230 i)
                                                                :data {:type "EssentialElements\\Text"
                                                                       :properties {:content {:text "Test card content"}
                                                                                   :design {:typography {:size {:number 12 :unit "px"}
                                                                                                       :color "hsl(var(--muted-foreground))"}}}}
                                                                :children []
                                                                :_parentId (+ 210 i)}]
                                                     :_parentId 202})
                                                  (range 4))
                                   :_parentId 200}]
                        :_parentId 1}

                       ;; Test 3: Spacing variants (Mesh uses precise spacing)
                       {:id 300
                        :data {:type "EssentialElements\\Section"
                               :properties {:design {:padding "40px 20px"
                                                    :background "hsl(var(--muted))"
                                                    :layout_v2 {:layout "vertical"}}}}
                        :children [{:id 301
                                   :data {:type "EssentialElements\\Heading"
                                          :properties {:content {:content {:text "Spacing Test" :tags "h2"}}
                                                      :design {:typography {:size {:number 24 :unit "px"}
                                                                          :weight "600"}
                                                              :spacing {:margin {:bottom {:number 16 :unit "px"}}}}}}
                                   :children []
                                   :_parentId 300}
                                  {:id 302
                                   :data {:type "EssentialElements\\Div"
                                          :properties {:design {:spacing {:margin {:top {:number 8 :unit "px"}
                                                                                  :bottom {:number 8 :unit "px"}}}}}}
                                   :children [{:id 303
                                              :data {:type "EssentialElements\\Text"
                                                     :properties {:content {:text "8px margin"}}}
                                              :children []
                                              :_parentId 302}]
                                   :_parentId 300}
                                  {:id 304
                                   :data {:type "EssentialElements\\Div"
                                          :properties {:design {:spacing {:margin {:top {:number 16 :unit "px"}
                                                                                  :bottom {:number 16 :unit "px"}}}}}}
                                   :children [{:id 305
                                              :data {:type "EssentialElements\\Text"
                                                     :properties {:content {:text "16px margin"}}}
                                              :children []
                                              :_parentId 304}]
                                   :_parentId 300}
                                  {:id 306
                                   :data {:type "EssentialElements\\Div"
                                          :properties {:design {:spacing {:margin {:top {:number 24 :unit "px"}
                                                                                  :bottom {:number 24 :unit "px"}}}}}}
                                   :children [{:id 307
                                              :data {:type "EssentialElements\\Text"
                                                     :properties {:content {:text "24px margin"}}}
                                              :children []
                                              :_parentId 306}]
                                   :_parentId 300}]
                        :_parentId 1}

                       ;; Test 4: Typography scale (Mesh uses 10-14px for most text)
                       {:id 400
                        :data {:type "EssentialElements\\Section"
                               :properties {:design {:padding "40px 20px"
                                                    :background "hsl(var(--background))"
                                                    :layout_v2 {:layout "vertical"}}}}
                        :children [{:id 401
                                   :data {:type "EssentialElements\\Heading"
                                          :properties {:content {:content {:text "Typography Scale Test" :tags "h2"}}
                                                      :design {:typography {:size {:number 24 :unit "px"}
                                                                          :weight "600"}
                                                              :spacing {:margin {:bottom {:number 12 :unit "px"}}}}}}
                                   :children []
                                   :_parentId 400}
                                  {:id 402
                                   :data {:type "EssentialElements\\Text"
                                          :properties {:content {:text "10px text (Mesh legal links)"}
                                                      :design {:typography {:size {:number 10 :unit "px"}
                                                                          :color "hsl(var(--muted-foreground))"}
                                                              :spacing {:margin {:bottom {:number 4 :unit "px"}}}}}}
                                   :children []
                                   :_parentId 400}
                                  {:id 403
                                   :data {:type "EssentialElements\\Text"
                                          :properties {:content {:text "12px text (Mesh links and section headings)"}
                                                      :design {:typography {:size {:number 12 :unit "px"}
                                                                          :weight "600"}
                                                              :spacing {:margin {:bottom {:number 4 :unit "px"}}}}}}
                                   :children []
                                   :_parentId 400}
                                  {:id 404
                                   :data {:type "EssentialElements\\Text"
                                          :properties {:content {:text "14px text (Mesh body copy)"}
                                                      :design {:typography {:size {:number 14 :unit "px"}}
                                                              :spacing {:margin {:bottom {:number 4 :unit "px"}}}}}}
                                   :children []
                                   :_parentId 400}
                                  {:id 405
                                   :data {:type "EssentialElements\\Text"
                                          :properties {:content {:text "16px text (Mesh card titles)"}
                                                      :design {:typography {:size {:number 16 :unit "px"}
                                                                          :weight "600"}}}}
                                   :children []
                                   :_parentId 400}]
                        :_parentId 1}]}}

        body {:title "Mesh Styling Comprehensive Test"
              :post_type "page"
              :status "draft"
              :tree tree}

        url (str wordpress-url "/index.php?rest_route=/oxygen/v1/save")]

    (println "Deploying comprehensive Mesh styling test...")
    (println "Testing:")
    (println "  ✓ CSS variables (hsl(var(--primary)))")
    (println "  ✓ Grid layouts (repeat(4, 1fr), gap)")
    (println "  ✓ Precise spacing (8px, 16px, 24px margins)")
    (println "  ✓ Typography scale (10-36px)")
    (println "  ✓ Hover states (border, shadow)")
    (println "  ✓ Color system (foreground, muted, primary)")
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
            (println "✅ Test page deployed successfully!")
            (println)
            (println "Page ID:" (:id data))
            (println "Preview URL:" (:url data))
            (println "Edit URL:" (:edit_url data))
            (println)
            (println "🔍 Verification Steps:")
            (println "1. Open preview URL in browser")
            (println "2. Check if CSS variables are resolved")
            (println "3. Verify grid layout displays correctly")
            (println "4. Test hover states on cards")
            (println "5. Measure spacing values match expectations")
            (println "6. Confirm typography sizes are correct")
            (println)
            (println "🎨 If styling looks correct, we're ready for full Mesh import!")
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
  ;; Run the comprehensive styling test
  (create-styled-test-page)

  ;; After running, open the preview URL and verify:
  ;; - Do CSS variables resolve correctly?
  ;; - Does the grid layout work?
  ;; - Do hover states apply?
  ;; - Is spacing accurate?
  ;; - Are font sizes correct?
  )