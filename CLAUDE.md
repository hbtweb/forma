# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

**Forma** is a UI + Geometry compiler that transforms EDN (Extensible Data Notation) into multiple output formats. It's part of the Kora framework and compiles to HTML/CSS/JavaScript, Oxygen Builder (WordPress), Flutter, SwiftUI, 3D Graphics (Three.js, WebGL), and Game Engines.

**Key Concept:** Forma uses an inheritance-based design system where elements inherit properties through a hierarchy: `[:global :components :sections :templates :pages]`. Design tokens and styling are resolved at compile time using the Kora Core foundation.

## Technology Stack

- **Language:** Clojure 1.11.1
- **Core Dependency:** kora-core (local dependency at `../kora/core`)
- **Platform:** JDK 11+
- **Key Libraries:** hiccup (HTML generation), cheshire (JSON), clj-http (HTTP client)
- **Primary Integration:** Oxygen Builder / Breakdance 6.0 (WordPress page builder)

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
(compiler/compile-to-html [[:button {:text "Click"}]] {})

;; Test Oxygen integration
(load-file "test_oxygen_connection.clj")
(in-ns 'test-oxygen-connection)
(test-connection)
```

### Working with WordPress/Oxygen Integration

**Prerequisites:** Set WordPress credentials as system properties:

```clojure
(System/setProperty "WORDPRESS_URL" "http://your-site.test")
(System/setProperty "WORDPRESS_USER" "admin")
(System/setProperty "WORDPRESS_APP_PASSWORD" "your-app-password")
```

**Deploy a page:**

```clojure
(load-file "src/forma/oxygen_deploy_demo.clj")
(in-ns 'forma.oxygen-deploy-demo)
(deploy-hero-section)
```

## Architecture Overview

### Directory Structure

- **`src/forma/`** - Main source code
  - `compiler.clj` - Core compiler extending kora.core.compiler
  - `components/` - UI components (data-table, form-ssr, kanban-board, etc.)
  - `integrations/oxygen/` - Oxygen Builder integration
    - `compiler.clj` - Forma EDN → Oxygen JSON tree compiler
    - `elements.clj` - Element type mappings
    - `reverse-compiler.clj` - Oxygen JSON → Forma EDN (reverse compilation)
    - `templates.clj` - Template generation
  - `platforms/` - Platform-specific compilers (html.clj, css.clj)
  - `styling/` - Styling system (design tokens, CSS generation)
  - `cache/` - Compiler caching and incremental compilation
  - `hierarchy/` - Inheritance resolution
  - `tokens/` - Design token system
  - `build/` - Build pipeline and asset management
  - `dev/` - Development server and tooling

- **`default/`** - Default resources (resolved with lowest priority)
  - `components/` - Component EDN definitions
  - `sections/` - Section defaults (header, sidebar, footer)
  - `templates/` - Page templates
  - `global/` - Global defaults, tokens, base styles
  - `styles/` - Styling systems (shadcn-ui, etc.)
  - `platforms/` - Platform configurations (HTML, Oxygen)

- **`library/`** - Shared component library (medium priority)
- **`projects/`** - Project-specific overrides (highest priority)

- **`test/forma/`** - Test files
- **Root `.clj` files** - Deployment scripts and ad-hoc tests

### Compilation Pipeline

```
EDN Input
    ↓
Parse (forma.compiler/parse-element)
    ↓
Expand (property shortcuts like :text → {:content {:text ...}})
    ↓
Resolve (inheritance hierarchy + design tokens)
    ↓
Apply Styling (design system: shadcn-ui, etc.)
    ↓
Compile (platform-specific: HTML, Oxygen, etc.)
    ↓
Output (HTML string, Oxygen JSON tree, CSS, etc.)
```

### Resolution Hierarchy

**Priority order:** `:project` → `:library` → `:default`

**Hierarchy levels:** `[:global :components :sections :templates :pages]`

Properties are resolved by merging from lowest to highest specificity:
1. Global defaults
2. Component defaults
3. Section defaults
4. Template defaults
5. Page-specific overrides

### Oxygen Builder Integration

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

4. **Node ID Rules:**
   - Root always has `id: 1`
   - IDs are scoped per page/template (different pages can reuse same IDs)
   - `_nextNodeId` tracks next available ID (MAX(all IDs) + 1)
   - Must be numbers, not strings

5. **Critical Fields:**
   - `_nextNodeId` - Required for Oxygen builder to work
   - `status: "exported"` - Marks tree as complete
   - Properties nested as: `{content: {}, design: {}, settings: {}, meta: {}}`

**Reference Documents:**
- See [OXYGEN_ARCHITECTURE.md](OXYGEN_ARCHITECTURE.md) for complete Oxygen/Breakdance architecture
- See [WORDPRESS_INTEGRATION.md](WORDPRESS_INTEGRATION.md) for REST API details
- See [FORMA_OXYGEN_COMPILER_ROADMAP.md](FORMA_OXYGEN_COMPILER_ROADMAP.md) for compiler development plan

## Configuration

Main config file: **`config.edn`** (root level)

Key configuration sections:
- `:paths` - Resource paths (default, library, projects)
- `:resolution-order` - Priority order for resource resolution
- `:defaults` - Default platform stack and styling system
- `:folders` - Folder structure definitions
- `:features` - Feature flags (library-enabled, pre-resolution, etc.)
- `:cache` - Caching settings
- `:styling` - Styling behavior (dedupe-classes, apply-base-when-explicit, etc.)
- `:minification` - Minification settings per platform

## Common Workflows

### 1. Creating a New Component

Add component definition to `default/components/my-component.edn`:

```clojure
{:type :section
 :design/background "#1a202c"
 :design/padding "60px 20px"
 :children [{:type :heading
             :content/text "My Component"
             :content/tag "h2"}]}
```

### 2. Compiling to HTML

```clojure
(require '[forma.compiler :as compiler])

(def elements [[:button {:text "Click me"}]])
(def context {:platform-stack [:html]})

(compiler/compile-to-html elements context)
;; Returns HTML string
```

### 3. Deploying to WordPress/Oxygen

```clojure
;; 1. Set credentials (see "Working with WordPress/Oxygen Integration" above)

;; 2. Load deployment script
(load-file "deploy_correct_structure.clj")

;; 3. Create and deploy page
(def my-tree
  {:_nextNodeId 150
   :status "exported"
   :root {:id 1
          :data {:type "root" :properties []}
          :children [...]}})

(deploy-to-oxygen my-tree {:title "My Page" :status "publish"})
```

### 4. Reverse Compilation (Oxygen → Forma)

```clojure
(require '[forma.integrations.oxygen.reverse-compiler :as reverse])

;; Fetch page from WordPress
(def oxygen-page (fetch-page-from-wordpress 46))

;; Convert to Forma EDN
(reverse/oxygen->forma (:tree oxygen-page))
;; Returns Forma EDN structure
```

## Important Conventions

### Element Naming

- **Clojure:** Use kebab-case keywords (`:woo-product-price`, `:button`)
- **Oxygen Output:** Converts to PHP class names (`"EssentialElements\\WooProductPrice"`)
- Conversion handled by `forma.integrations.oxygen.compiler/element-key->php-class`

### Property Shortcuts

Forma supports shortcuts that expand during compilation:

```clojure
;; Shortcut
{:text "Hello"}

;; Expands to
{:content {:content {:text "Hello"}}}
```

### Design Tokens

Use design tokens with `design/` prefix:

```clojure
{:design/background "#1a202c"
 :design/padding "60px 20px"
 :design/color "var(--colors-primary)"}
```

### Testing Changes

When modifying the compiler or styling system:

1. Run `run_tests.clj` to verify core functionality
2. Test with a simple HTML compilation
3. Test with Oxygen deployment if changing Oxygen integration
4. Check that Oxygen builder can open deployed pages

## Key Files to Understand

- **[src/forma/compiler.clj](src/forma/compiler.clj)** - Main compiler entry point, extends kora.core
- **[src/forma/integrations/oxygen/compiler.clj](src/forma/integrations/oxygen/compiler.clj)** - EDN → Oxygen JSON tree compilation
- **[src/forma/styling/core.clj](src/forma/styling/core.clj)** - Styling system and design tokens
- **[src/forma/hierarchy/](src/forma/hierarchy/)** - Inheritance resolution
- **[config.edn](config.edn)** - Main configuration file

## Debugging Tips

### Enable Logging

Forma uses `clojure.tools.logging`. Check console output for warnings about:
- Missing config files
- Resource resolution failures
- Compilation errors

### Common Issues

**"Could not load config.edn"** - Config loads from: `config.edn` (root) → `forma/config.edn` → resources. Check file exists.

**Oxygen deployment fails** - Verify:
1. WordPress credentials are set correctly
2. REST API endpoint format: `http://site.test/index.php?rest_route=/oxygen/v1/save`
3. `_nextNodeId` and `status: "exported"` are present in tree

**Element doesn't render in Oxygen** - Check PHP class name is correct:
- Use `EssentialElements\` for modern elements
- Use `OxygenElements\` for legacy elements
- Element names are case-sensitive and must match exactly

## Development Philosophy

1. **EDN-First:** Everything starts as EDN data structures
2. **Inheritance Over Repetition:** Use hierarchy levels to avoid duplicating properties
3. **Platform-Agnostic Core:** Keep platform logic in `/platforms/` and `/integrations/`
4. **Convention Over Configuration:** Use naming conventions for element mapping
5. **Incremental Compilation:** Leverage caching for faster rebuilds

## Migration Notes

This project was migrated from `src/corebase/` to `src/forma/`. All namespaces have been updated:
- `corebase.ui.*` → `forma.*`
- `corebase.server.*` → `forma.server.*`
- `corebase.integrations.*` → `forma.integrations.*`

Legacy `src/corebase/` remains for backward compatibility during transition.
