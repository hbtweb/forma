(ns forma.policy.performance
  "Phase 5.6 - Performance Policy Checks

   Validates performance metrics:
   - Bundle size limits (HTML, CSS, JS)
   - Unused code detection
   - Optimization validation
   - Image optimization

   Usage:
     (check-bundle-size compiled check config context)
     (check-unused-code elements check config context)
     (check-optimization-required context check config)"
  (:require [clojure.string :as str]
            [clojure.set]
            [forma.policy.core :as policy]))

;;
;; Bundle Size Checks
;;

(defn calculate-bundle-size
  "Calculate bundle size in KB from string content"
  [content]
  (when (string? content)
    (/ (count (.getBytes content "UTF-8")) 1024.0)))

(defn exceeds-limit?
  "Check if size exceeds limit"
  [size limit-kb]
  (> size limit-kb))

(defmethod policy/apply-policy-check :bundle-size
  [element check config context]
  (let [compiled-output (get context :compiled-output)
        limits (get-in check [:config :limits])
        violations []]

    (if (and compiled-output limits)
      (concat
       violations

       ;; Check HTML size
       (when-let [html-limit (get-in limits [:html :max-kb])]
         (let [html-content (get compiled-output :html "")
               html-size (calculate-bundle-size html-content)
               warn-limit (get-in limits [:html :warn-kb] (* html-limit 0.8))]
           (cond
             (exceeds-limit? html-size html-limit)
             [(policy/violation
               :performance
               :error
               :bundle-size-html-exceeded
               :html
               (str "HTML bundle size " (format "%.1f" html-size) "KB exceeds limit of " html-limit "KB")
               (str "Reduce HTML size or increase limit")
               nil
               {:size-kb html-size :limit-kb html-limit})]

             (exceeds-limit? html-size warn-limit)
             [(policy/violation
               :performance
               :warning
               :bundle-size-html-warning
               :html
               (str "HTML bundle size " (format "%.1f" html-size) "KB approaching limit of " html-limit "KB")
               (str "Consider optimizing HTML to stay within limits")
               nil
               {:size-kb html-size :limit-kb html-limit :warn-kb warn-limit})]

             :else
             [])))

       ;; Check CSS size
       (when-let [css-limit (get-in limits [:css :max-kb])]
         (let [css-content (get compiled-output :css "")
               css-size (calculate-bundle-size css-content)
               warn-limit (get-in limits [:css :warn-kb] (* css-limit 0.8))]
           (cond
             (exceeds-limit? css-size css-limit)
             [(policy/violation
               :performance
               :error
               :bundle-size-css-exceeded
               :css
               (str "CSS bundle size " (format "%.1f" css-size) "KB exceeds limit of " css-limit "KB")
               (str "Reduce CSS size, enable minification, or increase limit")
               nil
               {:size-kb css-size :limit-kb css-limit})]

             (exceeds-limit? css-size warn-limit)
             [(policy/violation
               :performance
               :warning
               :bundle-size-css-warning
               :css
               (str "CSS bundle size " (format "%.1f" css-size) "KB approaching limit of " css-limit "KB")
               (str "Consider optimizing CSS to stay within limits")
               nil
               {:size-kb css-size :limit-kb css-limit :warn-kb warn-limit})]

             :else
             [])))

       ;; Check JS size
       (when-let [js-limit (get-in limits [:js :max-kb])]
         (let [js-content (get compiled-output :js "")
               js-size (calculate-bundle-size js-content)
               warn-limit (get-in limits [:js :warn-kb] (* js-limit 0.8))]
           (cond
             (exceeds-limit? js-size js-limit)
             [(policy/violation
               :performance
               :error
               :bundle-size-js-exceeded
               :js
               (str "JS bundle size " (format "%.1f" js-size) "KB exceeds limit of " js-limit "KB")
               (str "Reduce JS size, enable minification, or increase limit")
               nil
               {:size-kb js-size :limit-kb js-limit})]

             (exceeds-limit? js-size warn-limit)
             [(policy/violation
               :performance
               :warning
               :bundle-size-js-warning
               :js
               (str "JS bundle size " (format "%.1f" js-size) "KB approaching limit of " js-limit "KB")
               (str "Consider optimizing JS to stay within limits")
               nil
               {:size-kb js-size :limit-kb js-limit :warn-kb warn-limit})]

             :else
             []))))
      ;; No compiled output or limits configured
      [])))

