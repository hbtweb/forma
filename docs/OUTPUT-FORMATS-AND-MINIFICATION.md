# Output Formats and Minification

## Overview

This document explains how compilation output formats are defined in platforms, configured by project config, and how minification works internally in the compiler.

---

## 1. Output Format Architecture

### Principle

**Output options defined in platforms, configured by project config**

- **Platforms define capabilities**: What output formats are available
- **Project config selects**: Which format to use
- **Separation of concerns**: Platforms = "what's possible", Projects = "what to use"

### Platform Defines Output Formats

```clojure
;; platforms/html.edn
{:platform :html
 :description "HTML SSR compilation rules"
 :compiler {
   :output-formats {
     :hiccup {
       :description "Hiccup data structure (default)"
       :default true
     }
     :html-string {
       :description "HTML string output"
       :minify-option true
     }
     :html-file {
       :description "Write to HTML file"
       :output-path "index.html"
       :minify-option true
     }
   }
   :default-output-format :hiccup
   :default-element "div"
   :class-attr :class
 }}

;; platforms/css.edn
{:platform :css
 :extends :html
 :compiler {
   :extractors {:styles {...}}
   :output-formats {
     :inline {
       :description "Inline styles (default)"
       :default true
     }
     :css-file {
       :description "Separate CSS file"
       :output-path "styles.css"
       :minify-option true
     }
     :css-in-js {
       :description "CSS-in-JS for React"
       :requires [:react]
     }
   }
   :default-output-format :inline
 }}

;; platforms/react.edn (future)
{:platform :react
 :extends :html
 :compiler {
   :output-formats {
     :jsx {
       :description "JSX output"
       :default true
       :minify-option true
     }
     :tsx {
       :description "TypeScript JSX output"
       :minify-option true
     }
   }
   :default-output-format :jsx
 }}
```

### Project Config Selects Format

```clojure
;; projects/my-app/config.edn
{:project-name "my-app"
 :defaults {
   :platform-stack [:html :css :htmx]
   :styling-system :shadcn-ui
 }
 
 ;; Output format selection per platform
 :output {
   :html :html-string      ; Use html-string instead of hiccup
   :css :css-file          ; Use separate CSS file instead of inline
   :minify true            ; Enable minification
 }
 
 ;; Environment-specific overrides
 :environments {
   :development {
     :output {:minify false}
   }
   :production {
     :output {:minify true
              :html :html-file
              :css :css-file}
   }
 }}
```

### Implementation

```clojure
(defn get-output-format
  "Get output format for platform from project config"
  [platform-name context]
  (let [project-output (get-in context [:output platform-name])
        platform-config (load-platform-config platform-name)
        available-formats (get-in platform-config [:compiler :output-formats])
        default-format (get-in platform-config [:compiler :default-output-format])]
    (or (when (contains? available-formats project-output)
          project-output)
        default-format)))
```

---

## 2. Minification Strategy

### Principle

**Minification happens internally, determined by project config**

- **Internal to compiler**: Part of compilation process, not external step
- **Project config controlled**: Dev vs prod behavior
- **Avoids "compiling noise"**: Clean output to all platforms

### Why Internal Minification?

**Problem with external minification:**
- Compiles unused code to every platform
- Wastes compilation time
- Produces larger output
- Requires separate minification step

**Solution: Internal minification**
- Only compile what's needed
- Minify during compilation
- Project config controls behavior
- Clean output to all platforms

### Minification Configuration

```clojure
;; projects/my-app/config.edn
{:project-name "my-app"
 :defaults {
   :platform-stack [:html :css :htmx]
 }
 
 ;; Minification settings
 :minify {
   :enabled true           ; Master switch
   :environment :production ; or :development
   :html {
     :enabled true
     :remove-comments true
     :collapse-whitespace true
     :remove-optional-tags true
   }
   :css {
     :enabled true
     :remove-comments true
     :collapse-whitespace true
     :optimize-colors true
   }
   :js {
     :enabled true
     :remove-comments true
     :collapse-whitespace true
     :mangle-names false   ; Keep names readable in dev
   }
 }}
```

### Implementation

```clojure
(defn should-minify?
  "Determine if minification should be applied"
  [context output-format]
  (let [minify-config (get context :minify)
        environment (get context :environment :production)
        format-config (get-in minify-config [output-format])]
    (and (get minify-config :enabled false)
         (or (= (get minify-config :environment) environment)
             (= environment :production))
         (get format-config :enabled true))))

(defn apply-minification
  "Apply minification based on project config"
  [compiled-element context output-format]
  (if (should-minify? context output-format)
    (minify-element compiled-element (get-in context [:minify output-format]))
    compiled-element))
```

