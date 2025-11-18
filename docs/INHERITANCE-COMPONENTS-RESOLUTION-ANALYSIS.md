# Inheritance, Components, and Resolution Analysis

## Overview

This document provides a comprehensive analysis of how inheritance, component resolution, and property/token resolution work in the Forma/Kora system.

---

## 1. Inheritance System

### Architecture

The inheritance system is **universal** and **domain-agnostic**, implemented in `kora.core.inheritance`. It works for any hierarchy structure:

- **Forma (UI)**: `[:global :components :sections :templates :pages]`
- **Interlude (Music)**: `[:project :track :note]`
- **Games/3D**: Any custom hierarchy

### Hierarchy Levels (Forma)

```
Priority Order (lowest → highest):
1. Global      (global/defaults.edn)     - Base tokens and component defaults
2. Components  (components/*.edn)       - Component structure definitions
3. Sections    (sections/*.edn)          - Section-specific overrides
4. Templates   (templates/*.edn)         - Template-specific overrides
5. Pages       (instance props)          - Instance-specific props (highest priority)
```

### Resolution Algorithm

```clojure
(defn resolve-inheritance
  "Universal inheritance resolution"
  [element context hierarchy-levels]
  ;; Step 1: Get properties from each hierarchy level
  (let [level-props (resolve-hierarchy-levels element context hierarchy-levels)
        
        ;; Step 2: Merge hierarchy properties (lowest priority first)
        merged-from-hierarchy (merge-hierarchy-properties level-props hierarchy-levels)
        
        ;; Step 3: Merge instance properties (highest priority - overrides everything)
        instance-props (:props element {})
        
        ;; Step 4: Final merge (hierarchy + instance)
        final-props (deep-merge merged-from-hierarchy instance-props)]
    final-props))
```

### Deep Merge Algorithm

```clojure
(defn deep-merge
  "Deep merge two maps. Values from right map override left map.
   Nested maps are merged recursively."
  [left right]
  (cond
    (and (map? left) (map? right))
    (merge-with deep-merge left right)
    
    (nil? left) right
    (nil? right) left
    
    :else right))
```

**Key Behavior**: Right map values override left map values. Nested maps are merged recursively, not replaced.

### Example: Inheritance Flow

```clojure
;; 1. Global (global/defaults.edn)
{:components {:button {:padding "$spacing.md $spacing.lg"
                      :border-radius "$border-radius.md"}}}

;; 2. Component (components/button.edn)
{:button {:structure {:type :button}
         :variants {:primary {:background "$colors.primary"}}}}

;; 3. Section (sections/header.edn)
{:header {:components {:button {:size :sm}}}}

;; 4. Template (templates/dashboard.edn)
{:dashboard {:components {:button {:variant :primary}}}}

;; 5. Instance (page element)
[:button {:text "Click Me"}]

;; Final Resolved Props:
{:padding "$spacing.md $spacing.lg"      ; from Global
 :border-radius "$border-radius.md"      ; from Global
 :background "$colors.primary"            ; from Component variant
 :size :sm                               ; from Section
 :variant :primary                       ; from Template
 :text "Click Me"}                       ; from Instance (highest priority)
```

### Hierarchy Level Resolution

The system resolves properties at each level based on element type:

```clojure
(defn resolve-hierarchy-levels
  "Resolve properties for each level in the hierarchy"
  [element context hierarchy-levels]
  (reduce
   (fn [level-props level-name]
     (let [level-context (get-in context [level-name])
           element-type (:type element)
           props-at-level (cond
                            ;; Global level: components definitions
                            (= level-name (first hierarchy-levels))
                            (get-in level-context [:components element-type])
                            
                            ;; Component level: variants
                            (= level-name (second hierarchy-levels))
                            (when-let [variant (:variant element)]
                              (get-in level-context [element-type :variants variant]))
                            
                            ;; Section/Template level: section-specific overrides
                            (contains? (set (drop 2 hierarchy-levels)) level-name)
                            (get-in level-context [(:section element) :components element-type])
                            
                            ;; Default: level-specific element properties
                            :else
                            (get-in level-context [(:key element) :components element-type]))]
       (assoc level-props level-name props-at-level)))
   {}
   hierarchy-levels))
```

---

## 2. Component System

### Component Structure

Components are defined in EDN files with a consistent structure:

