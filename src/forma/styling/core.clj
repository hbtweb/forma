(ns forma.styling.core
  "Styling system loading and application
   Handles loading styling systems (Tailwind, shadcn-ui, etc.) with extension support,
   stacking multiple systems, and applying styles to elements"
  (:require [clojure.string :as str]))

(defn deep-merge
  "Deep merge two maps, with the second map taking precedence"
  [a b]
  (if (and (map? a) (map? b))
    (merge-with deep-merge a b)
    b))

(defn- option-entry [m k]
  (when (and (map? m) (contains? m k))
    {:value (get m k)}))

(defn- resolve-styling-option
  [context styling-config element-type resolved-props option default]
  (let [sources [(option-entry (get resolved-props :styling-options) option)
                 (option-entry (get context :styling) option)
                 (option-entry (:styling-config styling-config) option)
                 (option-entry (get-in styling-config [:components element-type :styling-config]) option)]]
    (loop [[source & rest] sources]
      (if source
        (:value source)
        (if (seq rest)
          (recur rest)
          default)))))

(defn- classes->seq [value]
  (cond
    (nil? value) []
    (vector? value) (mapcat classes->seq value)
    (string? value) (-> value
                        str/trim
                        (str/split #"\s+")
                        (->> (remove str/blank?)))
    :else [(str value)]))

(defn- aggregate-class-provenance
  [class-source-pairs dedupe?]
  (reduce (fn [{:keys [order provenance]} [cls detail]]
            (let [updated-provenance (update provenance cls (fnil conj []) detail)
                  already-present? (some #(= % cls) order)]
              (if (and dedupe? already-present?)
                {:order order :provenance updated-provenance}
                {:order (conj order cls) :provenance updated-provenance})))
          {:order [] :provenance {}}
          class-source-pairs))

(defn- load-styling-system*
  [system-name project-name load-resource-fn visited]
  (when (visited system-name)
    (throw (ex-info "Styling system cycle detected"
                    {:cycle (conj (vec visited) system-name)})))
  (let [styling (or (load-resource-fn (str "styles/" (name system-name) ".edn") project-name)
                    {})
        extends (get styling :extends)
        visited' (conj visited system-name)]
    (cond
      (keyword? extends)
      (deep-merge (load-styling-system* extends project-name load-resource-fn visited') styling)

      (sequential? extends)
      (reduce (fn [acc system]
                (deep-merge acc (load-styling-system* system project-name load-resource-fn visited')))
              styling
              extends)

      :else
      styling)))

(defn load-styling-system
  "Load styling system, resolving :extends if present
   Optionally takes project-name for project-aware loading
   Takes load-resource-fn to avoid circular dependency with forma.compiler"
  ([system-name load-resource-fn]
   (load-styling-system system-name nil load-resource-fn))
  ([system-name project-name load-resource-fn]
   (load-styling-system* system-name project-name load-resource-fn #{})))

(defn- detect-extension-overlap
  "Detect if a system in the stack extends another system already in the stack.

   Returns: {:has-overlap? boolean
             :overlaps [{:system :shadcn-ui :extends :tailwind :already-in-stack true}]}

   Edge Case #5: Stacking Extended Systems (Duplicate Classes)"
  [styling-stack styling-systems]
  (let [stack-set (set styling-stack)
        overlaps (for [[idx system] (map-indexed vector styling-stack)
                       :let [config (nth styling-systems idx)
                             extends (get config :extends)]
                       :when (some? extends)
                       extended (if (sequential? extends) extends [extends])
                       :when (contains? stack-set extended)]
                   {:system system
                    :extends extended
                    :already-in-stack true
                    :position-in-stack (.indexOf (vec styling-stack) extended)})]
    {:has-overlap? (seq overlaps)
     :overlaps (vec overlaps)}))

(defn load-styling-stack
  "Load all styling systems in stack, resolving extensions

   Edge Case #5: Detects when stacked systems extend each other.
   Options (via opts map):
   - :allow-stacking? - Allow stacking even with overlap (default: false from config)
   - :warn-on-overlap? - Emit warning when overlap detected (default: true)
   - :dedupe-extensions? - Auto-dedupe by skipping already-extended systems (default: true)

   When :dedupe-extensions? is true, systems that extend ones already in stack
   are kept, but their extended parent is not duplicated.

   Optionally takes project-name for project-aware loading
   Takes load-resource-fn to avoid circular dependency with forma.compiler"
  ([styling-stack load-resource-fn]
   (load-styling-stack styling-stack nil load-resource-fn {}))
  ([styling-stack project-name load-resource-fn]
   (load-styling-stack styling-stack project-name load-resource-fn {}))
  ([styling-stack project-name load-resource-fn opts]
   (let [systems (mapv #(load-styling-system % project-name load-resource-fn) styling-stack)
         overlap-info (detect-extension-overlap styling-stack systems)
         warn-on-overlap? (get opts :warn-on-overlap? true)
         allow-stacking? (get opts :allow-stacking? false)
         dedupe-extensions? (get opts :dedupe-extensions? true)]

     ;; Warn if overlap detected and warnings enabled
     (when (and (:has-overlap? overlap-info) warn-on-overlap?)
       (doseq [{:keys [system extends]} (:overlaps overlap-info)]
         (println (str "WARNING: Styling system :" (name system)
                       " extends :" (name extends)
                       " which is already in the stack."
                       (if dedupe-extensions?
                         " Extension will be deduplicated automatically."
                         " This may cause duplicate classes.")))))

     ;; Return systems (deduplication happens during class application)
     systems)))

(defn- get-variant-dimensions
  "Get variant dimensions and their order from styling config.

   Edge Case #8: Multiple Variant Dimensions
   Supports multiple variant dimensions like :variant, :size, :tone applied
   in a declared order for predictable results.

   Returns: vector of dimension keywords in application order
   Example: [:variant :size :tone]"
  [element-styles]
  (or (get element-styles :variant-order)
      ;; Default order if not specified
      [:variant :size :tone :state :theme]))

(defn- collect-variant-classes
  "Collect variant classes from multiple dimensions.

   Edge Case #8: Applies variant dimensions in declared order.

   Arguments:
   - element-styles: styling config for this element type
   - resolved-props: properties after inheritance resolution

   Returns: sequence of [class {:dimension :variant :value :primary}] pairs"
  [element-styles resolved-props system-name]
  (let [variants (get element-styles :variants {})
        dimensions (get-variant-dimensions element-styles)]
    ;; Use keep to filter out nils, then flatten the results
    (mapcat identity
      (keep
       (fn [dimension]
         (when-let [value (get resolved-props dimension)]
           (let [variant-def (get variants dimension)
                 variant-classes (cond
                                  ;; Nested structure: {:variant {:primary [...] :secondary [...]}}
                                  (map? variant-def)
                                  (get variant-def value)

                                  ;; Flat structure (backward compat): {:variants {:primary [...]}}
                                  (and (= dimension :variant) (contains? variants value))
                                  (get variants value)

                                  :else nil)]
             (when variant-classes
               (map (fn [cls]
                      [cls {:system system-name
                            :source :variant
                            :dimension dimension
                            :value value}])
                    (classes->seq variant-classes))))))
       dimensions))))

(defn apply-styling-from-stack
  "Apply styling from multiple stacked styling systems.

   Edge Case #5: Handles deduplication when systems extend each other.
   Edge Case #8: Supports multiple variant dimensions (:variant, :size, :tone, etc.)

   Arguments:
   - props: element properties map
   - element-type: element type keyword (e.g., :button)
   - styling-configs: vector of styling config maps (from load-styling-stack)
   - resolved-props: properties after inheritance resolution
   - context: compilation context with configuration options

   Returns: props with :class updated to include all styling system classes,
   deduplicated while preserving order."
  [props element-type styling-configs resolved-props context]
  (let [dedupe? (get-in context [:styling-options :dedupe-classes?] true)

        ;; Collect all classes from all systems with provenance
        class-source-pairs
        (for [[idx config] (map-indexed vector styling-configs)
              :let [styling (get config :components {})
                    element-styles (get styling element-type)
                    system-name (get config :system-name (keyword (str "system-" idx)))]
              :when element-styles
              :let [base-classes (classes->seq (get element-styles :base))
                    ;; Edge Case #8: Collect variant classes from multiple dimensions
                    variant-pairs (collect-variant-classes element-styles resolved-props system-name)
                    base-pairs (mapv (fn [cls]
                                      [cls {:system system-name
                                            :source :base}])
                                    base-classes)
                    ;; Ensure variant-pairs is a vector to avoid lazy seq issues
                    variant-pairs-vec (vec variant-pairs)
                    all-pairs (concat base-pairs variant-pairs-vec)]]
          all-pairs)

        ;; Flatten nested pairs - mapcat instead of apply concat for clarity
        flattened-pairs (mapcat identity class-source-pairs)

        ;; Add existing explicit classes at end (highest priority)
        explicit-classes (classes->seq (get resolved-props :class))
        explicit-pairs (map (fn [cls] [cls {:source :explicit}]) explicit-classes)

        all-pairs (concat flattened-pairs explicit-pairs)

        ;; Aggregate with deduplication if enabled
        {:keys [order provenance]} (aggregate-class-provenance all-pairs dedupe?)

        class-string (str/join " " order)]

    (cond-> props
      (seq class-string) (assoc :class class-string)
      ;; Optionally attach provenance metadata for debugging/tooling
      (get-in context [:styling-options :record-provenance?] false)
      (assoc-in [:meta :class-provenance] provenance))))

(defn apply-styling-from-config
  "Apply styling from a single styling config
   Helper function for apply-styling to support stacking

   TODO: Full implementation with provenance tracking (see STYLING-EDGE-CASES.md)"
  ([props element-type styling-config resolved-props]
   (apply-styling-from-config props element-type styling-config resolved-props {}))
  ([props element-type styling-config resolved-props context]
   ;; Use stack application with single system
   (apply-styling-from-stack props element-type [styling-config] resolved-props context)))

