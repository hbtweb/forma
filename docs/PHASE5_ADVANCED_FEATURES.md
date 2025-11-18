# Phase 5: Advanced Features

**Status:** 📋 PLANNING
**Timeline:** 4-6 weeks
**Prerequisites:** Phase 3 + Phase 4.1-4.4 complete

---

## Overview

Phase 5 introduces production-ready advanced features for optimization, performance, and developer experience:

1. **Pre-Compilation Optimization** - Static analysis and dead code elimination
2. **Minification System** - HTML/CSS/JS minification with source maps
3. **Intelligent Caching** - Multi-layer caching for fast rebuilds
4. **Build Pipeline** - Production vs development builds
5. **Hot Reload** - File watching + WebSocket for instant updates
6. **Policy Enforcement** - Design system constraints and validation

---

## Phase 5.1: Pre-Compilation Optimization (1 week)

### Goal
Analyze compiled output and optimize before writing files.

### Features

#### 1. Dead Code Elimination
Remove unused styles, components, and tokens.

**Example:**
```clojure
;; Input: global/defaults.edn
{:colors {:primary "#4f46e5"
          :secondary "#64748b"
          :unused-color "#ff0000"}}  ; ← Never referenced

;; After optimization:
{:colors {:primary "#4f46e5"
          :secondary "#64748b"}}
;; unused-color removed
```

**Implementation:**
- Scan all elements for token references
- Build dependency graph
- Mark unused tokens/components/styles
- Optionally remove (with warnings) or report only

#### 2. CSS Deduplication
Merge duplicate CSS rules and optimize selectors.

**Example:**
```css
/* Before */
.btn { padding: 1rem; color: #fff; }
.button { padding: 1rem; color: #fff; }

/* After */
.btn, .button { padding: 1rem; color: #fff; }
```

#### 3. Property Inlining
Inline frequently-used token values for performance.

**Example:**
```clojure
;; Before: Token reference
{:background "$colors.primary"}

;; After optimization (production):
{:background "#4f46e5"}  ; Inlined for performance
```

**Configuration:**
```edn
{:optimization {
   :dead-code-elimination true
   :css-deduplication true
   :inline-tokens {:threshold 5}  ; Inline if used 5+ times
   :tree-shaking true}}
```

---

## Phase 5.2: Minification System (1 week)

### Goal
Minify HTML/CSS/JS output with optional source maps.

### Features

#### 1. HTML Minification
Remove whitespace, comments, and optimize attributes.

**Example:**
```html
<!-- Before -->
<div class="card">
  <h1>Title</h1>
  <p>Content</p>
</div>

<!-- After -->
<div class="card"><h1>Title</h1><p>Content</p></div>
```

#### 2. CSS Minification
Minify CSS rules, remove comments, shorten selectors.

**Example:**
```css
/* Before */
.button {
  background-color: #4f46e5;
  padding: 1rem 2rem;
}

/* After */
.button{background-color:#4f46e5;padding:1rem 2rem}
```

#### 3. Source Maps
Generate source maps for debugging minified output.

**Configuration:**
```edn
{:minification {
   :html true
   :css true
   :js true
   :source-maps {:development false
                 :production true}
   :remove-comments true
   :optimize-whitespace true}}
```

**Implementation:**
- `forma.optimization.minifier` namespace
- Multi-method dispatch on format (`:html`, `:css`, `:js`)
- Configurable levels (`:none`, `:basic`, `:aggressive`)
- Source map generation

---

## Phase 5.3: Intelligent Caching (1 week)

### Goal
Multi-layer caching for fast rebuilds and hot reload.

### Features

#### 1. Compilation Cache
Cache resolved contexts and compiled elements.

**Layers:**
1. **Token Cache**: Resolved token values
2. **Hierarchy Cache**: Resolved inheritance chains
3. **Element Cache**: Compiled element output
4. **File Cache**: Generated file content

**Example:**
```clojure
;; Cache key generation
(defn cache-key [element context]
  (hash [(:type element)
         (:properties element)
         (select-keys context [:hierarchy-levels :tokens])]))

;; Cache lookup
(defn compile-element-cached [element context]
  (if-let [cached (cache-lookup (cache-key element context))]
    cached
    (let [result (compile-element element context)]
      (cache-store! (cache-key element context) result)
      result)))
```

#### 2. Incremental Compilation
Only recompile changed files and dependencies.

**Dependency Tracking:**
```clojure
;; Track dependencies
{:file "components/button.edn"
 :depends-on ["global/defaults.edn"
              "styles/shadcn-ui.edn"]
 :hash "abc123..."}

;; On file change, recompile dependents
(defn recompile-changed [changed-file]
  (let [dependents (find-dependents changed-file)]
    (doseq [file dependents]
      (recompile file))))
```

