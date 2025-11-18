(ns forma.compiler
  "Forma UI compiler - compiles EDN to HTML/HTMX

   Extends kora.core.compiler for UI-specific compilation.
   Uses Forma hierarchy: [:global :components :sections :templates :pages]"
  (:require [kora.core.compiler :as core-compiler]
            [kora.core.tokens :as tokens]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [forma.optimization :as optimization]
            [forma.styling.core :as styling]
            [forma.platforms.html :as html-platform]
            [forma.platforms.css :as css-platform]
            [forma.config.precedence :as precedence]
            [forma.inheritance.tracking :as tracking]
            [forma.minification.core :as minification]
            [forma.policy.core :as policy]
            [forma.policy.reporting :as policy-reporting]))

;; ============================================================================
;; CONFIGURATION LOADING
;; ============================================================================

(defn load-config
  "Load root config.edn from forma root or resources
   Returns config map with paths, resolution order, defaults, etc."
  []
  (try
    (or
     ;; Try root config.edn first (for compiled application)
     (when (.exists (io/file "config.edn"))
       (edn/read-string (slurp "config.edn")))
     ;; Try forma/config.edn (for development)
     (when (.exists (io/file "forma/config.edn"))
       (edn/read-string (slurp "forma/config.edn")))
     ;; Fallback to resources
     (try
       (-> "forma/config.edn"
           io/resource
           slurp
           edn/read-string)
       (catch Exception _ nil)))
    (catch Exception e
      (log/warn (str "Could not load config.edn, using defaults: " (.getMessage e)))
      ;; Default config
      {:paths {:default "default/" :library "library/" :projects "projects/"}
       :resolution-order [:project :library :default]
       :defaults {:platform-stack [:html :css :htmx] :styling-system :shadcn-ui}
       :folders {:components "components/" :platforms "platforms/" :styles "styles/" 
                 :global "global/" :sections "sections/" :templates "templates/"}
       :features {:library-enabled true :project-isolation true :pre-resolution true :platform-discovery true}
       :cache {:platform-configs true :hierarchy-data true :styling-systems true :pre-resolved-contexts true}})))

(def forma-config (memoize load-config))

(def forma-hierarchy-levels [:global :components :sections :templates :pages])

;; ============================================================================
;; TEMPLATE RESOLUTION
;; ============================================================================

(defn flatten-context
  "Flatten nested context map for template resolution.
   {:customer {:name 'John'}} → {:customer.name 'John'}"
  [context]
  (into {}
        (for [[k v] context]
          (if (map? v)
            (into {} (for [[k2 v2] v]
                      [(keyword (str (name k) "." (name k2))) v2]))
            [k v]))))

(defn resolve-vars
  "Resolve template variables in text.
   Example: '{{customer.name}}' with context {:customer {:name 'John'}} → 'John'"
  [text context]
  (if (and text (string? text))
    (reduce-kv
     (fn [result path value]
       (str/replace result
                   (re-pattern (str "\\{\\{" (name path) "\\}\\}"))
                   (str value)))
     text
     (flatten-context context))
    text))

;; ============================================================================
;; CONFIG HELPERS
;; ============================================================================

(defn- merge-config-maps
  "Deep merge config maps, keeping right-most scalar values"
  [& maps]
  (letfn [(merge-entry [a b]
            (cond
              (nil? a) b
              (nil? b) a
              (and (map? a) (map? b)) (merge-with merge-entry a b)
              :else b))]
    (reduce merge-entry {} (remove nil? maps))))

(defn- normalize-environment
  [env]
  (cond
    (keyword? env) env
    (string? env) (keyword env)
    (nil? env) nil
    :else (keyword (name env))))

