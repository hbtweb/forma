(ns forma.css.processor
  "CSS processing utilities for Forma compiler

   Handles:
   - Vendor prefix generation (-webkit-, -moz-, -ms-, -o-)
   - CSS variable (custom property) support
   - Property normalization
   - Value validation
   - Minification preparation"
  (:require [clojure.string :as str]))

;; ============================================================================
;; VENDOR PREFIXES
;; ============================================================================

(def ^:const properties-requiring-prefixes
  "CSS properties that may require vendor prefixes for cross-browser compatibility"
  {:transform [:webkit :moz :ms :o]
   :transition [:webkit :moz :ms :o]
   :animation [:webkit :moz :ms :o]
   :box-shadow [:webkit :moz]
   :border-radius [:webkit :moz]
   :box-sizing [:webkit :moz]
   :user-select [:webkit :moz :ms]
   :appearance [:webkit :moz]
   :flex [:webkit :ms]
   :flex-direction [:webkit :ms]
   :flex-wrap [:webkit :ms]
   :justify-content [:webkit :ms]
   :align-items [:webkit :ms]
   :align-self [:webkit :ms]
   :order [:webkit :ms]
   :filter [:webkit]
   :backdrop-filter [:webkit]
   :clip-path [:webkit]
   :mask [:webkit]
   :columns [:webkit :moz]
   :column-count [:webkit :moz]
   :column-gap [:webkit :moz]
   :break-inside [:webkit :moz :ms]})

(def ^:const vendor-prefix-map
  "Vendor prefix strings"
  {:webkit "-webkit-"
   :moz "-moz-"
   :ms "-ms-"
   :o "-o-"})

(defn property-needs-prefix?
  "Check if a CSS property needs vendor prefixes"
  [property]
  (contains? properties-requiring-prefixes (keyword property)))

(defn get-required-prefixes
  "Get list of required vendor prefixes for a property"
  [property]
  (get properties-requiring-prefixes (keyword property) []))

(defn add-vendor-prefix
  "Add vendor prefix to a CSS property"
  [property vendor]
  (str (get vendor-prefix-map vendor) property))

(defn generate-prefixed-properties
  "Generate all vendor-prefixed versions of a CSS property.

   Returns: vector of [property value] pairs

   Example:
   (generate-prefixed-properties 'transform' 'rotate(45deg)')
   => [['transform' 'rotate(45deg)']
       ['-webkit-transform' 'rotate(45deg)']
       ['-moz-transform' 'rotate(45deg)']
       ['-ms-transform' 'rotate(45deg)']
       ['-o-transform' 'rotate(45deg)']]"
  [property value]
  (let [prop-key (keyword property)]
    (if (property-needs-prefix? property)
      (let [prefixes (get-required-prefixes property)
            prefixed (map (fn [vendor]
                           [(add-vendor-prefix property vendor) value])
                         prefixes)]
        (conj (vec prefixed) [property value])) ;; Standard property last
      [[property value]]))) ;; No prefixes needed

;; ============================================================================
;; CSS VARIABLES (CUSTOM PROPERTIES)
;; ============================================================================

(defn css-variable?
  "Check if a value is a CSS variable reference.

   Examples:
   - var(--primary-color) → true
   - var(--spacing-md, 1rem) → true
   - #fff → false"
  [value]
  (and (string? value)
       (str/starts-with? (str/trim value) "var(")))