#### 3. Cache Invalidation
Intelligent cache invalidation based on dependencies.

**Strategies:**
- **Content-based**: Hash file content, invalidate on change
- **Timestamp-based**: Check modification time
- **Dependency-based**: Invalidate dependents when dependency changes

**Configuration:**
```edn
{:cache {
   :enabled true
   :strategy :content-hash  ; or :timestamp
   :ttl 3600  ; seconds
   :max-size 1000  ; entries
   :invalidation :dependency-based}}
```

---

## Phase 5.4: Build Pipeline (1 week)

### Goal
Separate development and production builds with different optimizations.

### Features

#### 1. Build Modes
Different configurations for development vs production.

**Development Mode:**
- No minification
- Source maps enabled
- Full metadata (sync mode)
- Fast compilation (aggressive caching)
- Hot reload enabled

**Production Mode:**
- Full minification
- No metadata (export mode)
- Dead code elimination
- Tree shaking
- Optimized output

**Configuration:**
```edn
;; config.edn
{:builds {
   :development {
     :minification false
     :source-maps true
     :metadata :sync
     :cache {:aggressive true}
     :hot-reload true}

   :production {
     :minification {:level :aggressive}
     :source-maps false
     :metadata :export
     :optimization {
       :dead-code-elimination true
       :tree-shaking true
       :inline-tokens true}
     :cache {:enabled false}}}}
```

#### 2. Build Tasks
Define build workflows as EDN tasks.

**Example:**
```edn
{:tasks {
   :build-dev {
     :description "Development build with hot reload"
     :steps [
       {:task :clean :target "dist/dev"}
       {:task :compile :mode :development}
       {:task :copy-assets :source "assets/" :target "dist/dev/assets"}
       {:task :start-dev-server :port 3000}]}

   :build-prod {
     :description "Production build with full optimization"
     :steps [
       {:task :clean :target "dist/prod"}
       {:task :compile :mode :production}
       {:task :minify :formats [:html :css]}
       {:task :compress :level :gzip}
       {:task :copy-assets :source "assets/" :target "dist/prod/assets"}]}}}
```

#### 3. Build API
Programmatic build interface.

**Example:**
```clojure
(require '[forma.build.pipeline :as build])

;; Development build
(build/compile-project "dashboard-example"
  {:mode :development
   :watch true
   :hot-reload true})

;; Production build
(build/compile-project "dashboard-example"
  {:mode :production
   :output-dir "dist/prod"
   :minify true
   :optimize true})
```

---

## Phase 5.5: Hot Reload (1-2 weeks)

### Goal
File watching + WebSocket for instant UI updates during development.

### Features

#### 1. File Watcher
Monitor file changes and trigger recompilation.

**Implementation:**
```clojure
(require '[clojure.java.io :as io]
         '[hawk.core :as hawk])

(defn start-file-watcher [project-name callback-fn]
  (hawk/watch! [{:paths ["default/" "library/" (str "projects/" project-name "/")]
                 :filter hawk/file?
                 :handler (fn [ctx {:keys [kind file]}]
                           (when (= kind :modify)
                             (callback-fn file))
                           ctx)}]))
```

#### 2. WebSocket Server
Push updates to connected clients.

**Architecture:**
```
File Change → Recompile → WebSocket → Browser Reload
     ↓
   Cache Update
```

**Implementation:**
```clojure
(require '[org.httpkit.server :as http])

(defn websocket-handler [request]
  (http/with-channel request channel
    (swap! clients conj channel)
    (http/on-close channel (fn [_]
                             (swap! clients disj channel)))))

(defn broadcast-update [update-data]
  (doseq [client @clients]
    (http/send! client (json/encode update-data))))
```

#### 3. Client Library
JavaScript client for receiving updates.

**Example:**
```javascript
// forma-hot-reload.js
const FormaHotReload = {
  connect(port = 3000) {
    const ws = new WebSocket(`ws://localhost:${port}/forma-reload`);

    ws.onmessage = (event) => {
      const update = JSON.parse(event.data);

      if (update.type === 'full-reload') {
        window.location.reload();
      } else if (update.type === 'css-update') {
        this.updateCSS(update.css);
      } else if (update.type === 'element-update') {
        this.updateElement(update.element, update.html);
      }
    };
  },

  updateCSS(css) {
    const styleEl = document.getElementById('forma-styles');
    if (styleEl) styleEl.textContent = css;
  },

  updateElement(selector, html) {
    const el = document.querySelector(selector);
    if (el) el.outerHTML = html;
  }
};

