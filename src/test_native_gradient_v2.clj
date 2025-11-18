(ns test-native-gradient-v2
  "Test deployment with native Oxygen gradient parsing"
  (:require [clj-http.client :as http]
            [cheshire.core :as json]
            [forma.platforms.oxygen-mapper :as om]))

(def wordpress-url "http://hbtcomputers.com.au.test")
(def wordpress-user "admin")
(def wordpress-password "T8W8rxIo5y566jm79HgSs9Mi")

(defn test-gradient-parsing []
  (println "\n=== TESTING NATIVE GRADIENT PARSING ===\n")

  ;; Test the parser directly first
  (let [test-gradient "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
        parsed (om/parse-linear-gradient test-gradient)]
    (println "Input CSS gradient:")
    (println test-gradient)
    (println "\nParsed to Oxygen format:")
    (println (json/generate-string parsed {:pretty true}))
    (println))

  ;; Now test full compilation with Forma → Oxygen
  (let [forma-props {:background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
                     :background-hover "linear-gradient(135deg, #764ba2 0%, #667eea 100%)"}
        oxygen-props (om/tailwind->oxygen-properties forma-props)]
    (println "Forma properties:")
    (println (json/generate-string forma-props {:pretty true}))
    (println "\nCompiled to Oxygen properties:")
    (println (json/generate-string oxygen-props {:pretty true}))
    (println))

  ;; Deploy test page with gradient
  (let [tree {:_nextNodeId 200
              :status "exported"
              :root {:id 1
                     :data {:type "root" :properties []}
                     :children [
                       ;; Section with linear gradient (parsed from CSS)
                       {:id 200
                        :data {:type "EssentialElements\\Section"
                               :properties {:design {:sizing {:minHeight {:number 400 :unit "px"}}
                                                    :spacing {:padding {:top {:number 60 :unit "px"}
                                                                       :bottom {:number 60 :unit "px"}}}
                                                    ;; Native Oxygen gradient format (parsed from CSS string)
                                                    :background (om/parse-linear-gradient
                                                                 "linear-gradient(135deg, #667eea 0%, #764ba2 100%)")}}}
                        :children [{:id 201
                                   :data {:type "EssentialElements\\Heading"
                                          :properties {:content {:content {:text "Native Gradient Test"
                                                                          :tags "h1"}}
                                                      :design {:typography {:size {:number 48 :unit "px"}
                                                                           :weight "700"
                                                                           :color "#ffffff"}
                                                              :spacing {:margin {:bottom {:number 24 :unit "px"}}}}}}
                                   :children []
                                   :_parentId 200}
                                  {:id 202
                                   :data {:type "EssentialElements\\Text"
                                          :properties {:content {:content {:text "This gradient was parsed from CSS string to Oxygen's native structured format."}}
                                                      :design {:typography {:size {:number 18 :unit "px"}
                                                                           :color "#ffffff"}}}}
                                   :children []
                                   :_parentId 200}]
                        :_parentId 1}

                       ;; Section with radial gradient
                       {:id 210
                        :data {:type "EssentialElements\\Section"
                               :properties {:design {:sizing {:minHeight {:number 300 :unit "px"}}
                                                    :spacing {:padding {:top {:number 40 :unit "px"}
                                                                       :bottom {:number 40 :unit "px"}}}
                                                    :background (om/parse-radial-gradient
                                                                 "radial-gradient(#667eea 0%, #764ba2 100%)")}}}
                        :children [{:id 211
                                   :data {:type "EssentialElements\\Heading"
                                          :properties {:content {:content {:text "Radial Gradient Test"
                                                                          :tags "h2"}}
                                                      :design {:typography {:size {:number 36 :unit "px"}
                                                                           :weight "700"
                                                                           :color "#ffffff"}
                                                              :spacing {:margin {:bottom {:number 16 :unit "px"}}}}}}
                                   :children []
                                   :_parentId 210}]
                        :_parentId 1}

                       ;; Section with background color hover (native Oxygen support)
                       {:id 220
                        :data {:type "EssentialElements\\Section"
                               :properties {:design {:sizing {:minHeight {:number 200 :unit "px"}}
                                                    :spacing {:padding {:top {:number 40 :unit "px"}
                                                                       :bottom {:number 40 :unit "px"}}}
                                                    :background {:color "#667eea"
                                                                :color_hover "#764ba2"
                                                                :transition_duration {:number 500
                                                                                     :unit "ms"
                                                                                     :style "500ms"}}}}}
                        :children [{:id 221
                                   :data {:type "EssentialElements\\Heading"
                                          :properties {:content {:content {:text "Hover Me! (Native color transition)"
                                                                          :tags "h2"}}
                                                      :design {:typography {:size {:number 32 :unit "px"}
                                                                           :weight "700"
                                                                           :color "#ffffff"}}}}
                                   :children []
                                   :_parentId 220}]
                        :_parentId 1}]}}
        body {:title "Native Gradient Parser Test"
              :post_type "page"
              :status "draft"
              :tree tree}
        url (str wordpress-url "/index.php?rest_route=/oxygen/v1/save")]

    (println "\n=== DEPLOYING TEST PAGE ===\n")
    (println "Testing:")
    (println "  1. Linear gradient (parsed from CSS string)")
    (println "  2. Radial gradient (parsed from CSS string)")
    (println "  3. Background color hover (native Oxygen support)")
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
            (println "Verification checklist:")
            (println "  1. Does section 1 show smooth purple-to-violet gradient?")
            (println "  2. Does section 2 show radial gradient from center?")
            (println "  3. Does section 3 change color smoothly on hover?")
            (println "  4. Are gradients EDITABLE in Oxygen visual editor?")
            (println)
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

(test-gradient-parsing)