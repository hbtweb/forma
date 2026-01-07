# Adding New Platform Targets

Forma's architecture makes adding new platforms straightforward through EDN-driven configuration and platform-specific compilers.

## Overview

Adding a platform involves three steps:
1. Create platform configuration (EDN)
2. Implement platform compiler (Clojure)
3. Register platform in main compiler

## Step 1: Create Platform Configuration

Create `default/platforms/yourplatform.edn`:

```clojure
{:platform-id :yourplatform

 ;; Optional: Inherit from existing platform
 :extends [:html]

 ;; Element mappings (Forma element → Platform widget)
 :elements {
   :button {:target "Button"
            :props {:onClick :onPress}}
   :div {:target "View"}
   :section {:target "Container"}
   :text-input {:target "TextField"}
   :heading {:target "Text"
             :props {:level :style}}
 }

 ;; Property extractors (Forma props → Platform props)
 :extractors {
   :design {
     :background :backgroundColor
     :padding :padding
     :margin :margin
     :color :textColor
   }
   :content {
     :text :text
     :placeholder :placeholder
   }
 }

 ;; Output format configuration
 :output {
   :format :code-string  ; or :ast, :json, :file
   :extension ".dart"    ; File extension (if applicable)
 }
}
```

### Configuration Sections Explained

**`:platform-id`** - Unique identifier for the platform

**`:extends`** - Inherit configuration from other platforms
```clojure
:extends [:html]        ; Single parent
:extends [:html :css]   ; Multiple parents (composition)
```

**`:elements`** - Map Forma elements to platform widgets
```clojure
:button {:target "ElevatedButton"
         :props {:text :child       ; Rename props
                 :onClick :onPressed}
         :wrapper :Center}          ; Optional wrapper widget
```

**`:extractors`** - Transform Forma properties to platform properties
```clojure
:extractors {
  :design {
    :background :backgroundColor
    :padding :padding
  }
}
```

## Step 2: Implement Platform Compiler

Create `src/forma/platforms/yourplatform.clj`:

```clojure
(ns forma.platforms.yourplatform
  "Compiler for YourPlatform

  Transforms Forma EDN elements into YourPlatform native code."
  (:require [forma.compiler :as compiler]
            [clojure.string :as str]))

;; ============================================================================
;; CONFIGURATION LOADING
;; ============================================================================

(defn load-platform-config []
  "Load platform configuration from default/platforms/yourplatform.edn"
  ;; Implementation depends on Forma's config system
  (compiler/load-platform-config :yourplatform))

;; ============================================================================
;; ELEMENT COMPILATION
;; ============================================================================

(defn compile-element
  "Compile a single Forma element to platform-native code"
  [element context]
  (let [element-type (:type element)
        platform-config (:platform-config context)
        target-widget (get-in platform-config [:elements element-type :target])
        props (:props element)
        children (:children element)]

    ;; Example: Generate platform code
    (str target-widget "("
         (compile-props props context) ", "
         (compile-children children context)
         ")")))

(defn compile-props
  "Transform Forma properties to platform properties"
  [props context]
  (let [extractors (get-in context [:platform-config :extractors])]
    ;; Apply property transformations
    (transform-props props extractors)))

(defn compile-children
  "Compile child elements"
  [children context]
  (str/join ", " (map #(compile-element % context) children)))

;; ============================================================================
;; OUTPUT GENERATION
;; ============================================================================

(defn to-platform-output
  "Generate final platform output (code string, AST, etc.)"
  [elements context]
  (let [compiled-elements (map #(compile-element % context) elements)
        output-format (get-in context [:platform-config :output :format])]

    (case output-format
      :code-string (str/join "\n" compiled-elements)
      :ast compiled-elements
      :json (generate-json compiled-elements)
      compiled-elements)))

;; ============================================================================
;; PUBLIC API
;; ============================================================================

(defn compile-to-yourplatform
  "Main entry point for compiling to YourPlatform

  Usage:
    (compile-to-yourplatform elements {:platform-stack [:yourplatform]})"
  [elements context]
  (let [platform-config (load-platform-config)
        enhanced-context (assoc context :platform-config platform-config)]
    (to-platform-output elements enhanced-context)))
```

### Key Functions

**`compile-element`** - Core transformation logic
- Maps Forma element types to platform widgets
- Applies property transformations
- Handles children recursively

**`compile-props`** - Property transformation
- Uses `:extractors` from config
- Renames properties (`:onClick` → `:onPressed`)
- Applies platform-specific formatting

**`to-platform-output`** - Output generation
- Determines output format (string, AST, JSON, etc.)
- Applies platform-specific formatting
- Handles multiple output modes

