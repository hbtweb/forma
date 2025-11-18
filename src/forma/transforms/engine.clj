(ns forma.transforms.engine
  "Generic transform engine - applies EDN-defined value transforms

  This is the core of Forma's platform-agnostic compilation.
  All platform-specific logic is defined in EDN config files,
  and this engine applies those transforms bidirectionally.

  Transform Types:
  - :identity      - Pass through unchanged
  - :map-lookup    - Simple value mapping
  - :conditional   - Conditional logic
  - :template      - String templating
  - :regex-extract - Pattern extraction
  - :path-access   - Nested property access
  - :construct     - Build objects from templates
  - :function      - Call registered function

  Example EDN Transform:
  {:type :regex-extract
   :pattern \"repeat\\((\\d+),\\s*1fr\\)\"
   :capture-group 1
   :parse-as :integer
   :default 4}

  Usage:
  (apply-transform \"repeat(4, 1fr)\" transform-config transform-registry)
  => 4"
  (:require [clojure.string :as str]))

;; =============================================================================
;; Transform Function Registry
;; =============================================================================

(defonce ^:private transform-registry (atom {}))

(defn register-transform-fn!
  "Register a transform function by keyword

  Example:
  (register-transform-fn! :css-gradient-to-oxygen
    forma.transforms.oxygen/css-gradient-to-oxygen)"
  [fn-key fn-var]
  (swap! transform-registry assoc fn-key fn-var))

(defn get-transform-fn
  "Get a registered transform function by keyword"
  [fn-key]
  (get @transform-registry fn-key))

;; =============================================================================
;; Template Rendering
;; =============================================================================

(defn render-template
  "Render a template string with variable substitution

  Template: 'repeat({{count}}, 1fr)'
  Context: {:count 4}
  Result: 'repeat(4, 1fr)'"
  [template context]
  (reduce-kv
    (fn [s k v]
      (str/replace s (str "{{" (name k) "}}") (str v)))
    template
    context))

;; =============================================================================
;; Core Transform Functions
;; =============================================================================

(defmulti apply-transform
  "Apply a transform to a value based on transform config

  Dispatches on :type key in transform-config"
  (fn [_value transform-config _all-transforms _context]
    (:type transform-config)))

(defmethod apply-transform :identity
  [value _transform-config _all-transforms _context]
  value)

(defmethod apply-transform :map-lookup
  [value transform-config _all-transforms _context]
  (get (:map transform-config) value (:default transform-config)))

(defmethod apply-transform :template
  [value transform-config _all-transforms context]
  (let [template (:template transform-config)
        context-data (if (map? value) value (assoc context :value value))]
    (render-template template context-data)))

(defmethod apply-transform :regex-extract
  [value transform-config _all-transforms _context]
  (when value
    (let [pattern (re-pattern (:pattern transform-config))
          match (re-find pattern (str value))
          capture-group (:capture-group transform-config)
          parse-as (:parse-as transform-config)]
      (if match
        (let [captured (if capture-group
                        (nth match capture-group)
                        (second match))
              parsed (case parse-as
                       :integer (Integer/parseInt captured)
                       :float (Double/parseDouble captured)
                       :boolean (Boolean/parseBoolean captured)
                       captured)]
          parsed)
        (:default transform-config)))))

(defmethod apply-transform :regex-extract-map
  [value transform-config _all-transforms _context]
  (when value
    (let [pattern (re-pattern (:pattern transform-config))
          match (re-find pattern (str value))
          captures (:captures transform-config)
          output-template (:output transform-config)]
      (if match
        (let [captured-values (reduce-kv
                                (fn [m k idx]
                                  (assoc m k (nth match idx)))
                                {}
                                captures)
              ;; Parse numbers if specified
              parsed-values (if (:parse-number transform-config)
                             (update-vals captured-values
                               (fn [v]
                                 (if (re-matches #"\d+" v)
                                   (Integer/parseInt v)
                                   v)))
                             captured-values)]
          (render-template (str output-template) parsed-values))
        (:default transform-config)))))

(defmethod apply-transform :path-access
  [value transform-config _all-transforms _context]
  (get-in value (:path transform-config) (:default transform-config)))

(defmethod apply-transform :construct
  [value transform-config _all-transforms context]
  (let [template (:template transform-config)
        context-data (if (map? value) value (assoc context :value value))]
    (cond
      ;; If template is a map, recursively render all values
      (map? template)
      (reduce-kv
        (fn [m k v]
          (assoc m k (if (string? v)
                      (render-template v context-data)
                      v)))
        {}
        template)

      ;; If template is a string, render it
      (string? template)
      (render-template template context-data)

      ;; Otherwise return as-is
      :else template)))

(defmethod apply-transform :conditional
  [value transform-config all-transforms context]
  (let [rules (:rules transform-config)
        matching-rule (first
                        (filter
                          (fn [rule]
                            (if-let [when-clause (:when rule)]
                              (let [check-key (:key when-clause)
                                    check-value (:value when-clause)
                                    actual-value (get context check-key value)]
                                (= actual-value check-value))
                              (:else rule)))
                          rules))]
    (if matching-rule
      (if-let [then-value (:then matching-rule)]
        then-value
        (if-let [else-value (:else matching-rule)]
          else-value
          value))
      value)))

(defmethod apply-transform :function
  [value transform-config all-transforms context]
  (let [fn-key (:fn transform-config)
        transform-fn (get-transform-fn fn-key)
        args (:args transform-config)]
    (if transform-fn
      (if args
        ;; Call with multiple args from context
        (apply transform-fn (map #(get context %) args))
        ;; Call with single value
        (transform-fn value))
      (throw (ex-info (str "Transform function not found: " fn-key)
                     {:fn-key fn-key
                      :available-fns (keys @transform-registry)})))))

(defmethod apply-transform :chain
  [value transform-config all-transforms context]
  (let [steps (:steps transform-config)]
    (reduce
      (fn [v step-key]
        (let [step-config (get all-transforms step-key)]
          (apply-transform v step-config all-transforms context)))
      value
      steps)))

(defmethod apply-transform :default
  [value transform-config _all-transforms _context]
  (throw (ex-info (str "Unknown transform type: " (:type transform-config))
                 {:transform-config transform-config
                  :value value})))

;; =============================================================================
;; Property Transformation
;; =============================================================================

(defn transform-property
  "Transform a single property from source to target format

  Args:
  - source-value: The value to transform
  - mapping: Property mapping config from EDN
  - all-transforms: Map of all available transforms
  - context: Additional context for transforms
  - direction: :forward or :reverse

  Returns: Transformed value"
  [source-value mapping all-transforms context direction]
  (let [transform-key (if (= direction :forward)
                       (:forward mapping (:transform mapping))
                       (:reverse mapping))
        transform-config (get all-transforms transform-key)]
    (if transform-config
      (apply-transform source-value transform-config all-transforms context)
      source-value)))

(defn transform-properties
  "Transform all properties from source to target format

  Args:
  - source-props: Source property map
  - property-mappings: Vector of property mapping configs
  - all-transforms: Map of all available transforms
  - direction: :forward or :reverse

  Returns: Transformed property map"
  [source-props property-mappings all-transforms direction]
  (reduce
    (fn [target-props mapping]
      (let [source-key (if (= direction :forward)
                        (:forma-key mapping)
                        (:target-key mapping (:forma-key mapping)))
            source-value (get source-props source-key)
            target-path (if (= direction :forward)
                         (:target-path mapping)
                         [(:forma-key mapping)])
            context (assoc source-props :_current-value source-value)
            transformed-value (transform-property source-value mapping all-transforms context direction)]
        (if (some? transformed-value)
          (assoc-in target-props target-path transformed-value)
          target-props)))
    {}
    property-mappings))

;; =============================================================================
;; Initialization
;; =============================================================================

(defn load-transform-functions!
  "Load all transform functions from a platform config

  Reads :transform-functions from config and registers them"
  [platform-config]
  (when-let [transform-fns (:transform-functions platform-config)]
    (doseq [[fn-key fn-config] transform-fns]
      (when-let [impl (:implementation fn-config)]
        (try
          (let [fn-var (requiring-resolve (symbol impl))]
            (register-transform-fn! fn-key fn-var))
          (catch Exception e
            (println (str "Warning: Could not load transform function "
                         fn-key " from " impl ": " (.getMessage e)))))))))

;; =============================================================================
;; Public API
;; =============================================================================

(defn compile-with-transforms
  "Compile properties using EDN-defined transforms

  Args:
  - source-props: Source property map (e.g., Forma EDN)
  - platform-config: Platform config with :property-mappings and :transforms
  - direction: :forward (Forma→Platform) or :reverse (Platform→Forma)

  Returns: Transformed property map"
  [source-props platform-config direction]
  (let [property-mappings (get-in platform-config [:parser :property-mappings])
        all-transforms (get platform-config :transforms)]
    (transform-properties source-props property-mappings all-transforms direction)))