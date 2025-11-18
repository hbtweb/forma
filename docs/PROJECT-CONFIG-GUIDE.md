# Project Configuration Guide

## Overview

Forma supports per-project configuration via `projects/{project-name}/config.edn`. This allows each project to override default settings like platform stack, styling system, and feature flags.

## Project Config Schema

```clojure
{:project-name "example"
 :description "Optional project description"
 
 ;; Override default platform stack
 :defaults {
   :platform-stack [:html :css :htmx]  ; or [:html :react] etc.
   :styling-system :shadcn-ui          ; or :tailwind, :material, etc.
   :styling-stack [:tailwind :shadcn-ui]  ; or single :styling-system
 }
 
 ;; Minification settings
 :minify {
   :enabled true
   :environment :production  ; or :development
   :hiccup {:enabled false}  ; Hiccup doesn't need minification
   :html-string {:enabled true :remove-whitespace true :remove-comments true}
   :css-string {:enabled true :remove-whitespace true :remove-comments true}
 }
 
 ;; Override resolution order (optional)
 :resolution-order [:project :library :default]
 
 ;; Override feature flags (optional)
 :features {
   :pre-resolution true
   :platform-discovery true
 }
 
 ;; Override paths (optional)
 :paths {
   :components "custom-components/"
   :styles "custom-styles/"
 }
}
```

## Usage

### Automatic Loading

Project configs are automatically loaded when you use `build-context` with a `project-name`:

```clojure
(require '[forma.compiler :as compiler])

;; Build context with project config automatically merged
(let [context (compiler/build-context {} {:project-name "example"})]
  ;; context now includes project-specific :platform-stack, :styling-system, etc.
  (compiler/compile-to-html elements context))
```

### Manual Loading

You can also use the `forma.project-config` namespace directly:

```clojure
(require '[forma.project-config :as project-config])

;; Load project config
(let [config (project-config/load-project-config "example")]
  ;; Use config...
  )

;; Get project platform stack
(let [stack (project-config/get-project-platform-stack "example")]
  ;; [:html :css :htmx] or project override
  )
```

## Example Project Structure

```
projects/
  example/
    config.edn          # Project configuration
    components/         # Project-specific components
    styles/            # Project-specific styles
    sections/          # Project-specific sections
    templates/         # Project-specific templates
```

## Configuration Merging

Project configs merge with base config using deep merge:
- Top-level keys override base config
- Nested maps are merged (project values override base values)
- Missing keys fall back to base config

## Status

✅ **Implemented** - Project config schema fully implemented and integrated into `build-context`.

