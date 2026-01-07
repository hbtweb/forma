# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

**Forma** is a multi-platform UI framework that compiles declarative EDN (Extensible Data Notation) into native UI components across different platforms. Think of it as a meta-framework similar to Astro (imports from different platforms) with the scope of Flutter (comprehensive UI toolkit) and the approach of IUP.next (compiles to native platform widgets).

**Write once in EDN, compile to native widgets everywhere.**

### Platform Philosophy

Forma is **not** a web framework that "also works" on mobile. It's a true multi-platform framework where each target receives platform-native code:
- **HTML/HTMX** → Semantic HTML + HTMX attributes
- **React** → JSX with React hooks
- **Oxygen Builder** → WordPress page builder JSON
- **Flutter** → Dart widgets (experimental)
- **SwiftUI** → Swift view protocols (experimental)
- **Game Engines** → Unity/Unreal/Godot components (experimental)

The key difference: Forma compiles to **native platform constructs**, not universal web components. Each platform gets idiomatic, performant code that leverages platform-specific features.

### Platform Status

| Platform | Status | Maturity | Notes |
|----------|--------|----------|-------|
| **HTML/CSS/HTMX** | Production | 100% | Fully functional, well-tested |
| **Oxygen Builder** | Production | 100% | WordPress page builder integration |
| **React/JSX** | Experimental | 40% | Config complete, compiler in progress |
| **Flutter** | Experimental | 0% | Architecture planned |
| **SwiftUI** | Experimental | 0% | Architecture planned |
| **Three.js/WebGL** | Experimental | 0% | 3D graphics target |
| **Unity/Unreal/Godot** | Experimental | 0% | Game engine targets |

**Current Focus:** HTML/HTMX and Oxygen Builder are production-ready. These serve as reference implementations while other platforms are being developed.

**Oxygen/WordPress Context:** This platform receives heavy development attention because it's one of the first production targets needed for the Lume web framework (which uses Forma for UI generation and platform interop). It will be one of many equally-supported production targets.

### Key Architectural Concepts

**1. EDN-First Design**
Everything starts as EDN data structures. No JSX, no template languages, no proprietary DSLs.

**2. Inheritance-Based Styling**
Properties inherit through a hierarchy: `[:global :components :sections :templates :pages]`. Design tokens and styling resolve at compile time using the Kora Core foundation.

**3. Platform Compilation**
Each platform has a dedicated compiler that understands how to transform generic Forma elements into platform-native code:
- HTML compiler → `<button class="...">`
- React compiler → `<Button onClick={...} />`
- Oxygen compiler → `{type: "EssentialElements\\Button", properties: {...}}`

**4. Native Widget Mapping**
Unlike web-first frameworks that wrap everything in divs, Forma maps to actual platform widgets:
- `:button` → `<button>` (HTML), `Button()` (SwiftUI), `ElevatedButton()` (Flutter)
- `:text-input` → `<input>` (HTML), `TextField` (SwiftUI), `TextField()` (Flutter)

## Technology Stack

- **Language:** Clojure 1.11.1
- **Core Dependency:** kora-core (local dependency at `../kora/core`)
- **Platform:** JDK 11+
- **Key Libraries:** hiccup (HTML generation), cheshire (JSON), clj-http (HTTP client)
- **Integration Layer:** Platform-specific compilers for each target

## Development Commands

### Starting a REPL

```powershell
# From repository root
clojure -M:repl
```

### Running Tests

```powershell
# Run main test suite
clojure -M -m forma.run-tests

# Load and run specific test file
clojure -M
# Then in REPL:
(load-file "run_tests.clj")
```

### Testing Individual Components

```clojure
;; Test compiler
(require '[forma.compiler :as compiler])

;; Compile to HTML
(compiler/compile-to-html [[:button {:text "Click"}]] {:platform-stack [:html]})

;; Compile to React (when implemented)
(compiler/compile-to-react [[:button {:text "Click"}]] {:platform-stack [:react]})
```

### Platform-Specific Testing

**HTML/HTMX Platform:**
```clojure
(require '[forma.platforms.html :as html])
(html/to-html-string [[:div {:class "container"} [:button "Click"]]])
```