;;
;; Unused Code Detection
;;

(defn collect-token-references
  "Collect all token references from elements"
  [elements]
  (let [ref-pattern #"\$([a-zA-Z0-9._-]+)"]
    (reduce (fn [refs element]
             (let [props (:props element)
                   values (vals props)
                   matches (mapcat #(when (string? %)
                                    (map second (re-seq ref-pattern %)))
                                  values)]
               (concat refs matches)))
           []
           elements)))

(defn find-unused-tokens
  "Find tokens defined but not used in elements"
  [token-registry element-refs]
  (let [defined-tokens (set (keys token-registry))
        used-tokens (set element-refs)
        unused (clojure.set/difference defined-tokens used-tokens)]
    unused))

(defmethod policy/apply-policy-check :unused-code
  [element check config context]
  (let [token-registry (get context :token-registry {})
        all-elements (get context :all-elements [])
        threshold (get-in check [:config :threshold] 0.1)  ; Warn if >10% unused

        token-refs (collect-token-references all-elements)
        unused-tokens (find-unused-tokens token-registry token-refs)
        unused-count (count unused-tokens)
        total-count (count token-registry)
        unused-percentage (if (zero? total-count) 0.0 (/ unused-count total-count))]

    (if (> unused-percentage threshold)
      [(policy/violation
        :performance
        :warning
        :unused-tokens
        :tokens
        (str unused-count " of " total-count " tokens are unused ("
            (format "%.1f" (* 100 unused-percentage)) "%)")
        (str "Remove unused tokens or increase threshold")
        nil
        {:unused-count unused-count
         :total-count total-count
         :percentage unused-percentage
         :threshold threshold
         :unused-tokens (take 10 unused-tokens)})]  ; Show first 10
      [])))

;;
;; Optimization Validation
;;

(defmethod policy/apply-policy-check :optimization-required
  [element check config context]
  (let [environment (get context :environment :development)
        required-env (get-in check [:config :environment] :production)
        required-opts (get-in check [:config :required] {})
        build-config (get context :build-config {})
        violations []]

    ;; Only check in specified environment
    (if (= environment required-env)
      (concat
       violations

       ;; Check minification enabled
       (when (get required-opts :minification)
         (let [minification-enabled? (get-in build-config [:minification :enabled])]
           (if-not minification-enabled?
             [(policy/violation
               :performance
               :error
               :minification-required
               :build
               (str "Minification is required in " (name environment) " environment")
               (str "Enable minification in build configuration")
               nil
               {:environment environment})]
             [])))

       ;; Check optimization enabled
       (when (get required-opts :optimization)
         (let [optimization-enabled? (get-in build-config [:optimization :enabled])]
           (if-not optimization-enabled?
             [(policy/violation
               :performance
               :error
               :optimization-required
               :build
               (str "Optimization is required in " (name environment) " environment")
               (str "Enable optimization in build configuration")
               nil
               {:environment environment})]
             [])))

       ;; Check dead code elimination
       (when (get required-opts :dead-code-elimination)
         (let [dce-enabled? (get-in build-config [:optimization :dead-code-elimination])]
           (if-not dce-enabled?
             [(policy/violation
               :performance
               :warning
               :dead-code-elimination-recommended
               :build
               (str "Dead code elimination is recommended in " (name environment) " environment")
               (str "Enable dead-code-elimination in optimization config")
               nil
               {:environment environment})]
             []))))
      ;; Not in required environment
      [])))

