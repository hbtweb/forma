# Forma Compiler Architecture Discussion Summary

## Overview
This document summarizes the architectural discussions and decisions made regarding the Forma compiler system, from initial verification through to performance optimization for AI/visual builder use cases.

**See also**: [`ARCHITECTURE.md`](./ARCHITECTURE.md) for the complete architecture specification.

---

## 1. Compiler Verification & Parity

### Initial State
- **New Compiler**: `forma.compiler` extending `kora.core.compiler`
- **Old Compiler**: `corebase.ui.compiler` (being migrated)
- **Goal**: Verify new compiler works and achieve parity with old compiler

### Key Findings
- New compiler successfully integrated with `kora.core` (inheritance, tokens, universal pipeline)
- Some features missing from old compiler (identified in parity report)
- Hiccup compilation issues fixed (children handling, empty vectors)
- Oxygen compiler integration fixed (direct calls instead of dynamic resolution)

### Parity Report
Created `forma/PARITY-REPORT.md` documenting:
- Test results for element parsing, property expansion, HTML compilation
- Feature comparison table (old vs new)
- Missing features list
- Recommended fixes

---

## 2. Architecture Diagrams

### Generated D2 Diagrams
1. **kora-core-compiler.d2** - Universal compiler pipeline architecture
2. **old-compiler.d2** - Legacy corebase.ui compiler structure
3. **kora-inheritance.d2** - Generic inheritance resolution system
4. **old-inheritance.d2** - Old compiler's inheritance approach
5. **kora-tokens.d2** - Token resolution system
6. **old-tokens.d2** - Old compiler's token handling
7. **forma-kora-integration.d2** - How Forma integrates with Kora Core

### Key Insights from Diagrams
- New compiler uses universal pipeline (Translate → Parse → Expand → Resolve → Transform → Output)
- Old compiler was monolithic with hardcoded logic
- New compiler separates concerns (inheritance, tokens, styling, platform)

---

## 3. Compiler Comparison

### Old Compiler (`corebase.ui.compiler`)
- **Pros**: Feature-complete, battle-tested
- **Cons**: Monolithic, hardcoded rules, not reusable, difficult to extend

### New Compiler (`forma.compiler` + `kora.core.compiler`)
- **Pros**: 
  - Universal architecture (works for any domain)
  - Separation of concerns (inheritance, tokens, styling, platform)
  - Extensible (EDN-driven configuration)
  - Reusable (Kora core can be used by other applications)
- **Cons**: Some features still missing (being addressed)

### Verdict
New compiler is **more optimal, powerful, intuitive, and easier to understand** due to:
- Clear separation of concerns
- Universal architecture
- EDN-driven configuration
- Better maintainability

---

## 4. Three-Layer Architecture

### Layer Separation
Identified three distinct layers with clear responsibilities:

#### Layer 1: Component Definitions (`/components`)
- **Purpose**: What the component is (structure, props, slots)
- **Contains**: Structure, defaults, validation hints, accessibility
- **No styling, no platform specifics**

#### Layer 2: Styling Systems (`/styles`)
- **Purpose**: How the component looks (classes, variants, themes)
- **Contains**: CSS classes, variants, base styles
- **No platform specifics, no component structure**

#### Layer 3: Platform Compilation (`/platforms`)
- **Purpose**: How the component compiles to target platform
- **Contains**: Element mappings, attribute mappings, content handling rules
- **No styling, no component structure**

### Benefits
- **Separation of concerns**: Each layer has single responsibility
- **Independent variation**: Same component → different styles/platforms
- **Composability**: HTML + HTMX = `html.edn` + `htmx.edn`
- **Extensibility**: Add new styles/platforms without changing components

---

## 5. Inheritance System Integration

### Question
How does inheritance (Global → Components → Sections → Templates → Instances) interact with the three-layer system?

### Answer
**Inheritance applies to property values**, not to:
- Component definitions (structure)
- Styling systems (appearance)
- Platform rules (compilation)

### Flow
1. **Component definitions** define structure (fixed)
2. **Inheritance** merges property values across hierarchy (values flow)
3. **Styling** converts properties → CSS classes (transformation)
4. **Platform** compiles to output format (compilation)

### What Gets Inherited
- **Tokens** (`:tokens` in hierarchy data) - colors, spacing, typography
- **Component properties** (`:components` in hierarchy data) - variant, size, etc.
- **Both flow through the same inheritance system**

