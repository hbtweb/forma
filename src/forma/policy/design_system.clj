(ns forma.policy.design-system
  "Phase 5.6 - Design System Policy Checks

   Validates design system consistency:
   - Token enforcement (colors, spacing, typography)
   - Color palette compliance
   - Spacing scale adherence
   - Typography scale consistency
   - Border-radius consistency

   Usage:
     (check-token-enforcement element check config context)
     (check-color-palette element check config context)
     (check-spacing-scale element check config context)"
  (:require [clojure.string :as str]
            [forma.policy.core :as policy]))

;;
;; Pattern Detection
;;

(def hardcoded-color-pattern
  "Regex pattern for hardcoded color values (hex, rgb, rgba, hsl)"
  #"(?i)#[0-9a-f]{3,8}|rgba?\([^)]+\)|hsla?\([^)]+\)")

(def hardcoded-spacing-pattern
  "Regex pattern for hardcoded spacing values (px, rem, em)"
  #"\d+(?:px|rem|em|pt)")

(def hardcoded-size-pattern
  "Regex pattern for hardcoded size values"
  #"\d+(?:px|rem|em|%|vw|vh)")

(defn token-reference?
  "Check if a value is a token reference ($token.path)"
  [value]
  (and (string? value)
       (str/starts-with? value "$")))

(defn extract-color-values
  "Extract all color values from element props"
  [props]
  (let [color-props [:color :background :background-color :border-color
                    :text-color :fill :stroke]]
    (reduce (fn [acc prop]
             (if-let [value (get props prop)]
               (conj acc {:prop prop :value value})
               acc))
           []
           color-props)))

(defn extract-spacing-values
  "Extract all spacing values from element props"
  [props]
  (let [spacing-props [:padding :margin :gap :spacing
                      :padding-top :padding-right :padding-bottom :padding-left
                      :margin-top :margin-right :margin-bottom :margin-left]]
    (reduce (fn [acc prop]
             (if-let [value (get props prop)]
               (conj acc {:prop prop :value value})
               acc))
           []
           spacing-props)))

(defn extract-typography-values
  "Extract all typography values from element props"
  [props]
  (let [typography-props [:font-size :line-height :font-weight :font-family]]
    (reduce (fn [acc prop]
             (if-let [value (get props prop)]
               (conj acc {:prop prop :value value})
               acc))
           []
           typography-props)))

;;
;; Token Enforcement Checks
;;

(defmethod policy/apply-policy-check :token-enforcement
  [element check config context]
  (let [props (:props element)
        element-type (:type element)
        check-colors? (get-in check [:config :check-colors] true)
        check-spacing? (get-in check [:config :check-spacing] true)
        check-typography? (get-in check [:config :check-typography] true)
        violations []]

    ;; Check colors
    (concat
     violations
     (when check-colors?
       (let [color-values (extract-color-values props)]
         (reduce (fn [acc {:keys [prop value]}]
                  (if (and (string? value)
                          (not (token-reference? value))
                          (re-find hardcoded-color-pattern value))
                    (conj acc
                         (policy/violation
                          :design-system
                          :error
                          :token-enforcement-colors
                          element-type
                          (str "Property '" (name prop) "' uses hardcoded color '" value "' instead of token")
                          (str "Replace with a color token like $colors.primary.500")
                          nil
                          {:property prop :value value}))
                    acc))
                []
                color-values)))

     ;; Check spacing
     (when check-spacing?
       (let [spacing-values (extract-spacing-values props)]
         (reduce (fn [acc {:keys [prop value]}]
                  (if (and (string? value)
                          (not (token-reference? value))
                          (re-find hardcoded-spacing-pattern value))
                    (conj acc
                         (policy/violation
                          :design-system
                          :error
                          :token-enforcement-spacing
                          element-type
                          (str "Property '" (name prop) "' uses hardcoded spacing '" value "' instead of token")
                          (str "Replace with a spacing token like $spacing.md or $spacing.lg")
                          nil
                          {:property prop :value value}))
                    acc))
                []
                spacing-values)))

     ;; Check typography
     (when check-typography?
       (let [typo-values (extract-typography-values props)]
         (reduce (fn [acc {:keys [prop value]}]
                  (if (and (string? value)
                          (not (token-reference? value))
                          (or (and (= prop :font-size) (re-find hardcoded-size-pattern value))
                              (and (= prop :font-weight) (number? value))
                              (and (= prop :line-height) (number? value))))
                    (conj acc
                         (policy/violation
                          :design-system
                          :warning
                          :token-enforcement-typography
                          element-type
                          (str "Property '" (name prop) "' uses hardcoded value '" value "' instead of token")
                          (str "Replace with a typography token like $typography.font-size.md")
                          nil
                          {:property prop :value value}))
                    acc))
                []
                typo-values))))))

;;
;; Color Palette Compliance
;;

(defmethod policy/apply-policy-check :color-palette
  [element check config context]
  (let [props (:props element)
        element-type (:type element)
        allowed-sources (get-in check [:config :allowed-sources] [:tokens :theme])
        strict-mode? (get-in check [:config :strict] false)
        color-values (extract-color-values props)]

    (reduce (fn [acc {:keys [prop value]}]
             (cond
               ;; Token reference is always allowed
               (token-reference? value)
               acc

               ;; Check if hardcoded color in strict mode
               (and strict-mode? (re-find hardcoded-color-pattern value))
               (conj acc
                    (policy/violation
                     :design-system
                     :error
                     :color-palette-strict
                     element-type
                     (str "Property '" (name prop) "' uses hardcoded color '" value "' (strict mode)")
                     (str "Only token references are allowed in strict mode")
                     nil
                     {:property prop :value value}))

               ;; Warn about non-token colors in non-strict mode
               (re-find hardcoded-color-pattern value)
               (conj acc
                    (policy/violation
                     :design-system
                     :warning
                     :color-palette-compliance
                     element-type
                     (str "Property '" (name prop) "' uses hardcoded color '" value "'")
                     (str "Consider using a color token for design system consistency")
                     nil
                     {:property prop :value value}))

               :else
               acc))
           []
           color-values)))

;;
;; Spacing Scale Compliance
;;

(defmethod policy/apply-policy-check :spacing-scale
  [element check config context]
  (let [props (:props element)
        element-type (:type element)
        allowed-values (get-in check [:config :allowed-values]
                              [:xs :sm :md :lg :xl :2xl :3xl :4xl])
        spacing-values (extract-spacing-values props)]

    (reduce (fn [acc {:keys [prop value]}]
             (cond
               ;; Token reference is allowed
               (token-reference? value)
               acc

               ;; Check if hardcoded spacing
               (and (string? value) (re-find hardcoded-spacing-pattern value))
               (conj acc
                    (policy/violation
                     :design-system
                     :warning
                     :spacing-scale-compliance
                     element-type
                     (str "Property '" (name prop) "' uses arbitrary spacing '" value "'")
                     (str "Use spacing tokens from the approved scale: " (str/join ", " (map #(str "$spacing." (name %)) allowed-values)))
                     nil
                     {:property prop :value value :allowed-values allowed-values}))

               :else
               acc))
           []
           spacing-values)))

;;
;; Typography Scale Compliance
;;

(defmethod policy/apply-policy-check :typography-scale
  [element check config context]
  (let [props (:props element)
        element-type (:type element)
        check-font-size? (get-in check [:config :check-font-size] true)
        check-line-height? (get-in check [:config :check-line-height] true)
        check-font-weight? (get-in check [:config :check-font-weight] true)
        typo-values (extract-typography-values props)

        approved-font-sizes [:xs :sm :md :lg :xl :2xl :3xl :4xl]
        approved-line-heights [:tight :normal :relaxed]
        approved-font-weights [:normal :medium :semibold :bold]]

    (reduce (fn [acc {:keys [prop value]}]
             (cond
               ;; Token reference is allowed
               (token-reference? value)
               acc

               ;; Check font-size
               (and check-font-size?
                   (= prop :font-size)
                   (string? value)
                   (re-find hardcoded-size-pattern value))
               (conj acc
                    (policy/violation
                     :design-system
                     :warning
                     :typography-font-size-scale
                     element-type
                     (str "Property 'font-size' uses arbitrary value '" value "'")
                     (str "Use font-size tokens from the approved scale: " (str/join ", " (map #(str "$typography.font-size." (name %)) approved-font-sizes)))
                     nil
                     {:property prop :value value}))

               ;; Check line-height
               (and check-line-height?
                   (= prop :line-height)
                   (number? value))
               (conj acc
                    (policy/violation
                     :design-system
                     :warning
                     :typography-line-height-scale
                     element-type
                     (str "Property 'line-height' uses arbitrary value '" value "'")
                     (str "Use line-height tokens: " (str/join ", " (map #(str "$typography.line-height." (name %)) approved-line-heights)))
                     nil
                     {:property prop :value value}))

               ;; Check font-weight
               (and check-font-weight?
                   (= prop :font-weight)
                   (number? value))
               (conj acc
                    (policy/violation
                     :design-system
                     :warning
                     :typography-font-weight-scale
                     element-type
                     (str "Property 'font-weight' uses numeric value '" value "'")
                     (str "Use font-weight tokens: " (str/join ", " (map #(str "$typography.font-weight." (name %)) approved-font-weights)))
                     nil
                     {:property prop :value value}))

               :else
               acc))
           []
           typo-values)))

;;
;; Border Radius Consistency
;;

(defmethod policy/apply-policy-check :border-radius-consistency
  [element check config context]
  (let [props (:props element)
        element-type (:type element)
        border-radius (get props :border-radius)
        approved-values [:none :sm :md :lg :xl :full]]

    (if (and border-radius
            (string? border-radius)
            (not (token-reference? border-radius))
            (re-find hardcoded-size-pattern border-radius))
      [(policy/violation
        :design-system
        :warning
        :border-radius-consistency
        element-type
        (str "Property 'border-radius' uses arbitrary value '" border-radius "'")
        (str "Use border-radius tokens: " (str/join ", " (map #(str "$border-radius." (name %)) approved-values)))
        nil
        {:property :border-radius :value border-radius})]
      [])))

;;
;; Token Usage Statistics
;;

(defn analyze-token-usage
  "Analyze token usage in element props.
   Returns statistics about token vs hardcoded values"
  [element]
  (let [props (:props element)
        all-values (concat (extract-color-values props)
                          (extract-spacing-values props)
                          (extract-typography-values props))
        total-count (count all-values)
        token-count (count (filter #(token-reference? (:value %)) all-values))
        hardcoded-count (- total-count token-count)
        token-percentage (if (zero? total-count)
                          0.0
                          (/ (* 100.0 token-count) total-count))]
    {:total total-count
     :tokens token-count
     :hardcoded hardcoded-count
     :token-percentage token-percentage
     :values all-values}))

;;
;; Exports
;;

(comment
  ;; Example usage

  (def element
    {:type :button
     :props {:background "#4f46e5"           ; Hardcoded color
             :color "#ffffff"                 ; Hardcoded color
             :padding "16px"                  ; Hardcoded spacing
             :font-size "14px"                ; Hardcoded size
             :font-weight 500                 ; Hardcoded weight
             :border-radius "4px"}})          ; Hardcoded radius

  (def check
    {:type :token-enforcement
     :config {:check-colors true
             :check-spacing true
             :check-typography true}})

  (def context {:project-name "my-project"})

  ;; Run token enforcement check
  (policy/apply-policy-check element check {} context)
  ;; => [violation violation ...]

  ;; Analyze token usage
  (analyze-token-usage element)
  ;; => {:total 6 :tokens 0 :hardcoded 6 :token-percentage 0.0}

  ;; Example with tokens
  (def element-with-tokens
    {:type :button
     :props {:background "$colors.primary.500"
             :color "$colors.background"
             :padding "$spacing.md $spacing.lg"
             :font-size "$typography.font-size.sm"
             :border-radius "$border-radius.md"}})

  (analyze-token-usage element-with-tokens)
  ;; => {:total 5 :tokens 5 :hardcoded 0 :token-percentage 100.0}
  )
