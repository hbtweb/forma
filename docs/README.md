# Forma Documentation

## Overview

Forma is a generic UI compiler that separates concerns into three distinct layers: **Definitions**, **Styles**, and **Platforms**. Components are platform-agnostic and define only structure. The platform stack is determined at compile time, allowing the same generic components to compile to multiple platforms (HTML + CSS + HTMX, HTML + CSS + React, etc.).

**Core Principle**: Forma follows Kora's universal EDN format convention - a single EDN format that works for every style system and platform, with no custom code needed. Everything is driven by EDN conventions.

---

## Core Documentation

### Architecture

- **[ARCHITECTURE.md](./ARCHITECTURE.md)** - Complete architecture specification
  - Three-layer separation (Definitions, Styles, Platforms)
  - Platform stack architecture
  - Compilation flow
  - Generic component mapping

- **[PLATFORM-EXTENSION-STACKING.md](./PLATFORM-EXTENSION-STACKING.md)** - Platform extension and stacking
  - How platforms extend each other (`:extends`)
  - How platform stacking works
  - Sequential compilation flow
  - Cross-platform mappings

- **[OUTPUT-FORMATS-AND-MINIFICATION.md](./OUTPUT-FORMATS-AND-MINIFICATION.md)** - Output configuration and optimization
  - Output format selection (platforms define, projects configure)
  - Internal minification strategy
  - Pre-compilation optimization (tree-shaking, flattening)
  - Static analysis and dependency graphs

- **[INHERITANCE-COMPONENTS-RESOLUTION-ANALYSIS.md](./INHERITANCE-COMPONENTS-RESOLUTION-ANALYSIS.md)** - Inheritance and resolution
  - How inheritance works across hierarchy levels
  - Component system and resolution
  - Token resolution
  - Complete resolution pipeline

- **[INTERACTIVE-DEV-ENVIRONMENT.md](./INTERACTIVE-DEV-ENVIRONMENT.md)** - Interactive dev environment considerations (Next.js-like)
  - Development vs production modes
  - Hot module replacement (HMR)
  - Caching strategies
  - Request-time vs build-time compilation
  - Source maps and error handling

---

## Implementation Documentation

- **[COMPILER-DISCUSSION-SUMMARY.md](./COMPILER-DISCUSSION-SUMMARY.md)** - Architectural decisions and discussions
  - Compiler verification and parity
  - Three-layer architecture
  - Inheritance system integration
  - Performance optimizations
  - Key decisions made

- **[IMPLEMENTATION-STATUS.md](./IMPLEMENTATION-STATUS.md)** - Current implementation status
  - Completed features
  - In progress features
  - Not yet implemented
  - Priority order

- **[MULTI-PROJECT-ARCHITECTURE.md](./MULTI-PROJECT-ARCHITECTURE.md)** - Multi-project structure
  - Three-tier resource resolution
  - Project-specific configuration
  - Library sharing
  - Directory structure

---

## Guides

- **[PROJECT-CONFIG-GUIDE.md](./PROJECT-CONFIG-GUIDE.md)** - Project configuration guide
  - Project config schema
  - Configuration merging
  - Usage examples

---

## Key Concepts

### Three-Layer Separation

1. **Component Definitions** (`/components`) - What the component is (structure, props, slots)
2. **Styling Systems** (`/styles`) - How the component looks (classes, variants, themes)
3. **Platform Compilation** (`/platforms`) - How the component compiles to target platform

### Platform Stack

Platforms are compiled sequentially in a stack:
- **Extension**: Platforms can extend other platforms (`:extends :html`)
- **Stacking**: Multiple platforms process elements in order
- **Configuration**: Stack determined at compile time via project config

### Inheritance System

Properties flow through hierarchy levels:
- **Global** → **Components** → **Sections** → **Templates** → **Pages**
- Deep merge algorithm (right overrides left)
- Token resolution after inheritance, before styling

### Output Formats

- **Platforms define** available output formats
- **Project config selects** which format to use
- **Environment-specific** overrides supported

### Optimization

- **Pre-compilation**: Tree-shaking and flattening before compilation
- **Static analysis**: Determine what's used from declarative structure
- **Internal minification**: During compilation, controlled by project config

---

## Quick Start

### Basic Compilation

```clojure
(require '[forma.compiler :as compiler])

;; Compile elements to HTML
(let [elements [[:button {:text "Click"}]]
      context (compiler/build-context {})]
  (compiler/compile-to-html elements context))
```

### With Project Config

```clojure
;; projects/my-app/config.edn
{:defaults {:platform-stack [:html :css :htmx]}}

;; Compile with project config
(let [elements [[:button {:text "Click"}]]
      context (compiler/build-context {} {:project-name "my-app"})]
  (compiler/compile-to-html elements context))
```

---

## Architecture Principles

1. **Components are generic** - no platform references in components
2. **Stack determined at compile time** - specified in context, not derived
3. **Platforms extend base** - HTMX extends HTML, React extends HTML
4. **EDN-driven** - all rules in EDN, compiler is generic engine
5. **Three-layer separation** - Definitions, Styles, Platforms
6. **Inheritance for properties** - not structure, styling, or platform rules
7. **Pre-resolution optimization** - pay inheritance cost once, not per element
8. **Universal EDN convention** - single EDN format for all platforms, no custom code needed
9. **Output formats in platforms** - platforms define capabilities
10. **Project config selects** - projects choose what to use
11. **Internal minification** - during compilation, not external step
12. **Pre-compilation optimization** - tree-shaking and flattening before compilation

---

## Related Projects

- **Kora Core** - Universal compiler foundation
  - Inheritance system
  - Token resolution
  - Universal pipeline

---

## Status

✅ **Core architecture** - Implemented
✅ **Platform extension** - Implemented
✅ **Platform stacking** - Implemented
✅ **Inheritance system** - Implemented
✅ **Token resolution** - Implemented
✅ **Pre-resolution** - Implemented
🚧 **Output format selection** - Planned
🚧 **Internal minification** - Planned
🚧 **Pre-compilation optimization** - Planned

---

## Contributing

See individual documentation files for detailed implementation guides and architecture decisions.