### Hierarchy Levels
- **Global** (`global/defaults.edn`): Base tokens and component defaults
- **Components** (`components/*.edn`): Component structure definitions
- **Sections** (`sections/*.edn`): Section-specific overrides
- **Templates** (`templates/*.edn`): Template-specific overrides
- **Instances** (pages): Instance-specific props (highest priority)

---

## 6. Styling Customization

### Token Customization
Tokens can be overridden at any hierarchy level:
- **Global**: Base tokens (lowest priority)
- **Sections**: Section-specific token overrides
- **Templates**: Template-specific token overrides
- **Instances**: Instance-specific tokens (highest priority)

### Styling System Options
1. **Extend existing systems**: Create `my-brand-shadcn.edn` extending `shadcn-ui.edn`
2. **Token-driven styling**: Use CSS variables with token values
3. **Custom styling systems**: Create `pure-tailwind.edn`, `flutter-material.edn`, etc.

### Platform-Specific CSS Output
- **Inline classes**: Current approach (requires Tailwind loaded separately)
- **Generate minified CSS file**: Compile-time CSS generation
- **CSS-in-JS**: For React/Flutter platforms

### Customization Levels
1. **Token customization**: Override colors/spacing/typography via inheritance
2. **Styling system extension**: Extend shadcn-ui with brand overrides
3. **Custom styling systems**: Create pure Tailwind, Flutter Material, etc.
4. **CSS output control**: Generate minified files, CSS-in-JS, or inline classes

---

## 7. Custom Components & Platform Mappings

### Current State
- Custom components **require explicit platform mappings** in `platforms/*.edn`
- Falls back to hardcoded `case` statement if no mapping exists
- Unknown components may error or return nil

### Proposed: Automatic Inference
Three options considered:

#### Option A: Default Inference Rules
```clojure
;; Infer from component type
:html → {:element "div" :class-attr :class}
:oxygen → {:element "ct_div_block" :class-key :ct-classes}
```

#### Option B: Convention-Based Inference
```clojure
;; Infer from component definition
{:slots [:children]} → {:element "div"}
{:slots []} → {:element "span"}
```

#### Option C: Hybrid Approach (Recommended)
1. Check explicit platform mapping (highest priority)
2. Check component definition hints (`:platform-hints`)
3. Use default inference rules
4. Fall back to generic div/container

### Recommendation
**Explicit mappings for complex components, inference for simple ones**

---

## 8. Ad-Hoc Component Definitions

### Question
Can components be defined once (in section/template/instance) and automatically compile without separate styling/platform files?

### Proposed Architecture
Three changes needed:

#### 1. Component Discovery from Hierarchy
- Check hierarchy levels for `:components` definitions
- Fall back to `/components` directory
- Support inline component definitions

#### 2. Styling via Inheritance Override
- Check hierarchy levels for `:styling` definitions
- Merge with styling system (hierarchy overrides system)
- Support per-component styling at any level

#### 3. Automatic Platform Compilation
- Auto-infer from component definition
- Support `:platform-hints` in component def
- Generic fallback for unknown platforms

### Example
```clojure
;; sections/header.edn
{:header
 {:components
  {:custom-logo
   {:slots [:children]
    :props [:size :variant]}}
  
  :styling
  {:components
   {:custom-logo
    {:base ["logo" "inline-block"]
     :variants {:md ["h-8" "w-8"]}}}}
  
  :structure [...]}}
```

---

## 9. Performance for AI/Visual Builders

### Key Insight
**System is for AI/NLP content generation and visual builders, not human EDN writing**

### Requirements
- **Ergonomic**: Simple, easy to generate
- **Performant**: Fast compilation (not slow)
- **Cheap**: Low compute costs

### Performance Concerns
- **Inheritance cost**: Multiple references are the cost of inheritance-based savings
- **Discovery overhead**: Checking multiple hierarchy levels per component
- **Token resolution**: Resolving tokens across levels

### Optimization Strategy

#### 1. Pre-Resolve Context (One-Time Cost)
```clojure
;; Pre-resolve all inheritance and tokens once
(defn pre-resolve-context [context hierarchy-levels]
  ;; Resolve all components once, cache results
  ...)

;; Fast compile (just lookup)
(defn compile-element-fast [element pre-resolved-context]
  (let [resolved-props (get pre-resolved-context (:type element))]
    ...))
```

