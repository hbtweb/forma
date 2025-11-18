(ns forma.config.precedence
  "Configuration precedence resolver for Forma compiler

   Implements Edge Case #4: Configuration Precedence

   Precedence order (highest to lowest):
   1. Element override (per-instance in props)
   2. Project config (projects/my-app/config.edn)
   3. Styling system global config (styles/foo.edn :styling-config)
   4. Component-specific config (within styling system)
   5. Default behaviour (forma/config.edn or compiler fallback)

   All configuration options follow this precedence chain for deterministic resolution."
  (:require [clojure.string :as str]))

;; ============================================================================
;; PRECEDENCE RESOLUTION
;; ============================================================================

(defn resolve-config-option
  "Resolve a configuration option following strict precedence rules.

   Precedence (highest to lowest):
   1. Element override - explicit per-instance override in element props
   2. Project config - project-level settings
   3. Styling system global - styling system's global config
   4. Component-specific - component config within styling system
   5. Default - compiler default or root config default

   Arguments:
   - option-key: keyword for the option to resolve (e.g., :apply-base-when-explicit)
   - element-props: element's resolved properties (may contain :styling-options map)
   - project-config: project configuration map
   - styling-config: styling system configuration map
   - element-type: element type keyword (e.g., :button, :div)
   - default-value: fallback value if not found in any source

   Returns: resolved value for the option"
  [option-key element-props project-config styling-config element-type default-value]
  ;; Use cond instead of or to handle false values correctly
  (cond
    ;; 1. Element override - highest priority
    (contains? (get element-props :styling-options {}) option-key)
    (get-in element-props [:styling-options option-key])

    ;; 2. Project config
    (contains? (get project-config :styling {}) option-key)
    (get-in project-config [:styling option-key])

    ;; 3. Styling system global config
    (contains? (get styling-config :styling-config {}) option-key)
    (get-in styling-config [:styling-config option-key])

    ;; 4. Component-specific config within styling system
    (contains? (get-in styling-config [:components element-type :styling-config] {}) option-key)
    (get-in styling-config [:components element-type :styling-config option-key])

    ;; 5. Default value - lowest priority
    :else
    default-value))

(defn resolve-multiple-options
  "Resolve multiple configuration options at once.

   Returns a map of option-key → resolved-value for all requested options.

   Arguments:
   - option-keys: collection of option keywords to resolve
   - element-props: element's resolved properties
   - project-config: project configuration map
   - styling-config: styling system configuration map
   - element-type: element type keyword
   - defaults: map of default values for each option

   Example:
   (resolve-multiple-options
     [:apply-base-when-explicit :dedupe-classes?]
     element-props
     project-config
     styling-config
     :button
     {:apply-base-when-explicit true :dedupe-classes? true})"
  [option-keys element-props project-config styling-config element-type defaults]
  (into {}
        (map (fn [opt-key]
               [opt-key (resolve-config-option
                         opt-key
                         element-props
                         project-config
                         styling-config
                         element-type
                         (get defaults opt-key))]))
        option-keys))

