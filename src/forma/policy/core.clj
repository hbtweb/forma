(ns forma.policy.core
  "Phase 5.6 - Policy Enforcement Core Engine

   EDN-driven policy system for validating:
   - Design system consistency (tokens, colors, spacing)
   - Accessibility compliance (ARIA, semantic HTML)
   - Performance metrics (bundle size, optimization)

   Architecture:
   - EDN-driven policy definitions (zero hardcoded rules)
   - Three-tier configuration resolution (Project � Library � Default)
   - Configurable severity levels (:error, :warning, :info)
   - Generic policy check executor

   Usage:
     (check-policies element context)
     (load-policy-config :design-system)
     (report-violations violations)"
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;;
;; Policy Violation Data Structure
;;

(defrecord PolicyViolation
  [type           ; :design-system, :accessibility, :performance, :custom
   severity       ; :error, :warning, :info
   rule-id        ; Unique rule identifier
   element-type   ; :button, :heading, :div, etc.
   message        ; Human-readable description
   location       ; {:file path :line number} (optional)
   fix-suggestion ; Optional auto-fix suggestion
   metadata])     ; Additional context map

(defn violation
  "Create a policy violation record"
  ([type severity rule-id element-type message]
   (violation type severity rule-id element-type message nil nil nil))
  ([type severity rule-id element-type message fix-suggestion]
   (violation type severity rule-id element-type message fix-suggestion nil nil))
  ([type severity rule-id element-type message fix-suggestion location metadata]
   (->PolicyViolation type severity rule-id element-type message location fix-suggestion metadata)))

;;
;; Configuration Loading (Three-Tier Resolution)
;;

(defn- deep-merge
  "Deep merge maps, keeping right-most scalar values"
  [& maps]
  (apply merge-with
        (fn [v1 v2]
          (if (and (map? v1) (map? v2))
            (deep-merge v1 v2)
            v2))
        maps))

(defn load-resource
  "Load EDN resource using three-tier resolution:
   1. projects/{project-name}/policies/{name}.edn
   2. library/policies/{name}.edn
   3. default/policies/{name}.edn"
  [resource-name project-name]
  (let [paths (if project-name
                [(str "projects/" project-name "/policies/" resource-name)
                 (str "library/policies/" resource-name)
                 (str "default/policies/" resource-name)]
                [(str "default/policies/" resource-name)])]
    (some (fn [path]
           (when-let [resource (io/resource path)]
             (try
               (with-open [r (io/reader resource)]
                 (edn/read (java.io.PushbackReader. r)))
               (catch Exception e
                 nil))))  ; Return nil and try next path
         paths)))

(defn load-policy-config
  "Load policy configuration using three-tier resolution.
   Resolves :extends if present (similar to platform configs)"
  ([policy-name]
   (load-policy-config policy-name nil))
  ([policy-name project-name]
   (let [config (load-resource (str (name policy-name) ".edn") project-name)]
     (if-let [extends (:extends config)]
       ;; Deep merge with parent config
       (deep-merge (load-policy-config extends project-name) config)
       config))))

;; Memoize for performance (cleared on config reload)
(def load-policy-config-memo
  (memoize load-policy-config))

(defn clear-policy-cache!
  "Clear memoized policy configurations (call after config changes)"
  []
  (alter-var-root #'load-policy-config-memo
                  (constantly (memoize load-policy-config))))

;;
;; Policy Configuration Precedence Resolution
;;

(defn resolve-policy-option
  "Resolve policy configuration with precedence:
   1. Element override (props :policy-options)
   2. Project config (:policies key)
   3. Policy system global config
   4. Default value"
  [option-key element-props project-config policy-config default-value]
  (cond
    ;; 1. Element-level override
    (contains? (get element-props :policy-options {}) option-key)
    (get-in element-props [:policy-options option-key])

    ;; 2. Project-level config
    (contains? (get project-config :policies {}) option-key)
    (get-in project-config [:policies option-key])

    ;; 3. Policy config
    (contains? policy-config option-key)
    (get policy-config option-key)

    ;; 4. Default
    :else
    default-value))

(defn policy-enabled?
  "Check if a policy is enabled based on configuration"
  [policy-config config-key context]
  (let [element-props (:props context)
        project-config (:project-config context)]
    (resolve-policy-option
      config-key
      element-props
      project-config
      policy-config
      true))) ; Enabled by default

;;
;; Generic Policy Check Executor
;;

(defmulti apply-policy-check
  "Apply a single policy check to an element.
   Dispatch on check :type keyword"
  (fn [element check config context] (:type check)))

;; Default implementation for unknown check types
(defmethod apply-policy-check :default
  [element check config context]
  [(violation :custom
              :warning
              :unknown-check-type
              (:type element)
              (str "Unknown policy check type: " (:type check)))])

;; Forward declaration for check implementations
;; These will be implemented in design_system.clj, accessibility.clj, etc.
(declare check-token-enforcement
         check-color-palette
         check-spacing-scale
         check-typography-scale
         check-aria-attributes
         check-semantic-html
         check-color-contrast
         check-bundle-size
         check-unused-code
         apply-custom-check)

;;
;; Policy Rule Execution
;;

(defn execute-policy-rule
  "Execute a single policy rule on an element"
  [element rule config context]
  (when (policy-enabled? config (:config-key rule :enabled) context)
    (let [severity (or (:severity rule) :warning)]
      ;; Apply the check and set severity on violations
      (map #(assoc % :severity severity)
           (apply-policy-check element rule config context)))))

(defn execute-policy-rules
  "Execute all rules from a policy configuration"
  [element policy-config context]
  (let [rules (:rules policy-config)]
    (reduce (fn [violations rule]
              (concat violations (execute-policy-rule element rule policy-config context)))
            []
            rules)))

;;
;; Main Policy Check API
;;

(defn check-policies
  "Check all applicable policies for an element.
   Returns vector of PolicyViolation records.

   Options:
     :policies - Vector of policy names to check (default: all)
     :severity-threshold - Only return violations >= threshold
     :on-violation - :error (throw), :warn (continue), :ignore"
  ([element context]
   (check-policies element context {}))
  ([element context options]
   (let [project-name (get context :project-name)
         policy-names (or (:policies options)
                         (get-in context [:policies :configs])
                         [:design-system :accessibility :performance])
         violations (reduce (fn [acc policy-name]
                             (let [policy-config (load-policy-config-memo policy-name project-name)]
                               (concat acc (execute-policy-rules element policy-config context))))
                           []
                           policy-names)]
     ;; Filter by severity threshold if specified
     (if-let [threshold (:severity-threshold options)]
       (let [severity-order {:error 3 :warning 2 :info 1}]
         (filter #(>= (severity-order (:severity %))
                     (severity-order threshold))
                violations))
       violations))))

