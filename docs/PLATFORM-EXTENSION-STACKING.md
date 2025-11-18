# Platform Extension and Stacking Architecture

## Overview

This document explains how platforms extend each other and how platform stacking works in the Forma compiler system.

---

## 1. Platform Extension (`:extends`)

### Concept

Platforms can extend other platforms using the `:extends` keyword, similar to class inheritance. This allows platforms to build upon base functionality without duplication.

### How It Works

```clojure
;; Base platform (html.edn)
{:platform :html
 :description "HTML SSR compilation rules"
 :compiler {:output-format :hiccup
            :default-element "div"
            :class-attr :class}
 :elements {:button {:element "button" ...}}}

;; Extended platform (css.edn)
{:platform :css
 :description "CSS styling compilation - extends HTML"
 :extends :html  ; ← Extends HTML
 :compiler {
   :extractors {
     :styles {
       :type :property-selector
       :keys [:background :color :font-size ...]
       :output-format :css-string
       :output-key :style
     }
   }
 }}

;; Extended platform (htmx.edn)
{:platform :htmx
 :description "HTMX interactivity compilation - extends HTML"
 :extends :html  ; ← Extends HTML
 :compiler {
   :extractors {
     :attributes {
       :type :attribute-selector
       :keys [:hx-get :hx-post :hx-patch ...]
       :output-format :attributes
     }
   }
 }
 :component-mappings {...}}
```

### Extension Algorithm

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
```

**Key Behavior:**
- **Deep merge**: Base config merged with extending config
- **Recursive**: Supports extension chains (A extends B, B extends C)
- **Override**: Extending config overrides base config values
- **Additive**: Extending config adds new keys

### Example: Extension Merge

```clojure
;; Base (html.edn)
{:platform :html
 :elements {:button {:element "button" :class-attr :class}}}

;; Extended (css.edn)
{:platform :css
 :extends :html
 :compiler {:extractors {:styles {...}}}}

;; Result after merge:
{:platform :css
 :elements {:button {:element "button" :class-attr :class}}  ; from HTML
 :compiler {:extractors {:styles {...}}}}                     ; from CSS
```

---

## 2. Platform Stacking

### Concept

Platforms are compiled sequentially in a stack. Each platform in the stack processes the element in order, building upon the previous platform's output.

### Stack Configuration

```clojure
;; Stack specified in context or project config
{:platform-stack [:html :css :htmx]}

;; Compilation flow:
Element → HTML → CSS → HTMX → Final Output
```

### Stack Loading

```clojure
(defn load-platform-stack
  "Load all platform configs in stack, resolving extensions"
  [platform-stack project-name]
  (mapv #(load-platform-config % project-name) platform-stack))
```

### Sequential Compilation

```clojure
(compile-element [compiler element context]
  (let [platform-stack (get context :platform-stack [:html])
        platform-configs (load-platform-stack-memo platform-stack project-name)
        ;; Compile element through all platforms in stack sequentially
        compiled (reduce
                 (fn [result platform-config]
                   (apply-platform-compilation result platform-config 
                                               platform-configs context compiler))
                 element
                 platform-configs)]
    compiled))
```

### Complete Flow Example

**Input:**
```clojure
[:button {:text "Click" 
          :background "#667eea"
          :hx-get "/api/click"
          :hx-target ".result"}]
```

**Stack:** `[:html :css :htmx]`

**Step 1: HTML Platform**
```clojure
;; html.edn applied
- Maps :button → <button> element
- Sets up base structure
- Result: {:type :button :props {...} :children []}
```

**Step 2: CSS Platform (extends HTML)**
```clojure
;; css.edn applied (has HTML's elements via :extends)
- Extracts :background → inline style
- Result: {:type :button 
           :props {:style "background: #667eea;" ...}}
```

**Step 3: HTMX Platform (extends HTML)**
```clojure
;; htmx.edn applied (has HTML's elements via :extends)
- Extracts :hx-get → hx-get attribute
- Extracts :hx-target → hx-target attribute
- Result: {:type :button
           :props {:style "background: #667eea;"
                   :hx-get "/api/click"
                   :hx-target ".result"
                   :text "Click"}}
```

**Final Output:**
```clojure
[:button {:style "background: #667eea;"
          :hx-get "/api/click"
          :hx-target ".result"} "Click"]
```

---

## 3. Key Concepts

### Platform Extension
- **`:extends :html`** merges base platform config
- **Recursive**: Can extend extended platforms
- **Deep merge**: Nested maps merge, not replace
- **Override**: Extending config overrides base values

### Platform Stacking
- **Sequential**: Platforms applied in order
- **Cumulative**: Each platform builds on previous
- **All configs available**: Each platform sees all configs for cross-platform mappings

### Cross-Platform Mappings
```clojure
;; htmx.edn can map generic props to HTMX attributes
:component-mappings
{:state-manager {:mappings {:state :hx-vals
                           :on-change :hx-trigger}
                 :default-attrs {:hx-swap "outerHTML"}}}
```

---

## 4. Platform Independence

**Important**: Platforms should not reference other platforms directly.

✅ **Correct:**
```clojure
;; htmx.edn extends html.edn
{:platform :htmx
 :extends :html
 :compiler {:extractors {...}}}
```

❌ **Incorrect:**
```clojure
;; css.edn should not have direct Oxygen references
{:platform :css
 :compiler {:oxygen-mappings {...}}}  ; Wrong! Should be in oxygen.edn
```

---

## 5. Stack Configuration

Stack is determined at compile time:

```clojure
;; In context
{:platform-stack [:html :css :htmx]}

;; Or in project config
{:defaults {:platform-stack [:html :css :htmx]}}

;; Or in build-context options
(build-context data {:platform-stack [:html :css :react]})
```

**Default stack:**
```clojure
{:defaults {:platform-stack [:html :css :htmx]}}
```

---

## 6. Example Stacks

- **`[:html :css :htmx]`** - Web with HTMX interactivity
- **`[:html :css :react]`** - Web with React (future)
- **`[:html :css]`** - Static HTML with styling
- **`[:html]`** - Minimal HTML only

---

## 7. Benefits

✅ **Reusable base platforms** (HTML)
✅ **Specialized extensions** (CSS, HTMX)
✅ **Composable**: Mix and match platforms
✅ **Generic**: No hardcoded platform logic
✅ **Extensible**: Add new platforms without changing existing ones

---

## 8. Summary

**Platform Extension:**
- Uses `:extends` keyword
- Deep merges base platform config
- Recursive (supports chains)
- Extending config overrides/adds to base

**Platform Stacking:**
- Sequential compilation through stack
- Each platform builds on previous
- All configs available for cross-platform mappings
- Stack specified in context at compile time

This architecture allows generic components to compile to different platform combinations without code changes.

