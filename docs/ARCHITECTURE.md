# Forma Compiler Architecture

## Overview

Forma is a generic UI compiler that separates concerns into three distinct layers: **Definitions**, **Styles**, and **Platforms**. Components are platform-agnostic and define only structure. The platform stack is determined at compile time, allowing the same generic components to compile to multiple platforms (HTML + CSS + HTMX, HTML + CSS + React, etc.).

**Core Principle**: Forma follows Kora's universal EDN format convention - a single EDN format that works for every style system and platform, with no custom code needed. Everything is driven by EDN conventions.

---

## Core Principles

### 1. Components Define Structure Only
Components are **generic and platform-agnostic**. They define:
- **Structure**: Slots, props, defaults
- **Validation**: Schema hints
- **Accessibility**: A11y attributes

**They do NOT define:**
- Styling (appearance)
- Platform-specific behavior
- State management implementation

### 2. Three-Layer Separation

#### Layer 1: Component Definitions (`/components`)
**Purpose**: What the component is (structure, props, slots)

**Example**: `components/button.edn`
```clojure
{:button
 {:slots [:children]
  :props [:variant :size :disabled :type :on-click]
  :a11y {:role "button"}
  :default-props {:type "button" :disabled false}
  :schema-hints {:variant [:primary :secondary :destructive ...]}}}
```

**Contains**: Structure, defaults, validation hints, accessibility
**No styling, no platform specifics**

#### Layer 2: Styling Systems (`/styles`)
**Purpose**: How the component looks (classes, variants, themes)

**Example**: `styles/shadcn-ui.edn`
```clojure
{:components
 {:button
  {:base ["inline-flex" "items-center" "justify-center" ...]
   :variants {:primary ["bg-blue-600" "text-white" ...]
              :secondary ["bg-secondary" ...]}}}}
```

**Contains**: CSS classes, variants, base styles
**No platform specifics, no component structure**

#### Layer 3: Platform Compilation (`/platforms`)
**Purpose**: How the component compiles to target platform

**Example**: `platforms/html.edn` + `platforms/htmx.edn`
```clojure
;; html.edn - Base HTML structure
{:platform :html
 :elements {:button {:element "button" :class-attr :class}}}

;; htmx.edn - Extends HTML, adds interactivity
{:platform :htmx
 :extends :html
 :attributes {:htmx {:keys [:hx-get :hx-post ...]}}
 :component-mappings {:state-manager {:state-to-htmx {...}}}}
```

**Contains**: Element mappings, attribute mappings, content handling rules
**No styling, no component structure**

---

## Platform Stack Architecture

### Stack Determined at Compile Time

The platform stack is **specified in the compile-time context**, not derived from components.

```clojure
;; Compile with stack: HTML + CSS + HTMX
(let [context {:platform-stack [:html :css :htmx]
               :styling-system :shadcn-ui}]
  (compile-to-stack elements context))

;; Compile with stack: HTML + CSS + React
(let [context {:platform-stack [:html :css :react]
               :styling-system :shadcn-ui}]
  (compile-to-stack elements context))
```

### Platform Extension Model

Platforms can extend other platforms:

```clojure
;; platforms/html.edn (base)
{:platform :html
 :description "HTML structure compilation"
 :elements {...}}

;; platforms/css.edn (extends HTML)
{:platform :css
 :extends :html
 :description "CSS styling compilation"
 :style-properties [...]}

;; platforms/htmx.edn (extends HTML)
{:platform :htmx
 :extends :html
 :description "HTMX interactivity compilation"
 :attributes {:htmx {:keys [...]}}
 :component-mappings {...}}

;; platforms/react.edn (extends HTML)
{:platform :react
 :extends :html
 :description "React compilation"
 :component-mappings {...}}
```

**Key Point**: HTMX extends HTML (not a separate platform). React extends HTML. CSS extends HTML.

### Platform Loading with Extension

```clojure
(defn load-platform-config
  "Load platform config, resolving :extends if present"
  [platform-name project-name]
  (let [config (load-resource (str "platforms/" (name platform-name) ".edn") project-name)
        extends (get config :extends)]
    (if extends
      ;; Recursively merge with base platform
      (deep-merge (load-platform-config extends project-name) config)
      ;; Standalone platform (no extension)
      config)))

(defn load-platform-stack
  "Load all platform configs in stack, resolving extensions"
  [platform-stack project-name]
  (mapv #(load-platform-config % project-name) platform-stack))
```

**See also**: [Platform Extension and Stacking](./PLATFORM-EXTENSION-STACKING.md) for detailed documentation.