(defn- clean-style-string
  [style]
  (-> (or style "")
      str
      str/trim
      (str/replace #";\s*$" "")))

(defn- parse-css-properties
  "Parse CSS string into map of properties.
   Edge case #4: Detects and removes duplicate properties (rightmost wins)"
  [css-string]
  (cond
    ;; If it's already a map, return it
    (map? css-string) css-string
    ;; If it's blank, return empty map
    (or (nil? css-string) (and (string? css-string) (str/blank? css-string))) {}
    ;; Parse string
    :else
    (try
      (let [css-str (str css-string)
            declarations (str/split (str/trim css-str) #";\s*")
            prop-map (reduce
                      (fn [acc decl]
                        (if (str/blank? decl)
                          acc
                          (let [parts (str/split decl #":\s*" 2)]
                            (if (= (count parts) 2)
                              (let [[prop value] parts]
                                (if (and prop value (not (str/blank? prop)) (not (str/blank? value)))
                                  (assoc acc (str/trim prop) (str/trim value))
                                  acc))
                              acc))))
                      {}
                      declarations)]
        prop-map)
      (catch Exception e
        ;; If parsing fails, return empty map to avoid breaking compilation
        {}))))

(defn- css-map-to-string
  "Convert CSS property map to string"
  [css-map]
  (when (seq css-map)
    (str/join "; " (map (fn [[k v]] (str k ":" v)) css-map))))

(defn- merge-style-strings
  "Merge explicit and resolved style strings, removing duplicates.
   Edge case #4: Rightmost property wins for duplicates"
  [explicit resolved]
  (let [explicit-clean (clean-style-string explicit)
        resolved-clean (clean-style-string resolved)
        ;; Parse both to detect duplicates
        explicit-props (or (parse-css-properties explicit-clean) {})
        resolved-props (or (parse-css-properties resolved-clean) {})
        ;; Merge with explicit taking precedence
        merged-props (merge resolved-props explicit-props)]
    (css-map-to-string merged-props)))

;; ============================================================================
;; GENERIC UTILITY FUNCTIONS (EDN-driven)
;; ============================================================================

(defn collect-extractors
  "Collect all extractors of a given type from platform configs"
  [platform-configs extractor-type]
  (reduce
   (fn [acc platform-config]
     (let [extractors (get-in platform-config [:compiler :extractors] {})
           extractor (get extractors extractor-type)]
       (if extractor
         (conj acc extractor)
         acc)))
   []
   platform-configs))

(defn extract-by-extractor-config
  "Generic extractor - reads extractor config from platform EDN.
   No platform references - fully generic based on EDN conventions."
  [props platform-configs extractor-type]
  (let [extractors (collect-extractors platform-configs extractor-type)]
    (reduce
     (fn [result extractor-config]
       (case (:type extractor-config)
         ;; Property selector: extract specific keys and format as CSS string
         :property-selector
         (let [keys-to-extract (get extractor-config :keys [])
               extracted (select-keys props keys-to-extract)
               output-format (get extractor-config :output-format :css-string)
               output-key (get extractor-config :output-key :style)]
           (if (seq extracted)
             (case output-format
               :css-string
               (let [;; Filter out empty values, nil values, and empty strings
                     valid-pairs (filter (fn [[k v]] 
                                         (and v 
                                              (not= v "") 
                                              (not= (str v) "nil")
                                              (not (str/ends-with? (str v) ":")))) extracted)
                     css-string (if (seq valid-pairs)
                                (str/join "; " (map (fn [[k v]] (str (name k) ":" (str v))) valid-pairs))
                                "")]
                 (if (seq css-string)
                   (assoc result output-key css-string)
                   result))
               :attributes
               result
               result)
             result))
         
         ;; Attribute selector: extract attributes directly
         :attribute-selector
         (let [keys-to-extract (get extractor-config :keys [])
               extracted (select-keys props keys-to-extract)
               sugar (get extractor-config :sugar {})
               ;; Apply sugar transformations
               sugared (reduce-kv
                       (fn [acc sugar-key sugar-map]
                         (if (contains? props sugar-key)
                           (let [sugar-value (get sugar-map (get props sugar-key))]
                             (if sugar-value
                               (assoc acc (get extractor-config :output-key sugar-key) sugar-value)
                               acc))
                           acc))
                       extracted
                       sugar)]
           (merge result sugared))
         
         ;; Property mapper: map properties from one format to another (e.g., oxygen to CSS)
         :property-mapper
         (let [mappings (get extractor-config :mappings {})
               target-extractor (get extractor-config :target-extractor)
               mapped-props (select-keys props (keys mappings))
               mapped (reduce-kv
                      (fn [result source-prop target-prop]
                        (if (contains? mapped-props source-prop)
                          (assoc result target-prop (get mapped-props source-prop))
                          result))
                      {}
                      mappings)]
           ;; If target-extractor specified, pass to that extractor
           (if target-extractor
             (let [temp-props (merge props mapped)]
               (extract-by-extractor-config temp-props platform-configs target-extractor))
             (merge result mapped)))
         
         ;; Unknown extractor type - skip
         result))
     {}
     extractors)))

;; extract-htmx-attrs removed - now handled by generic extract-by-extractor-config

(defn apply-component-mapping
  "Apply component mapping from platform config (generic props → platform-specific)
   Generic - uses :mappings key, not platform-specific keys like :state-to-htmx"
  [element-type props platform-config]
  (let [component-mappings (get platform-config :component-mappings {})
        mapping (get component-mappings element-type)]
    (if mapping
      (let [mappings (get mapping :mappings {})  ; Generic :mappings, not :state-to-htmx
            default-attrs (get mapping :default-attrs {})
            mapped-props (reduce-kv
                         (fn [result generic-key platform-key]
                           (if (contains? props generic-key)
                             (assoc result platform-key (get props generic-key))
                             result))
                         {}
                         mappings)]
        (merge default-attrs mapped-props))
      {})))

(defn element-styles
  "Convert props map to inline CSS styles and HTMX attributes
   Fully generic - uses EDN extractor conventions, no platform references.
   Reads :extractors from platform configs."
  [props platform-configs]
  (merge
   ;; Extract styles (CSS)
   (extract-by-extractor-config props platform-configs :styles)
   ;; Extract attributes (HTMX, etc.)
   (extract-by-extractor-config props platform-configs :attributes)))

(defn get-content
  "Get content from element using config-driven content source
   Generic function that derives content handling from EDN config"
  [props children element-config context compiler]
  (let [content-source (get element-config :content-source :children)
        content-handling (get element-config :content-handling :resolve-vars)]
    (case content-source
      :children (if (seq children)
                  (let [child (first children)]
                    (if (vector? child)
                      (core-compiler/compile-with-pipeline compiler child context forma-hierarchy-levels)
                      child))
                  nil)
      :text (get props :text)
      :content.content.text (get props :content.content.text)
      :first-child (if (seq children) (first children) nil)
      ;; Default: try children, then text, then content.content.text
      (or (when (seq children)
            (let [child (first children)]
              (if (vector? child)
                (core-compiler/compile-with-pipeline compiler child context forma-hierarchy-levels)
                child)))
          (:text props)
          (:content.content.text props)
          (when (= content-handling :resolve-vars)
            (resolve-vars (or (:content.content.text props) (:text props)) context))))))

(defn compile-children
  "Compile children using config-driven children handling
   Generic function that derives children handling from EDN config"
  [children element-config context compiler]
  (let [children-handling (get element-config :children-handling :compile-all)
        content-handling (get element-config :content-handling :none)
        should-resolve-vars (= content-handling :resolve-vars)]
    (case children-handling
      :none []
      :compile-all (mapv #(cond
                           (vector? %) (core-compiler/compile-with-pipeline compiler % context forma-hierarchy-levels)
                           (map? %) (core-compiler/compile-element compiler % context)
                           (and (string? %) should-resolve-vars) (resolve-vars % context)
                           :else %)
                        children)
      :first-only (if (seq children)
                    (let [child (first children)]
                      (cond
                        (vector? child) (core-compiler/compile-with-pipeline compiler child context forma-hierarchy-levels)
                        (map? child) (core-compiler/compile-element compiler child context)
                        (and (string? child) should-resolve-vars) (resolve-vars child context)
                        :else child))
                    nil)
      ;; Default: compile all
      (mapv #(cond
              (vector? %) (core-compiler/compile-with-pipeline compiler % context forma-hierarchy-levels)
              (map? %) (core-compiler/compile-element compiler % context)
              (and (string? %) should-resolve-vars) (resolve-vars % context)
              :else %)
           children))))

(defn apply-attr-map
  "Apply attribute mapping from config (generic props → platform attributes)
   Generic function that derives attribute mapping from EDN config"
  [props attr-map-config]
  (reduce-kv
   (fn [result k v]
     (if (contains? props k)
       (assoc result v (get props k))
       result))
   {}
   attr-map-config))

(defn apply-default-attrs
  "Apply default attributes from config
   Generic function that derives default attributes from EDN config"
  [props default-attrs-config]
  (merge default-attrs-config props))

(defn apply-platform-compilation
  "Apply a single platform's compilation rules to an element
   This is called for each platform in the stack.
   Note: styled-attrs needs all platform configs for cross-platform mappings."
  [element platform-config all-platform-configs context compiler]
  (let [{:keys [type props children]} element
        element-config (get-in platform-config [:elements type])
        ;; Props are already resolved by compile-with-pipeline, just update content.content.text if needed
        resolved-props (if (map? props)
                       (update props :content.content.text #(resolve-vars % context))
                       {})]
    (if element-config
      ;; Element has platform-specific config
      (let [;; Determine element tag
            element-by-prop (get element-config :element-by-prop {})
            base-element (get element-config :element "div")
            element-tag (if-let [prop-map (get element-by-prop :level)]
                         (if-let [level (:level resolved-props)]
                           (keyword (get prop-map level (str "h" level)))
                           (keyword base-element))
                         (keyword base-element))
            class-attr (get element-config :class-attr :class)
            attr-map (get element-config :attr-map {})
            default-attrs (get element-config :default-attrs {})
            exclude-from-styles (get element-config :exclude-from-styles [])
            
            ;; Apply attribute mappings
            mapped-props (apply-attr-map resolved-props attr-map)
            
            ;; Get classes - treat blank strings as no explicit override
            ;; Edge case #2: Empty explicit class {:class ""} should allow inherited classes
            classes (let [explicit-class (cond
                                          (contains? resolved-props :class) (:class resolved-props)
                                          (contains? resolved-props class-attr) (get resolved-props class-attr)
                                          :else nil)]
                     ;; If explicit class is blank/whitespace-only, treat as nil (no override)
                     (if (and (string? explicit-class) (str/blank? explicit-class))
                       nil
                       explicit-class))
            
            ;; Apply component mapping (generic → platform-specific)
            component-mapped (apply-component-mapping type resolved-props platform-config)
            
            ;; Extract styles and HTMX attributes (needs all platform configs for cross-platform mappings)
            ;; Preserve original inline style attribute
            original-style (:style resolved-props)
            ;; Remove class and style from props-for-styles (classes and styles are handled separately)
            props-for-styles (apply dissoc resolved-props (conj exclude-from-styles :class class-attr :style))
            styled-attrs (element-styles props-for-styles all-platform-configs)

            ;; Edge Case #10: Configurable style merging behavior
            ;; Get merge-explicit-style? config option with precedence
            merge-explicit-style? (precedence/resolve-config-option
                                  :merge-explicit-style?
                                  props  ;; element props (may have :styling-options)
                                  (get context :project-config {})
                                  (get context :styling-config {})
                                  type   ;; element type for component-specific config
                                  false) ;; default: explicit style blocks inherited

            ;; Merge original style with token-resolved styles
            ;; Always merge with explicit (original-style) taking precedence on conflicts
            merged-styles (if (and original-style (seq (str original-style)))
                            (let [resolved-style (:style styled-attrs)
                                  ;; merge-style-strings: first arg (explicit) wins on conflicts
                                  merged (merge-style-strings original-style resolved-style)]
                              (cond-> (dissoc styled-attrs :style)
                                (seq merged) (assoc :style merged)))
                            styled-attrs)
            
            ;; Get content based on element config
            content (get-content resolved-props children element-config context compiler)
            
            ;; Compile children if needed
            compiled-children (compile-children children element-config context compiler)]
        
        ;; Build final element
        (let [attrs-raw (merge (when classes {class-attr classes})
                               merged-styles  ; Contains both CSS styles and HTMX attributes, with original styles preserved
                               mapped-props
                               component-mapped
                               default-attrs)
              ;; Edge Case #6: Deduplicate CSS properties in final style attribute
              attrs (if-let [style-str (:style attrs-raw)]
                      (let [deduped-props (parse-css-properties style-str)
                            deduped-str (css-map-to-string deduped-props)]
                        (if (seq deduped-str)
                          (assoc attrs-raw :style deduped-str)
                          (dissoc attrs-raw :style)))
                      attrs-raw)
              base-element [element-tag attrs]
              content-source (get element-config :content-source :children)]
          (cond
            ;; Has content from :text or :content.content.text source (not :children) - use content
            (and content (not= content "") (some? content) 
                 (not= content-source :children)
                 (not= content-source :none))
            (vec (concat base-element [content]))
            
            ;; Has compiled children - check children-handling (this handles :children content-source)
            (and (seq compiled-children) 
                 (not= (:children-handling element-config) :none))
            (vec (concat base-element compiled-children))
            
            ;; Has content but no compiled children (fallback for :text source)
            (and content (not= content "") (some? content))
            (vec (concat base-element [content]))
            
            ;; Self-closing or no content/children
            :else
            base-element)))
      
      ;; No platform-specific config - return element as-is (will be handled by next platform or fallback)
      element)))

;; ============================================================================
;; RESOURCE LOADING - THREE-TIER RESOLUTION
;; ============================================================================

(defn resolve-resource-path
  "Resolve resource path using three-tier resolution: Project → Library → Default
   Returns first found path or nil"
  [resource-type resource-name project-name]
  (let [config (forma-config)
        resolution-order (get config :resolution-order [:project :library :default])
        folders (get config :folders {})
        base-folder (get folders resource-type "")]
    (some
     (fn [tier]
       (case tier
         :project (let [project-path (str (get-in config [:paths :projects]) project-name "/" base-folder resource-name)]
                   (when (.exists (io/file project-path))
                     project-path))
         :library (let [library-path (str (get-in config [:paths :library]) base-folder resource-name)]
                   (when (.exists (io/file library-path))
                     library-path))
         :default (let [default-path (str (get-in config [:paths :default]) base-folder resource-name)
                        ;; Also check old resources/forma/ path for backward compatibility
                        old-resource-path (str "resources/forma/" base-folder resource-name)
                        resource-path (str "forma/" base-folder resource-name)]
                   (or (when (.exists (io/file default-path))
                         default-path)
                       (when (.exists (io/file old-resource-path))
                         old-resource-path)
                       (when (io/resource resource-path)
                         resource-path)))))
     resolution-order)))

(defn load-resource
  "Load EDN file using three-tier resolution: Project → Library → Default
   Falls back to forma resources for backward compatibility"
  ([path]
   (load-resource path nil))
  ([path project-name]
   (try
     (let [config (forma-config)
           resolution-order (get config :resolution-order [:project :library :default])
           folders (get config :folders {})
           ;; Determine resource type from path
           resource-type (cond
                          (str/includes? path "components/") :components
                          (str/includes? path "platforms/") :platforms
                          (str/includes? path "styles/") :styles
                          (str/includes? path "global/") :global
                          (str/includes? path "sections/") :sections
                          (str/includes? path "templates/") :templates
                          :else :unknown)
           resource-name (last (str/split path #"/"))]
       ;; Try three-tier resolution
       (if-let [resolved-path (resolve-resource-path resource-type resource-name project-name)]
         (edn/read-string (slurp resolved-path))
         ;; Fallback to old paths for backward compatibility
         (or (try
               (when (.exists (io/file (str "resources/forma/" path)))
                 (edn/read-string (slurp (str "resources/forma/" path)))))
             (try
               (-> (str "forma/" path)
                   io/resource
                   slurp
                   edn/read-string)
               (catch Exception _ {})))))
     (catch Exception e
       (log/warn (str "Could not load forma resource " path ": " (.getMessage e)))
       {}))))

(defn load-styling-system
  "Load styling system, resolving :extends if present
   Optionally takes project-name for project-aware loading
   Delegates to forma.styling.core"
  ([system-name]
   (load-styling-system system-name nil))
  ([system-name project-name]
   (styling/load-styling-system system-name project-name load-resource)))

(defn load-styling-stack
  "Load all styling systems in stack, resolving extensions
   Optionally takes project-name for project-aware loading
   Delegates to forma.styling.core"
  ([styling-stack]
   (load-styling-stack styling-stack nil))
  ([styling-stack project-name]
   (styling/load-styling-stack styling-stack project-name load-resource)))

(defn discover-platforms
  "Discover all platforms from platforms/ directory
   Checks project, library, and default in order"
  [project-name]
  (let [config (forma-config)
        resolution-order (get config :resolution-order [:project :library :default])
        platforms-dir (get-in config [:folders :platforms] "platforms/")]
    (set
     (flatten
      (for [tier resolution-order]
        (try
          (case tier
            :project (when project-name
                      (let [project-platforms-dir (str (get-in config [:paths :projects]) project-name "/" platforms-dir)]
                        (when (.exists (io/file project-platforms-dir))
                          (for [file (.listFiles (io/file project-platforms-dir))
                                :when (.endsWith (.getName file) ".edn")]
                            (-> file .getName (str/replace #"\.edn$" "") keyword)))))
            :library (let [library-platforms-dir (str (get-in config [:paths :library]) platforms-dir)]
                      (when (.exists (io/file library-platforms-dir))
                        (for [file (.listFiles (io/file library-platforms-dir))
                              :when (.endsWith (.getName file) ".edn")]
                          (-> file .getName (str/replace #"\.edn$" "") keyword))))
            :default (let [default-platforms-dir (str (get-in config [:paths :default]) platforms-dir)
                          resource-platforms-dir (str "forma/" platforms-dir)]
                      (or (when (.exists (io/file default-platforms-dir))
                            (for [file (.listFiles (io/file default-platforms-dir))
                                  :when (.endsWith (.getName file) ".edn")]
                              (-> file .getName (str/replace #"\.edn$" "") keyword)))
                          (when-let [resource-dir (io/resource resource-platforms-dir)]
                            (for [file (-> resource-dir io/file .listFiles)
                                  :when (.endsWith (.getName file) ".edn")]
                              (-> file .getName (str/replace #"\.edn$" "") keyword))))))
          (catch Exception _ [])))))))

(defn load-platform-config
  "Load platform configuration using three-tier resolution
   Resolves :extends if present, merging with base platform config"
  ([platform-name]
   (load-platform-config platform-name nil))
  ([platform-name project-name]
   (let [config (load-resource (str "platforms/" (name platform-name) ".edn") project-name)
         extends (get config :extends)]
     (if extends
       ;; Merge with base platform (recursive)
       (styling/deep-merge (load-platform-config extends project-name) config)
       ;; Standalone platform
       config))))

(defn load-platform-stack
  "Load all platform configs in stack, resolving extensions
   Optionally takes project-name for project-aware loading"
  ([platform-stack]
   (load-platform-stack platform-stack nil))
  ([platform-stack project-name]
   (mapv #(load-platform-config % project-name) platform-stack)))

;; Memoize platform config loading for performance
;; Note: Memoization handles both single-arg (platform-name) and two-arg (platform-name project-name) calls
(def load-platform-config-memo 
  (memoize (fn [platform-name & [project-name]]
             (load-platform-config platform-name project-name))))

(def load-platform-stack-memo 
  (memoize (fn [platform-stack & [project-name]]
             (load-platform-stack platform-stack project-name))))

;; Memoize styling system loading for performance
(def load-styling-system-memo
  (memoize (fn [system-name & [project-name]]
             (load-styling-system system-name project-name))))

(def load-styling-stack-memo
  (memoize (fn [styling-stack & [project-name]]
             (load-styling-stack styling-stack project-name))))

(defn get-output-format
  "Get output format from context or platform default"
  ([context platform-config]
   (get-output-format (:platform platform-config) context platform-config))
  ([platform-name context platform-config]
   (let [available-formats (get-in platform-config [:compiler :output-formats] {})
         default-format (or (get-in platform-config [:compiler :default-output-format])
                            (first (keys available-formats)))
         context-output (get context :output {})
         requested-format (or (get context :output-format)
                              (when platform-name
                                (get context-output platform-name)))]
     (cond
       (and requested-format (contains? available-formats requested-format))
       requested-format
       (and default-format (contains? available-formats default-format))
       default-format
       (seq available-formats)
       (first (keys available-formats))
       :else
       default-format))))

(defn- minify-settings
  "Retrieve configured minifier settings for output format/platform"
  [context output-format platform-name]
  (let [minify-config (get context :minify {})]
    (or (get minify-config output-format)
        (get minify-config platform-name)
        {})))

(defn should-minify?
  "Determine if minification should be applied"
  [context output-format platform-name]
  (let [minify-config (get context :minify {})
        environment (get context :environment :production)
        configured-env (get minify-config :environment)
        env-match? (cond
                     (nil? configured-env) true
                     (sequential? configured-env) (some #{environment} configured-env)
                     :else (= configured-env environment))
        format-config (minify-settings context output-format platform-name)
        format-enabled (get format-config :enabled true)]
    (and (get minify-config :enabled false)
         env-match?
         format-enabled)))

(defn minify-element
  "Minify compiled element using EDN-driven platform minification.

   This function replaces the hardcoded get-platform-minifier dispatcher
   with generic EDN-driven minification via forma.minification.core.

   The minification operations are defined in platform EDN configs
   (e.g., forma/default/platforms/html.edn, css.edn) following
   the same pattern as extractors and transformers."
  [compiled-element context output-format platform-name]
  (if (should-minify? context output-format platform-name)
    (let [platform-config (load-platform-config platform-name)
          minify-config (dissoc (minify-settings context output-format platform-name) :enabled :environment)]
      (case output-format
        (:html-string :html-file)
        (update compiled-element :html
                #(minification/minify-with-platform-config % platform-config output-format minify-config))
        (:css-string :css-file)
        (update compiled-element :style
                #(minification/minify-with-platform-config % platform-config output-format minify-config))
        compiled-element))
    compiled-element))

(defn load-hierarchy-data
  "Load hierarchy data for all levels using three-tier resolution
   Optionally takes project-name for project-aware loading"
  ([]
   (load-hierarchy-data nil))
  ([project-name]
   (try
     (let [config (forma-config)
           resolution-order (get config :resolution-order [:project :library :default])
           folders (get config :folders {})
           global (load-resource "global/defaults.edn" project-name)
           ;; Load components from all tiers
           components (reduce
                       (fn [acc tier]
                         (try
                           (let [components-dir (case tier
                                                  :project (when project-name
                                                            (str (get-in config [:paths :projects]) project-name "/" (get folders :components)))
                                                  :library (str (get-in config [:paths :library]) (get folders :components))
                                                  :default (or (str (get-in config [:paths :default]) (get folders :components))
                                                              (str "forma/" (get folders :components))))
                                 resource-dir (when (not= tier :default)
                                                (io/resource (str "forma/" (get folders :components))))]
                             (if-let [dir (or (when components-dir
                                               (let [f (io/file components-dir)]
                                                 (when (.exists f) f)))
                                             (when resource-dir
                                               (io/file resource-dir)))]
                               (merge acc
                                      (into {}
                                            (for [file (.listFiles dir)
                                                  :when (.endsWith (.getName file) ".edn")]
                                              (let [name (-> file .getName (str/replace #"\.edn$" "") keyword)]
                                                [name (load-resource (str "components/" (.getName file)) project-name)]))))
                               acc))
                           (catch Exception _ acc)))
                       {}
                       resolution-order)
           ;; Load sections
           sections (reduce
                     (fn [acc tier]
                       (try
                         (let [sections-dir (case tier
                                              :project (when project-name
                                                        (str (get-in config [:paths :projects]) project-name "/" (get folders :sections)))
                                              :library (str (get-in config [:paths :library]) (get folders :sections))
                                              :default (or (str (get-in config [:paths :default]) (get folders :sections))
                                                          (str "forma/" (get folders :sections))))
                               resource-dir (when (not= tier :default)
                                            (io/resource (str "forma/" (get folders :sections))))]
                           (if-let [dir (or (when sections-dir
                                             (let [f (io/file sections-dir)]
                                               (when (.exists f) f)))
                                           (when resource-dir
                                             (io/file resource-dir)))]
                             (merge acc
                                    (into {}
                                          (for [file (.listFiles dir)
                                                :when (.endsWith (.getName file) ".edn")]
                                            (let [name (-> file .getName (str/replace #"\.edn$" "") keyword)]
                                              [name (load-resource (str "sections/" (.getName file)) project-name)]))))
                             acc))
                         (catch Exception _ acc)))
                     {}
                     resolution-order)
           ;; Load templates
           templates (reduce
                     (fn [acc tier]
                       (try
                         (let [templates-dir (case tier
                                               :project (when project-name
                                                         (str (get-in config [:paths :projects]) project-name "/" (get folders :templates)))
                                               :library (str (get-in config [:paths :library]) (get folders :templates))
                                               :default (or (str (get-in config [:paths :default]) (get folders :templates))
                                                           (str "forma/" (get folders :templates))))
                               resource-dir (when (not= tier :default)
                                            (io/resource (str "forma/" (get folders :templates))))]
                           (if-let [dir (or (when templates-dir
                                             (let [f (io/file templates-dir)]
                                               (when (.exists f) f)))
                                           (when resource-dir
                                             (io/file resource-dir)))]
                             (merge acc
                                    (into {}
                                          (for [file (.listFiles dir)
                                                :when (.endsWith (.getName file) ".edn")]
                                            (let [name (-> file .getName (str/replace #"\.edn$" "") keyword)]
                                              [name (load-resource (str "templates/" (.getName file)) project-name)]))))
                             acc))
                         (catch Exception _ acc)))
                     {}
                     resolution-order)]
       {:global global
        :components components
        :sections sections
        :templates templates})
     (catch Exception e
       (log/warn (str "Could not load hierarchy data: " (.getMessage e)))
       {}))))

;; Cache hierarchy data
(def hierarchy-data (memoize load-hierarchy-data))

(defrecord FormaCompiler []
  core-compiler/CompilerPipeline
  
  (parse-element [_ element]
    "Parse Forma element (handles vector and map syntax, including tag.class#id syntax)"
    (cond
      (vector? element)
      (let [[tag attrs & children] element
            ;; Parse tag.class#id syntax (e.g., :div.card#main → :div + {:class "card" :id "main"})
            [base-tag tag-props] (if (keyword? tag)
                                   (let [tag-str (name tag)
                                         ;; Split by . and # to extract tag, classes, and id
                                         parts (str/split tag-str #"(?=[.#])")
                                         base (first parts)
                                         modifiers (rest parts)
                                         classes (vec (keep #(when (str/starts-with? % ".")
                                                              (subs % 1))
                                                           modifiers))
                                         id-part (first (keep #(when (str/starts-with? % "#")
                                                                 (subs % 1))
                                                              modifiers))]
                                     [(keyword base)
                                      (cond-> {}
                                        (seq classes) (assoc :class (str/join " " classes))
                                        id-part (assoc :id id-part))])
                                   [tag {}])
            [attrs children] (if (map? attrs)
                              [attrs children]
                              [{} (cons attrs children)])
            ;; Merge tag-props (from tag.class#id) with explicit attrs
            ;; Explicit attrs take precedence
            merged-props (merge tag-props attrs)
            ;; If no children but :text property exists, convert :text to a child
            final-children (if (and (empty? children) (:text merged-props))
                            [(:text merged-props)]
                            (vec children))]
        {:type (if (keyword? base-tag) base-tag :div)
         :props merged-props
         :children final-children}) ; Children will be parsed recursively during compilation

      (map? element)
      element

      :else
      {:type :text :props {:text (str element)} :children []}))
  
  (expand-properties [_ props]
    "Expand Forma property shortcuts (:bg → :background, :pd → :padding, etc.)
     Also supports Oxygen conventions-based expansion if conventions.edn is available"
    (let [;; Hardcoded shortcuts (always available)
          shortcuts {:bg :background
                     :pd :padding
                     :mg :margin
                     :txt :text
                     :url :href
                     :w :width
                     :h :height
                     :fz :font-size
                     :fw :font-weight
                     :lh :line-height}
          ;; Try to load Oxygen conventions for additional shortcuts
          oxygen-conventions (try
                              (load-resource "oxygen/conventions.edn")
                              (catch Exception _ {}))
          oxygen-shortcuts (get oxygen-conventions :prop-shortcuts {})
          ;; Merge hardcoded + conventions-based shortcuts
          ;; Convert oxygen-shortcuts from string keys/values to keywords
          oxygen-shortcuts-keywords (into {}
                                         (map (fn [[k v]] 
                                               [(if (keyword? k) k (keyword (str k)))
                                                (if (keyword? v) v (keyword (str v)))]))
                                         oxygen-shortcuts)
          all-shortcuts (merge shortcuts oxygen-shortcuts-keywords)
          ;; Expand shortcuts
          expanded (reduce-kv
                   (fn [result shortcut full-key]
                     (if (contains? result shortcut)
                       (-> result
                           (dissoc shortcut)
                           (assoc full-key (get result shortcut)))
                       result))
                   props
                   all-shortcuts)
          ;; Expand feature values (animations, etc.) if conventions available
          feature-patterns (get oxygen-conventions :feature-value-patterns {})]
      (reduce-kv
       (fn [result feature-key patterns]
         (if (contains? result feature-key)
           (let [value (get result feature-key)
                 pattern (get patterns value)]
             (if pattern
               (assoc result feature-key pattern)
               result))
           result))
       expanded
       feature-patterns)))
  
  (apply-styling [_ element resolved-props context]
    "Apply Forma styling (from styles/shadcn-ui.edn, etc.)
     Returns props with styling classes merged in (as :class string)
     Handles both string and vector formats for classes
     Supports :styling-stack for multiple systems or :styling-system for single system"
    (let [styling-stack (or (get context :styling-stack)
                           ;; Backward compatible: single system
                           [(get context :styling-system :shadcn-ui)])
          styling-configs (load-styling-stack-memo styling-stack (get context :project-name))
          element-type (:type element)]
      (reduce
       (fn [result styling-config]
         (styling/apply-styling-from-config result element-type styling-config resolved-props context))
       resolved-props
       styling-configs)))
  
  (compile-element [this element context]
    "Compile Forma element using platform stack from context
     Applies all platforms in stack sequentially (e.g., HTML → CSS → HTMX)"
    (let [platform-stack (get context :platform-stack [:html])
          project-name (get context :project-name)
          platform-configs (load-platform-stack-memo platform-stack project-name)
          ;; Compile element through all platforms in stack
          ;; Pass all platform configs to each step for cross-platform mappings
          compiled (reduce
                   (fn [result platform-config]
                     (apply-platform-compilation result platform-config platform-configs context this))
                   element
                   platform-configs)]
      ;; If no platform config matched, use generic fallback
      ;; All elements should be in EDN configs, so this should rarely happen
      (if (and (= compiled element) (not (get-in (first platform-configs) [:elements (:type element)])))
        ;; Fallback: try to compile with default element config
        (let [{:keys [type props children]} element
              resolved-props (if (map? props)
                              (update props :content.content.text #(resolve-vars % context))
                              {})
              base-config (first platform-configs)
              default-element (get-in base-config [:compiler :default-element] "div")
              styled-attrs (element-styles resolved-props platform-configs)]
          ;; Use default element with styled attrs
          (if (seq children)
            (let [compiled-children (mapv #(if (vector? %)
                                            (core-compiler/compile-with-pipeline this % context forma-hierarchy-levels)
                                            (if (map? %)
                                              (core-compiler/compile-element this % context)
                                              %))
                                         children)]
              (vec (concat [(keyword default-element) styled-attrs] compiled-children)))
            [(keyword default-element) (merge styled-attrs {:class "unknown-element" :data-type (str type)})]))
        ;; Successfully compiled through platform stack
        compiled))))


(def forma-compiler (->FormaCompiler))

;; ============================================================================
;; PUBLIC API - Property expansion (for backward compatibility)
;; ============================================================================

(defn expand-property-shortcuts
  "Expand property shortcuts - public API for use by other compilers
   
   This is a convenience function that uses the FormaCompiler's expand-properties"
  [props]
  (core-compiler/expand-properties forma-compiler props))

(defn pre-resolve-context
  "Pre-resolve inheritance and tokens once per request/page
   This is a performance optimization - resolves everything upfront"
  [context]
  (let [config (forma-config)
        pre-resolution-enabled (get-in config [:features :pre-resolution] true)]
    (if pre-resolution-enabled
      (let [hierarchy (hierarchy-data (get context :project-name))
            tokens (get-in hierarchy [:global :tokens] {})
            ;; Pre-resolve all tokens in context
            resolved-tokens (reduce-kv
                           (fn [acc k v]
                             (assoc acc k (tokens/resolve-token-reference v tokens)))
                           {}
                           tokens)]
        (merge context
               {:tokens resolved-tokens
                :hierarchy hierarchy
                :pre-resolved true}))
      context)))

;; Cache pre-resolved contexts
(def pre-resolved-context-cache (atom {}))

(defn- load-and-merge-configs
  "Load base and project configs, merge them"
  [project-name]
  (let [base-config (forma-config)
         project-config (when project-name
                          (try
                            (let [config-path (str "projects/" project-name "/config.edn")
                                  config-file (io/file config-path)]
                              (when (.exists config-file)
                                (edn/read-string (slurp config-file))))
                            (catch Exception e
                              (log/debug (str "No project config for " project-name ": " (.getMessage e)))
                             {})))]
    (if project-config
                        (merge-with
                         (fn [base-val project-val]
                           (if (map? base-val)
                             (merge base-val project-val)
                             project-val))
                         base-config
                         project-config)
      base-config)))

(defn- extract-context-options
  "Extract platform-stack, styling-stack, environment from merged config and options"
  [merged-config base-config options data]
  (let [system-env (some-> (System/getProperty "forma.environment") keyword)
        environment (or (normalize-environment (get options :environment))
                        (normalize-environment (get data :environment))
                        (normalize-environment (get-in merged-config [:defaults :environment]))
                        (normalize-environment (get-in base-config [:defaults :environment]))
                        system-env
                        :production)
        env-config (get-in merged-config [:environments environment] {})]
    {:platform-stack (get options :platform-stack 
                          (get-in merged-config [:defaults :platform-stack]
                                  (get-in base-config [:defaults :platform-stack] [:html :css :htmx])))
     :styling-stack (get options :styling-stack
                         (get-in merged-config [:defaults :styling-stack]))
     :styling-system (get options :styling-system
                          (get-in merged-config [:defaults :styling-system]
                                  (get-in base-config [:defaults :styling-system] :shadcn-ui)))
     :environment environment
     :config-output (merge-config-maps
                     (get base-config :output {})
                     (get merged-config :output {})
                     (get env-config :output {}))
     :config-minify (merge-config-maps
                     (get base-config :minify {})
                     (get merged-config :minify {})
                     (get env-config :minify {}))
     :config-optimization (merge-config-maps
                           (get base-config :optimization {})
                           (get merged-config :optimization {})
                           (get env-config :optimization {}))
     :config-styling (merge-config-maps
                      (get base-config :styling {})
                      (get merged-config :styling {})
                      (get env-config :styling {}))}))

(defn- build-base-context
  "Build base context from configs, hierarchy, and options"
  [_merged-config hierarchy options context-options data]
  (let [{:keys [platform-stack styling-system styling-stack environment
                config-output config-minify config-optimization config-styling]} context-options
        tokens (get-in hierarchy [:global :tokens] {})
        merged-output (merge-config-maps config-output (get data :output) (get options :output))
        merged-minify (merge-config-maps config-minify (get data :minify) (get options :minify))
        merged-optimization (merge-config-maps config-optimization (get data :optimization) (get options :optimization))
        merged-styling (merge-config-maps config-styling (get data :styling) (get options :styling))
        data-clean (dissoc (or data {}) :output :minify :optimization :styling)
        options-clean (dissoc options :output :minify :optimization :styling)]
    (merge {:domain :forma
            :project-name (get options :project-name)
            :tokens tokens
            :global (get hierarchy :global)
            :components (get hierarchy :components)
            :sections (get hierarchy :sections)
            :templates (get hierarchy :templates)
            :platform-stack platform-stack
            :styling-system styling-system
            :styling-stack styling-stack
            :environment environment
            :output merged-output
            :minify merged-minify
            :optimization merged-optimization
            :styling merged-styling}
           data-clean
           options-clean)))

(defn build-context
  "Build full context for Forma compilation
   Optionally takes project-name for project-aware loading
   Automatically merges project config if project-name is provided"
  ([data] (build-context data {}))
  ([data options]
   (let [project-name (get options :project-name)
         merged-config (load-and-merge-configs project-name)
         base-config (forma-config)
         data-map (or data {})
         context-options (extract-context-options merged-config base-config options data-map)
         hierarchy (hierarchy-data project-name)
         base-context (build-base-context merged-config hierarchy options context-options data-map)]
     ;; Apply pre-resolution if enabled
     (let [pre-resolution-enabled (get-in merged-config [:features :pre-resolution] true)]
       (if pre-resolution-enabled
         (pre-resolve-context base-context)
         base-context)))))

(defn apply-optimization-if-enabled
  "Apply pre-compilation optimization if enabled in context"
  [elements context]
  (let [optimization-enabled (get-in context [:optimization :pre-compilation] false)]
    (if optimization-enabled
      (let [optimized (optimization/optimize-edn-structure elements context)]
        (merge context
              {:components (:components optimized)
               :tokens (:tokens optimized)}))
      context)))

;; ============================================================================
;; POLICY ENFORCEMENT (Phase 5.6)
;; ============================================================================

(defn check-policies-if-enabled
  "Check policy violations if policies are enabled in context.
   Returns {:violations [...] :context context} or original context if disabled"
  [elements context compiled-output]
  (let [policies-enabled? (get-in context [:policies :enabled] true)
        environment (get context :environment :development)]
    (if (and policies-enabled?
            (policy/should-enforce-policies? context))
      (let [;; Add compiled output to context for performance checks
            policy-context (assoc context
                                 :compiled-output compiled-output
                                 :all-elements elements)
            ;; Check policies on all elements
            all-violations (reduce (fn [acc element]
                                    (concat acc (policy/check-policies element policy-context)))
                                  []
                                  elements)
            ;; Handle violations based on on-violation setting
            on-violation (get-in context [:policies :on-violation] :warn)
            violation-counts (policy/violation-count all-violations)]

        ;; Report violations
        (when (seq all-violations)
          (policy-reporting/report-violations all-violations
                                             {:colorize? false
                                              :show-summary? true}))

        ;; Handle based on severity
        (case on-violation
          :error (when (policy/has-errors? all-violations)
                  (throw (ex-info "Policy violations detected"
                                 {:violations all-violations
                                  :counts violation-counts})))
          :warn (when (seq all-violations)
                 (log/warn (str "Policy violations: "
                              (:errors violation-counts) " errors, "
                              (:warnings violation-counts) " warnings")))
          :ignore nil)

        ;; Return context with violations for reporting
        {:violations all-violations
         :context context})
      ;; Policies disabled - return context unchanged
      {:violations []
       :context context})))

(defn compile-to-html
  "Compile Forma EDN elements to HTML output respecting project configuration.
   Uses platform stack [:html] by default, or [:html :css :htmx] if specified in context.
   Applies pre-compilation optimization, minification, and policy checks when configured."
  ([elements] (compile-to-html elements {}))
  ([elements context]
   (let [;; Let build-context determine platform-stack from config defaults
         base-context (build-context context {})
         ;; Apply pre-compilation optimization if enabled
         full-context (apply-optimization-if-enabled elements base-context)
         project-name (get full-context :project-name)
         platform-stack (get full-context :platform-stack [:html :css :htmx])
         platform-configs (load-platform-stack-memo platform-stack project-name)
         html-config (some #(when (= (:platform %) :html) %) platform-configs)
         output-format (if html-config
                         (get-output-format :html full-context html-config)
                         :html-string)
         compiled (core-compiler/compile-collection
                   forma-compiler
                   elements
                   full-context
                   forma-hierarchy-levels)
         ;; Generate output before policy check (for size calculations)
         output (case output-format
                 :hiccup
                 compiled
                 (:html-string :html-file)
                 (let [html-string (html-platform/to-html-string compiled)
                       minify-config (dissoc (minify-settings full-context output-format :html) :enabled :environment)
                       html-platform-config (load-platform-config :html)
                       final-html (if (should-minify? full-context output-format :html)
                                    (minification/minify-with-platform-config html-string html-platform-config output-format minify-config)
                                    html-string)]
                   (if (= output-format :html-file)
                     {:output-path (get-in html-config [:compiler :output-formats :html-file :output-path] "index.html")
                      :content final-html}
                     final-html))
                 compiled)
         ;; Check policies if enabled (Phase 5.6)
         output-str (cond
                     (string? output) output
                     (map? output) (:content output)
                     :else (html-platform/to-html-string output))
         policy-result (check-policies-if-enabled elements full-context {:html output-str})]
     ;; Return output (policies are enforced via side effects)
     output))))

(defn html-output->string
  "Normalize compile-to-html output to a string for downstream tooling."
  [output]
  (cond
    (string? output) output
    (map? output) (:content output)
    (sequential? output) (html-platform/to-html-string output)
    :else (str output)))

(defn compile-to-stack
  "Compile Forma EDN elements to platform stack specified in context
   Example: {:platform-stack [:html :css :htmx]}
   Applies pre-compilation optimization if enabled"
  ([elements] (compile-to-stack elements {}))
  ([elements context]
   (let [platform-stack (get context :platform-stack [:html])
         base-context (build-context context {:platform-stack platform-stack})
         ;; Apply pre-compilation optimization if enabled
         full-context (apply-optimization-if-enabled elements base-context)
         compiled (core-compiler/compile-collection
                   forma-compiler
                   elements
                   full-context
                   forma-hierarchy-levels)]
     compiled)))
