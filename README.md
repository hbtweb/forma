# Forma - UI + Geometry Application

## Overview

Forma is the unified UI + Geometry application of the Kora framework. It compiles EDN to multiple targets including HTML/CSS/JavaScript, Flutter, SwiftUI, 3D Graphics (Three.js, WebGL), and Game Engines (Unity, Unreal, Godot).

**Architecture:**
- Built on Kora Core foundation (inheritance, tokens, compiler pipeline)
- Uses Forma hierarchy: `[:global :components :sections :templates :pages]`
- Extends `kora.core.compiler` for UI-specific compilation
- Supports multiple platforms: HTML/HTMX, Oxygen Builder, and more

## Quick Start

```clojure
;; Import Forma compiler
(require '[forma.compiler :as compiler]
         '[forma.layout :as layout])

;; Compile EDN to HTML
(compiler/compile-to-html
  [[:div {:class "card"}
    [:h1 "Hello Forma"]]])

;; Use unified layout
(layout/unified-layout
  {:title "My Page"
   :user-roles [:role/admin]
   :current-path "/"}
  [:div.card
   [:h1 "Hello World"]])
```

## Structure

```
forma/
├── src/forma/
│   ├── compiler.clj              # Forma compiler (extends kora.core.compiler)
│   ├── layout.clj                 # Unified layout system
│   ├── pages.clj                  # Dynamic page generators
│   ├── render.clj                 # Rendering functions
│   ├── navigation.clj              # Navigation builder
│   ├── admin.clj                  # Admin & DevOps UI
│   ├── pos.clj                    # POS Terminal UI
│   ├── components/                # UI Component Library (SSR)
│   │   ├── data-table.clj
│   │   ├── form-ssr.clj
│   │   ├── kanban-board.clj
│   │   └── ...
│   ├── integrations/              # Platform integrations
│   │   └── oxygen/                # Oxygen Builder compiler
│   ├── compilers/                 # Platform-specific compilers
│   │   ├── html.clj              # HTML/HTMX compiler
│   │   └── oxygen.clj            # Oxygen Builder compiler
│   ├── stubs/                     # Stub implementations for standalone dev
│   └── dev/                       # Development tools
└── resources/forma/
    ├── global/                    # Global defaults (tokens, base defaults)
    ├── components/                # Component definitions (EDN)
    ├── sections/                  # Section defaults (header, sidebar, footer)
    ├── templates/                 # Page templates
    ├── styles/                    # Styling systems (shadcn-ui, etc.)
    ├── platforms/                 # Platform configurations (HTML, Oxygen)
    └── oxygen/                     # Oxygen-specific resources
```

## Inheritance Hierarchy

Forma uses the following hierarchy levels (resolved via `kora.core.inheritance`):

1. **Global** - Base tokens, colors, spacing, typography defaults
2. **Components** - Component-specific defaults and variants
3. **Sections** - Section defaults (header, sidebar, footer)
4. **Templates** - Page-type templates (dashboard, list-page, etc.)
5. **Pages** - Runtime instances (generated with minimal overrides)

## Compilation

### HTML/HTMX Compilation

```clojure
(require '[forma.compilers.html :as html])

(html/compile-to-html
  [[:button {:variant :primary} "Click me"]]
  {:platform :html})
```

### Oxygen Builder Compilation

```clojure
(require '[forma.compilers.oxygen :as oxygen])

(oxygen/compile-to-oxygen
  [[:button {:variant :primary} "Click me"]]
  {:platform :oxygen})
```

## Development

### Prerequisites

- Java JDK 11+
- Clojure CLI tools
- Kora Core (as local dependency)

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

## Migration from Corebase

This project was migrated from `src/corebase/`. All namespaces have been updated:
- `corebase.ui.*` → `forma.*`
- `corebase.server.*` → `forma.server.*`
- `corebase.integrations.*` → `forma.integrations.*`

The old `src/corebase/` structure remains unchanged during the transition period for backward compatibility.

## Dependencies

- `kora/kora-core` - Universal foundation (inheritance, tokens, compiler)
- `hiccup/hiccup` - HTML generation
- `org.clojure/tools.logging` - Logging

## Status

✅ **Kora Core Integration**  
✅ **HTML/HTMX Compilation**  
✅ **Oxygen Builder Integration**  
✅ **Component Library**  
✅ **Inheritance System**  
✅ **Token System**

---

**Version:** 1.0  
**Updated:** 2025-05  
**Status:** ✅ MIGRATED FROM COREBASE

