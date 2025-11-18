(ns forma.policy.accessibility
  "Phase 5.6 - Accessibility Policy Checks

   Validates accessibility compliance:
   - ARIA attributes (aria-label, role, aria-* attributes)
   - Semantic HTML (heading hierarchy, alt text, labels)
   - Keyboard navigation (tabindex, focus indicators)
   - Color contrast (WCAG AA/AAA compliance)

   Usage:
     (check-aria-attributes element check config context)
     (check-semantic-html element check config context)
     (check-color-contrast element check config context)"
  (:require [clojure.string :as str]
            [forma.policy.core :as policy]))

;;
;; ARIA Attribute Validation
;;

(def interactive-elements
  "Elements that typically require ARIA attributes"
  #{:button :link :tooltip :progress :alert :dialog :menu :tab :accordion})

(def required-aria-attrs
  "Required ARIA attributes for specific elements"
  {:button [:aria-label]  ; If no text content
   :tooltip [:role :aria-label]
   :progress [:role :aria-valuenow :aria-valuemin :aria-valuemax]
   :alert [:role]
   :dialog [:role :aria-modal :aria-labelledby]
   :menu [:role]
   :tab [:role :aria-selected]})

(defn has-text-content?
  "Check if element has text content (children)"
  [element]
  (boolean
   (some string? (:children element))))

(defn has-aria-attr?
  "Check if element has specific ARIA attribute"
  [props attr]
  (contains? props attr))

(defmethod policy/apply-policy-check :aria-required
  [element check config context]
  (let [props (:props element)
        element-type (:type element)
        required-attrs (get required-aria-attrs element-type)
        violations []]

    (if (and required-attrs
            (contains? interactive-elements element-type))
      ;; Check for required ARIA attributes
      (concat
       violations
       (reduce (fn [acc attr]
                (cond
                  ;; Special case: button needs aria-label only if no text
                  (and (= element-type :button)
                      (= attr :aria-label)
                      (has-text-content? element))
                  acc

                  ;; Check if required attribute is missing
                  (not (has-aria-attr? props attr))
                  (conj acc
                       (policy/violation
                        :accessibility
                        :error
                        :aria-attribute-required
                        element-type
                        (str "Element '" (name element-type) "' is missing required ARIA attribute '" (name attr) "'")
                        (str "Add " (name attr) " attribute for accessibility")
                        nil
                        {:required-attribute attr}))

                  :else
                  acc))
              []
              required-attrs))
      ;; No required attrs for this element
      [])))

;;
;; Semantic HTML Validation
;;

(def heading-levels #{:h1 :h2 :h3 :h4 :h5 :h6})

(defn heading-level
  "Extract numeric heading level from element type"
  [element-type]
  (when (heading-levels element-type)
    (Integer/parseInt (str (last (name element-type))))))

(defmethod policy/apply-policy-check :semantic-html
  [element check config context]
  (let [props (:props element)
        element-type (:type element)
        check-headings? (get-in check [:config :check-headings] true)
        check-alt-text? (get-in check [:config :check-alt-text] true)
        check-labels? (get-in check [:config :check-labels] true)
        violations []]

    (concat
     violations

     ;; Check heading hierarchy
     (when (and check-headings? (heading-levels element-type))
       (let [level (heading-level element-type)
             prev-level (get context :last-heading-level 0)]
         (if (and (> level 1)
                 (> level (inc prev-level)))
           [(policy/violation
             :accessibility
             :warning
             :heading-hierarchy
             element-type
             (str "Heading level " level " skips level " (inc prev-level) " (invalid hierarchy)")
             (str "Use h" (inc prev-level) " instead, or add intermediate headings")
             nil
             {:current-level level :previous-level prev-level})]
           [])))

     ;; Check alt text for images
     (when (and check-alt-text? (#{:image :img} element-type))
       (if-not (get props :alt)
         [(policy/violation
           :accessibility
           :error
           :image-alt-text
           element-type
           "Image is missing 'alt' attribute"
           "Add descriptive alt text for screen readers"
           nil
           nil)]
         []))

     ;; Check labels for form inputs
     (when (and check-labels? (#{:input :textarea :select} element-type))
       (let [has-label? (or (get props :label)
                           (get props :aria-label)
                           (get props :aria-labelledby))]
         (if-not has-label?
           [(policy/violation
             :accessibility
             :warning
             :form-input-label
             element-type
             "Form input is missing a label"
             "Add a label, aria-label, or aria-labelledby attribute"
             nil
             nil)]
           []))))))

;;
;; Color Contrast Checks
;;

(defn parse-hex-color
  "Parse hex color to RGB values"
  [hex]
  (when (and (string? hex) (re-matches #"#[0-9a-fA-F]{6}" hex))
    (let [r (Integer/parseInt (subs hex 1 3) 16)
          g (Integer/parseInt (subs hex 3 5) 16)
          b (Integer/parseInt (subs hex 5 7) 16)]
      {:r r :g g :b b})))

(defn relative-luminance
  "Calculate relative luminance for color contrast (WCAG formula)"
  [{:keys [r g b]}]
  (let [to-linear (fn [c]
                   (let [c-srgb (/ c 255.0)]
                     (if (<= c-srgb 0.03928)
                       (/ c-srgb 12.92)
                       (Math/pow (/ (+ c-srgb 0.055) 1.055) 2.4))))
        r-linear (to-linear r)
        g-linear (to-linear g)
        b-linear (to-linear b)]
    (+ (* 0.2126 r-linear)
       (* 0.7152 g-linear)
       (* 0.0722 b-linear))))

(defn contrast-ratio
  "Calculate contrast ratio between two colors (WCAG formula)"
  [color1 color2]
  (let [l1 (relative-luminance color1)
        l2 (relative-luminance color2)
        lighter (max l1 l2)
        darker (min l1 l2)]
    (/ (+ lighter 0.05)
       (+ darker 0.05))))

(defn wcag-level
  "Determine WCAG compliance level for contrast ratio"
  [ratio font-size]
  (let [large-text? (or (>= font-size 24)
                       (and (>= font-size 19) (>= font-size 18.5))) ; Bold
        aa-threshold (if large-text? 3.0 4.5)
        aaa-threshold (if large-text? 4.5 7.0)]
    (cond
      (>= ratio aaa-threshold) :AAA
      (>= ratio aa-threshold) :AA
      :else :fail)))

(defmethod policy/apply-policy-check :color-contrast
  [element check config context]
  (let [props (:props element)
        element-type (:type element)
        min-ratio (get-in check [:config :min-ratio] 4.5)
        wcag-level-required (get-in check [:config :wcag-level] :AA)

        text-color (or (get props :color) (get props :text-color))
        bg-color (or (get props :background) (get props :background-color))
        font-size (get props :font-size 16)]

    ;; Only check if we have both text and background colors as hex
    (if (and text-color bg-color
            (string? text-color) (string? bg-color)
            (re-matches #"#[0-9a-fA-F]{6}" text-color)
            (re-matches #"#[0-9a-fA-F]{6}" bg-color))
      (let [text-rgb (parse-hex-color text-color)
            bg-rgb (parse-hex-color bg-color)
            ratio (contrast-ratio text-rgb bg-rgb)
            level (wcag-level ratio font-size)]

        (if (or (= level :fail)
               (and (= wcag-level-required :AAA) (not= level :AAA)))
          [(policy/violation
            :accessibility
            :warning
            :color-contrast
            element-type
            (str "Color contrast ratio " (format "%.2f" ratio) " does not meet "
                (name wcag-level-required) " standards (minimum " min-ratio ")")
            (str "Adjust text or background colors to improve contrast")
            nil
            {:text-color text-color
             :background-color bg-color
             :contrast-ratio ratio
             :wcag-level level
             :required-level wcag-level-required})]
          []))
      ;; Can't check contrast without valid hex colors
      [])))

;;
;; Keyboard Navigation Checks
;;

(def focusable-elements
  "Elements that should be keyboard-accessible"
  #{:button :link :input :textarea :select :checkbox :radio :toggle})

(defmethod policy/apply-policy-check :keyboard-navigation
  [element check config context]
  (let [props (:props element)
        element-type (:type element)
        check-tabindex? (get-in check [:config :check-tabindex] true)
        check-focus-indicator? (get-in check [:config :check-focus-indicator] true)
        violations []]

    (if (contains? focusable-elements element-type)
      (concat
       violations

       ;; Check for tabindex on custom interactive elements
       (when (and check-tabindex?
                 (not (#{:input :textarea :select :button} element-type)) ; Native focusable
                 (not (contains? props :tabindex)))
         [(policy/violation
           :accessibility
           :warning
           :keyboard-tabindex
           element-type
           "Interactive element may not be keyboard-accessible"
           "Add tabindex='0' for keyboard navigation"
           nil
           nil)])

       ;; Check for focus indicator styles
       (when (and check-focus-indicator?
                 (not (or (get props :focus-outline)
                         (get props :focus-ring)
                         (get props :focus-visible))))
         [(policy/violation
           :accessibility
           :info
           :keyboard-focus-indicator
           element-type
           "Element should have visible focus indicator"
           "Add focus-outline or focus-ring style for keyboard navigation"
           nil
           nil)]))
      ;; Not a focusable element
      [])))

;;
;; Accessibility Score
;;

(defn calculate-accessibility-score
  "Calculate accessibility score for an element (0-100)"
  [element violations]
  (let [error-count (count (filter #(= :error (:severity %)) violations))
        warning-count (count (filter #(= :warning (:severity %)) violations))
        info-count (count (filter #(= :info (:severity %)) violations))

        ;; Scoring: errors -20, warnings -10, info -5
        deductions (+ (* error-count 20)
                     (* warning-count 10)
                     (* info-count 5))
        score (max 0 (- 100 deductions))]
    {:score score
     :errors error-count
     :warnings warning-count
     :info info-count
     :grade (cond
             (>= score 90) :excellent
             (>= score 70) :good
             (>= score 50) :fair
             :else :poor)}))

;;
;; Exports
;;

(comment
  ;; Example usage

  ;; Button without aria-label
  (def button-no-aria
    {:type :button
     :props {:on-click "handleClick()"}})

  (def check-aria
    {:type :aria-required
     :config {}})

  (policy/apply-policy-check button-no-aria check-aria {} {})
  ;; => [violation about missing aria-label]

  ;; Image without alt text
  (def img-no-alt
    {:type :image
     :props {:src "photo.jpg"}})

  (def check-semantic
    {:type :semantic-html
     :config {:check-alt-text true}})

  (policy/apply-policy-check img-no-alt check-semantic {} {})
  ;; => [violation about missing alt]

  ;; Low contrast colors
  (def low-contrast
    {:type :text
     :props {:color "#777777"
             :background "#888888"
             :font-size 16}})

  (def check-contrast
    {:type :color-contrast
     :config {:min-ratio 4.5 :wcag-level :AA}})

  (policy/apply-policy-check low-contrast check-contrast {} {})
  ;; => [violation about insufficient contrast]

  ;; Calculate accessibility score
  (def all-violations
    [(policy/violation :accessibility :error :aria-required :button "Missing aria-label")
     (policy/violation :accessibility :warning :color-contrast :text "Low contrast")])

  (calculate-accessibility-score button-no-aria all-violations)
  ;; => {:score 70 :errors 1 :warnings 1 :info 0 :grade :good}
  )