(defn parse-css-variable
  "Parse CSS variable reference.

   Returns: {:variable '--primary-color' :fallback '1rem'} or nil

   Examples:
   - var(--primary-color) → {:variable '--primary-color' :fallback nil}
   - var(--spacing, 1rem) → {:variable '--spacing' :fallback '1rem'}"
  [value]
  (when (css-variable? value)
    (let [trimmed (str/trim value)
          inner (subs trimmed 4 (dec (count trimmed))) ;; Remove "var(" and ")"
          parts (str/split inner #",\s*" 2)]
      {:variable (first parts)
       :fallback (second parts)})))

(defn make-css-variable-reference
  "Create CSS variable reference string.

   Example:
   (make-css-variable-reference '--primary-color' '1rem')
   => 'var(--primary-color, 1rem)'"
  ([variable]
   (str "var(" variable ")"))
  ([variable fallback]
   (if fallback
     (str "var(" variable ", " fallback ")")
     (str "var(" variable ")"))))

(defn define-css-variable
  "Create CSS variable definition.

   Returns: ['--variable-name' 'value']

   Example:
   (define-css-variable 'primary-color' '#4f46e5')
   => ['--primary-color' '#4f46e5']"
  [variable-name value]
  [(str "--" (name variable-name)) (str value)])

(defn extract-css-variables
  "Extract all CSS variable references from a CSS properties map.

   Returns: set of variable names

   Example:
   {'background' 'var(--bg-color)' 'color' 'var(--text-color, #000)'}
   => #{'--bg-color' '--text-color'}"
  [css-props]
  (->> css-props
       vals
       (keep parse-css-variable)
       (map :variable)
       set))

;; ============================================================================
;; PROPERTY NORMALIZATION
;; ============================================================================

(def ^:const property-aliases
  "Common CSS property aliases/shorthands"
  {:bg :background
   :bg-color :background-color
   :fg :color
   :text-color :color
   :pd :padding
   :mg :margin
   :w :width
   :h :height
   :max-w :max-width
   :min-w :min-width
   :max-h :max-height
   :min-h :min-height
   :bd :border
   :bd-radius :border-radius
   :bd-color :border-color
   :bd-width :border-width
   :bd-style :border-style
   :fs :font-size
   :fw :font-weight
   :lh :line-height
   :ls :letter-spacing
   :ta :text-align
   :td :text-decoration
   :tt :text-transform
   :va :vertical-align
   :ws :white-space
   :ov :overflow
   :pos :position
   :z :z-index
   :d :display
   :float :float})

(defn normalize-property-name
  "Normalize CSS property name.

   Handles:
   - Aliases (e.g., :bg → :background)
   - Kebab-case conversion
   - Keyword → string conversion"
  [property]
  (let [prop-key (keyword property)
        normalized (get property-aliases prop-key prop-key)]
    (name normalized)))

(defn expand-shorthand-properties
  "Expand CSS shorthand properties.

   Limited implementation - handles common cases:
   - padding → padding-top, padding-right, padding-bottom, padding-left
   - margin → margin-top, margin-right, margin-bottom, margin-left

   Full implementation would handle all CSS shorthands."
  [property value]
  (case (keyword property)
    :padding
    (let [parts (str/split (str value) #"\s+")]
      (case (count parts)
        1 [["padding-top" (first parts)]
           ["padding-right" (first parts)]
           ["padding-bottom" (first parts)]
           ["padding-left" (first parts)]]
        2 [["padding-top" (first parts)]
           ["padding-right" (second parts)]
           ["padding-bottom" (first parts)]
           ["padding-left" (second parts)]]
        4 [["padding-top" (nth parts 0)]
           ["padding-right" (nth parts 1)]
           ["padding-bottom" (nth parts 2)]
           ["padding-left" (nth parts 3)]]
        [[property value]])) ;; Invalid, keep as-is

    :margin
    (let [parts (str/split (str value) #"\s+")]
      (case (count parts)
        1 [["margin-top" (first parts)]
           ["margin-right" (first parts)]
           ["margin-bottom" (first parts)]
           ["margin-left" (first parts)]]
        2 [["margin-top" (first parts)]
           ["margin-right" (second parts)]
           ["margin-bottom" (first parts)]
           ["margin-left" (second parts)]]
        4 [["margin-top" (nth parts 0)]
           ["margin-right" (nth parts 1)]
           ["margin-bottom" (nth parts 2)]
           ["margin-left" (nth parts 3)]]
        [[property value]]))

    ;; Default: no expansion
    [[property value]]))

;; ============================================================================
;; CSS PROPERTY PROCESSING
;; ============================================================================

(defn process-css-property
  "Process a single CSS property with all transformations.

   Options:
   - :vendor-prefixes? - Add vendor prefixes (default: false)
   - :normalize-names? - Normalize property names (default: true)
   - :expand-shorthands? - Expand shorthand properties (default: false)

   Returns: vector of [property value] pairs (may be multiple if prefixed/expanded)"
  [property value & {:keys [vendor-prefixes? normalize-names? expand-shorthands?]
                     :or {vendor-prefixes? false
                          normalize-names? true
                          expand-shorthands? false}}]
  (let [;; 1. Normalize property name
        normalized-prop (if normalize-names?
                         (normalize-property-name property)
                         (name property))

        ;; 2. Expand shorthands
        expanded (if expand-shorthands?
                  (expand-shorthand-properties normalized-prop value)
                  [[normalized-prop value]])

        ;; 3. Add vendor prefixes
        with-prefixes (if vendor-prefixes?
                       (mapcat (fn [[p v]] (generate-prefixed-properties p v))
                               expanded)
                       expanded)]

    with-prefixes))

(defn process-css-properties
  "Process a map of CSS properties with all transformations.

   Returns: vector of [property value] pairs in order"
  [css-props & opts]
  (mapcat (fn [[property value]]
            (apply process-css-property property value opts))
          css-props))

;; ============================================================================
;; CSS STRING GENERATION
;; ============================================================================

(defn properties-to-css-string
  "Convert processed properties to CSS string.

   Options:
   - :minify? - Remove whitespace (default: false)
   - :trailing-semicolon? - Add trailing semicolon (default: true)"
  [properties & {:keys [minify? trailing-semicolon?]
                 :or {minify? false
                      trailing-semicolon? true}}]
  (let [separator (if minify? ";" "; ")
        declarations (map (fn [[prop val]]
                           (str prop (if minify? ":" ": ") val))
                         properties)
        css-str (str/join separator declarations)]
    (if trailing-semicolon?
      (if (str/ends-with? css-str ";")
        css-str
        (str css-str ";"))
      css-str)))

(defn css-map-to-string
  "Convert CSS properties map to string with full processing.

   Options:
   - :vendor-prefixes? - Add vendor prefixes (default: false)
   - :normalize-names? - Normalize property names (default: true)
   - :expand-shorthands? - Expand shorthand properties (default: false)
   - :minify? - Minify output (default: false)
   - :trailing-semicolon? - Add trailing semicolon (default: true)"
  [css-props & {:as opts}]
  (let [processed (apply process-css-properties css-props (mapcat identity opts))]
    (apply properties-to-css-string processed (mapcat identity opts))))
