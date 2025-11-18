# Forma Multi-Project Architecture

## Overview

Forma is structured as a compiled, distributable application that supports multiple isolated projects with a three-tier resource resolution system.

**Core Principle**: Forma follows Kora's universal EDN format convention - a single EDN format that works for every style system and platform. The compiler is fully generic with no platform references in code. Everything is driven by EDN conventions.

---

## Directory Structure

```
forma/
├── config.edn                      # Root system configuration (load at init)
│   └── Defines: paths, variables, defaults, feature flags
│
├── default/                        # Shipped defaults (read-only, part of Forma)
│   ├── components/                 # Default components (button, input, etc.)
│   ├── platforms/                 # Default platforms (html, css, htmx, oxygen)
│   ├── styles/                     # Default styles (shadcn-ui, etc.)
│   └── global/                     # Global defaults (tokens + component defaults)
│       └── defaults.edn
│
├── library/                        # Design Library (drag-and-drop sharing)
│   ├── styles/                     # Shareable style systems
│   ├── platforms/                  # Shareable platforms
│   ├── components/                 # Shareable component libraries
│   ├── sections/                   # Shareable sections
│   └── templates/                  # Shareable templates
│
└── projects/                       # User projects (isolated workspaces)
    └── project-1/
        ├── components/             # Project-specific components
        ├── global/                 # Project-specific tokens/defaults
        ├── sections/               # Project-specific sections
        ├── templates/              # Project-specific templates
        └── config.edn              # Project configuration
```

---

## Key Concepts

### 1. Three-Tier Resource Resolution

**Priority (highest to lowest):**
1. **Project-specific** (`projects/{project-name}/`)
2. **Library** (`library/`) - drag-and-drop resources
3. **Default** (`default/`) - Forma defaults

Resources are resolved in this order, with project-specific taking precedence.

### 2. Tokens = Global

- **Tokens and global are synonymous**
- Use `global/` folder (not separate `tokens/`)
- Contains: tokens, component defaults, base styles

### 3. Library = Design Library

- `/library` serves the same purpose as Oxygen's design library
- Shareable templates, sections, components, styles, platforms
- Drag-and-drop resources between projects
- Community-contributed resources

### 4. Root `config.edn`

Single source of truth for system configuration, loaded at initialization:

```clojure
{
  :paths {
    :default "default/"
    :library "library/"
    :projects "projects/"
  }
  :resolution-order [:project :library :default]
  :defaults {
    :platform-stack [:html :css :htmx]
    :styling-system :shadcn-ui
  }
  :folders {
    :components "components/"
    :platforms "platforms/"
    :styles "styles/"
    :global "global/"
  }
  :variables {
    :forma-version "1.0.0"
  }
  :features {
    :library-enabled true
    :project-isolation true
  }
}
```

---

## Comparison with Oxygen

### Similarities
- Design library concept (Forma's `/library` = Oxygen's `design-library/`)
- Modular element/component system
- Extensibility model

### Differences
- **Oxygen**: WordPress plugin, database-backed, feature-based modules
- **Forma**: Standalone app, file-based (EDN), resource-type organization
- **Oxygen**: Global installation, multi-site aware
- **Forma**: Explicit project isolation, file-system separation
- **Oxygen**: PHP + Twig templating
- **Forma**: Data-driven compilation (EDN)

---

## Resource Loading Strategy

```clojure
(defn resolve-resource
  "Resolve resource with three-tier priority"
  [project-name resource-type resource-name]
  (let [config (load-forma-config)
        resolution-order (get config :resolution-order)]
    (or
      ;; 1. Project-specific
      (load-project-resource project-name resource-type resource-name)
      ;; 2. Library (if enabled)
      (when (library-enabled? project-name)
        (load-library-resource resource-type resource-name))
      ;; 3. Default (Forma defaults)
      (load-default-resource resource-type resource-name))))
```

---

## Project Configuration

```clojure
;; projects/project-1/config.edn
{
  :project-name "project-1"
  :display-name "My Project"
  
  ;; Resource references
  :styling-system :shadcn-ui        ; from default/styles or library/styles
  :platform-stack [:html :css :htmx]
  
  ;; Custom paths (optional)
  :components-path "components/"
  :global-path "global/"
  
  ;; Inheritance
  :extends nil                       ; or extend another project
}
```

---

## Compiler Context

```clojure
(defn build-context
  "Build context with project-aware resource loading"
  [project-name data options]
  (let [project-config (load-project-config project-name)
        hierarchy (load-project-hierarchy project-name project-config)]
    (merge
      {:domain :forma
       :project project-name
       :project-config project-config
       :platform-stack (:platform-stack project-config [:html])
       :styling-system (:styling-system project-config)
       :global (get hierarchy :global)
       :components (get hierarchy :components)
       :sections (get hierarchy :sections)
       :templates (get hierarchy :templates)}
      data
      options)))
```

---

## Benefits

1. **Project Isolation**: Each project has its own components, tokens, sections, templates
2. **Resource Sharing**: Library enables drag-and-drop sharing between projects
3. **Clear Defaults**: Default folder provides shipped defaults
4. **Configurable**: Root config.edn allows system-wide customization
5. **Extensible**: Easy to add new resources via library or projects
6. **Distribution-Ready**: Default folder ships with Forma, projects/library are user workspace

---

## Migration Path

1. Move current `resources/forma/` to `default/`
2. Create root `config.edn` with paths and settings
3. Create `projects/` structure
4. Create `library/` structure
5. Update resource loading to support three-tier resolution
6. Update compiler context to be project-aware

---

## Platform Independence

**Important**: Platforms should not reference other platforms directly.

- ✅ **Correct**: `htmx.edn` extends `html.edn` via `:extends :html`
- ✅ **Correct**: `css.edn` extends `html.edn` via `:extends :html`
- ❌ **Incorrect**: `css.edn` has direct `:oxygen-mappings` (should be in `oxygen.edn`)

**Solution**: Move platform-specific mappings to their respective platform configs. CSS should read mappings generically if present, not hardcode Oxygen references.