// Auto-connect in development
if (import.meta.env.DEV) {
  FormaHotReload.connect();
}
```

#### 4. Selective Updates
Update only changed elements (not full page reload).

**Strategy:**
- **Full Reload**: Config/global changes
- **CSS Update**: Style-only changes
- **Element Update**: Single element changes
- **No Reload**: Metadata-only changes

**Configuration:**
```edn
{:hot-reload {
   :enabled true
   :port 3000
   :strategy :selective  ; or :full
   :debounce 200  ; ms
   :inject-client true}}
```

---

## Phase 5.6: Policy Enforcement (1 week)

### Goal
Validate design system constraints and enforce best practices.

### Features

#### 1. Design System Policies
Enforce design system rules.

**Examples:**
```edn
{:policies {
   :tokens {
     :rule :only-use-design-tokens
     :severity :error
     :message "Only use design tokens, not hardcoded values"}

   :colors {
     :rule :no-hardcoded-colors
     :severity :warn
     :pattern #"#[0-9a-fA-F]{6}"}

   :spacing {
     :rule :use-spacing-scale
     :severity :error
     :allowed ["0" "0.25rem" "0.5rem" "1rem" "1.5rem" "2rem"]}

   :typography {
     :rule :use-font-scale
     :severity :warn}}}
```

#### 2. Accessibility Policies
Enforce WCAG compliance.

**Examples:**
```edn
{:policies {
   :accessibility {
     :rule :wcag-aa
     :checks [
       {:rule :alt-text-required :elements [:img]}
       {:rule :label-required :elements [:input :textarea :select]}
       {:rule :contrast-ratio :min 4.5}
       {:rule :heading-hierarchy :enforce-order true}]}}}
```

#### 3. Performance Policies
Warn about performance issues.

**Examples:**
```edn
{:policies {
   :performance {
     :max-css-size 50000  ; bytes
     :max-html-size 100000
     :max-inline-styles 20
     :unused-code-threshold 0.1}}}  ; 10% unused = warn
```

#### 4. Policy Reporting
Generate policy violation reports.

**Output:**
```
=== POLICY VIOLATIONS ===

ERROR - tokens/no-hardcoded-colors
  File: components/button.edn
  Line: 12
  Property: :background
  Value: "#ff0000"
  Message: Only use design tokens, not hardcoded values
  Suggestion: Use $colors.danger instead

WARN - accessibility/alt-text-required
  File: pages/home.edn
  Line: 45
  Element: :img
  Message: Image missing alt text
  Suggestion: Add :alt property

=== SUMMARY ===
Errors: 1
Warnings: 1
```

---

## Implementation Priority

### Week 1: Phase 5.1 - Pre-Compilation Optimization
- Dead code elimination
- CSS deduplication
- Property inlining

### Week 2: Phase 5.2 - Minification System
- HTML/CSS minification
- Source map generation
- Configurable levels

### Week 3: Phase 5.3 - Intelligent Caching
- Multi-layer cache
- Incremental compilation
- Dependency tracking

### Week 4: Phase 5.4 - Build Pipeline
- Build modes (dev/prod)
- Build tasks
- Build API

### Weeks 5-6: Phase 5.5 - Hot Reload
- File watcher
- WebSocket server
- Client library
- Selective updates

### Week 6: Phase 5.6 - Policy Enforcement
- Design system policies
- Accessibility policies
- Policy reporting

---

## Testing Strategy

**Test Coverage:**
- Optimization correctness (output parity)
- Cache invalidation accuracy
- Minification output validation
- Hot reload message flow
- Policy detection accuracy

**Test Types:**
- Unit tests: Individual optimizers/minifiers
- Integration tests: Full build pipeline
- Performance tests: Benchmark compilation speed
- E2E tests: Hot reload in real browser

---

## Success Criteria

- ✅ Development builds < 1 second (with cache)
- ✅ Production builds fully optimized (minified, tree-shaken)
- ✅ Hot reload < 200ms (selective updates)
- ✅ Zero false positives in policy enforcement
- ✅ 90%+ test coverage
- ✅ Zero regressions on existing functionality

---

## Optional Enhancements (Post-Phase 5)

- **Bundle Splitting**: Split output into multiple files for code splitting
- **Asset Optimization**: Image compression, SVG optimization
- **CDN Integration**: Deploy assets to CDN automatically
- **Analytics**: Track compilation metrics and usage patterns
- **Plugin System**: Allow user-defined optimizations and policies

---

**Last Updated:** 2025-01-12
**Status:** Planning
**Next Step:** Begin Phase 5.1 implementation