**Oxygen Builder Platform:**
```clojure
;; Prerequisites: Set WordPress credentials
(System/setProperty "WORDPRESS_URL" "http://your-site.test")
(System/setProperty "WORDPRESS_USER" "admin")
(System/setProperty "WORDPRESS_APP_PASSWORD" "your-app-password")

;; Test connection
(load-file "test_oxygen_connection.clj")
(in-ns 'test-oxygen-connection)
(test-connection)

;; Deploy a page
(load-file "src/forma/oxygen_deploy_demo.clj")
(in-ns 'forma.oxygen-deploy-demo)
(deploy-hero-section)
```

## Architecture Overview

### Multi-Platform Compilation Pipeline

```
EDN Input (Platform-agnostic)
    ↓
Parse (forma.compiler/parse-element)
    ↓
Expand (property shortcuts like :text → {:content {:text ...}})
    ↓
Resolve (inheritance hierarchy + design tokens)
    ↓
Apply Styling (design system: shadcn-ui, etc.)
    ↓
Platform Detection (:platform-stack in context)
    ↓
    ├─→ HTML Compiler → Semantic HTML + CSS
    ├─→ React Compiler → JSX + React hooks
    ├─→ Oxygen Compiler → WordPress JSON tree
    ├─→ Flutter Compiler → Dart widgets (future)
    └─→ SwiftUI Compiler → Swift views (future)
    ↓
Platform-Native Output
```

### Directory Structure

- **`src/forma/`** - Main source code
  - `compiler.clj` - Core compiler extending kora.core.compiler
  - `platforms/` - **Platform-specific compilers**
    - `html.clj` - HTML/HTMX compiler
    - `css.clj` - CSS generation
    - `react.clj` - React/JSX compiler (in progress)
    - *(future: flutter.clj, swiftui.clj)*
  - `integrations/` - **Platform integrations**
    - `oxygen/` - Oxygen Builder (WordPress) integration
      - `compiler.clj` - Forma EDN → Oxygen JSON tree
      - `reverse-compiler.clj` - Oxygen JSON → Forma EDN
      - `elements.clj` - Element type mappings
  - `components/` - UI component implementations
  - `styling/` - Styling system (design tokens, CSS generation)
  - `cache/` - Compiler caching and incremental compilation
  - `hierarchy/` - Inheritance resolution
  - `tokens/` - Design token system
  - `build/` - Build pipeline and asset management
  - `dev/` - Development server and tooling

- **`default/`** - Default resources (platform-agnostic)
  - `components/` - Component EDN definitions (56 components)
  - `sections/` - Section defaults (header, sidebar, footer)
  - `templates/` - Page templates
  - `global/` - Global defaults, tokens, base styles
  - `styles/` - Styling systems (shadcn-ui, etc.)
  - `platforms/` - **Platform configurations** (EDN-driven)
    - `html.edn` - HTML element mappings, extractors
    - `css.edn` - CSS property mappings
    - `htmx.edn` - HTMX attribute mappings
    - `react.edn` - React/JSX mappings (config complete)
    - `oxygen.edn` - Oxygen element mappings

- **`library/`** - Shared component library (medium priority)
- **`projects/`** - Project-specific overrides (highest priority)

- **`test/forma/`** - Test files
- **`docs/`** - Framework documentation
- **Root `.clj` files** - Deployment scripts and ad-hoc tests

### Resolution Hierarchy

**Priority order:** `:project` → `:library` → `:default`

**Hierarchy levels:** `[:global :components :sections :templates :pages]`

Properties are resolved by merging from lowest to highest specificity:
1. Global defaults
2. Component defaults
3. Section defaults
4. Template defaults
5. Page-specific overrides

This enables:
- Design system consistency across platforms
- Component reusability
- Project-specific overrides without forking
- Drag-and-drop library sharing

### Adding New Platform Targets

Forma's architecture makes adding new platforms straightforward:

**1. Create Platform Configuration** (`default/platforms/yourplatform.edn`)
```clojure
{:platform-id :yourplatform
 :extends [:html]  ; Optional: inherit from existing platform
 :elements {
   :button {:target "Button" :props {:onClick :onPress}}
   :div {:target "View"}
   ; ... element mappings
 }
 :extractors {
   :design {:background :backgroundColor}
   ; ... property transformations
 }}
```

**2. Implement Platform Compiler** (`src/forma/platforms/yourplatform.clj`)
```clojure
(ns forma.platforms.yourplatform
  (:require [forma.compiler :as compiler]))

(defn compile-element [element context]
  ;; Transform Forma element to platform-native code
  )

(defn to-platform-output [elements context]
  ;; Generate final output (code string, AST, etc.)
  )
```

