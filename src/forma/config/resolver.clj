(ns forma.config.resolver
  "Utilities for resolving configuration precedence across hierarchy levels.")

(def precedence-order
  [:element :project :styling-system :component :default])

(defn resolve-option
  "Resolve an option based on the precedence order.
  `option-key` is the configuration key, `contexts` is a map keyed by
  precedence keywords containing partial configurations.
  Returns the first non-nil value following `precedence-order`."
  [option-key contexts]
  (some (fn [level]
          (when-some [value (get-in contexts [level option-key])]
            value))
        precedence-order))