#### 2. Explicit Over Implicit (AI-Friendly)
- **Explicit**: `[:button {:variant :primary}]` - Simple, fast
- **Implicit**: Discovery from multiple places - Complex, slow

#### 3. Aggressive Caching
- Cache component definitions
- Cache styling systems
- Cache platform mappings
- Cache pre-resolved contexts

#### 4. Simple AI Generation Pattern
- Keep vector syntax as primary: `[:button {:variant :primary} "Click"]`
- Explicit component definitions (not discovery)
- Pre-resolve context once per request/page

### Performance Comparison
- **Current (naive)**: O(components × hierarchy-levels × elements)
- **Optimized**: O(components × hierarchy-levels) once + O(elements) lookups
- **Speedup**: ~333x for large pages

### Final Recommendation
1. **Keep EDN as intermediate format** (AI can generate it)
2. **Use explicit component definitions** (not discovery)
3. **Pre-resolve context once** per request/page
4. **Cache aggressively** (component defs, styling, platform mappings)
5. **Simple vector syntax** for AI generation
6. **Inheritance is worth it**, but optimize with pre-resolution

---

## 10. Generic Utility Functions

### Requested Functions
Make utility functions generic, especially:
- `get-content` - Extract content from props/children
- `compile-children` - Compile child elements
- `apply-attr-map` - Map properties to attributes
- `parse-tag-with-classes-id` - Parse tag with classes/ID
- `apply-default-attrs` - Apply default attributes
- `extract-htmx-attrs` - Extract HTMX attributes (uses htmx.edn)
- `extract-css-styles` - Extract CSS styles (uses platform config)

### Design Approach
- Functions take EDN configuration as input
- Remove hardcoded logic from main compiler
- Support cross-compilation (HTML + HTMX, HTML + React)

### Cross-Compilation Support
- Generic components should cross-compile to multiple platforms
- State management maps to HTMX or React
- Using HTMX requires both `html.edn` and `htmx.edn` for definitions

---

## 11. EDN-Driven Architecture

### Critical Decision
**"We shouldn't need @compilers; everything should be in @platforms which is discovered by @compiler.clj"**

### Goal
Move ALL hardcoded rules from compiler to EDN configuration files. The compiler should be completely generic and discover platforms automatically.

### Architecture Decision
1. **No separate compiler files**: Remove `compilers/html.clj` and `compilers/oxygen.clj`
2. **Platform discovery**: Compiler automatically discovers platforms from `platforms/` directory
3. **Single generic compiler**: `compiler.clj` applies rules generically from EDN
4. **No hardcoded case statements**: All element logic defined in EDN

### Complete EDN Schema Required

#### Platform Config Structure
```clojure
{:platform :html
 :description "HTML SSR compilation rules"
 :compiler {:output-format :hiccup  ; or :html-string
            :default-element "div"
            :class-attr :class
            :htmx-config "platforms/htmx.edn"  ; Reference HTMX config
            :style-function "forma.compiler/element-styles"  ; Generic function
            :template-function "forma.compiler/resolve-vars"}  ; Generic function
 :elements
 {:h1 {:element "h1"
       :class-attr :class
       :content-source :children  ; :children, :text, :content.content.text, :first-child
       :content-handling :resolve-vars  ; :resolve-vars, :none, :raw
       :children-handling :compile-all  ; :compile-all, :first-only, :none
       :attr-map {}
       :default-attrs {}
       :exclude-from-styles [:level :text :content.content.text]}
  
  :button {:element "a"  ; Special: button maps to <a> tag
           :class-attr :class
           :default-attrs {:class "btn"}
           :attr-map {:url :href}
           :content-source :children
           :content-handling :resolve-vars
           :children-handling :first-only
           :exclude-from-styles [:url]}
  
  :data-table {:element "table"
               :class-attr :class
               :custom-renderer "corebase.ui.components.data-table/render-table"
               :custom-args [:resource-key :request :data :props]
               :content-handling :none
               :children-handling :none}}}
```

### Hardcoded Rules to Move to EDN

#### 1. Parse-element rules
- Default tag type (`:div` if not keyword)
- Default element type (`:text` for non-vector/non-map)
- Tag parsing with classes/ID (`:div.card#main`)