(defn build-precedence-context
  "Build a full precedence context for debugging and provenance tracking.

   Returns a map showing where each configuration option is defined and its value
   at each level of the precedence hierarchy.

   This is useful for:
   - Debugging configuration issues
   - Style provenance tracking (Edge Case #11)
   - Configuration documentation and tooling

   Returns map structure:
   {:option-key {:sources [{:level :element-override :value X :present? true}
                           {:level :project-config :value Y :present? false}
                           ...]
                 :resolved X
                 :source :element-override}}"
  [option-keys element-props project-config styling-config element-type defaults]
  (into {}
        (map (fn [opt-key]
               (let [sources [{:level :element-override
                               :value (get-in element-props [:styling-options opt-key])
                               :present? (contains? (get element-props :styling-options {}) opt-key)}
                              {:level :project-config
                               :value (get-in project-config [:styling opt-key])
                               :present? (contains? (get project-config :styling {}) opt-key)}
                              {:level :styling-system-global
                               :value (get-in styling-config [:styling-config opt-key])
                               :present? (contains? (get styling-config :styling-config {}) opt-key)}
                              {:level :component-specific
                               :value (get-in styling-config [:components element-type :styling-config opt-key])
                               :present? (contains? (get-in styling-config [:components element-type :styling-config] {}) opt-key)}
                              {:level :default
                               :value (get defaults opt-key)
                               :present? (contains? defaults opt-key)}]
                     resolved (resolve-config-option
                               opt-key element-props project-config
                               styling-config element-type (get defaults opt-key))
                     ;; Find which source provided the resolved value
                     source-level (or (->> sources
                                           (filter :present?)
                                           (filter #(= (:value %) resolved))
                                           first
                                           :level)
                                      :default)]
                 [opt-key {:sources sources
                           :resolved resolved
                           :source source-level}])))
        option-keys))

;; ============================================================================
;; STYLING OPTIONS
;; ============================================================================

(def ^:const common-styling-options
  "Common styling configuration options used throughout Forma.

   These options control styling behavior and can be overridden at any
   precedence level."
  [:apply-base-when-explicit  ;; Apply base classes even when explicit classes exist?
   :dedupe-classes?           ;; Remove duplicate classes from output?
   :blank-class->nil?         ;; Treat blank/whitespace class strings as nil?
   :record-duplicate-classes? ;; Track duplicate classes for warnings?
   :merge-explicit-style?     ;; Merge inherited CSS with explicit :style attribute?
   :only-extract-explicit?    ;; Only extract CSS properties set explicitly (not inherited)?
   :class-conflict-warnings?  ;; Emit warnings for conflicting classes (bg-blue-500 + bg-red-500)?
   :allow-stacking?])         ;; Allow styling system stacking even when systems extend each other?

(defn get-styling-options
  "Get all common styling options resolved via precedence chain.

   Returns a map of option-key → resolved-value.
   Uses defaults from forma/config.edn if available, otherwise uses sensible defaults."
  ([element-props project-config styling-config element-type]
   (get-styling-options element-props project-config styling-config element-type {}))
  ([element-props project-config styling-config element-type forma-config-defaults]
   (let [defaults (merge
                   ;; Sensible hardcoded defaults
                   {:apply-base-when-explicit true
                    :dedupe-classes? true
                    :blank-class->nil? true
                    :record-duplicate-classes? false
                    :merge-explicit-style? false
                    :only-extract-explicit? false
                    :class-conflict-warnings? false
                    :allow-stacking? false}
                   ;; Override with forma config defaults if provided
                   forma-config-defaults)]
     (resolve-multiple-options
      common-styling-options
      element-props
      project-config
      styling-config
      element-type
      defaults))))

;; ============================================================================
;; UTILITY FUNCTIONS
;; ============================================================================

(defn precedence-report
  "Generate a human-readable report of configuration precedence.

   Useful for debugging and documentation.

   Returns a formatted string showing:
   - Each configuration option
   - Its resolved value
   - Which precedence level provided that value
   - All values at each level (for debugging)"
  [precedence-context]
  (let [lines (for [[opt-key {:keys [sources resolved source]}] precedence-context]
                (str (name opt-key) " = " resolved " (from " (name source) ")\n"
                     (str/join "\n"
                               (map (fn [{:keys [level value present?]}]
                                      (str "  " (name level) ": "
                                           (if present?
                                             (str value " ✓")
                                             "not set")))
                                    sources))))]
    (str/join "\n\n" lines)))

(defn override-element-option
  "Create an element props map with a styling option override.

   Useful for per-instance overrides:

   Example:
   (override-element-option existing-props :apply-base-when-explicit false)
   => {:class \"btn\" :styling-options {:apply-base-when-explicit false}}"
  [element-props option-key option-value]
  (assoc-in element-props [:styling-options option-key] option-value))

(defn merge-element-overrides
  "Merge multiple styling option overrides into element props.

   Example:
   (merge-element-overrides
     {:class \"btn\"}
     {:apply-base-when-explicit false
      :dedupe-classes? true})"
  [element-props overrides-map]
  (update element-props :styling-options merge overrides-map))
