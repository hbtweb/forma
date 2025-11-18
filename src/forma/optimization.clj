(ns forma.optimization
  "Pre-compilation optimization: tree-shaking and flattening
  
   This namespace provides static analysis and optimization functions
   that remove unused components, tokens, and styles before compilation.
   
   The optimization process:
   1. Static Analysis: Build dependency graph from pages
   2. Tree-Shaking: Remove unused components, tokens, styles
   3. Flattening: Resolve all inheritance and tokens
   4. Result: Optimized structure ready for compilation"
  (:require [clojure.string :as str]
            [kora.core.inheritance :as inheritance]
            [kora.core.tokens :as tokens]))

(defn extract-components-from-element
  "Extract component types from a single element
   
   Args:
     element - EDN element (vector or map)
   
   Returns:
     Set of component type keywords"
  [element]
  (cond
    (vector? element)
    (let [type (first element)]
      (if (keyword? type)
        (conj (set (mapcat extract-components-from-element (rest element)))
              type)
        (set (mapcat extract-components-from-element (rest element)))))
    (map? element)
    (if-let [type (:type element)]
      (conj (set (mapcat extract-components-from-element (vals element)))
            type)
      (set (mapcat extract-components-from-element (vals element))))
    :else
    #{}))

(defn extract-components-from-pages
  "Extract all component types used in pages
   
   Args:
     pages - Collection of page elements
   
   Returns:
     Set of component type keywords"
  [pages]
  (set (mapcat extract-components-from-element pages)))

(defn extract-token-references
  "Extract token references from an element
   
   Args:
     element - EDN element
   
   Returns:
     Set of token path strings (e.g., \"$token.path\")"
  [element]
  (cond
    (string? element)
    (if (str/starts-with? element "$")
      #{(subs element 1)}
      #{})
    (vector? element)
    (set (mapcat extract-token-references element))
    (map? element)
    (set (mapcat extract-token-references (vals element)))
    :else
    #{}))

(defn extract-tokens-from-pages
  "Extract all token references from pages
   
   Args:
     pages - Collection of page elements
   
   Returns:
     Set of token path strings"
  [pages]
  (set (mapcat extract-token-references pages)))

(defn extract-styles-from-pages
  "Extract style references from pages
   
   Args:
     pages - Collection of page elements
     context - Compilation context
   
   Returns:
     Set of style/class references"
  [pages _context]
  ;; Extract class names, variant names, etc.
  (let [class-refs (set (mapcat 
                        (fn [element]
                          (if-let [class (:class element)]
                            (if (string? class)
                              (str/split class #"\s+")
                              [])
                            []))
                        (mapcat #(tree-seq vector? seq %) pages)))]
    class-refs))

(defn build-dependency-graph
  "Build dependency graph from pages
   
   Args:
     pages - Collection of page elements
     context - Compilation context
   
   Returns:
     Map with :components, :tokens, :styles keys"
  [pages context]
  {:components (extract-components-from-pages pages)
   :tokens (extract-tokens-from-pages pages)
   :styles (extract-styles-from-pages pages context)})

(defn tree-shake-components
  "Remove unused components
   
   Args:
     all-components - Map of all component definitions
     used-components - Set of component types that are used
   
   Returns:
     Map with only used components"
  [all-components used-components]
  (select-keys all-components used-components))

(defn select-used-tokens
  "Select only used tokens from token map
   
   Args:
     all-tokens - Map of all tokens
     used-token-paths - Set of token paths that are used
   
   Returns:
     Map with only used tokens"
  [all-tokens used-token-paths]
  (let [used-keys (set (mapcat 
                       (fn [path]
                         (let [path-parts (str/split path #"\.")]
                           (if (= (count path-parts) 1)
                             [(keyword (first path-parts))]
                             [])))
                       used-token-paths))]
    (select-keys all-tokens used-keys)))

(defn tree-shake-tokens
  "Remove unused tokens
   
   Args:
     all-tokens - Map of all tokens
     used-tokens - Set of token paths that are used
   
   Returns:
     Map with only used tokens"
  [all-tokens used-tokens]
  (select-used-tokens all-tokens used-tokens))

(defn select-used-utilities
  "Select only used utilities from utilities map
   
   Args:
     utilities - Map of utilities
     used-utilities - Set of utility names that are used
   
   Returns:
     Map with only used utilities"
  [utilities used-utilities]
  (select-keys utilities used-utilities))

(defn tree-shake-styling-systems
  "Remove unused utilities from styling systems
   
   Args:
     all-styling-systems - Vector of styling system configs
     used-utilities - Set of utility names that are used
   
   Returns:
     Vector of styling systems with unused utilities removed"
  [all-styling-systems used-utilities]
  (mapv
   (fn [styling-system]
     (if-let [utilities (get styling-system :utilities)]
       (assoc styling-system 
              :utilities (select-used-utilities utilities used-utilities))
       styling-system))
   all-styling-systems))

(defn resolve-inheritance-and-tokens
  "Resolve inheritance and tokens for a component definition
   
   Args:
     component-def - Component definition map
     tokens - Map of tokens
     context - Compilation context (must include hierarchy levels)
   
   Returns:
     Component definition with inheritance and tokens resolved"
  [component-def tokens context]
  (let [hierarchy-levels [:global :components :sections :templates :pages]
        element {:type (:type component-def) :props component-def}
        inherited (inheritance/resolve-inheritance element context hierarchy-levels)]
    (tokens/resolve-tokens inherited context)))

(defn flatten-inheritance
  "Resolve all inheritance and tokens, remove hierarchy structure
   
   Args:
     components - Map of component definitions
     tokens - Map of tokens
     context - Compilation context
   
   Returns:
     Map of flattened component definitions"
  [components tokens context]
  (reduce-kv
   (fn [acc component-type component-def]
     (let [resolved (resolve-inheritance-and-tokens component-def tokens context)]
       (assoc acc component-type resolved)))
   {}
   components))

(defn optimize-edn-structure
  "Static analysis and tree-shaking at EDN level
   Removes unused components, tokens, styles before compilation
   
   Args:
     pages - Collection of page elements
     context - Compilation context
   
   Returns:
     Map with :components, :tokens, :flattened keys"
  [pages context]
  (let [;; Step 1: Build dependency graph
        dependency-graph (build-dependency-graph pages context)
        
        ;; Step 2: Determine what's used
        used-components (:components dependency-graph)
        used-tokens (:tokens dependency-graph)
        _used-styles (:styles dependency-graph)
        
        ;; Step 3: Tree-shake (get all components/tokens from context)
        all-components (get context :components {})
        all-tokens (get context :tokens {})
        
        optimized-components (tree-shake-components all-components used-components)
        optimized-tokens (tree-shake-tokens all-tokens used-tokens)
        
        ;; Step 4: Flatten
        flattened (flatten-inheritance optimized-components optimized-tokens context)]
    {:components optimized-components
     :tokens optimized-tokens
     :flattened flattened}))

