(ns forma.integrations.oxygen.reverse-compiler
  "Convert Oxygen JSON tree structure to Corebase DSL format
  
  This is the inverse of the compiler - takes Oxygen's tree format
  and converts it back to friendly Clojure data structures."
  (:require [clojure.string :as str]))

;; ============================================================================
;; PHP CLASS TO ELEMENT KEY CONVERSION
;; ============================================================================

(defn php-class->element-key
  "Convert PHP class to Clojure keyword (inverse of element-key->php-class)
  
  Examples:
    \"EssentialElements\\\\Button\" -> :button
    \"EssentialElements\\\\WooProductPrice\" -> :woo-product-price
    \"EssentialElements\\\\AccordionContent\" -> :accordion-content
  
  Args:
    php-class - String PHP class name
  
  Returns:
    Clojure keyword"
  [php-class]
  (when php-class
    (let [;; Remove namespace prefix
          cleaned (str/replace php-class #"^EssentialElements\\\\" "")
          
          ;; Convert PascalCase to kebab-case
          ;; "WooProductPrice" -> "woo-product-price"
          ;; "Button" -> "button"
          kebab-case (str/replace cleaned
                                   #"([a-z])([A-Z])"
                                   (fn [[_ c1 c2]] (str c1 "-" c2)))]
      
      (keyword (str/lower-case kebab-case)))))

;; ============================================================================
;; PROPERTY EXTRACTION
;; ============================================================================

(defn extract-text
  "Extract text content from WP properties"
  [props]
  (or (:text props)
      (get-in props [:content :text])))

(defn extract-content
  "Extract content from WP properties"
  [props]
  (or (:content props)
      (get-in props [:content :content])))

(defn extract-advanced-settings
  "Extract advanced settings (custom ID, CSS classes) from WP properties"
  [props]
  (let [advanced (get-in props [:settings :advanced])]
    (cond-> {}
      (:id advanced) (assoc :html-id (:id advanced))
      (:classes advanced) (assoc :classes (if (vector? (:classes advanced))
                                           (:classes advanced)
                                           [(:classes advanced)])))))

(defn extract-url
  "Extract URL from WP properties"
  [props]
  (:url props))

(defn extract-src
  "Extract src (image source) from WP properties"
  [props]
  (:src props))

(defn extract-alt
  "Extract alt text from WP properties"
  [props]
  (:alt props))

(defn extract-level
  "Extract heading level from WP properties"
  [props]
  (or (:level props)
      (let [tags (get-in props [:content :content :tags])]
        (when (and (vector? tags) (seq tags))
          (first tags)))))

(defn extract-tag
  "Extract HTML tag from WP properties"
  [props element-type]
  (let [;; For heading elements, use content.content.tags
        heading-tag (get-in props [:content :content :tags])
        ;; For other elements, use settings.advanced.tag
        advanced-tag (get-in props [:settings :advanced :tag])]
    
    (cond
      (and heading-tag (vector? heading-tag) (seq heading-tag))
      (first heading-tag)
      
      advanced-tag
      advanced-tag
      
      :else
      nil)))

(defn extract-props
  "Extract all properties from WP node data.properties
  
  Args:
    wp-node - WordPress/Oxygen node
    element-type - Element type keyword (for tag detection)
  
  Returns:
    Map of Corebase props"
  [wp-node element-type]
  (let [props (get-in wp-node [:data :properties])
        
        ;; Extract basic content
        text (extract-text props)
        content (extract-content props)
        
        ;; Extract advanced settings
        advanced (extract-advanced-settings props)
        
        ;; Extract other properties
        url (extract-url props)
        src (extract-src props)
        alt (extract-alt props)
        level (extract-level props)
        tag (extract-tag props element-type)]
    
    (cond-> {}
      text (assoc :text text)
      content (assoc :content content)
      url (assoc :url url)
      src (assoc :src src)
      alt (assoc :alt alt)
      level (assoc :level level)
      tag (assoc :tag tag)
      
      ;; Merge advanced settings
      (seq advanced) (merge advanced)
      
      ;; Include design, animations, etc. if present
      (:design props) (assoc :design (:design props))
      (:animations props) (assoc :animations (:animations props))
      (:conditions props) (assoc :conditions (:conditions props))
      (:dynamicData props) (assoc :dynamicData (:dynamicData props))
      (:interactions props) (assoc :interactions (:interactions props)))))

;; ============================================================================
;; NODE CONVERSION
;; ============================================================================

(defn convert-wp-node-to-cb-element
  "Convert single WP node to CB element format
  
  WP format:
    {:id 100
     :data {:type \"EssentialElements\\\\Button\"
            :properties {...}}
     :children [...]}
  
  CB format:
    {:node-id 100
     :type :button
     :props {...}
     :children [...]}
  
  Args:
    wp-node - WordPress/Oxygen node
  
  Returns:
    Corebase element map"
  [wp-node]
  (let [node-id (:id wp-node)
        php-class (get-in wp-node [:data :type])
        element-type (php-class->element-key php-class)
        props (extract-props wp-node element-type)
        children (:children wp-node)
        
        converted-children (when (seq children)
                            (mapv convert-wp-node-to-cb-element children))]
    
    (cond-> {:node-id node-id
             :type (or element-type :unknown)
             :props props}
      (seq converted-children) (assoc :children converted-children))))

;; ============================================================================
;; TREE CONVERSION
;; ============================================================================

(defn convert-wp-tree-to-cb-elements
  "Convert complete WP tree to CB elements vector
  
  Args:
    wp-tree - Oxygen tree structure with :root node
  
  Returns:
    Vector of Corebase elements (root.children converted)"
  [wp-tree]
  (let [root (:root wp-tree)
        root-children (:children root)]
    (if (seq root-children)
      (mapv convert-wp-node-to-cb-element root-children)
      [])))

(defn convert-wp-tree-to-page
  "Convert WP tree to complete page structure
  
  Args:
    wp-tree - Oxygen tree structure
    page-meta - Map with :id, :title, etc. (from WP post)
  
  Returns:
    Complete page map with :elements and :sync-metadata"
  [wp-tree page-meta]
  (let [elements (convert-wp-tree-to-cb-elements wp-tree)
        max-node-id (loop [max-id 1
                          nodes [(:root wp-tree)]]
                     (if (seq nodes)
                       (let [node (first nodes)
                             children (:children node)
                             new-max (max max-id (:id node))]
                         (recur new-max
                               (concat (rest nodes) children)))
                       max-id))]
    
    (assoc page-meta
           :elements elements
           :sync-metadata {:wp-post-id (:id page-meta)
                          :next-node-id (inc max-node-id)
                          :cb-dirty? false
                          :sync-status :clean})))
