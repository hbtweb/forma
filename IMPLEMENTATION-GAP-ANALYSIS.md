# Implementation Gap Analysis

## Summary

The documentation describes an EDN-driven architecture where styling and CSS extraction behavior should be configurable via EDN. However, **the actual implementation is hardcoded** and lacks the EDN configuration options needed to control these behaviors.

For detailed edge cases and recommended handling, see [`docs/STYLING-EDGE-CASES.md`](./docs/STYLING-EDGE-CASES.md).

---

## What WAS Implemented ✅

### 1. Styling System Extension & Stacking
- ✅ `load-styling-system` with `:extends` support (recursive deep-merge)
- ✅ `load-styling-stack` for multiple styling systems
- ✅ `apply-styling-from-config` for applying styles from configs
- ✅ Extension chains work (e.g., `my-brand` extends `shadcn-ui` extends `tailwind`)

**Location**: `forma/src/forma/styling/core.clj`

### 2. Platform Extension & Stacking
- ✅ Platform `:extends` mechanism (e.g., `css.edn` extends `html.edn`)
- ✅ Platform stack compilation (sequential application)
- ✅ Deep merge of platform configs

**Location**: `forma/src/forma/compiler.clj`

### 3. CSS Extractor System
- ✅ Generic `extract-by-extractor-config` function
- ✅ Property selector extractor for CSS properties
- ✅ Configurable via `css.edn` `:extractors` map

**Location**: `forma/src/forma/compiler.clj`, `forma/default/platforms/css.edn`

- ✅ Documentation updated with edge cases, stacking policy, provenance & ad-hoc styling plan (see `docs/STYLING-EDGE-CASES.md`)
- ✅ Provenance & sync guidance now covers hierarchy + styling layers, external sync strategy, and provenance UI expectations
- ✅ Competitive comparison embedded in docs to track differentiation

---

## What WAS NOT Implemented ❌

### 1. EDN Config to Control Styling Application

**Problem**: Styling system always applies base classes, even when element has explicit classes.

**Current Behavior** (hardcoded in `apply-styling-from-config`):
```clojure
;; Always combines: base-classes + variant-classes + existing-class
combined-classes (str/trim (str/join " " (filter seq [base-classes variant-classes existing-class-str])))
```

**Missing EDN Config**:
```clojure
;; Should be in styles/shadcn-ui.edn or project config
{:components
 {:button
  {:base [...]
   :apply-base-when-explicit false  ; ← NOT IMPLEMENTED
   :respect-explicit-classes true    ; ← NOT IMPLEMENTED
   }}}
```

**Or in project config**:
```clojure
;; projects/my-app/config.edn
{:styling {
  :apply-base-when-explicit false  ; ← NOT IMPLEMENTED
  :respect-explicit-classes true   ; ← NOT IMPLEMENTED
}}
```

### 2. EDN Config to Exclude Inherited Defaults from CSS Extraction

**Problem**: CSS extractor extracts inherited global defaults (e.g., `:font-size`, `:font-weight` from `global/defaults.edn`), even when not explicitly set on element.

**Current Behavior** (hardcoded in `element-styles`):
```clojure
;; Extracts ALL props that match CSS extractor keys, including inherited ones
props-for-styles (apply dissoc resolved-props (conj exclude-from-styles :class class-attr :style))
styled-attrs (element-styles props-for-styles all-platform-configs)
```

**Missing EDN Config**:
```clojure
;; Should be in platforms/css.edn
{:compiler {
  :extractors {
    :styles {
      :type :property-selector
      :keys [...]
      :exclude-inherited true  ; ← NOT IMPLEMENTED
      :only-explicit true      ; ← NOT IMPLEMENTED
    }
  }
}}
```

**Or in project config**:
```clojure
;; projects/my-app/config.edn
{:css {
  :exclude-inherited-defaults true  ; ← NOT IMPLEMENTED
  :only-extract-explicit true       ; ← NOT IMPLEMENTED
}}
```

### 3. Style Merging Bug Fix

**Problem**: Double semicolon when original style ends with `;` and resolved style is added.

**Current Code** (line 330 in `compiler.clj`):
```clojure
(str original-style (when (seq resolved-style) (str "; " resolved-style)))
;; Result: "width: 100%; margin-bottom: 0.5rem;; font-size:14px;..." (double semicolon)
```

