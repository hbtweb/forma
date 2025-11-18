(ns deploy-correct-understanding
  "CORRECT: HtmlCode for BOTH <link> tags AND <style> tags with CSS"
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
                       ;; HtmlCode for Tailwind CDN <link>
                       {:id 10
                        :data {:type "OxygenElements\\HtmlCode"
                               :properties {:content {:content {:html_code "<link href=\"https://cdn.jsdelivr.net/npm/tailwindcss@3.4/dist/tailwind.min.css\" rel=\"stylesheet\">"}}}}
                        :children []
                        :_parentId 1}

                       ;; HtmlCode for <style> tag with design tokens
                       {:id 11
                        :data {:type "OxygenElements\\HtmlCode"
                               :properties {:content {:content {:html_code "<style>
:root {
  --primary: 221 83% 53%;
  --foreground: 222 47% 11%;
  --muted-foreground: 215 16% 47%;
  --background: 0 0% 100%;
  --card: 0 0% 100%;
  --border: 220 13% 91%;
}
</style>"}}}}
                        :children []
                        :_parentId 1}

                       ;; CssCode for CSS definitions (no <style> tags)
                       {:id 12
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
                                          :properties {:content {:content {:text "✅ CORRECT: CssCode = CSS definitions only!"
                                                                          :tags "h1"}}
                                                      :design {:typography {:size {:number 36 :unit "px"}
                                                                           :weight "700"
                                                                           :color "#ffffff"
                                                                           :text-align "center"}}}}
                                   :children []
                                   :_parentId 20}
                                  {:id 22
                                   :data {:type "EssentialElements\\Text"
                                          :properties {:content {:content {:text "HtmlCode for <link> and <style> tags, CssCode for raw CSS rules"}}
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
                                                                                   :design {:typography {:size {:number 48 :unit "px"}}}}}
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
        body {:title "CORRECT: CssCode for raw CSS only"
              :post_type "page"
              :status "draft"
              :tree tree}
        url (str wordpress-url "/index.php?rest_route=/oxygen/v1/save")]

    (println "\n=== CORRECT UNDERSTANDING ===\n")
    (println "✅ HtmlCode: <link> tags, <style> tags, any HTML")
    (println "✅ CssCode: Raw CSS definitions ONLY (no <style> wrapper)")
    (println "✅ Both approaches work for CSS injection")
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
            (println "🎉 SUCCESS! Correct implementation deployed!")
            (println)
            (println "Page ID:" (:id data))
            (println "Preview URL:" (:url data))
            (println)
            (println "Uses:")
            (println "  - HtmlCode (ID 10): Tailwind <link> tag")
            (println "  - HtmlCode (ID 11): <style> with design tokens")
            (println "  - CssCode (ID 12): Raw CSS definitions")
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