---

## Compilation Flow

### Generic Component → Platform Stack

```
1. Generic Component (structure only)
   ↓
   [:state-manager {:state {:count 0} :on-change :increment}]
   
2. Apply Styling (from /styles)
   ↓
   Add CSS classes: ["state-manager" "..." ...]
   
3. Compile to Stack: [:html :css :htmx]
   ↓
   a. HTML (html.edn): Structure → <div>
   b. CSS (css.edn): Styling → class="state-manager ..."
   c. HTMX (htmx.edn): Interactivity → hx-vals, hx-trigger
   ↓
4. Final Output
   ↓
   [:div {:class "state-manager ..." 
          :hx-vals {:count 0} 
          :hx-trigger "click"}]
```

### Compilation Function

```clojure
(defn compile-to-stack
  "Compile elements to platform stack specified in context"
  [elements context]
  (let [platform-stack (get context :platform-stack [:html])
        platform-configs (load-platform-stack platform-stack)
        styled-elements (apply-styling-to-elements elements context)]
    (mapv #(compile-element-to-stack % platform-configs context) styled-elements)))

(defn compile-element-to-stack
  "Compile element through all platforms in stack"
  [element platform-configs context]
  (reduce
   (fn [result platform-config]
     (apply-platform-compilation result platform-config context))
   element
   platform-configs))
```

---

## Generic Component Mapping

### Components Are Platform-Agnostic

**Generic component definition:**
```clojure
;; components/state-manager.edn
{:state-manager
 {:slots [:children]
  :props [:state :on-change :target]
  :default-props {}
  :schema-hints {}}}
```

**No platform references** - just generic props: `:state`, `:on-change`, `:target`

### Platform Configs Map Generic → Platform-Specific

```clojure
;; platforms/htmx.edn
{:component-mappings
 {:state-manager
  {:state-to-htmx {:state :hx-vals      ; Generic :state → HTMX :hx-vals
                   :on-change :hx-trigger  ; Generic :on-change → HTMX :hx-trigger
                   :target :hx-target}    ; Generic :target → HTMX :hx-target
   :default-attrs {:hx-swap "outerHTML"}}}}

;; platforms/react.edn (future)
{:component-mappings
 {:state-manager
  {:state-to-hooks {:state :useState      ; Generic :state → React :useState
                    :on-change :useCallback}  ; Generic :on-change → React :useCallback
   :output-format :jsx}}}
```

### Compilation Process

```clojure
;; Generic component usage
[:state-manager {:state {:count 0} :on-change :increment}]

;; Context: {:platform-stack [:html :htmx]}
;; Compiler:
;; 1. Loads [:html :htmx] stack
;; 2. Finds :state-manager mapping in htmx.edn
;; 3. Maps {:state {:count 0}} → {:hx-vals {:count 0}}
;; 4. Outputs: [:div {:hx-vals {:count 0} :hx-trigger "click"}]
```

---

## EDN-Driven Configuration

### Platform Config Schema

```clojure
{:platform :html
 :description "HTML SSR compilation rules"
 :extends nil  ; or :css, :htmx, etc. if extending another platform
 :compiler {:output-format :hiccup
            :default-element "div"
            :class-attr :class
            :style-properties [:background :color :font-size ...]  ; For CSS extraction
            :oxygen-mappings {:design.background :background ...}}  ; For Oxygen props
 :elements
 {:h1 {:element "h1"
       :class-attr :class
       :content-source :children  ; :children, :text, :content.content.text, :first-child
       :content-handling :resolve-vars  ; :resolve-vars, :none, :raw
       :children-handling :compile-all  ; :compile-all, :first-only, :none
       :attr-map {}
       :default-attrs {}
       :exclude-from-styles [:level :text :content.content.text]}}
 :component-mappings
 {:state-manager {:state-to-htmx {...}  ; Generic → platform mapping
                  :default-attrs {...}}}
 :attributes {:htmx {:keys [...]  ; HTMX attribute definitions
                    :sugar {...}}}}
```

### All Rules in EDN

**No hardcoded logic in compiler:**
- Element mappings → EDN
- Content handling → EDN (`:content-source`, `:content-handling`)
- Children handling → EDN (`:children-handling`)
- Attribute mappings → EDN (`:attr-map`)
- Default attributes → EDN (`:default-attrs`)
- HTMX attributes → EDN (`:attributes :htmx`)
- CSS properties → EDN (`:compiler :style-properties`)
- Component mappings → EDN (`:component-mappings`)

