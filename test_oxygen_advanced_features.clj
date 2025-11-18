(ns test-oxygen-advanced-features
  "Test what advanced features Oxygen supports natively"
  (:require [clj-http.client :as http]
            [cheshire.core :as json]))

(def wordpress-url "http://hbtcomputers.com.au.test")
(def wordpress-user "admin")
(def wordpress-password "T8W8rxIo5y566jm79HgSs9Mi")

(defn test-features []
  (let [tree {:_nextNodeId 100
              :status "exported"
              :root {:id 1
                     :data {:type "root" :properties []}
                     :children [
                       ;; Test 1: Try gradient in background property
                       {:id 10
                        :data {:type "EssentialElements\\Section"
                               :properties {:design {:sizing {:minHeight {:number 300 :unit "px"}}
                                                    :spacing {:padding {:top {:number 40 :unit "px"}
                                                                       :bottom {:number 40 :unit "px"}}}
                                                    ;; TEST: Can we pass gradient directly?
                                                    :background {:gradient "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"}}}}
                        :children [{:id 11
                                   :data {:type "EssentialElements\\Heading"
                                          :properties {:content {:content {:text "Test 1: Gradient Background"
                                                                          :tags "h2"}}
                                                      :design {:typography {:size {:number 24 :unit "px"}
                                                                           :weight "700"
                                                                           :color "#ffffff"}}}}
                                   :children []
                                   :_parentId 10}]
                        :_parentId 1}

                       ;; Test 2: Try hover in design property
                       {:id 20
                        :data {:type "EssentialElements\\Div"
                               :properties {:design {:background {:color "#ffffff"}
                                                    :spacing {:padding {:top {:number 24 :unit "px"}
                                                                       :bottom {:number 24 :unit "px"}
                                                                       :left {:number 24 :unit "px"}
                                                                       :right {:number 24 :unit "px"}}}
                                                    :borders {:border {:top {:width {:number 1 :unit "px"}
                                                                            :style "solid"
                                                                            :color "#e5e7eb"}}
                                                             :radius {:topLeft {:number 8 :unit "px"}
                                                                     :topRight {:number 8 :unit "px"}
                                                                     :bottomLeft {:number 8 :unit "px"}
                                                                     :bottomRight {:number 8 :unit "px"}}}
                                                    ;; TEST: Can we define hover states?
                                                    :hover {:transform "translateY(-4px)"
                                                           :boxShadow [{:x {:number 0 :unit "px"}
                                                                       :y {:number 10 :unit "px"}
                                                                       :blur {:number 30 :unit "px"}
                                                                       :spread {:number 0 :unit "px"}
                                                                       :color "rgba(0,0,0,0.15)"}]}}}}
                        :children [{:id 21
                                   :data {:type "EssentialElements\\Text"
                                          :properties {:content {:content {:text "Test 2: Hover State (hover over me)"}}
                                                      :design {:typography {:size {:number 16 :unit "px"}}}}}
                                   :children []
                                   :_parentId 20}]
                        :_parentId 1}

                       ;; Test 3: Try transition in effects
                       {:id 30
                        :data {:type "EssentialElements\\Div"
                               :properties {:design {:background {:color "#667eea"}
                                                    :spacing {:padding {:top {:number 24 :unit "px"}
                                                                       :bottom {:number 24 :unit "px"}}}
                                                    ;; TEST: Can we define transitions?
                                                    :effects {:transition {:property "all"
                                                                          :duration "0.3s"
                                                                          :timing "ease"}}}}}
                        :children [{:id 31
                                   :data {:type "EssentialElements\\Text"
                                          :properties {:content {:content {:text "Test 3: Transition Effect"}}
                                                      :design {:typography {:color "#ffffff"}}}}
                                   :children []
                                   :_parentId 30}]
                        :_parentId 1}

                       ;; Test 4: Solid background (control - should work)
                       {:id 40
                        :data {:type "EssentialElements\\Section"
                               :properties {:design {:sizing {:minHeight {:number 200 :unit "px"}}
                                                    :spacing {:padding {:top {:number 40 :unit "px"}
                                                                       :bottom {:number 40 :unit "px"}}}
                                                    :background {:color "#22c55e"}}}}
                        :children [{:id 41
                                   :data {:type "EssentialElements\\Heading"
                                          :properties {:content {:content {:text "Test 4: Solid Color (Control)"
                                                                          :tags "h2"}}
                                                      :design {:typography {:size {:number 24 :unit "px"}
                                                                           :weight "700"
                                                                           :color "#ffffff"}}}}
                                   :children []
                                   :_parentId 40}]
                        :_parentId 1}]}}
        body {:title "Oxygen Advanced Features Test"
              :post_type "page"
              :status "draft"
              :tree tree}
        url (str wordpress-url "/index.php?rest_route=/oxygen/v1/save")]

    (println "\n=== TESTING OXYGEN ADVANCED FEATURES ===\n")
    (println "Test 1: Gradient background (design.background.gradient)")
    (println "Test 2: Hover state (design.hover)")
    (println "Test 3: Transition (design.effects.transition)")
    (println "Test 4: Solid color (design.background.color) - CONTROL")
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
            (println "✅ Test page deployed!")
            (println)
            (println "Page ID:" (:id data))
            (println "Preview URL:" (:url data))
            (println)
            (println "Check in browser:")
            (println "  1. Does Test 1 show gradient background?")
            (println "  2. Does Test 2 card lift on hover?")
            (println "  3. Does Test 3 have smooth transitions?")
            (println "  4. Does Test 4 show solid green? (should work)")
            (println)
            (println "Report back which tests work!")
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

(test-features)