(ns forma.transforms.oxygen
  "Oxygen-specific transform functions

  These are the implementation functions referenced by oxygen.edn transforms.
  They handle complex transformations that can't be expressed purely in EDN."
  (:require [clojure.string :as str]))

;; =============================================================================
;; Spacing Transforms
;; =============================================================================

(defn spacing-to-shorthand
  "Convert Oxygen spacing object to CSS shorthand

  Input: {:top {:number 80 :unit 'px'}
          :right {:number 20 :unit 'px'}
          :bottom {:number 80 :unit 'px'}
          :left {:number 20 :unit 'px'}}
  Output: '80px 20px'"
  [oxygen-spacing]
  (when oxygen-spacing
    (let [top (get-in oxygen-spacing [:top :number])
          right (get-in oxygen-spacing [:right :number])
          bottom (get-in oxygen-spacing [:bottom :number])
          left (get-in oxygen-spacing [:left :number])
          unit (get-in oxygen-spacing [:top :unit] "px")]
      (cond
        ;; All sides equal: "20px"
        (= top right bottom left)
        (str top unit)

        ;; Top/bottom equal, left/right equal: "80px 20px"
        (and (= top bottom) (= left right))
        (str top unit " " right unit)

        ;; All different: "80px 20px 80px 20px"
        :else
        (str top unit " " right unit " " bottom unit " " left unit)))))