#### 2. Expand-properties rules
- Property shortcuts (`:bg → :background`, `:pd → :padding`, etc.)
- Should be in conventions.edn or platform config

#### 3. Element compilation rules (lines 241-449)
- Large `case` statement with hardcoded logic for:
  - `:h1`, `:h2`, `:h3`, `:h4`, `:h5`, `:h6`
  - `:button`, `:btn`, `:link`
  - `:input`, `:textarea`, `:select`
  - `:container`, `:div`, `:span`, `:section`
  - `:img`, `:video`
  - Custom element handling
- **All should be in EDN platform configs**

#### 4. Content handling logic
- Children vs text vs `content.content.text`
- Template variable resolution
- **Should be in EDN**: `:content-source`, `:content-handling`

#### 5. Children handling logic
- Compile all vs first only vs none
- **Should be in EDN**: `:children-handling`

#### 6. Special cases
- `:button` → `:a` tag with `:href`
- **Should be in EDN**: `:element` mapping + `:default-attrs`

### Generic Utility Functions (No Hardcoding)

The compiler should provide generic utilities that take EDN config:

```clojure
;; Generic utilities that take EDN config
(defn get-content [props children config context] ...)
(defn compile-children [children config context compiler hierarchy-levels] ...)
(defn apply-attr-map [props attr-map-config] ...)
(defn parse-tag-with-classes-id [tag] ...)
(defn apply-default-attrs [props default-attrs-config] ...)
(defn extract-htmx-attrs [props htmx-config] ...)  ; Uses htmx.edn
(defn extract-css-styles [props style-config] ...)  ; Uses platform config
```

### Proposed Structure
- **Component definitions** (`/components`): Structure, props, slots (generic, platform-agnostic)
- **Styling systems** (`/styles`): CSS classes, variants
- **Platform configs** (`/platforms`): Complete compilation rules for each platform
  - `html.edn`: Base HTML structure compilation
  - `css.edn`: Extends HTML, adds CSS styling rules
  - `htmx.edn`: Extends HTML, adds HTMX attributes and component mappings
  - `react.edn`: Extends HTML, adds React hooks and component mappings
  - `oxygen.edn`: Standalone Oxygen Builder compilation

### Benefits
- **Data-driven**: Rules in EDN, not code
- **Extensible**: Add new platforms/styles without code changes
- **Maintainable**: Update rules without recompiling
- **Testable**: EDN files are easy to test
- **Discoverable**: Compiler finds platforms automatically
- **No duplication**: Single generic compiler for all platforms

---

## 12. Key Decisions Made

### Critical Architecture Decisions

#### Decision 1: EDN-Driven Compiler (No Separate Compiler Files)
**"We shouldn't need @compilers; everything should be in @platforms which is discovered by @compiler.clj"**

- **Remove**: `compilers/html.clj` and `compilers/oxygen.clj`
- **Keep**: Single generic `compiler.clj` that discovers platforms automatically
- **Result**: All platform-specific rules in EDN, compiler is completely generic

#### Decision 2: Complete EDN Schema
**"The EDN files should define all rules, and the compiler should be generic"**

EDN must define:
- `:content-source` - Where content comes from (`:children`, `:text`, `:content.content.text`, `:first-child`)
- `:content-handling` - How to process content (`:resolve-vars`, `:none`, `:raw`)
- `:children-handling` - How to handle children (`:compile-all`, `:first-only`, `:none`)
- `:attr-map` - Property to attribute mappings
- `:default-attrs` - Default attributes to merge
- `:custom-renderer` - Function reference for custom elements
- `:exclude-from-styles` - Properties to exclude from style conversion

#### Decision 3: Generic Utility Functions
**"Make utility functions generic, especially the bottom two"**

All utilities take EDN config as input:
- `get-content` - Extract content from props/children (uses `:content-source`, `:content-handling`)
- `compile-children` - Compile child elements (uses `:children-handling`)
- `apply-attr-map` - Map properties to attributes (uses `:attr-map`)
- `extract-htmx-attrs` - Extract HTMX attributes (uses `htmx.edn`)
- `extract-css-styles` - Extract CSS styles (uses platform config)

#### Decision 4: Platform Discovery
Compiler automatically discovers platforms from `platforms/` directory:
- No hardcoded platform list
- Add new platform = add new EDN file
- Compiler finds and loads automatically

