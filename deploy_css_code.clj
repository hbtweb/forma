(ns deploy-css-code
  "Deploy page with correct CSS_Code element"
  (:require [clj-http.client :as http]
            [cheshire.core :as json]))

(def wordpress-url "http://hbtcomputers.com.au.test")
(def wordpress-user "admin")
(def wordpress-password "T8W8rxIo5y566jm79HgSs9Mi")

(defn deploy []
  (let [tree {:_nextNodeId 100
              :status "exported"
              :root {:id 1
                     :data {:type "root" :properties []}
                     :children [
                       ;; Tailwind CSS CDN link via HTML_Code
                       {:id 10
                        :data {:type "OxygenElements\\HTML_Code"
                               :properties {:content {:code "<link href=\"https://cdn.jsdelivr.net/npm/tailwindcss@3.4/dist/tailwind.min.css\" rel=\"stylesheet\">"}}}
                        :children []
                        :_parentId 1}

                       ;; Custom CSS via CSS_Code (CORRECT!)
                       {:id 11
                        :data {:type "OxygenElements\\CSS_Code"
                               :properties {:content {:code ".gradient-hero {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.card-hover {
  transition: all 0.3s ease;
}
.card-hover:hover {
  transform: translateY(-4px);
  box-shadow: 0 10px 30px rgba(0,0,0,0.15);
  border-color: #667eea !important;
}"}}}
                        :children []
                        :_parentId 1}

                       ;; Hero section
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
                                          :properties {:content {:content {:text "✅ FIXED: CSS_Code Element"
                                                                          :tags "h1"}}
                                                      :design {:typography {:size {:number 36 :unit "px"}
                                                                           :weight "700"
                                                                           :color "#ffffff"
                                                                           :text-align "center"}}}}
                                   :children []
                                   :_parentId 20}
                                  {:id 22
                                   :data {:type "EssentialElements\\Text"
                                          :properties {:content {:content {:text "Using OxygenElements\\CSS_Code for custom CSS + HTML_Code for Tailwind CDN"}}
                                                      :design {:typography {:size {:number 16 :unit "px"}
                                                                           :color "rgba(255,255,255,0.9)"
                                                                           :text-align "center"}}}}
                                   :children []
                                   :_parentId 20}]
                        :_parentId 1}

                       ;; Test cards with emojis
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
                                   :children [{:id 40
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
                                              :children [{:id 50
                                                         :data {:type "EssentialElements\\Text"
                                                                :properties {:content {:content {:text "💻"}}
                                                                            :design {:typography {:size {:number 48 :unit "px"}}}}}
                                                         :children []
                                                         :_parentId 40}
                                                        {:id 60
                                                         :data {:type "EssentialElements\\Heading"
                                                                :properties {:content {:content {:text "Computers" :tags "h3"}}
                                                                            :design {:typography {:size {:number 18 :unit "px"}
                                                                                                 :weight "600"}}}}
                                                         :children []
                                                         :_parentId 40}]
                                              :_parentId 31}
                                             {:id 41
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
                                              :children [{:id 51
                                                         :data {:type "EssentialElements\\Text"
                                                                :properties {:content {:content {:text "📱"}}
                                                                            :design {:typography {:size {:number 48 :unit "px"}}}}}
                                                         :children []
                                                         :_parentId 41}
                                                        {:id 61
                                                         :data {:type "EssentialElements\\Heading"
                                                                :properties {:content {:content {:text "Phones" :tags "h3"}}
                                                                            :design {:typography {:size {:number 18 :unit "px"}
                                                                                                 :weight "600"}}}}
                                                         :children []
                                                         :_parentId 41}]
                                              :_parentId 31}
                                             {:id 42
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
                                              :children [{:id 52
                                                         :data {:type "EssentialElements\\Text"
                                                                :properties {:content {:content {:text "🎧"}}
                                                                            :design {:typography {:size {:number 48 :unit "px"}}}}}
                                                         :children []
                                                         :_parentId 42}
                                                        {:id 62
                                                         :data {:type "EssentialElements\\Heading"
                                                                :properties {:content {:content {:text "Audio" :tags "h3"}}
                                                                            :design {:typography {:size {:number 18 :unit "px"}
                                                                                                 :weight "600"}}}}
                                                         :children []
                                                         :_parentId 42}]
                                              :_parentId 31}]
                                   :_parentId 30}]
                        :_parentId 1}]}}
        body {:title "FIXED: CSS_Code Element"
              :post_type "page"
              :status "draft"
              :tree tree}
        url (str wordpress-url "/index.php?rest_route=/oxygen/v1/save")]

    (println "\n=== CORRECT FIX: Using CSS_Code Element ===\n")
    (println "✅ OxygenElements\\CSS_Code for custom styles")
    (println "✅ OxygenElements\\HTML_Code for Tailwind CDN")
    (println "✅ Proper content.content.text paths")
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
            (println "🎉 Page deployed with CSS_Code!")
            (println)
            (println "Page ID:" (:id data))
            (println "Preview URL:" (:url data))
            (println)
            (println "Check browser - should see:")
            (println "  ✅ Tailwind CSS loaded")
            (println "  ✅ Custom CSS injected properly")
            (println "  ✅ Gradient hero background")
            (println "  ✅ Emoji icons visible")
            (println "  ✅ Hover effects working")
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