(defn shorthand-to-spacing
  "Parse CSS spacing shorthand to Oxygen object

  Input: '80px 20px'
  Output: {:top {:number 80 :unit 'px'}
           :right {:number 20 :unit 'px'}
           :bottom {:number 80 :unit 'px'}
           :left {:number 20 :unit 'px'}}"
  [shorthand]
  (when shorthand
    (let [parts (str/split (str/trim shorthand) #"\s+")
          parse-value (fn [s]
                       (let [match (re-find #"(\d+)(px|em|rem|%|vh|vw)?" s)]
                         (when match
                           {:number (Integer/parseInt (second match))
                            :unit (or (nth match 2) "px")})))]
      (case (count parts)
        ;; Single value: all sides
        1 (let [v (parse-value (first parts))]
            {:top v :right v :bottom v :left v})

        ;; Two values: top/bottom, left/right
        2 (let [tb (parse-value (first parts))
                lr (parse-value (second parts))]
            {:top tb :right lr :bottom tb :left lr})

        ;; Four values: explicit
        4 (let [[t r b l] (map parse-value parts)]
            {:top t :right r :bottom b :left l})

        ;; Invalid
        nil))))

;; =============================================================================
;; Gradient Transforms
;; =============================================================================

(defn parse-color-stop
  "Parse a single color stop from regex match

  Input: ['#667eea' '667eea' '0']
  Output: {:left 0 :red 102 :green 126 :blue 234 :alpha 1}"
  [[_ hex percent]]
  (let [r (Integer/parseInt (subs hex 0 2) 16)
        g (Integer/parseInt (subs hex 2 4) 16)
        b (Integer/parseInt (subs hex 4 6) 16)
        pos (Integer/parseInt percent)]
    {:left pos
     :red r
     :green g
     :blue b
     :alpha 1}))

(defn generate-svg-gradient
  "Generate SVG gradient string from points

  Input: [{:left 0 :red 102 :green 126 :blue 234 :alpha 1}
          {:left 100 :red 118 :green 75 :blue 162 :alpha 1}]
         135
  Output: '<linearGradient...>'"
  [points degree]
  (let [svg-stops (str/join ""
                   (map (fn [{:keys [left red green blue alpha]}]
                         (format "<stop stop-opacity=\"%s\" stop-color=\"#%02x%02x%02x\" offset=\"%s\"></stop>"
                                alpha red green blue (/ left 100.0)))
                       points))]
    (format "<linearGradient x1=\"0\" y1=\"0\" x2=\"1\" y2=\"1\" id=\"%%%%GRADIENTID%%%%\">%s</linearGradient>"
           svg-stops)))

(defn css-gradient-to-oxygen
  "Parse CSS linear-gradient to Oxygen structured format

  Input: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)'
  Output: {:gradient {:points [...] :type 'linear' :degree 135 ...} :type 'gradient'}"
  [gradient-str]
  (when (and gradient-str (str/starts-with? gradient-str "linear-gradient"))
    (let [angle-match (re-find #"(\d+)deg" gradient-str)
          degree (if angle-match (Integer/parseInt (second angle-match)) 180)
          color-stops (re-seq #"#([0-9a-fA-F]{6})\s+(\d+)%" gradient-str)
          points (mapv parse-color-stop color-stops)
          svg-value (generate-svg-gradient points degree)]
      {:gradient {:points points
                  :type "linear"
                  :degree degree
                  :svgValue svg-value
                  :value gradient-str}
       :type "gradient"})))

(defn point-to-css-stop
  "Convert Oxygen gradient point to CSS color stop

  Input: {:left 0 :red 102 :green 126 :blue 234 :alpha 1}
  Output: '#667eea 0%'"
  [{:keys [left red green blue alpha]}]
  (format "#%02x%02x%02x %d%%" red green blue left))

(defn gradient-to-css
  "Convert Oxygen gradient object to CSS string

  Input: {:gradient {:points [...] :degree 135 :type 'linear'} :type 'gradient'}
  Output: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)'"
  [oxygen-bg]
  (when (= (:type oxygen-bg) "gradient")
    (let [{:keys [points degree type]} (:gradient oxygen-bg)]
      (if (= type "linear")
        (str "linear-gradient(" degree "deg, "
             (str/join ", " (map point-to-css-stop points))
             ")")
        ;; Radial gradient
        (str "radial-gradient("
             (str/join ", " (map point-to-css-stop points))
             ")")))))

;; =============================================================================
;; Background Transforms
;; =============================================================================

(defn oxygen-background-to-css
  "Convert Oxygen background object to CSS value

  Handles both gradients and solid colors"
  [oxygen-bg]
  (cond
    (nil? oxygen-bg) nil
    (= (:type oxygen-bg) "gradient") (gradient-to-css oxygen-bg)
    (:color oxygen-bg) (:color oxygen-bg)
    :else nil))

(defn css-background-to-oxygen
  "Convert CSS background value to Oxygen object

  Detects gradients vs solid colors"
  [css-bg]
  (cond
    (nil? css-bg) nil
    (str/starts-with? css-bg "linear-gradient") (css-gradient-to-oxygen css-bg)
    (str/starts-with? css-bg "radial-gradient") (css-gradient-to-oxygen css-bg)
    :else {:color css-bg}))

;; =============================================================================
;; Unit Transforms
;; =============================================================================

(defn oxygen-unit-to-value
  "Convert Oxygen unit object to CSS value string

  Input: {:number 100 :unit 'px'}
  Output: '100px'"
  [oxygen-unit]
  (when oxygen-unit
    (str (:number oxygen-unit) (:unit oxygen-unit))))

(defn value-to-oxygen-unit
  "Parse CSS value to Oxygen unit object

  Input: '100px'
  Output: {:number 100 :unit 'px'}"
  [value-str]
  (when value-str
    (let [match (re-find #"(\d+)(px|em|rem|%|vh|vw)?" (str value-str))]
      (when match
        {:number (Integer/parseInt (second match))
         :unit (or (nth match 2) "px")}))))

;; =============================================================================
;; Layout Transforms
;; =============================================================================

(defn oxygen-layout-to-display
  "Convert Oxygen layout type to CSS display value

  Input: {:layout 'grid' :grid {...}}
  Output: 'grid'"
  [oxygen-layout]
  (case (:layout oxygen-layout)
    "grid" "grid"
    "vertical" "flex"
    "horizontal" "flex"
    "flex"))

(defn display-to-oxygen-layout
  "Convert CSS display to Oxygen layout type

  Requires full props context to determine vertical vs horizontal"
  [display flex-direction]
  (cond
    (= display "grid") "grid"
    (= flex-direction "column") "vertical"
    (= flex-direction "row") "horizontal"
    :else "vertical"))

;; =============================================================================
;; Grid Transforms
;; =============================================================================

(defn oxygen-grid-to-columns
  "Convert Oxygen grid config to CSS grid-template-columns

  Input: {:columnCount 4}
  Output: 'repeat(4, 1fr)'"
  [oxygen-grid]
  (when-let [count (:columnCount oxygen-grid)]
    (str "repeat(" count ", 1fr)")))

(defn columns-to-oxygen-grid
  "Parse CSS grid-template-columns to Oxygen grid config

  Input: 'repeat(4, 1fr)'
  Output: {:columnCount 4}"
  [columns-str]
  (when columns-str
    (let [match (re-find #"repeat\((\d+),\s*1fr\)" columns-str)]
      (when match
        {:columnCount (Integer/parseInt (second match))}))))

;; =============================================================================
;; Shadow Transforms
;; =============================================================================

(defn oxygen-shadow-to-css
  "Convert Oxygen shadow array to CSS box-shadow

  Input: [{:x 0 :y 4 :blur 12 :spread 0 :color 'rgba(0,0,0,0.1)' :inset false}]
  Output: '0 4px 12px 0 rgba(0,0,0,0.1)'"
  [oxygen-shadow]
  (when (seq oxygen-shadow)
    (str/join ", "
      (map (fn [s]
            (str (when (:inset s) "inset ")
                 (:x s 0) "px "
                 (:y s 0) "px "
                 (:blur s 0) "px "
                 (:spread s 0) "px "
                 (:color s "rgba(0,0,0,0.1)")))
           oxygen-shadow))))

(defn css-shadow-to-oxygen
  "Parse CSS box-shadow to Oxygen shadow array

  Input: '0 4px 12px 0 rgba(0,0,0,0.1)'
  Output: [{:x 0 :y 4 :blur 12 :spread 0 :color 'rgba(0,0,0,0.1)' :inset false}]"
  [css-shadow]
  (when css-shadow
    (let [parts (str/split css-shadow #",\s*")]
      (mapv (fn [part]
             (let [inset (str/starts-with? part "inset")
                   cleaned (str/replace part "inset" "")
                   tokens (str/split (str/trim cleaned) #"\s+")
                   [x y blur spread & color-parts] tokens]
               {:x (Integer/parseInt (str/replace x "px" ""))
                :y (Integer/parseInt (str/replace y "px" ""))
                :blur (Integer/parseInt (str/replace blur "px" ""))
                :spread (when spread (Integer/parseInt (str/replace spread "px" "")))
                :color (str/join " " color-parts)
                :inset inset}))
           parts))))