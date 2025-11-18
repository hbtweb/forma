(ns forma.minification.core
  "Generic EDN-driven minification engine.

   Minification operations are defined declaratively in platform EDN configs,
   following the same pattern as extractors and transformers.

   Example EDN configuration:
   {:compiler
    {:minification
     {:operations [{:type :regex-replace
                    :pattern #\"\\s+\"
                    :replacement \" \"
                    :config-key :remove-whitespace}
                   {:type :regex-replace
                    :pattern #\"<!--.*?-->\"
                    :replacement \"\"
                    :config-key :remove-comments}]
      :output-formats [:html-string :html-file]}}}

   This architecture allows new platforms to define minification without
   writing Clojure code, maintaining Forma's EDN-driven principles."
  (:require [clojure.string :as str]))

;; ============================================================================
;; Helper Functions for Context-Aware Operations
;; ============================================================================

(defn- find-preserve-zones
  "Find regions where content should be preserved (e.g., <pre>, <script>).

   Args:
     text - Text to scan
     tags - Set of tag names to preserve (e.g., #{\"pre\" \"script\"})

   Returns:
     Vector of [start end] position pairs"
  [text tags]
  (if (empty? tags)
    []
    (let [tag-list (str/join "|" tags)
          pattern (re-pattern (str "(?s)<(" tag-list ")(?:\\s[^>]*)?>.*?</\\1>"))
          matches (re-seq pattern text)]
      (loop [remaining matches
             pos 0
             zones []]
        (if (empty? remaining)
          zones
          (let [match-str (first (first remaining))
                start (.indexOf text match-str pos)]
            (if (< start 0)
              ;; Match not found, skip it
              (recur (rest remaining) pos zones)
              (let [end (+ start (count match-str))]
                (recur (rest remaining)
                       (inc end)
                       (conj zones [start end]))))))))))

(defn- in-preserve-zone?
  "Check if a position falls within any preserve zone."
  [pos zones]
  (some (fn [[start end]]
          (and (>= pos start) (<= pos end)))
        zones))

;; ============================================================================
;; Operation Execution
;; ============================================================================

(defn apply-regex-replace
  "Apply a regex-based string replacement operation.

   Args:
     text - String to transform
     operation - Map with :pattern (string or regex) and :replacement
     config - Minification config map

   Returns:
     Transformed string or original if operation disabled in config"
  [text operation config]
  (let [config-key (:config-key operation)
        enabled (get config config-key true)
        pattern-raw (:pattern operation)
        pattern (if (string? pattern-raw)
                  (re-pattern pattern-raw)
                  pattern-raw)
        replacement (:replacement operation)]
    (if (and enabled pattern)
      (str/replace text pattern replacement)
      text)))

(defn apply-context-aware-replace
  "Apply regex replacement while preserving content in specified tags.

   This operation respects :preserve-in-tags to avoid modifying content
   within <pre>, <script>, <style>, etc.

   Args:
     text - String to transform
     operation - Map with :pattern, :replacement, :preserve-in-tags
     config - Minification config map

   Returns:
     Transformed string with preserve zones untouched

   Example operation EDN:
     {:type :context-aware-replace
      :pattern \"\\\\s+\"
      :replacement \" \"
      :preserve-in-tags [\"pre\" \"code\" \"script\" \"style\"]
      :config-key :context-aware-whitespace}"
  [text operation config]
  (let [config-key (:config-key operation)
        enabled (get config config-key true)]
    (if-not enabled
      text
      (let [preserve-tags (set (:preserve-in-tags operation))
            zones (find-preserve-zones text preserve-tags)
            pattern-raw (:pattern operation)
            pattern (if (string? pattern-raw)
                      (re-pattern pattern-raw)
                      pattern-raw)
            replacement (:replacement operation)]
        (if (empty? zones)
          ;; No preserve zones, apply normally
          (str/replace text pattern replacement)
          ;; Split text into segments and process only non-preserve segments
          (let [segments (loop [pos 0
                                segs []
                                sorted-zones (sort-by first zones)]
                           (if (empty? sorted-zones)
                             (conj segs [:normal (subs text pos)])
                             (let [[start end] (first sorted-zones)]
                               (if (< pos start)
                                 (recur end
                                        (conj segs
                                              [:normal (subs text pos start)]
                                              [:preserve (subs text start end)])
                                        (rest sorted-zones))
                                 (recur end
                                        (conj segs [:preserve (subs text start end)])
                                        (rest sorted-zones))))))]
            (str/join ""
                      (map (fn [[type content]]
                             (if (= type :preserve)
                               content
                               (str/replace content pattern replacement)))
                           segments))))))))

(defn apply-conditional-replace
  "Apply replacement only when conditions are met.

   Conditions can check:
   - :not-between - Don't replace if between certain patterns
   - :only-between - Only replace if between certain patterns

   Args:
     text - String to transform
     operation - Map with :pattern, :replacement, :condition
     config - Minification config map

   Returns:
     Transformed string

   Example operation EDN:
     {:type :conditional-replace
      :pattern \">\\\\s+<\"
      :replacement \"><\"
      :condition {:not-between [\"<pre\" \"</pre>\"]}
      :config-key :remove-inter-tag-whitespace}"
  [text operation config]
  (let [config-key (:config-key operation)
        enabled (get config config-key true)]
    (if-not enabled
      text
      ;; For now, delegate to regex-replace (can enhance later)
      (apply-regex-replace text operation config))))

(defn apply-custom-function
  "Apply a custom function operation.

   Args:
     text - String to transform
     operation - Map with :function (var symbol)
     config - Minification config map

   Returns:
     Result of function application or original if function not found"
  [text operation config]
  (if-let [fn-var (resolve (:function operation))]
    (fn-var text config)
    text))

(defn apply-minification-operation
  "Apply a single minification operation based on its type.

   Supported operation types:
   - :regex-replace - String replacement via regex
   - :context-aware-replace - Regex replacement with preserve zones
   - :conditional-replace - Conditional regex replacement
   - :custom-function - Custom transformation function

   Args:
     text - String to transform
     operation - Operation map from platform EDN
     config - Minification config map

   Returns:
     Transformed string"
  [text operation config]
  (case (:type operation)
    :regex-replace (apply-regex-replace text operation config)
    :context-aware-replace (apply-context-aware-replace text operation config)
    :conditional-replace (apply-conditional-replace text operation config)
    :custom-function (apply-custom-function text operation config)
    text))

(defn minify-with-operations
  "Apply a sequence of minification operations to text.

   Operations are applied in order, with each operation receiving the
   output of the previous operation.

   Args:
     text - String to minify
     operations - Vector of operation maps from platform EDN
     config - Minification config map

   Returns:
     Minified string"
  [text operations config]
  (reduce (fn [result op]
            (apply-minification-operation result op config))
          text
          operations))

;; ============================================================================
;; Platform Integration
;; ============================================================================

(defn get-minification-config
  "Extract minification configuration from platform config.

   Args:
     platform-config - Platform EDN config map

   Returns:
     Minification config map with :operations and :output-formats"
  [platform-config]
  (get-in platform-config [:compiler :minification]))

(defn supports-output-format?
  "Check if platform minification supports the given output format.

   Args:
     minification-config - Minification config from platform EDN
     output-format - Format keyword (e.g., :html-string, :css-file)

   Returns:
     Boolean indicating format support"
  [minification-config output-format]
  (let [supported-formats (get minification-config :output-formats [])]
    (some #{output-format} supported-formats)))

(defn minify-with-platform-config
  "Minify text using operations defined in platform EDN config.

   This is the main entry point for EDN-driven minification. It:
   1. Extracts minification config from platform EDN
   2. Checks if output format is supported
   3. Applies operations in sequence if enabled

   Args:
     text - String to minify
     platform-config - Platform EDN config map
     output-format - Format keyword (e.g., :html-string)
     minify-config - User minification settings (e.g., {:remove-whitespace true})

   Returns:
     Minified string, or original if minification not supported for format"
  [text platform-config output-format minify-config]
  (let [minification-config (get-minification-config platform-config)]
    (if (and minification-config
             (supports-output-format? minification-config output-format))
      (let [operations (get minification-config :operations [])]
        (minify-with-operations text operations minify-config))
      text)))

;; ============================================================================
;; Utility Functions
;; ============================================================================

(defn describe-operations
  "Get human-readable descriptions of minification operations.

   Useful for debugging and documentation generation.

   Args:
     platform-config - Platform EDN config map

   Returns:
     Vector of operation description strings"
  [platform-config]
  (let [minification-config (get-minification-config platform-config)
        operations (get minification-config :operations [])]
    (mapv #(or (:description %)
               (str "Operation: " (:type %)))
          operations)))