```clojure
;; components/button.edn
{:button
 {:structure
  {:type :button
   :props {:class "btn"}}
  
  :variants
  {:primary {:background "$colors.primary"}
   :secondary {:background "$colors.secondary"}}
  
  :sizes
  {:sm {:padding "$spacing.sm"}
   :md {:padding "$spacing.md"}
   :lg {:padding "$spacing.lg"}}}}
```

### Component Loading

Components are loaded using **three-tier resolution**:

1. **Project** (highest priority)
2. **Library** (medium priority)
3. **Default** (lowest priority, fallback)

```clojure
(defn load-hierarchy-data
  "Load hierarchy data for all levels using three-tier resolution"
  ([project-name]
   (let [resolution-order [:project :library :default]
         components (reduce
                     (fn [acc tier]
                       (let [components-dir (case tier
                                              :project (str "projects/" project-name "/components/")
                                              :library (str "library/components/")
                                              :default (str "default/components/"))]
                         ;; Load components from this tier
                         (merge acc (load-components-from-dir components-dir))))
                     {}
                     resolution-order)]
     {:components components})))
```

### Component Discovery

Components are discovered from:
1. **Component definitions** (`components/*.edn`)
2. **Hierarchy levels** (sections, templates can define components inline)
3. **Fallback** to default components

### Component Resolution Flow

```
1. Parse element → {:type :button :props {...}}
   ↓
2. Load component structure from components/button.edn
   ↓
3. Apply inheritance (Global → Component → Section → Template → Instance)
   ↓
4. Resolve tokens ($colors.primary → "#4f46e5")
   ↓
5. Apply styling (from styles/shadcn-ui.edn)
   ↓
6. Compile to platform stack (HTML → CSS → HTMX)
```

---

## 3. Token Resolution

### Token System

Tokens are defined in `global/tokens.edn` and can be referenced using `$token.path` syntax:

```clojure
;; global/tokens.edn
{:tokens
 {:colors
  {:primary "#667eea"
   :secondary "#6c757d"}
  :spacing
  {:xs "0.25rem"
   :sm "0.5rem"
   :md "1rem"}}}
```

### Token Reference Syntax

```clojure
;; Simple reference
"$colors.primary"           → "#667eea"

;; Nested path
"$typography.button.font-size" → "14px"

;; With fallback
"$colors.primary || #ffffff" → "#667eea" (or "#ffffff" if not found)
```

### Token Resolution Algorithm

```clojure
(defn resolve-tokens
  "Resolve all token references in a properties map"
  [props context]
  (let [tokens (:tokens context)]
    (walk/postwalk
     (fn [value]
       (cond
         ;; String token reference
         (and (string? value) (.startsWith value "$"))
         (resolve-token-with-fallback value tokens)
         
         ;; Map: resolve recursively
         (map? value)
         (walk/postwalk
          (fn [v]
            (if (and (string? v) (.startsWith v "$"))
              (resolve-token-with-fallback v tokens)
              v))
          value)
         
         ;; Vector: resolve recursively
         (vector? value)
         (mapv #(resolve-tokens-in-value % tokens) value)
         
         ;; Other: return as-is
         :else value))
     props)))
```

### Token Resolution Flow

```
1. Properties with token references
   {:background "$colors.primary"
    :padding "$spacing.md"}
   ↓
2. Parse token paths
   "$colors.primary" → [:colors :primary]
   "$spacing.md" → [:spacing :md]
   ↓
3. Lookup in tokens map
   (get-in tokens [:colors :primary]) → "#667eea"
   (get-in tokens [:spacing :md]) → "1rem"
   ↓
4. Replace token references with values
   {:background "#667eea"
    :padding "1rem"}
```

### Token Inheritance

Tokens can be overridden at any hierarchy level:

```clojure
;; Global (global/tokens.edn)
{:tokens {:colors {:primary "#667eea"}}}

;; Section (sections/header.edn)
{:header {:tokens {:colors {:primary "#ff0000"}}}}  ; Override for header section

;; Template (templates/dashboard.edn)
{:dashboard {:tokens {:colors {:primary "#00ff00"}}}}  ; Override for dashboard template
```

**Resolution**: Tokens are resolved after inheritance, so section/template token overrides apply.

---

## 4. Complete Resolution Pipeline

### Full Compilation Flow