**3. Register Platform** (Add to compiler dispatch)
```clojure
;; In forma.compiler
(defmethod compile-to-platform :yourplatform [elements context]
  (yourplatform/to-platform-output elements context))
```

**4. Test Platform**
```clojure
(compiler/compile-to-platform
  [[:button {:text "Click"}]]
  {:platform-stack [:yourplatform]})
```

**Key Architecture Principles:**
- **EDN-driven configuration** - Element mappings in EDN, not code
- **Platform extension** - New platforms can extend existing ones (`:extends`)
- **Property extraction** - Declarative transformation of Forma props to platform props
- **Convention over configuration** - Sensible defaults reduce boilerplate

## Platform-Specific Documentation

### HTML/HTMX Platform

**Status:** Production-ready, fully tested

**Output:** Semantic HTML5 + CSS + HTMX attributes

**Example:**
```clojure
(require '[forma.compiler :as compiler])

(compiler/compile-to-html
  [[:section {:design/background "#1a202c"}
    [:heading {:text "Hello World" :level 1}]
    [:button {:text "Click" :variant :primary}]]]
  {:platform-stack [:html :css :htmx]})

;; Output:
;; <section style="background: #1a202c">
;;   <h1>Hello World</h1>
;;   <button class="btn btn-primary">Click</button>
;; </section>
```

**Reference:** See [docs/PLATFORM-EXTENSION-STACKING.md](docs/PLATFORM-EXTENSION-STACKING.md)

### Oxygen Builder Platform (WordPress)

**Status:** Production-ready, active deployments

**Output:** Oxygen/Breakdance JSON tree structure

Forma compiles to **Oxygen Builder** (actually Breakdance 6.0 running in Oxygen compatibility mode) for WordPress deployment.

**Key Architecture Points:**

1. **Tree Structure:** Oxygen pages are trees where each node has:
   - `id` (unique per page/template, not global)
   - `data.type` (PHP class like `"EssentialElements\\Button"`)
   - `data.properties` (content, design, settings, meta)
   - `children` (nested elements)

2. **Element Types:**
   - `EssentialElements\*` - Modern Breakdance elements (Button, Section, Heading, MenuBuilder, etc.)
   - `OxygenElements\*` - Legacy Oxygen elements (HtmlCode, CssCode, Component, etc.)

3. **Content Types:**
   - `page` - Individual pages
   - `oxygen_template` - Reusable page layouts
   - `oxygen_header` - Site-wide headers
   - `oxygen_footer` - Site-wide footers
   - `oxygen_block` - Global reusable components

4. **Critical Fields:**
   - `_nextNodeId` - Required for Oxygen builder to work
   - `status: "exported"` - Marks tree as complete
   - Properties nested as: `{content: {}, design: {}, settings: {}, meta: {}}`

**Example:**
```clojure
(require '[forma.integrations.oxygen.compiler :as oxygen])

(oxygen/compile-to-oxygen
  [{:type :section
    :design/background "#1a202c"
    :children [{:type :heading :text "Hello"}]}]
  {:platform :oxygen})

;; Output: Oxygen JSON tree ready for WordPress REST API
```

**Reference Documents:**
- [OXYGEN_ARCHITECTURE.md](OXYGEN_ARCHITECTURE.md) - Complete Oxygen/Breakdance architecture
- [WORDPRESS_INTEGRATION.md](WORDPRESS_INTEGRATION.md) - REST API details
- [FORMA_OXYGEN_COMPILER_ROADMAP.md](FORMA_OXYGEN_COMPILER_ROADMAP.md) - Compiler development plan

### React Platform (Experimental)

**Status:** 40% complete (config exists, compiler in progress)

**Output:** JSX with React hooks

**Configuration:** Platform config is complete (`default/platforms/react.edn`) with:
- Element mappings (HTML5 + semantic elements)
- Event handler transformations (onClick, onChange, etc.)
- Component mappings (useState, useEffect, etc.)
- JSX property transformations

**Next Steps:** Implement `src/forma/platforms/react.clj` compiler

### Flutter Platform (Experimental)

**Status:** Planned, 0% complete

**Output:** Dart code with Flutter widgets

**Approach:** Map Forma elements to Flutter widgets:
- `:button` → `ElevatedButton()`, `TextButton()`, `OutlinedButton()`
- `:section` → `Container()` or `Column()`
- `:text-input` → `TextField()`