(defn check-policies-batch
  "Check policies for multiple elements.
   Returns map of element-index � violations vector"
  [elements context options]
  (into {}
        (map-indexed (fn [idx element]
                      [idx (check-policies element context options)])
                    elements)))

;;
;; Violation Handling
;;

(defn group-violations-by-severity
  "Group violations by severity level"
  [violations]
  (group-by :severity violations))

(defn violations-by-type
  "Group violations by policy type"
  [violations]
  (group-by :type violations))

(defn has-errors?
  "Check if any violations are errors"
  [violations]
  (boolean (some #(= :error (:severity %)) violations)))

(defn has-warnings?
  "Check if any violations are warnings"
  [violations]
  (boolean (some #(= :warning (:severity %)) violations)))

(defn violation-count
  "Count violations by severity"
  [violations]
  {:errors (count (filter #(= :error (:severity %)) violations))
   :warnings (count (filter #(= :warning (:severity %)) violations))
   :info (count (filter #(= :info (:severity %)) violations))
   :total (count violations)})

;;
;; Context Helpers
;;

(defn policy-context
  "Create a policy check context from compilation context"
  [context]
  (assoc context
         :policies (get context :policies {})
         :project-config (get context :project-config {})
         :environment (get context :environment :development)))

(defn should-enforce-policies?
  "Determine if policies should be enforced based on context"
  [context]
  (let [policies-config (get context :policies {})
        enabled? (get policies-config :enabled true)
        environment (get context :environment :development)]
    (and enabled?
         (or (= environment :production)
             (get policies-config :enforce-in-dev false)))))

;;
;; Exports
;;

(comment
  ;; Example usage

  ;; Load policy configuration
  (def ds-config (load-policy-config :design-system "my-project"))

  ;; Check policies on an element
  (def element {:type :button
                :props {:background "#4f46e5"  ; Hardcoded color
                        :padding "16px"}})      ; Hardcoded spacing

  (def context {:project-name "my-project"
                :environment :production
                :policies {:configs [:design-system :accessibility]}})

  (def violations (check-policies element context))
  ;; => [{:type :design-system
  ;;      :severity :error
  ;;      :rule-id :token-enforcement-colors
  ;;      :message "Use color tokens instead of hardcoded hex values"
  ;;      :fix-suggestion "Replace with $colors.primary.500"}]

  ;; Group violations
  (group-violations-by-severity violations)
  ;; => {:error [...] :warning [...]}

  ;; Check for errors
  (has-errors? violations)
  ;; => true

  (violation-count violations)
  ;; => {:errors 2 :warnings 1 :info 0 :total 3}
  )