#### Decision 5: Cross-Compilation Architecture
**"The generic components I am using should cross-compile to multiple platforms. I.e. state management maps to htmx or react. and if using htmx, then I would need both html.edn and htmx.edn for definitions to compile outward."**

- **Generic components** should cross-compile to multiple platforms
- **Components are platform-agnostic**: Components define structure only, no platform references
- **Platform stack determined at compile time**: Stack specified in context (e.g., `{:platform-stack [:html :css :htmx]}`)
- **Platform extension**: HTMX extends HTML (`:extends :html`), React extends HTML, CSS extends HTML
- **Platform composition**: Compiler compiles through all platforms in stack sequentially
- **State management mapping**: Generic props (`:state`, `:on-change`) mapped to platform-specific attributes via `:component-mappings` in platform configs

#### Decision 8: Universal EDN Convention (No Platform References in Code)
**"Either we have no resource references in code (completely generic, compiler doesn't 'know' about css/html/htmx) and have everything in edn (convention); or we add an extension system for platform specific code."**

**Decision: Fully Generic EDN Convention**

- **No platform references in compiler code** - compiler is completely generic
- **EDN extractor conventions** - platforms define extractors in `:compiler :extractors`
- **Generic function names** - `extract-by-config` not `extract-css-styles`
- **Generic component mappings** - `:mappings` not `:state-to-htmx`
- **Follows Kora's universal EDN format** - single EDN convention for all platforms
- **No custom code needed** - everything driven by EDN conventions

#### Decision 6: Forma-Only Fixes (Kora Core Unchanged)
**"For the fixes, is it just a matter of adjusting forma to match? migrating structure? or does kora core need features?"**

**Answer: Almost all fixes are Forma-only**

- **Kora Core**: No changes needed - provides universal pipeline, inheritance, tokens, protocol
- **Forma-only fixes**:
  - Tag parsing with classes/ID (`:div.card#main`)
  - HTMX attributes preservation
  - Inline styles output
  - Template variable resolution
  - Custom element support
  - Feature expansion
- **Why Forma-only**: These are application-specific implementations, not universal concerns

#### Decision 7: AI/NLP-First Design
**"People probably won't write using edn. This is for AI, and nlp content generation. It should be ergonomic, simple, cheap to run (performant, not slow). users would mostly use visual builder/ai."**

- **Primary users**: AI/NLP content generation and visual builders, NOT human EDN writing
- **Requirements**:
  - **Ergonomic**: Simple, easy to generate
  - **Performant**: Fast compilation (not slow)
  - **Cheap**: Low compute costs
- **Implications**:
  - Pre-resolution optimization (pay inheritance cost once, not per element)
  - Explicit over implicit (better for AI, better performance)
  - Simple vector syntax for AI generation
  - Aggressive caching

#### Decision 8: Inheritance Cost Accepted
**"Multiple references is the cost of inheritance-based savings, I suppose. Thoughts?"**

- **Inheritance cost**: Multiple references (checking 5 hierarchy levels) are the cost
- **Savings**: Massive duplication avoidance (define once, use everywhere)
- **Trade-off accepted**: Inheritance is worth the cost, but optimize with pre-resolution
- **Optimization**: Pre-resolve context once per request/page, not per element

#### Decision 9: New Compiler is Superior
**Comparison verdict: New compiler is more optimal, powerful, intuitive, and easier to understand**

- **More optimal**: Separation of concerns, memoization, reusable pipeline
- **More powerful**: Multi-level inheritance, token system, universal architecture, extensibility
- **More intuitive**: Clear pipeline steps, explicit protocol methods (though more setup required)
- **Easier to understand**: Clear separation, protocol-based, better documentation

### Architecture Decisions
1. **Three-layer separation**: Definitions, Styles, Platforms
2. **Inheritance applies to properties**: Not structure, styling, or platform rules
3. **Explicit over implicit**: For AI/visual builder use cases
4. **Pre-resolution optimization**: Pay inheritance cost once, not per element
5. **EDN-driven configuration**: ALL rules in EDN, compiler is generic engine
6. **No separate compiler files**: Single generic compiler discovers platforms
7. **Cross-compilation support**: Generic components cross-compile to multiple platforms via platform composition
8. **Platform composition**: Multiple platform configs can be merged (HTML + HTMX, HTML + React)
9. **Forma-only fixes**: Almost all fixes belong in Forma, Kora Core unchanged
10. **AI/NLP-first design**: System optimized for AI/visual builders, not human EDN writing
11. **Inheritance cost accepted**: Multiple references worth the duplication savings
12. **New compiler superior**: More optimal, powerful, intuitive, and easier to understand than old compiler