### SwiftUI Platform (Experimental)

**Status:** Planned, 0% complete

**Output:** Swift code with SwiftUI view protocols

**Approach:** Map Forma elements to SwiftUI views:
- `:button` → `Button("Title") { action }`
- `:section` → `VStack { ... }` or `HStack { ... }`
- `:text-input` → `TextField("Placeholder", text: $binding)`

## Configuration

Main config file: **`config.edn`** (root level)

Key configuration sections:
- `:paths` - Resource paths (default, library, projects)
- `:resolution-order` - Priority order for resource resolution
- `:defaults` - Default platform stack and styling system
- `:folders` - Folder structure definitions
- `:features` - Feature flags (library-enabled, pre-resolution, platform-discovery, etc.)
- `:cache` - Caching settings
- `:styling` - Styling behavior (dedupe-classes, apply-base-when-explicit, etc.)
- `:minification` - Minification settings per platform
- `:platforms` - Platform registry (experimental)

## Common Workflows

### 1. Creating a Platform-Agnostic Component

Add component definition to `default/components/my-component.edn`:

```clojure
{:type :section
 :design/background "#1a202c"
 :design/padding "60px 20px"
 :children [{:type :heading
             :content/text "My Component"
             :content/tag "h2"}]}
```

This component works across all platforms - the compiler handles platform-specific output.

### 2. Compiling to Multiple Platforms

```clojure
(require '[forma.compiler :as compiler])

(def my-ui [[:button {:text "Click me" :variant :primary}]])

;; Compile to HTML
(compiler/compile-to-html my-ui {:platform-stack [:html :css]})
;; → "<button class=\"btn btn-primary\">Click me</button>"

;; Compile to Oxygen (WordPress)
(compiler/compile-to-oxygen my-ui {:platform :oxygen})
;; → {tree: {root: {...}}}

;; Compile to React (when implemented)
(compiler/compile-to-react my-ui {:platform-stack [:react]})
;; → "<Button variant=\"primary\">Click me</Button>"
```

### 3. Platform-Specific Deployment

**HTML/HTMX:**
```clojure
(require '[forma.compiler :as compiler])
(compiler/compile-to-html my-ui {:platform-stack [:html :css :htmx]})
;; → Save to .html file or serve via web server
```

**WordPress/Oxygen:**
```clojure
;; Set credentials
(System/setProperty "WORDPRESS_URL" "http://your-site.test")
(System/setProperty "WORDPRESS_USER" "admin")
(System/setProperty "WORDPRESS_APP_PASSWORD" "your-app-password")

;; Deploy
(load-file "deploy_correct_structure.clj")
(def my-tree (compile-to-oxygen-tree my-ui))
(deploy-to-oxygen my-tree {:title "My Page" :status "publish"})
```

### 4. Reverse Compilation (Import from Platform)

Forma supports bidirectional compilation for some platforms:

```clojure
(require '[forma.integrations.oxygen.reverse-compiler :as reverse])

;; Fetch page from WordPress
(def oxygen-page (fetch-page-from-wordpress 46))

;; Convert to Forma EDN
(reverse/oxygen->forma (:tree oxygen-page))
;; Returns Forma EDN structure

;; Now you can compile to other platforms!
(compiler/compile-to-html (reverse/oxygen->forma oxygen-page) {})
```

## Important Conventions

### Element Naming

- **Forma:** Use kebab-case keywords (`:button`, `:text-input`, `:woo-product-price`)
- **Platform Output:** Compiler handles platform conventions
  - HTML: `<button>`, `<input type="text">`
  - React: `<Button>`, `<TextInput>`
  - Oxygen: `"EssentialElements\\Button"`, `"EssentialElements\\WooProductPrice"`

### Property Shortcuts

Forma supports shortcuts that expand during compilation:

```clojure
;; Shortcut
{:text "Hello"}

;; Expands to
{:content {:content {:text "Hello"}}}
```

### Design Tokens

Use design tokens with `design/` prefix (platform-agnostic):

```clojure
{:design/background "#1a202c"
 :design/padding "60px 20px"
 :design/color "var(--colors-primary)"}
```

The compiler transforms these to platform-specific equivalents:
- HTML → `style="background: #1a202c; padding: 60px 20px;"`
- React → `style={{background: "#1a202c", padding: "60px 20px"}}`
- Flutter → `BoxDecoration(color: Color(0xFF1a202c))`