---

## 3. Pre-Compilation Optimization

### Principle

**Optimization happens before compilation since everything is declarative and inherited**

- **Static analysis**: Determine what's used from declarative structure
- **Tree-shaking**: Remove unused components, tokens, styles
- **Flattening**: Resolve inheritance and tokens before compilation
- **Efficiency**: Only compile what's declared and used

### Why Pre-Compilation Optimization?

**Declarative nature enables static analysis:**
- All components defined in EDN files
- All tokens defined in EDN files
- All styles defined in EDN files
- All pages/templates reference components explicitly
- Inheritance is traceable through hierarchy

**We can determine what's used before compilation:**
1. Start from entry points (pages)
2. Trace component usage
3. Trace token references
4. Trace style usage
5. Build dependency graph
6. Remove everything not in graph
7. THEN compile optimized structure

### Optimization Pipeline

```clojure
(defn optimize-edn-structure
  "Static analysis and tree-shaking at EDN level
   Removes unused components, tokens, styles before compilation"
  [pages context]
  (let [;; Step 1: Build dependency graph from pages
        dependency-graph (build-dependency-graph pages context)
        
        ;; Step 2: Determine what's actually used
        used-components (get-used-components dependency-graph)
        used-tokens (get-used-tokens dependency-graph)
        used-styles (get-used-styles dependency-graph)
        
        ;; Step 3: Remove unused declarations
        optimized-components (select-keys components used-components)
        optimized-tokens (select-used-tokens tokens used-tokens)
        optimized-styles (select-used-styles styles used-styles)
        
        ;; Step 4: Flatten inheritance (resolve all tokens, merge properties)
        flattened (flatten-inheritance optimized-components optimized-tokens)
        
        ;; Step 5: Return optimized structure
        {:components optimized-components
         :tokens optimized-tokens
         :styles optimized-styles
         :flattened flattened}))
```

### Static Analysis

```clojure
(defn build-dependency-graph
  "Build dependency graph from pages through all hierarchy levels"
  [pages context]
  (let [;; Start from pages (entry points)
        page-components (extract-components-from-pages pages)
        
        ;; Trace through templates
        template-components (extract-components-from-templates 
                            (get-templates-used page-components))
        
        ;; Trace through sections
        section-components (extract-components-from-sections
                           (get-sections-used template-components))
        
        ;; Trace component variants
        component-variants (extract-variants-used 
                          (concat page-components template-components section-components))
        
        ;; Trace token references
        token-references (extract-token-references 
                        (concat page-components template-components section-components))
        
        ;; Trace style usage
        style-usage (extract-style-usage component-variants)]
    {:components (set (concat page-components template-components section-components))
     :variants component-variants
     :tokens token-references
     :styles style-usage}))
```

### Tree-Shaking

```clojure
(defn tree-shake-components
  "Remove unused component definitions"
  [all-components used-components]
  (select-keys all-components used-components))

(defn tree-shake-tokens
  "Remove unused token definitions"
  [all-tokens used-token-paths]
  (let [used-paths (set used-token-paths)]
    (walk/postwalk
     (fn [x]
       (if (and (map? x) (contains? used-paths (current-path)))
         x
         (if (map? x)
           (into {} (filter (fn [[k v]] (used? k v used-paths))) x)
           x)))
     all-tokens)))

(defn tree-shake-styles
  "Remove unused style definitions"
  [all-styles used-styles]
  (select-keys all-styles used-styles))
```

### Flattening

```clojure
(defn flatten-inheritance
  "Flatten all inheritance before compilation
   Resolve all tokens, merge all properties, remove inheritance structure"
  [optimized-components optimized-tokens]
  (reduce-kv
   (fn [acc component-type component-def]
     (let [;; Resolve all inheritance for this component
           flattened (resolve-all-inheritance component-type optimized-components)
           ;; Resolve all tokens
           token-resolved (resolve-all-tokens flattened optimized-tokens)]
       (assoc acc component-type token-resolved)))
   {}
   optimized-components))
```

### Complete Flow

```
1. STATIC ANALYSIS
   Pages → Templates → Sections → Components
   Build dependency graph
   ↓
2. TREE-SHAKING
   Remove unused components
   Remove unused tokens
   Remove unused styles
   ↓
3. FLATTENING
   Resolve all inheritance
   Resolve all tokens
   Remove inheritance structure
   ↓
4. COMPILATION
   Compile optimized structure
   (Much faster - smaller input, pre-resolved)
   ↓
5. MINIFICATION (Optional)
   Minify HTML/CSS/JS output
```