**Fix Needed**: Trim semicolon from end of `original-style` before concatenating.

---

## Documentation vs Implementation

### What Documentation Says

**OUTPUT-FORMATS-AND-MINIFICATION.md** (Section 6):
- Describes styling system extension/stacking architecture
- Shows how systems extend and stack
- **Does NOT mention** config options to control when base classes are applied
- **Does NOT mention** config options to exclude inherited defaults

**ARCHITECTURE.md**:
- Describes three-layer separation
- Shows EDN-driven configuration
- **Does NOT specify** these particular config options

**COMPILER-DISCUSSION-SUMMARY.md**:
- Describes EDN-driven architecture
- Says "all rules in EDN, compiler is generic engine"
- **Does NOT list** these specific config options as implemented

### What Implementation Actually Does

1. **Styling**: Always applies base classes (hardcoded behavior)
2. **CSS Extraction**: Always extracts inherited defaults (hardcoded behavior)
3. **Style Merging**: Has double semicolon bug (code bug)

---

## What Needs to Be Added

### 1. Styling System Config Options

**Option A: Per-Component Config** (in styling system EDN)
```clojure
;; styles/shadcn-ui.edn
{:components
 {:button
  {:base [...]
   :variants {...}
   :apply-base-when-explicit false  ; NEW: Don't apply base if explicit classes exist
   :respect-explicit-classes true   ; NEW: Respect explicit classes
   }}}
```

**Option B: Global Config** (in styling system EDN)
```clojure
;; styles/shadcn-ui.edn
{:theme :shadcn-ui
 :styling-config {
   :apply-base-when-explicit false  ; NEW: Global default
   :respect-explicit-classes true   ; NEW: Global default
 }
 :components {...}}
```

**Option C: Project Config** (in project config.edn)
```clojure
;; projects/my-app/config.edn
{:styling {
  :apply-base-when-explicit false  ; NEW: Project-level override
  :respect-explicit-classes true   ; NEW: Project-level override
}}
```

**Implementation**: Update `apply-styling-from-config` to check these config options.

### 2. CSS Extractor Config Options

**In CSS Platform Config**:
```clojure
;; platforms/css.edn
{:compiler {
  :extractors {
    :styles {
      :type :property-selector
      :keys [...]
      :exclude-inherited true      ; NEW: Don't extract inherited defaults
      :only-explicit true         ; NEW: Only extract explicitly set props
      :exclude-from-inheritance [:font-size :font-weight :border-radius :transition]  ; NEW: Specific exclusions
    }
  }
}}
```

**Or in Project Config**:
```clojure
;; projects/my-app/config.edn
{:css {
  :exclude-inherited-defaults true  ; NEW: Project-level override
  :only-extract-explicit true      ; NEW: Project-level override
}}
```

**Implementation**: 
- Track which props are explicit vs inherited (requires context tracking)
- Filter inherited props before extraction
- Or exclude specific props from extraction

### 3. Style Merging Bug Fix

**Simple Fix** (no config needed):
```clojure
;; In compiler.clj line 330
(let [original-trimmed (str/trim (str/replace (str original-style) #";\s*$" ""))
      resolved-style (or % "")]
  (str/trim (str original-trimmed (when (seq resolved-style) (str "; " resolved-style)))))
```

---

## Recommended Implementation Plan

### Phase 1: Quick Fixes (No Config)
1. ✅ Fix double semicolon bug (simple code fix)
2. ✅ Make styling respect explicit classes by default (change default behavior)

### Phase 2: Add EDN Config Options
3. Add `:apply-base-when-explicit` to styling system configs
4. Add `:exclude-inherited` to CSS extractor config
5. Update `apply-styling-from-config` to read config
6. Update `element-styles` to filter inherited props

### Phase 3: Track Explicit vs Inherited Props
7. Track which props are explicit vs inherited in context
8. Use tracking to filter inherited props from CSS extraction
9. Document new config options

---

## Conclusion

**The architecture is EDN-driven, but these specific behaviors are hardcoded.**

The documentation describes the overall architecture correctly, but doesn't mention that these particular config options were never implemented. The code needs to be updated to:

1. **Read EDN config** to control styling application behavior
2. **Read EDN config** to control CSS extraction behavior  
3. **Fix the double semicolon bug** (simple code fix)

This aligns with the EDN-driven architecture described in the documentation, but requires actual implementation of the config options.

