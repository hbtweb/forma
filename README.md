# Forma - Multi-Platform UI Framework

**Write once in EDN, compile to native widgets everywhere.**

Forma is a multi-platform UI framework that compiles declarative EDN (Extensible Data Notation) into native UI components across different platforms. Think of it as a meta-framework similar to **Astro** (imports from different platforms) with the scope of **Flutter** (comprehensive UI toolkit) and the approach of **IUP.next** (compiles to native platform widgets).

## Why Forma?

**The Problem:** Building UI for multiple platforms typically means:
- Learning different languages (HTML/CSS, Dart, Swift, C#, etc.)
- Maintaining separate codebases
- Dealing with platform-specific quirks
- Choosing between "write once, run anywhere" (web wrappers) or native performance

**The Forma Solution:**
- ✅ **Write once** in EDN (platform-agnostic data structures)
- ✅ **Compile to native** platform code (HTML, JSX, Dart, Swift, etc.)
- ✅ **Native performance** - not web views or wrappers
- ✅ **Idiomatic output** - each platform gets its native widgets
- ✅ **Inheritance-based design system** - consistent styling across platforms
- ✅ **Extensible** - add new platforms via EDN config + compiler

## Platform Philosophy

Forma is **not** a web framework that "also works" on mobile. It's a true multi-platform framework where each target receives **platform-native code**:

- **HTML/HTMX** → Semantic HTML + HTMX attributes
- **React** → JSX with React hooks
- **Oxygen Builder** → WordPress page builder JSON
- **Flutter** → Dart widgets (experimental)
- **SwiftUI** → Swift view protocols (experimental)
- **Game Engines** → Unity/Unreal/Godot components (experimental)

The key difference: Forma compiles to **native platform constructs**, not universal web components. A `:button` becomes:
- `<button>` in HTML
- `<Button>` in React
- `Button()` in SwiftUI
- `ElevatedButton()` in Flutter

Each platform gets idiomatic, performant code that leverages platform-specific features.

## Platform Status

| Platform | Status | Maturity | Compiler | Notes |
|----------|--------|----------|----------|-------|
| **HTML/CSS/HTMX** | ✅ Production | 100% | ✅ Complete | Fully functional, well-tested |
| **Oxygen Builder** | ✅ Production | 100% | ✅ Complete | WordPress page builder integration |
| **React/JSX** | 🔧 Experimental | 40% | 🚧 In Progress | Config complete, compiler in development |
| **Flutter** | 🔬 Planned | 0% | 📋 Not Started | Architecture planned |
| **SwiftUI** | 🔬 Planned | 0% | 📋 Not Started | Architecture planned |
| **Three.js/WebGL** | 🔬 Planned | 0% | 📋 Not Started | 3D graphics target |
| **Unity/Unreal/Godot** | 🔬 Planned | 0% | 📋 Not Started | Game engine targets |

**Current Focus:** HTML/HTMX and Oxygen Builder are production-ready and serve as reference implementations while other platforms are being developed.

## Quick Start

### Prerequisites

- Java JDK 11+
- Clojure CLI tools
- Kora Core (as local dependency at `../kora/core`)

### Hello World (HTML)

```clojure
;; Start REPL
cd forma
clojure -M:repl

;; Compile to HTML
(require '[forma.compiler :as compiler])

(compiler/compile-to-html
  [[:section {:design/background "#1a202c" :design/padding "60px 20px"}
    [:heading {:text "Hello Forma" :level 1}]
    [:button {:text "Get Started" :variant :primary}]]]
  {:platform-stack [:html :css]})

;; Output:
;; <section style="background: #1a202c; padding: 60px 20px">
;;   <h1>Hello Forma</h1>
;;   <button class="btn btn-primary">Get Started</button>
;; </section>
```

### Hello World (WordPress/Oxygen)

```clojure
;; Set WordPress credentials
(System/setProperty "WORDPRESS_URL" "http://your-site.test")
(System/setProperty "WORDPRESS_USER" "admin")
(System/setProperty "WORDPRESS_APP_PASSWORD" "your-app-password")

;; Deploy to WordPress
(load-file "deploy_correct_structure.clj")
(deploy-hero-section)

;; Returns:
;; {:success true
;;  :url "http://your-site.test/homepage-hero/"
;;  :edit_url "..."}
```

### Multi-Platform Compilation (Same Code)

```clojure
(def my-ui [[:button {:text "Click me" :variant :primary}]])

;; Compile to HTML
(compiler/compile-to-html my-ui {:platform-stack [:html]})
;; → "<button class=\"btn btn-primary\">Click me</button>"

;; Compile to Oxygen (WordPress)
(compiler/compile-to-oxygen my-ui {:platform :oxygen})
;; → {tree: {root: {...}, _nextNodeId: 150}}

;; Compile to React (when implemented)
(compiler/compile-to-react my-ui {:platform-stack [:react]})
;; → "<Button variant=\"primary\">Click me</Button>"
```

## Architecture

### Forma uses a universal compilation pipeline:

```
EDN Input (Platform-agnostic)
    ↓
Parse → Expand → Resolve Inheritance → Apply Styling
    ↓
Platform Detection (:platform-stack)
    ↓
    ├─→ HTML Compiler → Semantic HTML + CSS
    ├─→ React Compiler → JSX + React hooks
    ├─→ Oxygen Compiler → WordPress JSON tree
    ├─→ Flutter Compiler → Dart widgets (future)
    └─→ SwiftUI Compiler → Swift views (future)
    ↓
Platform-Native Output
```

### Inheritance Hierarchy

Forma uses the following hierarchy levels (resolved via `kora.core.inheritance`):

1. **Global** - Base tokens, colors, spacing, typography defaults
2. **Components** - Component-specific defaults and variants
3. **Sections** - Section defaults (header, sidebar, footer)
4. **Templates** - Page-type templates (dashboard, list-page, etc.)
5. **Pages** - Runtime instances (generated with minimal overrides)

This enables:
- Design system consistency across platforms
- Component reusability
- Project-specific overrides without forking
- Drag-and-drop library sharing

### Three-Tier Resource Resolution

```
forma/
├── default/      # Lowest priority - framework defaults
├── library/      # Medium priority - shared components
└── projects/     # Highest priority - project overrides
```

**Resolution order:** `project → library → default`

This allows you to:
- Use Forma's default component library
- Add shared components to `library/` (drag-and-drop from other projects)
- Override anything in your `projects/myproject/` folder

## Structure

```
forma/
├── src/forma/
│   ├── compiler.clj              # Forma compiler (extends kora.core.compiler)
│   ├── platforms/                # Platform-specific compilers
│   │   ├── html.clj              # HTML/HTMX compiler
│   │   ├── css.clj               # CSS generation
│   │   └── react.clj             # React/JSX compiler (in progress)
│   ├── integrations/             # Platform integrations
│   │   └── oxygen/               # Oxygen Builder (WordPress) compiler
│   ├── components/               # UI Component Library (SSR)
│   ├── styling/                  # Styling systems
│   ├── hierarchy/                # Inheritance resolution
│   ├── tokens/                   # Design token system
│   └── build/                    # Build pipeline
│
├── default/                      # Framework defaults (platform-agnostic)
│   ├── components/               # Component definitions (56 components)
│   ├── sections/                 # Section defaults
│   ├── templates/                # Page templates
│   ├── global/                   # Global defaults, tokens
│   ├── styles/                   # Styling systems (shadcn-ui, etc.)
│   └── platforms/                # Platform configurations (EDN)
│       ├── html.edn              # HTML element mappings
│       ├── css.edn               # CSS property mappings
│       ├── htmx.edn              # HTMX attribute mappings
│       ├── react.edn             # React/JSX mappings
│       └── oxygen.edn            # Oxygen element mappings
│
├── library/                      # Shared component library
└── projects/                     # Project-specific overrides
```

## Adding New Platform Targets

Forma's architecture makes adding new platforms straightforward:

### 1. Create Platform Configuration (EDN)

Create `default/platforms/yourplatform.edn`:

```clojure
{:platform-id :yourplatform
 :extends [:html]  ; Optional: inherit from existing platform
 :elements {
   :button {:target "Button" :props {:onClick :onPress}}
   :div {:target "View"}
   :text-input {:target "TextField"}
 }
 :extractors {
   :design {:background :backgroundColor
            :padding :padding}
 }}
```

### 2. Implement Platform Compiler

Create `src/forma/platforms/yourplatform.clj`:

```clojure
(ns forma.platforms.yourplatform
  (:require [forma.compiler :as compiler]))

(defn compile-element [element context]
  ;; Transform Forma element to platform-native code
  (let [element-type (:type element)
        target-widget (get-in context [:platform-config :elements element-type :target])]
    ;; Generate platform-specific code
    ))

(defn to-platform-output [elements context]
  ;; Generate final output (code string, AST, etc.)
  (map #(compile-element % context) elements))
```

### 3. Register Platform

Add to `forma.compiler`:

```clojure
(defmethod compile-to-platform :yourplatform [elements context]
  (yourplatform/to-platform-output elements context))
```

### 4. Test

```clojure
(compiler/compile-to-platform
  [[:button {:text "Click"}]]
  {:platform-stack [:yourplatform]})
```

**Key Principles:**
- **EDN-driven** - Element mappings in config, not hardcoded
- **Platform extension** - Inherit from existing platforms (`:extends`)
- **Declarative transformations** - Property extractors in EDN
- **Convention over configuration** - Sensible defaults

## Examples

### Component Library

Forma includes 56 pre-built components from shadcn-ui:

```clojure
;; Buttons
[:button {:variant :primary} "Primary"]
[:button {:variant :secondary} "Secondary"]
[:button {:variant :destructive} "Delete"]

;; Cards
[:card
 [:card-header [:card-title "Title"]]
 [:card-content "Content here"]
 [:card-footer [:button "Action"]]]

;; Forms
[:form
 [:text-input {:label "Name" :placeholder "Enter name"}]
 [:textarea {:label "Description"}]
 [:button {:type :submit} "Submit"]]

;; Data Display
[:data-table {:data users :columns [:name :email :role]}]
[:kanban-board {:columns [:todo :in-progress :done]}]
[:timeline {:events timeline-data}]
```

All components work across all platforms - the compiler generates platform-specific code.

### Responsive Design

```clojure
[:section {:design/padding {:base "20px" :md "40px" :lg "60px"}
           :design/background "#1a202c"}
 [:heading {:text "Responsive Section"}]]

;; Compiles to platform-specific responsive code:
;; - HTML: media queries
;; - React: styled-components with breakpoints
;; - Flutter: responsive layout builders
```

### Design Tokens

```clojure
;; Define tokens (global/)
{:colors {:primary "#1a202c"
          :secondary "#E2D8FF"}
 :spacing {:sm "8px" :md "16px" :lg "32px"}
 :typography {:heading-font "gfont-abeezee"}}

;; Use tokens (platform-agnostic)
[:section {:design/background "var(--colors-primary)"
           :design/padding "var(--spacing-lg)"}]
```

## Development

### Running the Dev Server

```powershell
# Start dev server
clojure -M -m forma.dev.server

# Or start REPL
cd forma
clojure -M:repl
# Then in REPL:
(require '[forma.dev.server :as server])
(server/start-server server/demo-routes {:port 3000})
```

### Running Tests

```powershell
# Run test suite
clojure -M -m forma.run-tests

# Or in REPL
(load-file "run_tests.clj")
```

### Platform-Specific Testing

```clojure
;; Test HTML compilation
(require '[forma.platforms.html :as html])
(html/to-html-string [[:button "Click"]])

;; Test Oxygen deployment
(load-file "test_oxygen_connection.clj")
(in-ns 'test-oxygen-connection)
(test-connection)
```

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

## Dependencies

- `kora/kora-core` - Universal foundation (inheritance, tokens, compiler)
- `hiccup/hiccup` - HTML generation
- `cheshire/cheshire` - JSON handling
- `clj-http/clj-http` - HTTP client (for WordPress integration)
- `org.clojure/tools.logging` - Logging

## Comparison to Other Frameworks

| Framework | Approach | Output | Language | Multi-Platform |
|-----------|----------|--------|----------|----------------|
| **Forma** | Compile to native | Platform-native code | EDN (Clojure) | ✅ HTML, React, Flutter, etc. |
| **React Native** | JavaScript runtime | JavaScript bundle | JSX | 🟡 Mobile-focused |
| **Flutter** | Dart runtime | Dart compiled | Dart | ✅ Mobile, Web, Desktop |
| **SwiftUI** | Native iOS/macOS | Swift compiled | Swift | ❌ Apple only |
| **Astro** | Meta-framework | Multi-framework HTML | JSX/Astro | 🟡 Web-only |
| **IUP.next** | Compile to native widgets | Platform widgets | Lua | ✅ Multi-platform GUI |

**Forma's Unique Value:**
- **EDN-first** - Data-driven, no proprietary syntax
- **True multi-platform** - Not just mobile, includes web, desktop, game engines
- **Compile to native** - Not runtime wrappers (like React Native)
- **Extensible** - Add platforms via EDN config
- **Inheritance-based design system** - Consistent styling across platforms

## Status

✅ **Kora Core Integration**
✅ **HTML/HTMX Compilation** (Production)
✅ **Oxygen Builder Integration** (Production)
✅ **Component Library** (56 components)
✅ **Inheritance System**
✅ **Token System**
🔧 **React Platform** (40% - Config complete, compiler in progress)
📋 **Flutter Platform** (Planned)
📋 **SwiftUI Platform** (Planned)

## Roadmap

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

## Documentation

- **[CLAUDE.md](CLAUDE.md)** - Comprehensive developer guide for working with Forma
- **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** - Three-layer architecture details
- **[docs/PLATFORM-EXTENSION-STACKING.md](docs/PLATFORM-EXTENSION-STACKING.md)** - Platform composition system
- **[OXYGEN_ARCHITECTURE.md](OXYGEN_ARCHITECTURE.md)** - Oxygen/WordPress integration details
- **[QUICK_START.md](QUICK_START.md)** - Quick start guide for WordPress deployment

## Contributing

Forma is in active development. Key areas for contribution:
- **Platform compilers** - Implement React, Flutter, SwiftUI compilers
- **Component library** - Add more components to `default/components/`
- **Documentation** - Platform guides, tutorials, examples
- **Testing** - Cross-platform compilation tests
- **Examples** - Multi-platform demo projects

## License

[To be determined]

---

**Version:** 1.0
**Updated:** 2025-01
**Status:** ✅ Production (HTML/Oxygen), 🔧 Experimental (React), 📋 Planned (Flutter, SwiftUI, Game Engines)

**Built with ❤️ using Clojure and Kora Core**