```
Element: [:button {:text "Click" :variant :primary}]
   ↓
1. PARSE
   {:type :button
    :props {:text "Click" :variant :primary}
    :children []}
   ↓
2. INHERITANCE RESOLUTION
   - Load from Global: {:padding "$spacing.md"}
   - Load from Component: {:background "$colors.primary"}
   - Load from Section: {}
   - Load from Template: {}
   - Merge with Instance: {:text "Click" :variant :primary}
   
   Result: {:padding "$spacing.md"
            :background "$colors.primary"
            :text "Click"
            :variant :primary}
   ↓
3. TOKEN RESOLUTION
   - Resolve "$spacing.md" → "1rem"
   - Resolve "$colors.primary" → "#667eea"
   
   Result: {:padding "1rem"
            :background "#667eea"
            :text "Click"
            :variant :primary}
   ↓
4. PROPERTY EXPANSION
   - Expand shortcuts (:bg → :background, etc.)
   
   Result: (no changes in this example)
   ↓
5. STYLING APPLICATION
   - Load shadcn-ui.edn
   - Get base classes: ["inline-flex" "items-center" ...]
   - Get variant classes: ["bg-blue-600" "text-white" ...]
   - Merge with existing classes
   
   Result: {:padding "1rem"
            :background "#667eea"
            :text "Click"
            :variant :primary
            :class "inline-flex items-center ... bg-blue-600 text-white"}
   ↓
6. PLATFORM COMPILATION
   - HTML: Map to <button> element
   - CSS: Extract style properties → inline styles
   - HTMX: Extract HTMX attributes
   
   Result: [:button {:class "inline-flex items-center ... bg-blue-600 text-white"
                     :style "padding: 1rem; background: #667eea;"} "Click"]
```

### Implementation in Compiler

```clojure
(defrecord FormaCompiler []
  core-compiler/CompilerPipeline
  
  (parse-element [_ element]
    "Parse Forma element (handles vector and map syntax)")
  
  (expand-properties [_ props]
    "Expand property shortcuts (:bg → :background, etc.)")
  
  (apply-styling [_ element resolved-props context]
    "Apply styling from styles/shadcn-ui.edn")
  
  (compile-element [compiler element context]
    "Compile through platform stack (HTML → CSS → HTMX)"))
```

The compiler uses Kora's universal pipeline:

```clojure
(defn compile-with-pipeline
  "Universal compilation pipeline"
  [compiler element context hierarchy-levels]
  (let [;; Step 1: Parse
        parsed (parse-element compiler element)
        
        ;; Step 2: Resolve inheritance
        inherited (inheritance/resolve-inheritance parsed context hierarchy-levels)
        
        ;; Step 3: Resolve tokens
        resolved (tokens/resolve-tokens inherited context)
        
        ;; Step 4: Expand properties
        expanded (expand-properties compiler resolved)
        
        ;; Step 5: Apply styling
        styled (apply-styling compiler parsed expanded context)
        
        ;; Step 6: Compile to platform
        compiled (compile-element compiler (assoc parsed :props styled) context)]
    compiled))
```

---

## 5. Three-Tier Resource Resolution

### Resolution Order

Resources (components, platforms, styles) are resolved in this order:

1. **Project** (`projects/{project-name}/`) - Highest priority
2. **Library** (`library/`) - Medium priority
3. **Default** (`default/` or `forma/resources/forma/`) - Lowest priority, fallback

### Example: Component Resolution

```clojure
;; Looking for components/button.edn

1. Check: projects/dashboard-example/components/button.edn  ✅ Found!
   → Use this (project-specific button)

2. If not found, check: library/components/button.edn
   → Use this (shared library component)

3. If not found, check: default/components/button.edn
   → Use this (default component)
```

### Implementation

```clojure
(defn load-resource
  "Load resource using three-tier resolution"
  [resource-path project-name]
  (let [resolution-order [:project :library :default]
        resource (reduce
                   (fn [acc tier]
                     (if acc
                       acc  ; Already found, return it
                       (let [path (case tier
                                    :project (str "projects/" project-name "/" resource-path)
                                    :library (str "library/" resource-path)
                                    :default (str "default/" resource-path))]
                         (try
                           (load-edn-file path)
                           (catch Exception _ nil)))))
                   nil
                   resolution-order)]
    resource))
```

---

## 6. Performance Optimizations

### Pre-Resolution

Pre-resolve context once per request/page, not per element:

```clojure
(defn pre-resolve-context
  "Pre-resolve inheritance and tokens once per request/page"
  [context]
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
            :pre-resolved true})))
```