### Implementation Priorities
1. **Performance first**: Pre-resolve context, aggressive caching
2. **AI-friendly patterns**: Simple vector syntax, explicit definitions
3. **Backward compatibility**: Existing `/components` and `/styles` still work
4. **Extensibility**: Easy to add new platforms/styles

### Trade-offs Accepted
- **Inheritance cost**: Multiple references are worth the savings in duplication
- **Explicit mappings**: Some manual work for complex components, but better performance
- **Pre-resolution**: One-time cost per request/page, but massive speedup

---

## 13. Next Steps

### Immediate
1. **Implement platform discovery**: Compiler automatically discovers platforms from `platforms/` directory
2. **Complete EDN schema**: Add all required fields to platform configs (`:content-source`, `:content-handling`, `:children-handling`, etc.)
3. **Make utility functions generic**: All utilities take EDN config as input (derive HTMX/CSS from EDN, not hardcoded)
4. **Create `htmx.edn` platform configuration**: HTMX extends HTML (`:extends :html`), defines HTMX attributes and component mappings
5. **Create `css.edn` platform configuration**: CSS extends HTML, defines style properties and Oxygen mappings
6. **Implement platform stack compilation**: Compile to stack specified in context (e.g., `[:html :css :htmx]`)
7. **Remove hardcoded case statement**: Replace with generic compilation from EDN rules
8. **Implement pre-resolution and caching optimizations**
9. **Verify output parity**: Test that new compiler produces same output as old compilers before removing them

### Output Formats and Minification
10. **Implement output format selection**: Platforms define formats, project config selects
11. **Implement internal minification**: Minification during compilation, controlled by project config
12. **Implement pre-compilation optimization**: Tree-shaking and flattening before compilation
13. **Add environment detection**: Support dev/prod configurations

### Future
1. **Platform extension system**: Implement `:extends` mechanism for platform configs (HTMX extends HTML, React extends HTML)
2. **Generic component mapping**: Implement `:component-mappings` in platform configs for cross-compilation
3. **Platform stack compilation**: Compile element through all platforms in stack sequentially
4. Support ad-hoc component definitions in hierarchy
5. Automatic platform inference for custom components
6. CSS generation strategies (minified files, CSS-in-JS)
7. Flutter Material Design styling system
8. React platform compilation

---

## 14. Files Created/Modified

### Created
- `forma/src/forma/dev/verify.clj` - Compiler verification script
- `forma/src/forma/dev/parity.clj` - Parity verification script
- `forma/PARITY-REPORT.md` - Parity verification results
- `forma/docs/diagrams/*.d2` - Architecture diagrams (7 files)

### Modified
- `forma/src/forma/compiler.clj` - Fixed Hiccup compilation, added platform config support
- `forma/src/forma/compilers/oxygen.clj` - Fixed dynamic resolution issues
- `forma/deps.edn` - Added `org.clojure/tools.logging` dependency

---

## 15. Key Insights

### Architecture
- **Separation of concerns** enables flexibility and maintainability
- **Universal pipeline** works for any domain (UI, music, 3D, games)
- **EDN-driven** configuration makes system extensible without code changes

### Performance
- **Pre-resolution** is critical for AI/visual builder use cases
- **Explicit patterns** are faster and easier for AI to generate
- **Caching** is essential for production performance

### Design Philosophy
- **Inheritance is worth the cost** - saves massive duplication
- **Explicit over implicit** - better for AI, better performance
- **Pay once, use many** - pre-resolve context, not per element

---

## Conclusion

The Forma compiler architecture has evolved from a simple migration to a comprehensive, performance-optimized system designed for AI and visual builder use cases. The three-layer separation (definitions, styles, platforms) provides flexibility, while pre-resolution and caching ensure performance. The EDN-driven approach makes the system extensible without code changes, and the universal pipeline from Kora Core ensures the architecture can be reused across domains.