### Testing Changes

When modifying the compiler or adding platforms:

1. Run `run_tests.clj` to verify core functionality
2. Test with platform-specific compilation
3. Verify output works in target environment (browser, WordPress, etc.)
4. Add platform-specific tests if adding a new platform

## Key Files to Understand

**Core Compiler:**
- [src/forma/compiler.clj](src/forma/compiler.clj) - Main compiler, extends kora.core
- [src/forma/styling/core.clj](src/forma/styling/core.clj) - Styling system and design tokens
- [src/forma/hierarchy/](src/forma/hierarchy/) - Inheritance resolution
- [config.edn](config.edn) - Main configuration file

**Platform Implementations:**
- [src/forma/platforms/html.clj](src/forma/platforms/html.clj) - HTML compiler
- [src/forma/integrations/oxygen/compiler.clj](src/forma/integrations/oxygen/compiler.clj) - Oxygen compiler
- [default/platforms/html.edn](default/platforms/html.edn) - HTML platform config
- [default/platforms/react.edn](default/platforms/react.edn) - React platform config

**Documentation:**
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) - Three-layer architecture
- [docs/PLATFORM-EXTENSION-STACKING.md](docs/PLATFORM-EXTENSION-STACKING.md) - Platform composition
- [OXYGEN_ARCHITECTURE.md](OXYGEN_ARCHITECTURE.md) - Oxygen/WordPress integration

## Debugging Tips

### Enable Logging

Forma uses `clojure.tools.logging`. Check console output for warnings about:
- Missing config files
- Resource resolution failures
- Compilation errors
- Platform detection issues

### Common Issues

**"Could not load config.edn"** - Config loads from: `config.edn` (root) → `forma/config.edn` → resources. Check file exists.

**Platform compilation fails** - Verify:
1. Platform is in `:platform-stack` context (e.g., `{:platform-stack [:html :css]}`)
2. Platform config exists in `default/platforms/`
3. Platform compiler is implemented in `src/forma/platforms/`

**Oxygen deployment fails** - Verify:
1. WordPress credentials are set correctly
2. REST API endpoint format: `http://site.test/index.php?rest_route=/oxygen/v1/save`
3. `_nextNodeId` and `status: "exported"` are present in tree

**Element doesn't render correctly** - Check:
- Element mapping exists in platform config
- Property extractors are correct for target platform
- Platform-specific naming conventions are followed

## Development Philosophy

1. **EDN-First, Platform-Agnostic** - Write UI once, compile everywhere
2. **Native Output** - Generate idiomatic, performant code for each platform
3. **Inheritance Over Repetition** - Use hierarchy levels to avoid duplicating properties
4. **Convention Over Configuration** - Sensible defaults, extensible when needed
5. **Incremental Compilation** - Leverage caching for faster rebuilds
6. **Platform Parity** - Strive for feature parity across platforms where possible

## Relationship to Other Frameworks

**Lume Web Framework:** Lume uses Forma as its UI framework layer, leveraging Forma for:
- UI component generation
- Multi-platform compilation
- Platform interop (SSR, SPA, WordPress, etc.)

**Kora Framework:** Forma is built on Kora Core, which provides:
- Inheritance system
- Token resolution
- Compiler pipeline foundation

Forma extends Kora with UI-specific compilation and platform integrations.

## Migration Notes

This project was migrated from `src/corebase/` to `src/forma/`. All namespaces have been updated:
- `corebase.ui.*` → `forma.*`
- `corebase.server.*` → `forma.server.*`
- `corebase.integrations.*` → `forma.integrations.*`

Legacy `src/corebase/` remains for backward compatibility during transition.

## Future Roadmap

**Near Term (Next 3 months):**
- Complete React platform compiler
- Add Flutter platform support (experimental)
- Create multi-platform example projects
- Develop CLI tooling (`forma init`, `forma build`, etc.)

**Medium Term (6 months):**
- SwiftUI platform support
- Three.js/WebGL for 3D graphics
- Enhanced component library
- Performance optimization and benchmarks

**Long Term (12+ months):**
- Unity/Unreal/Godot game engine targets
- Visual builder/designer tool
- Community component marketplace
- Framework ecosystem growth

For detailed implementation status, see platform-specific documentation in [docs/](docs/).
