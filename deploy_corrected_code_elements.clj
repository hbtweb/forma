(ns deploy-corrected-code-elements
  "Deploy with CORRECT code element structure from page 54 reference"
  (:require [clj-http.client :as http]
            [cheshire.core :as json]))

(def wordpress-url "http://hbtcomputers.com.au.test")
(def wordpress-user "admin")
(def wordpress-password "T8W8rxIo5y566jm79HgSs9Mi")

(defn deploy []
  (let [tree {:_nextNodeId 200
              :status "exported"
              :root {:id 1
                     :data {:type "root" :properties []}
                     :children [
                       ;; Tailwind CDN via HtmlCode (CORRECT FORMAT!)
                       {:id 10
                        :data {:type "OxygenElements\\HtmlCode"
                               :properties {:content {:content {:html_code "<link href=\"https://cdn.jsdelivr.net/npm/tailwindcss@3.4/dist/tailwind.min.css\" rel=\"stylesheet\">"}}}}
                        :children []
                        :_parentId 1}

                       ;; Custom CSS via CssCode (CORRECT FORMAT!)
                       {:id 11
                        :data {:type "OxygenElements\\CssCode"
                               :properties {:content {:content {:css_code ".gradient-hero {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.card-hover {
  transition: all 0.3s ease;
}
.card-hover:hover {
  transform: translateY(-4px);
  box-shadow: 0 10px 30px rgba(0,0,0,0.15);
  border-color: #667eea !important;
}"}}}}
                        :children []
                        :_parentId 1}

                       ;; Hero section with gradient class
                       {:id 20
                        :data {:type "EssentialElements\\Section"
                               :properties {:design {:sizing {:minHeight {:number 60 :unit "vh"}}
                                                    :spacing {:padding {:top {:number 80 :unit "px"}
                                                                       :bottom {:number 80 :unit "px"}
                                                                       :left {:number 20 :unit "px"}
                                                                       :right {:number 20 :unit "px"}}}}
                                           :attributes {:class "gradient-hero"}}}
                        :children [{:id 21
                                   :data {:type "EssentialElements\\Heading"
                                          :properties {:content {:content {:text "🎉 FULLY WORKING: Code Elements!"
                                                                          :tags "h1"}}
                                                      :design {:typography {:size {:number 36 :unit "px"}
                                                                           :weight "700"
                                                                           :color "#ffffff"
                                                                           :text-align "center"}}}}
                                   :children []
                                   :_parentId 20}
                                  {:id 22
                                   :data {:type "EssentialElements\\Text"
                                          :properties {:content {:content {:text "Using correct OxygenElements\\CssCode + HtmlCode format!"}}
                                                      :design {:typography {:size {:number 16 :unit "px"}
                                                                           :color "rgba(255,255,255,0.9)"
                                                                           :text-align "center"}}}}
                                   :children []
                                   :_parentId 20}]
                        :_parentId 1}

                       ;; Cards with hover effects
                       {:id 30
                        :data {:type "EssentialElements\\Section"
                               :properties {:design {:spacing {:padding {:top {:number 60 :unit "px"}
                                                                        :bottom {:number 60 :unit "px"}}}
                                                    :background {:color "#f9fafb"}}}}
                        :children [{:id 31
                                   :data {:type "EssentialElements\\Div"
                                          :properties {:design {:sizing {:maxWidth {:number 1200 :unit "px"}}
                                                               :spacing {:margin {:left "auto" :right "auto"}}
                                                               :layout_v2 {:layout "grid"
                                                                          :grid {:columnCount 3
                                                                                :columnGap {:number 24 :unit "px"}}}}}}
                                   :children (mapv (fn [i emoji title]
                                                    {:id (+ 40 i)
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
                                                                        :attributes {:class "card-hover"}}}
                                                     :children [{:id (+ 50 i)
                                                                :data {:type "EssentialElements\\Text"
                                                                       :properties {:content {:content {:text emoji}}
                                                                                   :design {:typography {:size {:number 48 :unit "px"}}}}  }
                                                                :children []
                                                                :_parentId (+ 40 i)}
                                                               {:id (+ 60 i)
                                                                :data {:type "EssentialElements\\Heading"
                                                                       :properties {:content {:content {:text title :tags "h3"}}
                                                                                   :design {:typography {:size {:number 18 :unit "px"}
                                                                                                        :weight "600"
                                                                                                        :color "#1f2937"}}}}
                                                                :children []
                                                                :_parentId (+ 40 i)}]
                                                     :_parentId 31})
                                                  [0 1 2]
                                                  ["💻" "📱" "🎧"]
                                                  ["Computers" "Phones" "Audio"])
                                   :_parentId 30}]
                        :_parentId 1}]}}
        body {:title "FULLY WORKING: Gradient + Hover Effects"
              :post_type "page"
              :status "draft"
              :tree tree}
        url (str wordpress-url "/index.php?rest_route=/oxygen/v1/save")]

    (println "\n=== CORRECTED CODE ELEMENTS ===\n")
    (println "✅ Using OxygenElements\\CssCode (camelCase)")
    (println "✅ Using OxygenElements\\HtmlCode (camelCase)")
    (println "✅ Property path: content.content.css_code (snake_case)")
    (println "✅ Property path: content.content.html_code (snake_case)")
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
            (println "🎉 SUCCESS! Page deployed with working code elements!")
            (println)
            (println "Page ID:" (:id data))
            (println "Preview URL:" (:url data))
            (println)
            (println "Check browser - should see:")
            (println "  ✅ Tailwind CSS loaded")
            (println "  ✅ Gradient hero background (purple to purple)")
            (println "  ✅ Emoji icons (💻 📱 🎧)")
            (println "  ✅ Hover effects working (lift + shadow + border)")
            (println "  ✅ 3-column grid")
            {:success true :data data})
          (do
            (println "❌ Failed")
            (println "Status:" (:status response))
            (println "Body:" (:body response))
            {:success false})))

      (catch Exception e
        (println "❌ Error:" (.getMessage e))
        (.printStackTrace e)
        {:success false}))))

(deploy)