---

## Inheritance System

### What Gets Inherited

**Inheritance applies to property values**, not to:
- Component definitions (structure)
- Styling systems (appearance)
- Platform rules (compilation)

### Flow

1. **Component definitions** define structure (fixed)
2. **Inheritance** merges property values across hierarchy (values flow)
3. **Styling** converts properties → CSS classes (transformation)
4. **Platform** compiles to output format (compilation)

### Hierarchy Levels

- **Global** (`global/defaults.edn`): Base tokens and component defaults
- **Components** (`components/*.edn`): Component structure definitions
- **Sections** (`sections/*.edn`): Section-specific overrides
- **Templates** (`templates/*.edn`): Template-specific overrides
- **Instances** (pages): Instance-specific props (highest priority)

---

## Performance Optimizations

### Pre-Resolution

Pre-resolve context once per request/page, not per element:

```clojure
(defn pre-resolve-context
  "Pre-resolve all inheritance and tokens for a context (one-time cost)"
  [context hierarchy-levels]
  (let [pre-resolved-components
        (reduce-kv
         (fn [acc component-type _]
           (let [element {:type component-type}
                 inherited (inheritance/resolve-inheritance element context hierarchy-levels)
                 resolved (tokens/resolve-tokens inherited context)]
             (assoc acc component-type resolved)))
         {}
         (get-all-component-types context))]
    (assoc context :pre-resolved-components pre-resolved-components)))
```

### Pre-Compilation Optimization

Since everything is declarative and inherited, we can optimize before compilation:

1. **Static Analysis**: Build dependency graph from pages
2. **Tree-Shaking**: Remove unused components, tokens, styles
3. **Flattening**: Resolve all inheritance and tokens
4. **Compilation**: Compile optimized structure (much faster)

**See also**: [Output Formats and Minification](./OUTPUT-FORMATS-AND-MINIFICATION.md) for detailed documentation.

### Caching

- Cache platform configs (memoize `load-platform-config`)
- Cache platform stacks (memoize `load-platform-stack`)
- Cache pre-resolved contexts (per context hash)
- Cache styling system loads

---

## Key Decisions

1. **Components are generic** - no platform references in components
2. **Stack determined at compile time** - specified in context, not derived
3. **Platforms extend base** - HTMX extends HTML, React extends HTML
4. **EDN-driven** - all rules in EDN, compiler is generic engine
5. **Three-layer separation** - Definitions, Styles, Platforms
6. **Inheritance for properties** - not structure, styling, or platform rules
7. **Pre-resolution optimization** - pay inheritance cost once, not per element
8. **Universal EDN convention** - single EDN format for all platforms, no custom code needed
9. **No platform references in compiler** - compiler is fully generic, reads extractors from EDN
10. **Output formats in platforms** - platforms define capabilities, project config selects
11. **Internal minification** - during compilation, controlled by project config
12. **Pre-compilation optimization** - tree-shaking and flattening before compilation

---

## Example: Complete Compilation

```clojure
;; 1. Generic component
[:state-manager {:state {:count 0} :on-change :increment}]

;; 2. Context specifies stack
{:platform-stack [:html :css :htmx]
 :styling-system :shadcn-ui
 :tokens {...}}

;; 3. Compiler:
;;    a. Loads component structure (from /components)
;;    b. Applies styling (from /styles)
;;    c. Compiles through stack:
;;       - html.edn → base structure
;;       - css.edn → styling
;;       - htmx.edn → interactivity mapping
;;    d. Outputs platform-specific result

;; 4. Final output
[:div {:class "state-manager inline-flex items-center ..."
       :hx-vals {:count 0}
       :hx-trigger "click"}]
```

---

## Benefits

- **Generic components** - write once, compile to any platform
- **Explicit stack** - clear what you're compiling to
- **Platform composition** - mix and match platforms (HTML + CSS + HTMX)
- **EDN-driven** - change behavior without code changes
- **Performance** - pre-resolution, caching, and pre-compilation optimization
- **Separation of concerns** - structure, styling, and platforms are independent

---

## Related Documentation

- **[Platform Extension and Stacking](./PLATFORM-EXTENSION-STACKING.md)** - How platforms extend and stack
- **[Output Formats and Minification](./OUTPUT-FORMATS-AND-MINIFICATION.md)** - Output format selection and minification strategy
- **[Inheritance, Components, and Resolution](./INHERITANCE-COMPONENTS-RESOLUTION-ANALYSIS.md)** - How inheritance and resolution work