**Performance Impact**: 
- **Without pre-resolution**: O(components × hierarchy-levels × elements)
- **With pre-resolution**: O(components × hierarchy-levels) once + O(elements) lookups
- **Speedup**: ~333x for large pages

### Caching

- **Platform configs**: Memoized `load-platform-config`
- **Platform stacks**: Memoized `load-platform-stack`
- **Hierarchy data**: Memoized `hierarchy-data`
- **Styling systems**: Memoized `load-styling-system`
- **Pre-resolved contexts**: Cached per context hash

---

## 7. Key Insights

### What Gets Inherited

**Inheritance applies to property values**, not to:
- Component definitions (structure)
- Styling systems (appearance)
- Platform rules (compilation)

### Inheritance Flow

1. **Component definitions** define structure (fixed)
2. **Inheritance** merges property values across hierarchy (values flow)
3. **Styling** converts properties → CSS classes (transformation)
4. **Platform** compiles to output format (compilation)

### Token Resolution Timing

Tokens are resolved **after inheritance** but **before styling**:

```
Inheritance → Token Resolution → Styling → Platform Compilation
```

This allows:
- Token references in inherited properties
- Token overrides at hierarchy levels
- Token values used in styling system

### Component Discovery

Components can be defined:
1. **Explicitly** in `components/*.edn` files
2. **Inline** in sections/templates (future feature)
3. **Ad-hoc** in hierarchy levels (future feature)

---

## 8. Example: Complete Resolution

### Input

```clojure
;; Page element
[:button {:text "Click Me" :variant :primary}]

;; Global (global/defaults.edn)
{:tokens {:colors {:primary "#667eea"}
          :spacing {:md "1rem"}}
 :components {:button {:padding "$spacing.md"}}}

;; Component (components/button.edn)
{:button {:variants {:primary {:background "$colors.primary"}}}}

;; Section (sections/header.edn)
{:header {:components {:button {:size :sm}}}}

;; Template (templates/dashboard.edn)
{:dashboard {:components {:button {:variant :primary}}}}
```

### Resolution Steps

1. **Parse**: `{:type :button :props {:text "Click Me" :variant :primary}}`

2. **Inheritance**:
   - Global: `{:padding "$spacing.md"}`
   - Component: `{:background "$colors.primary"}` (from variant)
   - Section: `{:size :sm}`
   - Template: `{:variant :primary}`
   - Instance: `{:text "Click Me" :variant :primary}`
   - **Merged**: `{:padding "$spacing.md" :background "$colors.primary" :size :sm :variant :primary :text "Click Me"}`

3. **Token Resolution**:
   - `"$spacing.md"` → `"1rem"`
   - `"$colors.primary"` → `"#667eea"`
   - **Resolved**: `{:padding "1rem" :background "#667eea" :size :sm :variant :primary :text "Click Me"}`

4. **Styling**:
   - Base classes: `["inline-flex" "items-center" ...]`
   - Variant classes: `["bg-blue-600" "text-white" ...]`
   - **Styled**: `{:padding "1rem" :background "#667eea" :size :sm :variant :primary :text "Click Me" :class "inline-flex items-center ... bg-blue-600 text-white"}`

5. **Platform Compilation**:
   - HTML: `<button>`
   - CSS: Extract `:padding` and `:background` → inline styles
   - HTMX: No HTMX attributes
   - **Final**: `[:button {:class "inline-flex items-center ... bg-blue-600 text-white" :style "padding: 1rem; background: #667eea;"} "Click Me"]`

---

## 9. Summary

### Inheritance System
- ✅ Universal and domain-agnostic
- ✅ Works with any hierarchy structure
- ✅ Deep merge algorithm (right overrides left)
- ✅ Priority order: Global → Components → Sections → Templates → Pages

### Component System
- ✅ Three-tier resolution: Project → Library → Default
- ✅ Component definitions in EDN files
- ✅ Structure, variants, and sizes support
- ✅ Component discovery from multiple sources

### Token Resolution
- ✅ `$token.path` syntax
- ✅ Fallback support: `$token.path || fallback`
- ✅ Resolved after inheritance, before styling
- ✅ Token overrides at hierarchy levels

### Performance
- ✅ Pre-resolution optimization
- ✅ Aggressive caching
- ✅ ~333x speedup for large pages

### Key Principles
1. **Inheritance applies to property values**, not structure/styling/platform
2. **Tokens resolved after inheritance**, before styling
3. **Three-tier resolution** for all resources
4. **Universal pipeline** works for any domain

