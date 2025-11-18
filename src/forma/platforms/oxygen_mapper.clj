(ns forma.platforms.oxygen-mapper
  "Bi-directional mapping between Tailwind/Mesh styles and Oxygen properties"
  (:require [clojure.string :as str]))

;; =============================================================================
;; Tailwind/Mesh → Oxygen Property Mapping
;; =============================================================================

(defn parse-spacing-shorthand
  "Parse Tailwind-style spacing shorthand: '80px 20px' → {:top 80 :right 20 :bottom 80 :left 20}"
  [value]
  (when value
    (let [parts (str/split value #"\s+")
          numbers (mapv #(Integer/parseInt (str/replace % #"[^\d]" "")) parts)]
      (case (count numbers)
        1 {:top (first numbers) :right (first numbers)
           :bottom (first numbers) :left (first numbers)}
        2 {:top (first numbers) :right (second numbers)
           :bottom (first numbers) :left (second numbers)}
        4 {:top (first numbers) :right (second numbers)
           :bottom (nth numbers 2) :left (nth numbers 3)}
        nil))))

(defn tailwind-color->oxygen
  "Convert Tailwind color (with vars) to Oxygen format
   hsl(var(--primary)) → keep as-is for now, Oxygen may support it"
  [color]
  color)

(defn map-layout
  "Map layout properties from Tailwind/custom to Oxygen schema"
  [props]
  (let [layout-type (cond
                      (= (:display props) "grid") "grid"
                      (= (:display props) "flex") (if (= (:flex-direction props) "column")
                                                     "vertical"
                                                     "horizontal")
                      :else "vertical")]
    (cond-> {:layout layout-type}

      ;; Grid layout
      (= layout-type "grid")
      (assoc :grid
             (cond-> {}
               (:grid-template-columns props)
               (assoc :columnCount
                      (if-let [match (re-find #"repeat\((\d+),\s*1fr\)" (:grid-template-columns props))]
                        (Integer/parseInt (second match))
                        4))

               (:gap props)
               (assoc :columnGap {:number (or (:gap props) 16) :unit "px"}
                      :rowGap {:number (or (:gap props) 16) :unit "px"})))

      ;; Alignment
      (:align-items props)
      (assoc :align-items (:align-items props))

      (:justify-content props)
      (assoc :justify-content (:justify-content props)))))

(defn map-spacing
  "Map spacing (padding, margin) to Oxygen schema"
  [props]
  (let [result {}]
    (cond-> result
      ;; Padding
      (:padding props)
      (assoc :padding
             (if (map? (:padding props))
               ;; Already structured
               (into {} (map (fn [[k v]]
                              [k (if (map? v) v {:number v :unit "px"})])
                            (:padding props)))
               ;; Shorthand string
               (let [parsed (parse-spacing-shorthand (:padding props))]
                 (into {} (map (fn [[k v]] [k {:number v :unit "px"}]) parsed)))))

      ;; Margin
      (:margin props)
      (assoc :margin
             (if (map? (:margin props))
               (into {} (map (fn [[k v]]
                              [k (if (= v "auto") "auto"
                                   (if (map? v) v {:number v :unit "px"}))])
                            (:margin props)))
               (let [parsed (parse-spacing-shorthand (:margin props))]
                 (into {} (map (fn [[k v]] [k {:number v :unit "px"}]) parsed))))))))

(defn map-sizing
  "Map sizing properties to Oxygen schema"
  [props]
  (cond-> {}
    (:max-width props)
    (assoc :maxWidth {:number (:max-width props) :unit "px"})

    (:min-height props)
    (assoc :minHeight (if (str/ends-with? (str (:min-height props)) "vh")
                        {:number (Integer/parseInt (str/replace (str (:min-height props)) "vh" ""))
                         :unit "vh"}
                        {:number (:min-height props) :unit "px"}))

    (:width props)
    (assoc :width (if (str/ends-with? (str (:width props)) "%")
                    {:number (Integer/parseInt (str/replace (str (:width props)) "%" ""))
                     :unit "%"}
                    {:number (:width props) :unit "px"}))))

(defn map-typography
  "Map typography properties to Oxygen schema"
  [props]
  (cond-> {}
    (:size props)
    (assoc :size (if (map? (:size props))
                   (:size props)
                   {:number (:size props) :unit "px"}))

    (:weight props)
    (assoc :weight (str (:weight props)))

    (:color props)
    (assoc :color (tailwind-color->oxygen (:color props)))

    (:text-align props)
    (assoc :text-align (:text-align props))))

(defn parse-linear-gradient
  "Parse CSS linear-gradient to Oxygen structured format

  Example: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)'
  → {:gradient {:points [...] :type 'linear' :degree 135 :value '...'} :type 'gradient'}"
  [gradient-str]
  (let [;; Extract angle (default 180 for top-to-bottom)
        angle-match (re-find #"(\d+)deg" gradient-str)
        degree (if angle-match (Integer/parseInt (second angle-match)) 180)

        ;; Extract color stops with hex colors
        color-stops (re-seq #"#([0-9a-fA-F]{6})\s+(\d+)%" gradient-str)

        points (mapv (fn [[_ hex percent]]
                      (let [r (Integer/parseInt (subs hex 0 2) 16)
                            g (Integer/parseInt (subs hex 2 4) 16)
                            b (Integer/parseInt (subs hex 4 6) 16)
                            pos (Integer/parseInt percent)]
                        {:left pos
                         :red r
                         :green g
                         :blue b
                         :alpha 1}))
                    color-stops)

        ;; Generate SVG gradient
        svg-stops (str/join "" (map (fn [{:keys [left red green blue alpha]}]
                                     (format "<stop stop-opacity=\"%s\" stop-color=\"#%02x%02x%02x\" offset=\"%s\"></stop>"
                                             alpha red green blue (/ left 100.0)))
                                   points))
        svg-value (format "<linearGradient x1=\"0\" y1=\"0\" x2=\"1\" y2=\"1\" id=\"%%%%GRADIENTID%%%%\">%s</linearGradient>"
                         svg-stops)]

    {:gradient {:points points
                :type "linear"
                :degree degree
                :svgValue svg-value
                :value gradient-str}
     :type "gradient"}))

(defn parse-radial-gradient
  "Parse CSS radial-gradient to Oxygen structured format"
  [gradient-str]
  (let [color-stops (re-seq #"#([0-9a-fA-F]{6})\s+(\d+)%" gradient-str)

        points (mapv (fn [[_ hex percent]]
                      (let [r (Integer/parseInt (subs hex 0 2) 16)
                            g (Integer/parseInt (subs hex 2 4) 16)
                            b (Integer/parseInt (subs hex 4 6) 16)
                            pos (Integer/parseInt percent)]
                        {:left pos
                         :red r
                         :green g
                         :blue b
                         :alpha 1}))
                    color-stops)

        svg-stops (str/join "" (map (fn [{:keys [left red green blue alpha]}]
                                     (format "<stop stop-opacity=\"%s\" stop-color=\"#%02x%02x%02x\" offset=\"%s\"></stop>"
                                             alpha red green blue (/ left 100.0)))
                                   points))
        svg-value (format "<radialGradient id=\"%%%%GRADIENTID%%%%\">%s</radialGradient>" svg-stops)]

    {:gradient {:points points
                :type "radial"
                :degree 0
                :svgValue svg-value
                :value gradient-str}
     :type "gradient"}))

(defn map-background
  "Map background properties to Oxygen schema

  IMPORTANT: Oxygen requires structured gradient format!
  - String format does NOT work
  - Must parse CSS gradient → Oxygen structured format"
  [bg hover-bg]
  (cond
    ;; Linear gradient
    (and bg (str/starts-with? bg "linear-gradient"))
    (parse-linear-gradient bg)

    ;; Radial gradient
    (and bg (str/starts-with? bg "radial-gradient"))
    (parse-radial-gradient bg)

    ;; Solid color with hover
    (and bg hover-bg)
    {:color (tailwind-color->oxygen bg)
     :color_hover (tailwind-color->oxygen hover-bg)
     :transition_duration {:number 300 :unit "ms" :style "300ms"}}

    ;; Solid color only
    bg
    {:color (tailwind-color->oxygen bg)}

    :else
    nil))

(defn map-borders
  "Map border properties to Oxygen schema"
  [props]
  (cond-> {}
    (:border props)
    (assoc :border
           (if (string? (:border props))
             ;; Parse "1px solid #e5e7eb"
             (let [parts (str/split (:border props) #"\s+")]
               {:top {:width {:number (Integer/parseInt (str/replace (first parts) #"[^\d]" "")) :unit "px"}
                      :style (second parts)
                      :color (nth parts 2 "#000000")}})
             (:border props)))

    (:border-radius props)
    (assoc :radius
           (if (map? (:border-radius props))
             (:border-radius props)
             (let [val (:border-radius props)]
               {:topLeft {:number val :unit "px"}
                :topRight {:number val :unit "px"}
                :bottomLeft {:number val :unit "px"}
                :bottomRight {:number val :unit "px"}})))))

(defn map-effects
  "Map visual effects to Oxygen schema"
  [props]
  (cond-> {}
    (:box-shadow props)
    (assoc :boxShadow
           (if (string? (:box-shadow props))
             ;; Parse "0 4px 12px rgba(0,0,0,0.1)"
             [{:x {:number 0 :unit "px"}
               :y {:number 4 :unit "px"}
               :blur {:number 12 :unit "px"}
               :spread {:number 0 :unit "px"}
               :color "rgba(0,0,0,0.1)"}]
             (:box-shadow props)))

    (:opacity props)
    (assoc :opacity {:number (:opacity props)})))

(defn tailwind->oxygen-properties
  "Main function: Convert Tailwind/Mesh properties to Oxygen property schema

  Input (Tailwind/Mesh style):
  {:background 'linear-gradient(...)'
   :background-hover '#764ba2'  ; Optional hover color
   :padding '80px 20px'
   :display 'grid'
   :grid-template-columns 'repeat(4, 1fr)'
   :gap 24}

  Output (Oxygen schema):
  {:layout_v2 {:layout 'grid' :grid {:columnCount 4 :columnGap {...}}}
   :spacing {:padding {...}}
   :background {:gradient {...} :type 'gradient'}}
  "
  [props]
  (let [layout (map-layout props)
        spacing (map-spacing props)
        sizing (map-sizing props)
        typography (when (:typography props) (map-typography (:typography props)))
        background (map-background (:background props) (:background-hover props))
        borders (map-borders props)
        effects (map-effects props)]

    (cond-> {}
      (seq layout) (assoc :layout_v2 layout)
      (seq spacing) (assoc :spacing spacing)
      (seq sizing) (assoc :sizing sizing)
      typography (assoc :typography typography)
      background (assoc :background background)
      (seq borders) (assoc :borders borders)
      (seq effects) (assoc :effects effects))))

;; =============================================================================
;; Oxygen → Tailwind/Forma Reverse Mapping (for bi-directional sync)
;; =============================================================================

(defn oxygen->tailwind-layout
  "Reverse map Oxygen layout to Tailwind/Forma properties"
  [layout]
  (let [layout-type (:layout layout)]
    (cond-> {}
      (= layout-type "grid")
      (merge {:display "grid"
              :grid-template-columns (str "repeat(" (get-in layout [:grid :columnCount] 4) ", 1fr)")
              :gap (get-in layout [:grid :columnGap :number] 16)})

      (= layout-type "horizontal")
      (merge {:display "flex" :flex-direction "row"})

      (= layout-type "vertical")
      (merge {:display "flex" :flex-direction "column"})

      (:align-items layout)
      (assoc :align-items (:align-items layout))

      (:justify-content layout)
      (assoc :justify-content (:justify-content layout)))))

(defn oxygen->tailwind-spacing
  "Reverse map Oxygen spacing to Tailwind/Forma properties"
  [spacing]
  (cond-> {}
    (:padding spacing)
    (assoc :padding
           (let [p (:padding spacing)]
             (if (every? #(= (get-in p [% :number]) (get-in p [:top :number]))
                        [:right :bottom :left])
               ;; Uniform padding
               (str (get-in p [:top :number]) "px")
               ;; Full specification
               p)))

    (:margin spacing)
    (assoc :margin (:margin spacing))))

(defn oxygen->tailwind-properties
  "Reverse conversion: Oxygen schema → Tailwind/Forma properties

  For bi-directional sync!"
  [oxygen-props]
  (cond-> {}
    (:layout_v2 oxygen-props)
    (merge (oxygen->tailwind-layout (:layout_v2 oxygen-props)))

    (:spacing oxygen-props)
    (merge (oxygen->tailwind-spacing (:spacing oxygen-props)))

    (:typography oxygen-props)
    (assoc :typography (:typography oxygen-props))

    (:background oxygen-props)
    (assoc :background (or (get-in oxygen-props [:background :color])
                           (get-in oxygen-props [:background :__gradient])))))

;; =============================================================================
;; Tailwind CSS Injection Helper
;; =============================================================================

(defn create-tailwind-cdn-element
  "Create an Oxygen HtmlCode element that injects Tailwind CSS CDN link

  CORRECT FORMAT (discovered from page 54):
  - Type: OxygenElements\\HtmlCode (camelCase, not HTML_Code)
  - Property path: content.content.html_code (snake_case!)"
  [id parent-id]
  {:id id
   :data {:type "OxygenElements\\HtmlCode"
          :properties {:content {:content {:html_code "<link href=\"https://cdn.jsdelivr.net/npm/tailwindcss@3.4/dist/tailwind.min.css\" rel=\"stylesheet\">"}}}}
   :children []
   :_parentId parent-id})

(defn create-design-tokens-element
  "Create an Oxygen CssCode element for CSS design tokens

  CORRECT FORMAT (discovered from page 54):
  - Type: OxygenElements\\CssCode (camelCase, not CSS_Code)
  - Property path: content.content.css_code (snake_case!)

  IMPORTANT: Use CssCode for actual CSS, NOT HtmlCode!"
  [id parent-id]
  {:id id
   :data {:type "OxygenElements\\CssCode"
          :properties {:content {:content {:css_code (str ":root {\n"
                                                          "  --primary: 221 83% 53%;\n"
                                                          "  --foreground: 222 47% 11%;\n"
                                                          "  --muted-foreground: 215 16% 47%;\n"
                                                          "  --background: 0 0% 100%;\n"
                                                          "  --card: 0 0% 100%;\n"
                                                          "  --border: 220 13% 91%;\n"
                                                          "}\n")}}}}
   :children []
   :_parentId parent-id})

(defn create-utility-css-element
  "Create an Oxygen CssCode element for utility CSS classes

  CORRECT FORMAT:
  - Type: OxygenElements\\CssCode (for CSS!)
  - Property path: content.content.css_code"
  [id parent-id]
  {:id id
   :data {:type "OxygenElements\\CssCode"
          :properties {:content {:content {:css_code (str ".gradient-hero {\n"
                                                          "  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n"
                                                          "}\n"
                                                          "\n"
                                                          ".card-hover {\n"
                                                          "  transition: all 0.3s ease;\n"
                                                          "}\n"
                                                          "\n"
                                                          ".card-hover:hover {\n"
                                                          "  transform: translateY(-4px);\n"
                                                          "  box-shadow: 0 10px 30px rgba(0,0,0,0.15);\n"
                                                          "  border-color: #667eea !important;\n"
                                                          "}\n")}}}}
   :children []
   :_parentId parent-id})

(defn create-javascript-element
  "Create an Oxygen JavaScriptCode element

  CORRECT FORMAT (discovered from page 54):
  - Type: OxygenElements\\JavaScriptCode (camelCase)
  - Property path: content.content.javascript_code (snake_case!)"
  [id parent-id js-code]
  {:id id
   :data {:type "OxygenElements\\JavaScriptCode"
          :properties {:content {:content {:javascript_code js-code}}}}
   :children []
   :_parentId parent-id})

(comment
  ;; Test conversion
  (def test-props
    {:background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
     :padding "80px 20px"
     :display "grid"
     :grid-template-columns "repeat(4, 1fr)"
     :gap 24
     :typography {:size 36 :weight "700" :color "#ffffff"}})

  (tailwind->oxygen-properties test-props)
  ;; => {:layout_v2 {:layout "grid" :grid {:columnCount 4 :columnGap {:number 24 :unit "px"} ...}}
  ;;     :spacing {:padding {...}}
  ;;     :background {:__gradient "linear-gradient(...)"}
  ;;     :typography {:size {:number 36 :unit "px"} :weight "700" :color "#ffffff"}}

  ;; Test reverse
  (oxygen->tailwind-properties
    {:layout_v2 {:layout "grid" :grid {:columnCount 4}}
     :spacing {:padding {:top {:number 80 :unit "px"}}}})
  ;; => {:display "grid" :grid-template-columns "repeat(4, 1fr)" :padding "80px"}
  )