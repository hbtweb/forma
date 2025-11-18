(ns forma.integrations.oxygen.compiler
  "Compile Clojure data structures to Oxygen JSON tree format
   
   Oxygen stores pages as nested tree structures with:
   - root node (Document type)
   - child elements with id, data (type + properties), and children
   
   This compiler transforms friendly Clojure maps into Oxygen's format.
   Supports all post types, global styles, and 30+ elements."
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.java.io :as io]))

;; ============================================================================
;; CONFIGURATION LOADING
;; ============================================================================

(defn load-config
  "Load EDN configuration file"
  [filename]
  (try
    (-> (str "forma/" filename)
        io/resource
        slurp
        edn/read-string)
    (catch Exception e
      (println (str "Warning: Could not load " filename ": " (.getMessage e)))
      {})))

(def post-types-config (load-config "oxygen-post-types.edn"))
(def global-styles-config (load-config "oxygen-global-styles.edn"))
(def elements-config (load-config "oxygen-elements.edn"))
(def conventions-config (load-config "oxygen/conventions.edn"))
(def element-tags-config (load-config "oxygen/element-tags.edn"))
(def property-transformations-config (load-config "oxygen/property-transformations.edn"))

;; ============================================================================
;; ELEMENT NAME TRANSFORMATIONS - Convention-based mapping to Oxygen classes
;; ============================================================================
;; AI LEARNING PATTERN:
;;   Clojure keyword → PHP class name
;;   :button → "EssentialElements\\Button"
;;   :woo-product-price → "EssentialElements\\Wooproductprice"
;; 
;; RULES:
;;   1. kebab-case → PascalCase (remove hyphens, capitalize words)
;;   2. Add "EssentialElements\\" namespace
;;   3. EXCEPTION: Multi-word names in PHP use NO separators
;;      "Product Price" → "Wooproductprice" (not "WooProductPrice")
;; ============================================================================