### Project Config Control

```clojure
;; projects/my-app/config.edn
{:project-name "my-app"
 :optimization {
   :enabled true
   :tree-shake true        ; Remove unused components/tokens/styles
   :flatten true           ; Flatten inheritance before compilation
   :environment :production
 }
 :minify {
   :enabled true
   :environment :production
 }}
```

---

## 4. Benefits

### Output Format Architecture
- ✅ Platforms declare capabilities
- ✅ Projects choose what to use
- ✅ Easy to add new output formats
- ✅ Environment-specific configurations

### Internal Minification
- ✅ Clean output to all platforms
- ✅ Project-controlled (dev vs prod)
- ✅ Internal to compiler (not external step)
- ✅ Configurable per output type

### Pre-Compilation Optimization
- ✅ Faster compilation (smaller input)
- ✅ Smaller output (only what's used)
- ✅ Cleaner code (no unused declarations)
- ✅ Better performance (pre-resolved, flattened)

---

## 5. Example: Before vs After Optimization

### Before Optimization

```clojure
;; Load ALL components (100+ components)
{:components {:button {...} :card {...} :modal {...} ... 100 more}}

;; Load ALL tokens (1000+ tokens)
{:tokens {:colors {:primary "#667eea" ... 50 colors}
          :spacing {:xs "4px" ... 20 spacing}
          ... 1000 more tokens}}

;; Compile everything
;; Result: Large output with unused code
```

### After Optimization

```clojure
;; Only used components (5 components)
{:components {:button {...} :card {...} :heading {...} :div {...} :span {...}}}

;; Only used tokens (20 tokens)
{:tokens {:colors {:primary "#667eea" :secondary "#6c757d"}
          :spacing {:md "1rem" :lg "1.5rem"}}}

;; Flattened (no inheritance structure)
{:button {:padding "1rem" :background "#667eea" ...}}  ; Already resolved

;; Compile optimized structure
;; Result: Small, optimized output
```

---

## 6. Styling System Extension and Stacking

### Current State

**Styling systems are single files** (e.g., `shadcn-ui.edn`)
- No extension or stacking support
- Loaded via: `load-styling-system :shadcn-ui`
- Each styling system is standalone

### Proposed Architecture (Like Platforms)

Styling systems should work like platforms - with extension and stacking support.

#### A. Extension Model

```clojure
;; styles/tailwind.edn (base)
{:theme :tailwind
 :description "Base Tailwind utility system"
 :utilities {
   :spacing {:margin {:keys [:m :mx :my :mt :mr :mb :ml]
                      :values {:0 "0" :px "1px" :0.5 "0.125rem" :1 "0.25rem" ...}}
            :padding {:keys [:p :px :py :pt :pr :pb :pl]
                      :values {:0 "0" :px "1px" ...}}}
   :typography {:font-size {:keys [:text-xs :text-sm :text-base ...]
                            :values {:xs "0.75rem" :sm "0.875rem" ...}}}
   :colors {:background {:keys [:bg-*]
                         :palette {:slate {:50 "#f8fafc" ...}
                                  :gray {:50 "#f9fafb" ...}}}}
   ;; ... all Tailwind utility categories
 }}

;; styles/shadcn-ui.edn (extends Tailwind)
{:theme :shadcn-ui
 :description "shadcn-ui component patterns"
 :extends :tailwind  ; ← Extends Tailwind
 :components {
   :button {
     :base ["inline-flex" "items-center" "justify-center" ...]
     :variants {
       :primary ["bg-blue-600" "text-white" ...]
       :secondary ["bg-secondary" ...]
     }
   }
   ;; ... other components
 }}

;; styles/my-brand.edn (extends shadcn-ui)
{:theme :my-brand
 :description "Brand-specific overrides"
 :extends :shadcn-ui  ; ← Extends shadcn-ui
 :components {
   :button {
     :variants {
       :primary ["bg-brand-primary" "text-brand-foreground" ...]  ; Override
     }
   }
 }}
```

#### B. Stacking Model

```clojure
;; Stack multiple styling systems
{:styling-stack [:tailwind :shadcn-ui :custom-animations]}

;; Compilation:
;; 1. Tailwind: Base utilities
;; 2. shadcn-ui: Component patterns (extends Tailwind)
;; 3. custom-animations: Additional classes
;; Final: All classes combined
```

**Project Config:**
```clojure
;; projects/my-app/config.edn
{:defaults {
   :styling-stack [:tailwind :shadcn-ui :custom-animations]
   ;; OR single system (backward compatible)
   :styling-system :shadcn-ui
 }}
```

### Implementation

```clojure
(defn load-styling-system
  "Load styling system, resolving :extends if present"
  [system-name]
  (let [styling (load-resource (str "styles/" (name system-name) ".edn"))
        extends (get styling :extends)]
    (if extends
      ;; Merge with base styling system
      (deep-merge (load-styling-system extends) styling)
      ;; Standalone styling system
      styling)))

(defn load-styling-stack
  "Load all styling systems in stack, resolving extensions"
  [styling-stack]
  (mapv load-styling-system styling-stack))

(defn apply-styling-stack
  "Apply all styling systems in stack"
  [element resolved-props context]
  (let [styling-stack (or (get context :styling-stack)
                         ;; Backward compatible: single system
                         [(get context :styling-system :shadcn-ui)])
        styling-configs (load-styling-stack styling-stack)]
    (reduce
     (fn [result styling-config]
       (apply-styling-from-config result element styling-config))
     resolved-props
     styling-configs)))
```

### Interaction with Tree-Shaking

**How styling extension/stacking affects tree-shaking:**

When building the dependency graph, we need to trace through styling systems:

```clojure
(defn build-dependency-graph
  "Build dependency graph including styling systems"
  [pages context]
  (let [;; Component usage
        used-components (extract-components-from-pages pages)
        
        ;; Styling system stack
        styling-stack (get context :styling-stack 
                          [(get context :styling-system :shadcn-ui)])
        
        ;; Trace styling dependencies
        styling-deps (trace-styling-dependencies 
                     used-components styling-stack)
        
        ;; Used utilities from base systems
        used-utilities (extract-used-utilities styling-deps)]
    {:components used-components
     :styling-dependencies styling-deps
     :utilities used-utilities}))

(defn trace-styling-dependencies
  "Trace which styling systems and utilities are actually used"
  [used-components styling-stack]
  (let [;; Load all styling systems in stack (with extensions)
        all-styling-systems (load-styling-stack styling-stack)
        
        ;; For each used component, trace which styles it uses
        component-styles (reduce-kv
                         (fn [acc component-type _]
                           (let [styles (get-styles-for-component 
                                        component-type all-styling-systems)
                                 ;; Extract utility classes used
                                 utilities (extract-utilities-from-classes styles)]
                             (assoc acc component-type {:styles styles
                                                       :utilities utilities})))
                         {}
                         used-components)]
    component-styles))

(defn tree-shake-styling-systems
  "Remove unused utilities from styling systems"
  [all-styling-systems used-utilities]
  (mapv
   (fn [styling-system]
     (if-let [utilities (get styling-system :utilities)]
       ;; Remove unused utilities
       (assoc styling-system 
              :utilities (select-used-utilities utilities used-utilities))
       ;; No utilities (component-only system)
       styling-system))
   all-styling-systems))
```

**Example: Tree-Shaking with Extension**

```clojure
;; Input: shadcn-ui extends tailwind
;; Used components: [:button :card]
;; Button uses: ["inline-flex" "items-center" "bg-blue-600"]
;; Card uses: ["rounded-lg" "border" "bg-card"]

;; Tree-shaking analysis:
;; 1. shadcn-ui provides button/card component styles
;; 2. shadcn-ui extends tailwind
;; 3. Button uses: flex utilities, color utilities
;; 4. Card uses: border-radius, border, background utilities

;; Result: Only include used Tailwind utilities
;; - Keep: flex utilities, color utilities, border utilities, spacing utilities
;; - Remove: grid utilities, transform utilities, animation utilities (if unused)
```

**Key Insight:**
- **Extension**: When `shadcn-ui` extends `tailwind`, we can tree-shake unused Tailwind utilities
- **Stacking**: When stacking `[:tailwind :shadcn-ui :animations]`, we tree-shake each system independently
- **Efficiency**: Only include utilities that are actually referenced by used components

### Interaction with Minification

**How styling extension/stacking affects minification:**

```clojure
(defn minify-styling-classes
  "Minify CSS classes from styling systems"
  [classes minify-config]
  (if (get minify-config :enabled false)
    (let [;; If using Tailwind, can minify class names
          minify-classes (get minify-config :minify-classes true)
          ;; If stacking, preserve all classes
          preserve-stack (get minify-config :preserve-stack true)]
      (if (and minify-classes (not preserve-stack))
        ;; Minify individual class names
        (mapv minify-class-name classes)
        ;; Just remove whitespace/duplicates
        (distinct (filter seq classes))))
    classes))

(defn minify-class-name
  "Minify a single class name (if supported)"
  [class-name]
  ;; For Tailwind: "bg-blue-600" → "bg-b6" (if minification enabled)
  ;; For custom: preserve as-is
  (if (tailwind-class? class-name)
    (minify-tailwind-class class-name)
    class-name))
```

**Extension vs Stacking for Minification:**

**Extension (`:extends`):**
- Systems are merged before minification
- Can optimize merged result
- Single system in final output
- Easier to minify (one system)
- **Minification**: Optimize merged result, can minify class names

**Stacking (`:styling-stack`):**
- Systems applied sequentially
- All systems contribute classes
- Need to preserve all classes
- More complex minification (multiple systems)
- **Minification**: Preserve all classes, minify individually per system

### Complete Flow with Styling Systems

```
1. STATIC ANALYSIS
   Pages → Components → Styling Systems
   Build dependency graph
   Trace styling dependencies (extension chains, stacks)
   ↓
2. TREE-SHAKING
   Remove unused components
   Remove unused tokens
   Remove unused styles
   Remove unused utilities (from base systems via extension)
   Remove unused classes (from stacked systems)
   ↓
3. FLATTENING
   Resolve all inheritance
   Resolve all tokens
   Merge styling systems (extension)
   Combine styling stacks
   Remove inheritance structure
   ↓
4. COMPILATION
   Apply styling stack
   Compile optimized structure
   ↓
5. MINIFICATION (Optional)
   Minify HTML/CSS/JS output
   Minify class names (if enabled, per system)
```

### Benefits

✅ **Reusable base** (Tailwind utilities)
✅ **Composable** (stack multiple systems)
✅ **Override-friendly** (extend and customize)
✅ **Consistent with platform architecture**
✅ **Backward compatible** (single system still works)
✅ **Tree-shakeable** (remove unused utilities from extended systems)
✅ **Minifiable** (optimize class names, preserve stack if needed)

### Example: Styling Stack Flow

```clojure
;; Element
[:button {:variant :primary}]

;; Styling Stack: [:tailwind :shadcn-ui :custom-animations]

;; Step 1: Tailwind (base utilities)
;; - Provides utility classes
;; Result: {} (no component-specific classes yet)

;; Step 2: shadcn-ui (extends Tailwind)
;; - Base classes: ["inline-flex" "items-center" ...]
;; - Variant classes: ["bg-blue-600" "text-white" ...]
;; Result: {:class "inline-flex items-center ... bg-blue-600 text-white"}

;; Step 3: custom-animations (additional)
;; - Adds: ["animate-fade-in" "transition-all"]
;; Result: {:class "inline-flex items-center ... bg-blue-600 text-white animate-fade-in transition-all"}

;; Final: All classes combined
```

### Extension vs Stacking

**Extension (`:extends`):**
- One system builds on another
- Deep merge (base + extending)
- Override values
- Single system in final result
- **Tree-shaking**: Can remove unused base utilities
- **Minification**: Optimize merged result

**Stacking (`:styling-stack`):**
- Multiple systems applied sequentially
- Each adds to previous
- Cumulative classes
- All systems contribute
- **Tree-shaking**: Remove unused from each system
- **Minification**: Preserve all classes, minify individually

**Use Cases:**
- **Extension**: `my-brand` extends `shadcn-ui` (override specific components)
- **Stacking**: `[:tailwind :shadcn-ui :animations]` (combine multiple systems)

---

## 7. Summary

**Output Formats:**
- Defined in platforms (capabilities)
- Selected by project config (what to use)
- Environment-specific overrides supported

**Minification:**
- Internal to compiler
- Controlled by project config
- Avoids "compiling noise"
- Clean output to all platforms

**Pre-Compilation Optimization:**
- Static analysis determines what's used
- Tree-shaking removes unused declarations
- Flattening resolves inheritance before compilation
- Only compile what's declared and used

**Styling System Extension & Stacking:**
- Extension model (`:extends`) for building on base systems
- Stacking model (`:styling-stack`) for combining multiple systems
- Tree-shakeable: Remove unused utilities from extended systems
- Minifiable: Optimize class names, preserve stack if needed
- Consistent with platform architecture
- Backward compatible with single system approach

This architecture provides maximum efficiency and flexibility while maintaining clean separation of concerns.