;;
;; Image Optimization
;;

(defmethod policy/apply-policy-check :image-optimization
  [element check config context]
  (let [element-type (:type element)
        props (:props element)
        max-kb (get-in check [:config :max-kb] 500)
        approved-formats (get-in check [:config :formats] [:webp :avif :jpg :png])
        violations []]

    (if (#{:image :img} element-type)
      (let [src (get props :src)
            width (get props :width)
            height (get props :height)
            format (when (string? src)
                    (keyword (last (str/split src #"\."))))]

        (concat
         violations

         ;; Check format
         (when (and format (not (contains? (set approved-formats) format)))
           [(policy/violation
             :performance
             :warning
             :image-format
             element-type
             (str "Image format '" (name format) "' is not in approved list")
             (str "Use approved formats: " (str/join ", " (map name approved-formats)))
             nil
             {:format format :approved-formats approved-formats})])

         ;; Check dimensions specified
         (when-not (and width height)
           [(policy/violation
             :performance
             :info
             :image-dimensions
             element-type
             "Image missing width/height attributes"
             "Add explicit dimensions to prevent layout shift"
             nil
             nil)])))
      ;; Not an image element
      [])))

;;
;; Performance Score
;;

(defn calculate-performance-score
  "Calculate performance score based on violations (0-100)"
  [violations bundle-sizes]
  (let [error-count (count (filter #(= :error (:severity %)) violations))
        warning-count (count (filter #(= :warning (:severity %)) violations))

        ;; Bundle size score (0-40 points)
        total-size (reduce + 0 (vals bundle-sizes))
        size-score (max 0 (- 40 (* 0.1 total-size)))  ; -0.1 per KB

        ;; Violation score (0-60 points)
        deductions (+ (* error-count 15) (* warning-count 5))
        violation-score (max 0 (- 60 deductions))

        total-score (+ size-score violation-score)]

    {:score total-score
     :size-score size-score
     :violation-score violation-score
     :bundle-sizes bundle-sizes
     :total-size-kb total-size
     :errors error-count
     :warnings warning-count
     :grade (cond
             (>= total-score 90) :excellent
             (>= total-score 70) :good
             (>= total-score 50) :fair
             :else :poor)}))

;;
;; Exports
;;

(comment
  ;; Example usage

  ;; Check bundle size
  (def context-with-output
    {:compiled-output {:html "<html>...</html>"
                      :css "body { ... }"
                      :js "function() { ... }"}})

  (def check-bundle
    {:type :bundle-size
     :config {:limits {:html {:max-kb 500 :warn-kb 400}
                       :css {:max-kb 100 :warn-kb 80}
                       :js {:max-kb 200 :warn-kb 150}}}})

  (policy/apply-policy-check {} check-bundle {} context-with-output)
  ;; => [violations if any exceed limits]

  ;; Check unused tokens
  (def context-with-tokens
    {:token-registry {"colors.primary" "#4f46e5"
                     "colors.secondary" "#64748b"
                     "spacing.md" "12px"}
     :all-elements [{:props {:background "$colors.primary"}}]})

  (def check-unused
    {:type :unused-code
     :config {:threshold 0.1}})

  (policy/apply-policy-check {} check-unused {} context-with-tokens)
  ;; => [violation about unused tokens]

  ;; Check optimization required
  (def prod-context
    {:environment :production
     :build-config {:minification {:enabled false}
                   :optimization {:enabled true}}})

  (def check-opt
    {:type :optimization-required
     :config {:environment :production
             :required {:minification true :optimization true}}})

  (policy/apply-policy-check {} check-opt {} prod-context)
  ;; => [violation about minification not enabled]

  ;; Calculate performance score
  (calculate-performance-score
   [(policy/violation :performance :error :bundle-size :html "Too large")]
   {:html 250.5 :css 45.2 :js 120.8})
  ;; => {:score 65.3 :grade :fair ...}
  )