(defn element-name->php-class
  "Convert display name to exact PHP class name.
   
   Oxygen's PHP classes match folder names EXACTLY, which often differ from
   display names. This function mirrors Oxygen's naming convention.
   
   Examples:
     'Button'        → 'EssentialElements\\\\Button'
     'Product Price' → 'EssentialElements\\\\WooProductPrice'
   
   Why: Oxygen stores elements as PHP class names in JSON tree structure.
        Incorrect class names = element won't render in WordPress."
  [name]
  (let [cleaned (-> name
                    (str/replace #"\s+" "")        ; Remove ALL spaces
                    (str/replace #"-" "")         ; Remove hyphens
                    (str/replace #"_" "")         ; Remove underscores
                    (str/replace #"([a-z])([A-Z])" "$1$2"))] ; Handle camelCase
    (str "EssentialElements\\\\" 
         (if (re-find #"^[a-z]" cleaned)
           (str/replace cleaned #"^([a-z])" (fn [[_ match]] (str/upper-case match)))
           cleaned))))

(defn element-key->php-class  
  "Convert Clojure keyword to exact PHP class name.
   
   This is the PRIMARY function for element compilation. Takes our friendly
   :kebab-case keywords and converts to Oxygen's PHP class format.
   
   Transformation steps:
     1. :woo-product-price → 'woo-product-price' (keyword to string)
     2. 'woo-product-price' → 'woo_product_price' (kebab to snake)
     3. 'woo_product_price' → ['woo', 'product', 'price'] (split)
     4. ['woo', 'product', 'price'] → ['Woo', 'Product', 'Price'] (capitalize)
     5. ['Woo', 'Product', 'Price'] → 'WooProductPrice' (join)
     6. 'WooProductPrice' → 'EssentialElements\\\\WooProductPrice' (namespace)
   
   Examples:
     :button              → 'EssentialElements\\\\Button'
     :woo-product-price   → 'EssentialElements\\\\WooProductPrice'
     :accordion-content   → 'EssentialElements\\\\AccordionContent'
   
   Why: This allows AI/developers to use natural Clojure naming (:button)
        while compiling to exact Oxygen format."
  [kw]
  (let [name (name kw)
        pascal (-> name
                  (str/replace #"-" "_")      ; kebab-case → snake_case
                  (str/split #"_")            ; split into words
                  (->> (map str/capitalize)   ; Capitalize each word
                       (str/join "")))]       ; Join without separators
    (str "EssentialElements\\\\" pascal)))

;; ============================================================================
;; ID GENERATION
;; ============================================================================

(def node-id-counter (atom 99))

(defn generate-node-id
  "Generate sequential node ID starting at 100 (required by Oxygen)
   
   Oxygen uses sequential integers for node IDs:
   - Root is always 1
   - Children start at 100+
   - _nextNodeId tracks the next available ID"
  []
  (swap! node-id-counter inc))

(defn generate-id
  "Generate unique element ID (8 random hex chars) - DEPRECATED, use generate-node-id"
  []
  (str/lower-case (apply str (repeatedly 8 #(format "%x" (rand-int 16))))))

;; ============================================================================
;; PROPERTY TRANSFORMATION
;; ============================================================================ 

(defn transform-properties
  "Transform friendly property map to Oxygen properties format using EDN config"
  [props]
  (let [mappings (:property-mappings property-transformations-config)
        transforms (:transform-functions property-transformations-config)
        defaults (:defaults property-transformations-config)]
    (reduce
      (fn [transformed [css-key css-value]]
        (if-let [mapping (get mappings css-key)]
          (let [oxygen-key (:oxygen-key mapping)
                transform-fn (get transforms (:transform mapping) (:transform defaults))]
            (assoc transformed oxygen-key (transform-fn css-value)))
          transformed))
      {}
      props)))

;; ============================================================================
;; UNIFIED ADVANCED FEATURES EXPANSION ENGINE
;; ============================================================================

(defn expand-shorthand
  "Expand shorthand value using pattern from conventions"
  [feature-key value pattern]
  (let [defaults (get-in pattern [:full])
        shorthand-default (get-in pattern [:shorthand])]
    (case feature-key
      :anim (cond
              (keyword? value) (merge defaults {:type value})
              (string? value) (merge defaults {:type (keyword value)})
              :else (merge defaults {:type shorthand-default}))
      
      :cond (cond
              (map? value) (merge defaults value)
              (keyword? value) (merge defaults {:type value :op :eq :val true})
              :else (merge defaults {:type :always :op :eq :val true}))
      
      :dd (cond
            (string? value) value
            (keyword? value) (str "{{" (name value) "}}")
            :else "")
      
      :interact (cond
                  (map? value) (merge defaults value)
                  (keyword? value) (merge defaults {:on :click :action value})
                  :else (merge defaults {:on :click :action :show}))
      
      :sticky (cond
                (string? value) (merge defaults {:top value})
                (number? value) (merge defaults {:top (str value "px")})
                :else (merge defaults {:top "0px"}))
      
      :parallax (cond
                  (number? value) (merge defaults {:speed value})
                  (map? value) (merge defaults value)
                  :else (merge defaults {:speed 0.5}))
      
      :hide-on (cond
                 (keyword? value) (merge defaults {:device value})
                 (string? value) (merge defaults {:device (keyword value)})
                 :else (merge defaults {:device :mobile}))
      
      :show-on (cond
                 (keyword? value) (merge defaults {:device value})
                 (string? value) (merge defaults {:device (keyword value)})
                 :else (merge defaults {:device :desktop}))
      
      ;; Default: return as-is
      value)))

(defn expand-full-map
  "Expand full map using pattern from conventions"
  [feature-key value pattern]
  ;; Full maps are already complete, just ensure they have required fields
  (let [defaults (get-in pattern [:full])]
    (case feature-key
      :anim (merge defaults value)
      :cond (merge defaults value)
      :interact (merge defaults value)
      :sticky (merge defaults value)
      :parallax (merge defaults value)
      :hide-on (merge defaults value)
      :show-on (merge defaults value)
      ;; Default: return as-is
      value)))

(defn expand-feature
  "Expand single feature using pattern from conventions
  
   Works for ANY feature - animations, conditions, etc."
  [feature-key value conventions]
  (let [pattern (get-in conventions [:feature-patterns feature-key])]
    (cond
      ;; Shorthand (keyword/string/number)
      (or (keyword? value) (string? value) (number? value))
      (expand-shorthand feature-key value pattern)
      
      ;; Full map (already complete)
      (map? value)
      (expand-full-map feature-key value pattern)
      
      ;; Array (multiple of same feature)
      (vector? value)
      (mapv #(expand-feature feature-key % conventions) value)
      
      ;; Default: return as-is
      :else value)))

(defn expand-advanced-features
  "Expand all advanced features using unified pattern
  
   AI-friendly: Same expansion logic for everything"
  [props conventions]
  (let [features [:anim :cond :dd :interact :sticky :parallax 
                  :hide-on :show-on]]
    (reduce
      (fn [expanded-props feature-key]
        (if-let [feature-val (get props feature-key)]
          (let [expanded-val (expand-feature feature-key feature-val conventions)]
            (-> expanded-props
                (dissoc feature-key)  ; Remove original key
                (assoc feature-key expanded-val)))  ; Add expanded value under same key
          expanded-props))
      props
      features)))

(defn apply-property-shortcuts
  "Apply property shortcuts from conventions - delegates to shared ui/compiler"
  [props]
  (require 'forma.compiler)
  ((resolve 'forma.compiler/expand-property-shortcuts) props))

;; ============================================================================
;; ELEMENT TAG CONFIGURATION
;; ============================================================================


(defn get-element-tag-config
  "Get tag configuration for element type from EDN"
  [element-type]
  (let [configs (:element-tags element-tags-config)
        defaults (:defaults element-tags-config)]
    (or (get configs element-type)
        (:default configs)
        defaults)))

(defn is-heading-element?
  "Check if element is a Heading (uses content.content.tags)"
  [element-type]
  (= element-type "EssentialElements\\Heading"))

(defn validate-tag
  "Validate tag value for element type"
  [element-type tag-value]
  (let [config (get-element-tag-config element-type)]
    (if config
      (let [options (:tag-options config)]
        (or (nil? tag-value)
            (some #{tag-value} options)))
      true)))  ; Unknown element, allow any tag

(defn generate-tag-property
  "Generate tag property based on element type and value
  
   Rules:
   - Heading elements: use content.content.tags
   - All other elements: use settings.advanced.tag
   - Only generate if tag is different from default"
  [element-type tag-value]
  (let [config (get-element-tag-config element-type)
        default-tag (:default-tag config)
        tag-path-custom (:tag-path-custom config)]
    
    ;; Only generate if tag is specified and different from default
    (when (and tag-value 
               (not= tag-value default-tag)
               (validate-tag element-type tag-value))
      
      (if tag-path-custom
        ;; Custom path (only for Heading)
        (let [path-keys (str/split tag-path-custom #"\.")]
          (assoc-in {} (map keyword path-keys) tag-value))
        ;; Standard path (settings.advanced.tag)
        {:settings {:advanced {:tag tag-value}}}))))

;; ============================================================================
;; CUSTOM ID VALIDATION
;; ============================================================================

(def custom-id-pattern
  "Regex pattern for valid custom IDs based on Oxygen validation rules
  
   Rules from Oxygen:
   - Must start with letter (a-z, A-Z) or underscore (_)
   - Can contain letters, numbers, hyphen (-), underscore (_)
   - Cannot start with number
   - Pattern: ^[a-zA-Z_][a-zA-Z0-9_-]*$"
  #"^[a-zA-Z_][a-zA-Z0-9_-]*$")

(defn validate-custom-id
  "Validate custom ID against Oxygen rules
  
   Args:
     id - String ID to validate
   
   Returns:
     true if valid, false if invalid"
  [id]
  (if (string? id)
    (boolean (re-matches custom-id-pattern id))
    false))

(defn sanitize-custom-id
  "Sanitize custom ID to make it valid
  
   Args:
     id - String ID to sanitize
   
   Returns:
     Valid ID string or nil if cannot be sanitized"
  [id]
  (when (string? id)
    (let [sanitized (-> id
                       (str/replace #"[^a-zA-Z0-9_-]" "")  ; Remove invalid chars
                       (str/replace #"^[0-9]" "id_"))]     ; Prefix with 'id_' if starts with number
      (when (validate-custom-id sanitized)
        sanitized))))

;; ============================================================================
;; CSS CLASS HANDLING
;; ============================================================================

(defn normalize-css-classes
  "Normalize CSS classes to array format
  
   Args:
     classes - String (space-separated), vector, or nil
   
   Returns:
     Vector of class strings or nil if empty"
  [classes]
  (when classes
    (let [class-vector (cond
                        (string? classes) (str/split classes #"\s+")
                        (vector? classes) classes
                        :else [classes])
          cleaned-classes (->> class-vector
                              (map str/trim)
                              (remove str/blank?))]
      (when (seq cleaned-classes)
        cleaned-classes))))

(defn generate-advanced-settings
  "Generate advanced settings for custom ID and CSS classes
  
   Args:
     props - Element properties map
   
   Returns:
     Map with settings.advanced structure or nil if no advanced settings"
  [props]
  (let [custom-id (or (:html-id props) (:custom-id props) (:id props))
        classes (or (:classes props) (:class props))
        
        ;; Validate and sanitize custom ID
        valid-custom-id (when custom-id
                          (if (validate-custom-id custom-id)
                            custom-id
                            (sanitize-custom-id custom-id)))
        
        ;; Normalize CSS classes
        normalized-classes (normalize-css-classes classes)
        
        ;; Build advanced settings
        advanced-settings (cond-> {}
                            valid-custom-id (assoc :id valid-custom-id)
                            normalized-classes (assoc :classes normalized-classes))]
    
    (when (seq advanced-settings)
      {:settings {:advanced advanced-settings}})))

(defn get-element-class
  "Get Oxygen class name for element type from shared registry.
   
   Uses the shared element-registry for fast lookups.
   Falls back to auto-generated class name if not found."
  [element-type]
  (require 'forma.element-registry)
  (if-let [element-def ((resolve 'forma.element-registry/get-element-definition) element-type)]
    (:class element-def)
    ;; Fallback: auto-generate class name
    (element-key->php-class element-type)))

(defn compile-element
  "Compile a single element to Oxygen format with AI-optimized shortcuts
   
   Element format:
     {:type :section
      :props {:bg \"#fff\" :pd \"20px\" :anim :fade-in :cond {:role :admin} :tag \"header\"}
      :children [...]}
   
   Oxygen format:
     {:id 100
      :data {:type \"EssentialElements\\\\Section\"
             :properties {...}}
      :children [...]}"
  [element]
  (let [{:keys [type props children node-id]} element
        ;; Use existing node-id if present, otherwise generate new one
        element-id (or node-id (generate-node-id))
        element-type (or (:oxygen-type props) (get-element-class type))
        
        ;; Extract tag property
        tag-value (:tag props)
        tag-property (generate-tag-property element-type tag-value)
        
        ;; Generate advanced settings (custom ID and CSS classes)
        advanced-settings (generate-advanced-settings props)
        
        ;; Apply AI-optimized shortcuts and expand advanced features
        expanded-props (-> props
                          (dissoc :tag :html-id :custom-id :classes :class)  ; Remove processed props
                          (expand-advanced-features conventions-config)
                          (apply-property-shortcuts))
        
        ;; Merge expanded properties with transformed properties
        ;; First transform basic properties, then merge in expanded nested properties
        basic-props (dissoc expanded-props :id :oxygen-type :text :content :url :src :alt :level
                           :design :content :animations :conditions :dynamicData :interactions :sticky :parallax :hideOn :showOn)  ; Remove nested structures for basic transform
        
        properties (transform-properties basic-props)
        
        ;; Merge in the nested structures from shortcuts and advanced features
        properties-with-nested (merge properties
                                     (select-keys expanded-props [:design :content :animations :conditions :dynamicData :interactions :sticky :parallax :hideOn :showOn]))
        
        ;; Add content-specific properties
        properties-with-content (cond-> properties-with-nested
                                  ;; Add text property at top level for tests
                                  (or (:text props) (:txt props)) (assoc :text (or (:text props) (:txt props)))
                                  (:content props) (assoc :content (:content props))
                                  (:url props) (assoc :url (:url props))
                                  (:src props) (assoc :src (:src props))
                                  (:alt props) (assoc :alt (:alt props))
                                  (:level props) (assoc :level (:level props))
                                  
                                  ;; Auto-detect dynamic data in text content
                                  (and (or (:text props) (:txt props)) 
                                       (str/includes? (or (:text props) (:txt props)) "{{")) 
                                  (assoc :dynamicData (or (:text props) (:txt props))))
        
        ;; Merge tag property if generated
        final-properties (cond-> properties-with-content
                            tag-property (merge tag-property)
                            advanced-settings (merge advanced-settings))
        
        ;; Handle null properties (many elements support this)
        final-properties-or-null (if (empty? final-properties)
                                  nil
                                  final-properties)]
    
    {:id element-id
     :data {:type element-type
            :properties final-properties-or-null}
     :children (if (seq children)
                 (mapv compile-element children)
                 [])}))

(defn compile-tree
  "Compile element vector to Oxygen tree structure
   
   Args:
     elements - Vector of element maps
     root-properties-type - :object (for templates/headers) or :array (for pages/components)
   
   Returns:
     Oxygen tree with root node and _nextNodeId (required by Oxygen)"
  ([elements]
   (compile-tree elements :array))  ; Default to array for pages/components
  ([elements root-properties-type]
   (let [root-data (case root-properties-type
                     :object {:type "root" :properties {}}
                     :array {:type "root" :properties []}
                     :object)]  ; Default fallback
     {:_nextNodeId (generate-node-id)
      :root {:id 1
              :data root-data
              :children (mapv compile-element elements)}})))

;; ============================================================================
;; POST TYPE HANDLING
;; ============================================================================

(defn validate-post-type
  "Validate post type and return configuration"
  [post-type]
  (let [post-types (:post-types post-types-config)]
    (get post-types post-type)))

(defn compile-post-type-data
  "Compile post type specific data (template settings, popup settings, etc.)"
  [post-type data]
  (let [_config (validate-post-type post-type)]
    (cond
      (= post-type :template)
      {:template-settings (or (:template-settings data) {})}
      
      (= post-type :popup)
      {:popup-settings (or (:popup-settings data) {})}
      
      :else {})))

;; ============================================================================
;; GLOBAL STYLES HANDLING
;; ============================================================================

(defn compile-global-styles
  "Compile global styles to Oxygen format using EDN config"
  [global-styles]
  (let [config (:global-styles global-styles-config)
        defaults (:defaults config)]
    (reduce
      (fn [compiled [key value]]
        (if (contains? config key)
          (assoc compiled key value)
          compiled))
      {}
      global-styles)))

;; ============================================================================
;; COMPREHENSIVE COMPILATION
;; ============================================================================

(defn compile-document
  "Compile complete document with post type, global styles, and elements
   
   Args:
     document - Map with :post-type, :title, :elements, :global-styles, etc.
   
   Returns:
     Complete Oxygen document ready for API submission"
  [document]
  (let [{:keys [post-type title elements global-styles template-settings popup-settings]} document
        post-type-config (validate-post-type post-type)
        
        ;; Determine root properties type based on post type
        root-properties-type (case post-type
                               (:template :header) :object  ; Templates/Headers use {}
                               (:page :component :block) :array  ; Pages/Components use []
                               :array)  ; Default to array
        
        compiled-elements (compile-tree elements root-properties-type)
        compiled-global-styles (when global-styles (compile-global-styles global-styles))
        post-type-data (compile-post-type-data post-type document)]
    
    (cond-> {:title title
             :post_type (get-in post-type-config [:wp-post-type] "page")
             :status "publish"
             :tree compiled-elements}
      
      compiled-global-styles (assoc :global-styles compiled-global-styles)
      
      template-settings (assoc :template-settings template-settings)
      
      popup-settings (assoc :popup-settings popup-settings)
      
      (seq post-type-data) (merge post-type-data))))

;; ============================================================================
;; PUBLIC API
;; ============================================================================

(defn compile-to-oxygen
  "Compile Clojure elements to Oxygen tree format
   
   Args:
     elements - Vector of element maps
   
   Returns:
     Oxygen tree map ready for API submission"
  [elements]
  (compile-tree elements))

(defn compile-page
  "Compile complete page with all features
   
   Args:
     page-data - Map with :title, :elements, :global-styles, etc.
   
   Returns:
     Complete page data ready for WordPress API"
  [page-data]
  (compile-document (assoc page-data :post-type :page)))

(defn compile-template
  "Compile template with conditions
   
   Args:
     template-data - Map with :title, :elements, :template-settings, etc.
   
   Returns:
     Complete template data ready for WordPress API"
  [template-data]
  (compile-document (assoc template-data :post-type :template)))

(defn compile-header
  "Compile header template
   
   Args:
     header-data - Map with :title, :elements, etc.
   
   Returns:
     Complete header data ready for WordPress API"
  [header-data]
  (compile-document (assoc header-data :post-type :header)))

(defn compile-footer
  "Compile footer template
   
   Args:
     footer-data - Map with :title, :elements, etc.
   
   Returns:
     Complete footer data ready for WordPress API"
  [footer-data]
  (compile-document (assoc footer-data :post-type :footer)))

(defn compile-block
  "Compile reusable block
   
   Args:
     block-data - Map with :title, :elements, etc.
   
   Returns:
     Complete block data ready for WordPress API"
  [block-data]
  (compile-document (assoc block-data :post-type :block)))

(defn compile-popup
  "Compile popup with settings
   
   Args:
     popup-data - Map with :title, :elements, :popup-settings, etc.
   
   Returns:
     Complete popup data ready for WordPress API"
  [popup-data]
  (compile-document (assoc popup-data :post-type :popup)))

(comment
  ;; REPL usage examples - AI-OPTIMIZED SYNTAX
  
  ;; Simple page with AI shortcuts
  (compile-page
   {:title "Welcome Page"
    :elements [{:type :section
                :props {:bg "#f9fafb"     ; :bg instead of :background
                        :pd "60px"        ; :pd instead of :padding
                        :anim :fade-in}}  ; :anim instead of :animations
                :children [{:type :heading
                           :props {:txt "Welcome to Our Site"  ; :txt instead of :text
                                   :lvl 1                      ; :lvl instead of :level
                                   :sz "48px"}}]]})           ; :sz instead of :font-size
  
  ;; Page with global styles and advanced features
  (compile-page
   {:title "Styled Page"
    :global-styles {:colors {:primary "#007bff"}
                    :typography {:primary-font "Inter"}}
    :elements [{:type :section
                :props {:bg :primary      ; Global style reference
                        :cond {:role :admin}  ; Conditional visibility
                        :sticky "0px"      ; Sticky positioning
                        :parallax 0.3}    ; Parallax effect
                :children [{:type :button
                           :props {:txt "Get Started"
                                   :interact {:on :click :scroll-to "#about"}  ; Interaction
                                   :anim :bounce-in}}]}]})  ; Animation
  
  ;; Template with conditions and dynamic data
  (compile-template
   {:title "Blog Archive Template"
    :template-settings {:type :archive
                        :conditions {:post-types [:post]}}
    :elements [{:type :section
     :children [{:type :heading
                           :props {:txt "{{post.title}}"  ; Dynamic data
                                   :lvl 1
                                   :anim :fade-in-up}}]}]})
  
  ;; Header with responsive design
  (compile-header
   {:title "Main Header"
    :elements [{:type :section
                :props {:bg "#ffffff" 
                        :pd "20px"
                        :hide-on :mobile}  ; Hide on mobile
     :children [{:type :container
                           :children [{:type :div
                                      :props {:display "flex" 
                                              :justify "space-between"}
                                      :children [{:type :image :props {:src "/logo.svg"}}
                                               {:type :menu :props {:menu-id "primary"}}]}]}]}]})
  
  ;; Popup with advanced interactions
  (compile-popup
   {:title "Newsletter Signup"
    :popup-settings {:trigger :exit-intent
                     :animation :fade}
    :elements [{:type :section
                :props {:anim :zoom-in}  ; Entrance animation
                 :children [{:type :heading
                           :props {:txt "Subscribe to Newsletter"}}
                          {:type :form
                           :props {:action :email :to "admin@site.com"}
                           :children [{:type :form-input
                                      :props {:name "email" :type "email"}}
                                     {:type :form-submit
                                      :props {:txt "Subscribe"
                                              :interact {:on :click :add-class "submitted"}}}]}]}]})
  
  ;; WooCommerce product with dynamic data
  (compile-page
   {:title "Product Page"
    :elements [{:type :woo-product
                :props {:product-id "{{query.product_id}}"  ; Dynamic from URL
                        :show-price true
                        :hide-on :mobile}}]})  ; Responsive
  
  ;; Complex interaction example
  (compile-page
   {:title "Interactive Page"
    :elements [{:type :button
                :props {:txt "Toggle Menu"
                        :interact {:on :click 
                                  :toggle-class "open" 
                                  :target "#mobile-menu"
                                  :anim :slide}}}  ; Multiple actions
               {:type :section
                :props {:id "mobile-menu"
                        :hide-on :desktop}  ; Hidden by default on desktop
                :children [{:type :menu :props {:menu-id "mobile"}}]}]})
)