## Step 3: Register Platform

Add to `src/forma/compiler.clj`:

```clojure
(ns forma.compiler
  (:require [forma.platforms.yourplatform :as yourplatform]))

;; Add multimethod for platform dispatch
(defmethod compile-to-platform :yourplatform
  [elements context]
  (yourplatform/compile-to-yourplatform elements context))
```

Or create a convenience function:

```clojure
(defn compile-to-yourplatform
  "Compile Forma elements to YourPlatform

  Usage:
    (compile-to-yourplatform [[:button {:text \"Click\"}]] {})"
  [elements context]
  (compile-to-platform elements (assoc context :platform-stack [:yourplatform])))
```

## Step 4: Test Your Platform

```clojure
;; In REPL
(require '[forma.compiler :as compiler])

;; Test basic element
(compiler/compile-to-yourplatform
  [[:button {:text "Click me"}]]
  {})

;; Test with styling
(compiler/compile-to-yourplatform
  [[:button {:text "Click" :design/background "#1a202c"}]]
  {})

;; Test nested elements
(compiler/compile-to-yourplatform
  [[:section
    [:heading {:text "Title"}]
    [:button {:text "Action"}]]]
  {})
```

## Platform Examples

### React Platform

```clojure
;; Config: default/platforms/react.edn
{:platform-id :react
 :elements {
   :button {:target "button"}
   :div {:target "div"}
 }
 :extractors {
   :design {:background :style.background}
   :events {:onClick :onClick}
 }
 :output {:format :jsx-string}}

;; Compiler: src/forma/platforms/react.clj
(defn compile-to-react [elements context]
  ;; Generate JSX
  "<Button onClick={handleClick}>Click</Button>")
```

### Flutter Platform

```clojure
;; Config: default/platforms/flutter.edn
{:platform-id :flutter
 :elements {
   :button {:target "ElevatedButton"
            :props {:text :child :onClick :onPressed}}
   :section {:target "Container"}
 }
 :extractors {
   :design {:background :decoration.color}
 }
 :output {:format :dart-code :extension ".dart"}}

;; Compiler: src/forma/platforms/flutter.clj
(defn compile-to-flutter [elements context]
  ;; Generate Dart code
  "ElevatedButton(child: Text('Click'), onPressed: () {})")
```

## Best Practices

### 1. Use Platform Extension

Inherit from similar platforms to reduce duplication:

```clojure
{:platform-id :react-native
 :extends [:react]  ; Inherit React mappings
 :elements {
   ;; Override specific elements
   :button {:target "Pressable"}
 }}
```

### 2. Declarative Property Mapping

Define property transformations in config, not code:

```clojure
;; Good: Config-driven
:extractors {:design {:background :backgroundColor}}

;; Avoid: Hardcoded in compiler
(if (= prop :background) :backgroundColor ...)
```

### 3. Test Incrementally

Start with simple elements and gradually add complexity:
1. Single element (`:button`)
2. Element with props (`:button {:text "Click"}`)
3. Nested elements (`:section` with children)
4. Styling (`:design/background`)
5. Events (`:onClick`)

### 4. Document Platform Limitations

Not all Forma features may map to every platform:

```clojure
;; In platform config
:limitations {
  :no-gradients "Platform doesn't support CSS gradients"
  :no-animations "Use platform-specific animation system"
}
```

## Platform Architecture Patterns

### Pattern 1: Code Generation (React, Flutter, SwiftUI)

Generate source code as strings:

```clojure
(defn to-platform-output [elements context]
  (str/join "\n" (map #(generate-code % context) elements)))
```

### Pattern 2: AST Generation (Oxygen, JSON-based)

Generate structured data:

```clojure
(defn to-platform-output [elements context]
  {:tree {:root {:children (map #(generate-node % context) elements)}}})
```

### Pattern 3: Hybrid (HTML)

Generate both code and metadata:

```clojure
(defn to-platform-output [elements context]
  {:html (generate-html elements)
   :css (extract-css elements)
   :metadata {:classes [...] :tokens [...]}})
```

## Resources

- **Example Platform:** See `src/forma/platforms/html.clj` for production reference
- **Platform Config:** See `default/platforms/html.edn` for comprehensive config
- **Testing:** See `test/forma/` for platform testing patterns
- **Architecture:** See [docs/PLATFORM-EXTENSION-STACKING.md](../PLATFORM-EXTENSION-STACKING.md)

## Getting Help

If you're adding a platform and need assistance:
- Check existing platforms for patterns
- Review [ARCHITECTURE.md](../ARCHITECTURE.md) for core concepts
- Ask in [Community Discord/Slack] (coming soon)
- Open a GitHub Discussion for design questions
