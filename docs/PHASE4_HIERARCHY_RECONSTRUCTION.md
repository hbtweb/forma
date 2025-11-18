# Phase 4: Full Hierarchy Reconstruction - Implementation Plan

**Status:** 🚧 IN PROGRESS
**Timeline:** 2-3 months (estimated)
**Started:** 2025-01-12

---

## Executive Summary

**Goal:** Enable parsing of flattened HTML/JSX/CSS and reconstruct a complete multi-file Forma EDN project with proper hierarchy separation (global → components → sections → templates → pages).

**Current State (Phase 3):**
- ✅ Can compile Forma EDN → HTML/JSX with metadata
- ✅ Can parse HTML/JSX → Flattened Forma EDN
- ✅ Token tracking preserves `$token.path` references
- ✅ Property source tracking (explicit vs inherited)
- ✅ Provenance tracking (compilation stages, conflicts)

**Phase 4 Will Add:**
- 🔄 Property classification (where does each property belong in hierarchy?)
- 🔄 Token reverse lookup (build token registry from resolved values)
- 🔄 Multi-file generation (split flattened EDN into proper files)
- 🔄 Project reconciliation (3-way merge with conflict resolution)

---

## Architecture Overview

### Input → Output Flow

```
INPUT: Flattened HTML with metadata
↓
1. Parse (forma.parsers.universal)
   → Raw Forma EDN (single flat structure)
↓
2. Extract Metadata (forma.sync.metadata)
   → Token provenance, property sources, class attribution
↓
3. Classify Properties (NEW: forma.hierarchy.classifier)
   → Which properties belong at which hierarchy level?
↓
4. Reconstruct Tokens (NEW: forma.tokens.registry)
   → Build token definitions from resolved values
↓
5. Generate Files (NEW: forma.hierarchy.generator)
   → Split into global, components, sections, templates, pages
↓
6. Reconcile (NEW: forma.project.reconciliation)
   → 3-way merge with existing project (if exists)
↓
OUTPUT: Multi-file Forma EDN project
```

### Key Challenges

1. **Property Classification Ambiguity**
   - How do we know `:padding "2rem"` should be in global vs component vs page?
   - Need heuristics based on:
     - Frequency (appears in >50% of components → global)
     - Consistency (same value across instances → component)
     - Variance (different per instance → page/instance level)
     - Metadata hints (if available from sync mode)

2. **Token Reconstruction**
   - Input: `{:background "#4f46e5"}` (resolved value)
   - Need: `$colors.primary` (original token reference)
   - Solution: Build token registry, match by value, handle collisions

3. **Multi-File Organization**
   - How to split a flat structure into logical files?
   - Need to infer component boundaries, section groupings
   - Preserve design system structure

4. **Reconciliation Conflicts**
   - User edits project locally
   - Designer edits in WordPress/Oxygen
   - Both change same property → conflict resolution strategy

---

## Implementation Phases

### Phase 4.1: Property Classification System (2-3 weeks)

**Goal:** Determine which hierarchy level each property belongs to

#### Modules to Build

**1. `forma.hierarchy.classifier` (NEW)**

Core classification logic:
```clojure
(ns forma.hierarchy.classifier
  "Classify properties by hierarchy level based on heuristics")

(defn classify-property
  "Classify a single property based on usage patterns

  Returns: {:level :global|:components|:sections|:templates|:pages
            :confidence 0.0-1.0
            :reason \"...\"}"
  [property-key property-value element-type usage-stats metadata]
  ...)

(defn classify-element-properties
  "Classify all properties in an element

  Returns: {:global {...}
            :components {...}
            :sections {...}
            :templates {...}
            :pages {...}}"
  [element usage-stats metadata]
  ...)

(defn build-usage-statistics
  "Analyze flattened structure to build usage statistics

  Returns: {:properties {property-key {:frequencies {...}
                                       :values {...}
                                       :variance ...}}
            :elements {element-type {:count ...}}}"
  [flattened-edn]
  ...)
```

**Classification Heuristics:**

| Property | Heuristic | Level |
|----------|-----------|-------|
| Color values | Used across 50%+ components with same value | `:global` (token) |
| Spacing | Used across 50%+ components with same value | `:global` (token) |
| Typography | Used across 50%+ components with same value | `:global` (token) |
| Base classes | Present in all instances of component | `:components` (base) |
| Variant classes | Present in some instances, grouped | `:components` (variant) |
| Layout props | Specific to section/template context | `:sections` or `:templates` |
| Unique values | Only in one instance | `:pages` (instance) |

**Metadata-Driven Classification:**

If property has metadata (from sync mode):
```clojure
;; data-forma-token-provenance='{"background":"$colors.primary"}'
→ :level :global (it's a token reference)

;; data-forma-source='{"level":"components","explicit":true}'
→ :level :components (explicitly set at component level)

;; data-forma-class-attribution='{"btn":{"system":"shadcn-ui","type":"base"}}'
→ :level :components (base class from styling system)
```

**Output:**
```clojure
;; Classified properties
{:element-type :button
 :classifications
 {:global {:background "$colors.primary"  ; Token (80% confidence)
           :padding "$spacing.md"}        ; Token (75% confidence)
  :components {:class "btn"               ; Base class (95% confidence)
               :border-radius "0.5rem"}   ; Component default (70% confidence)
  :pages {:on-click "handleSubmit"        ; Instance-specific (100% confidence)
          :text "Submit Order"}}}         ; Instance-specific (100% confidence)
```

#### Tests

**Location:** `forma/src/forma/dev/phase4_hierarchy_tests.clj` (NEW)

```clojure
(deftest test-classify-property-by-frequency
  ;; Property used in 80% of buttons → :global
  ...)

(deftest test-classify-property-by-variance
  ;; Property has 10 different values across instances → :pages
  ...)

(deftest test-classify-property-with-metadata
  ;; Property has token provenance → :global
  ...)

(deftest test-classify-base-class
  ;; Class appears in all instances → :components
  ...)

(deftest test-classify-variant-class
  ;; Class appears in grouped instances → :components (variant)
  ...)

(deftest test-build-usage-statistics
  ;; Analyze flat structure, count frequencies
  ...)
```

**Deliverables:**
- [ ] `forma/src/forma/hierarchy/classifier.clj` (~400 LOC)
- [ ] `forma/src/forma/dev/phase4_hierarchy_tests.clj` (~200 LOC tests)
- [ ] Documentation with classification examples

---

### Phase 4.2: Token Reverse Lookup & Registry (2-3 weeks)

**Goal:** Reconstruct token definitions from resolved values

#### Modules to Build

**1. `forma.tokens.registry` (NEW)**

Token registry and reverse lookup:
```clojure
(ns forma.tokens.registry
  "Build token registry from resolved values, enable reverse lookup")

(defn build-token-registry
  "Analyze flattened structure + metadata, extract token definitions

  Returns: {:colors {:primary \"#4f46e5\"
                     :secondary \"#64748b\"}
            :spacing {:sm \"0.5rem\"
                      :md \"1rem\"}}"
  [flattened-edn metadata]
  ...)

(defn reverse-lookup-token
  "Find token reference for a resolved value

  Returns: {:token-path \"$colors.primary\"
            :confidence 0.9
            :alternatives [\"$colors.accent\"]}"  ; If multiple tokens have same value
  [value token-registry]
  ...)

(defn reconstruct-token-references
  "Replace resolved values with token references where possible

  Returns: {:background \"$colors.primary\"}  ; Instead of {:background \"#4f46e5\"}"
  [properties token-registry]
  ...)

(defn detect-token-patterns
  "Detect common patterns that should be tokenized

  Returns: [{:type :color :values [\"#4f46e5\" \"#4f46e5\" ...] :frequency 25}
            {:type :spacing :values [\"1rem\" \"1rem\" ...] :frequency 18}]"
  [flattened-edn]
  ...)
```

**Token Detection Heuristics:**

| Pattern | Detection | Token Type |
|---------|-----------|------------|
| Color values | Hex codes, rgb(), hsl() used >5 times | `:colors` |
| Spacing values | rem/px values used >5 times | `:spacing` |
| Font sizes | Font-size values used >3 times | `:typography.sizes` |
| Font families | Font-family values used >2 times | `:typography.families` |
| Border radius | Border-radius values used >5 times | `:borders.radius` |
| Shadows | Box-shadow values used >3 times | `:effects.shadows` |

**Metadata-Enhanced Reconstruction:**

If metadata contains original token:
```clojure
;; data-forma-token-provenance='{"background":"$colors.primary"}'
→ Use original token reference directly (100% confidence)

;; No metadata, but value "#4f46e5" appears 25 times
→ Create token "$colors.primary" (80% confidence)
→ Suggest alternatives if value has semantic meaning
```

**Output:**
```clojure
;; Token registry
{:tokens
 {:colors {:primary "#4f46e5"      ; From metadata or frequency
           :secondary "#64748b"}
  :spacing {:sm "0.5rem"
            :md "1rem"
            :lg "2rem"}
  :typography {:sizes {:base "1rem"
                       :lg "1.25rem"}}}

 :reconstructed-properties
 {:background "$colors.primary"    ; Instead of "#4f46e5"
  :padding "$spacing.md"}}         ; Instead of "1rem"
```

#### Tests

```clojure
(deftest test-build-token-registry-from-metadata
  ;; Metadata contains token provenance → extract tokens
  ...)

(deftest test-build-token-registry-from-frequency
  ;; No metadata, but value used 25 times → create token
  ...)

(deftest test-reverse-lookup-token-unique
  ;; Single token matches value → return token path
  ...)

(deftest test-reverse-lookup-token-collision
  ;; Multiple tokens have same value → return alternatives
  ...)

(deftest test-reconstruct-token-references
  ;; Replace resolved values with tokens
  ...)

(deftest test-detect-token-patterns-colors
  ;; Detect color values that should be tokenized
  ...)

(deftest test-detect-token-patterns-spacing
  ;; Detect spacing values that should be tokenized
  ...)
```

**Deliverables:**
- [ ] `forma/src/forma/tokens/registry.clj` (~350 LOC)
- [ ] Phase 4.2 tests in `phase4_hierarchy_tests.clj` (~150 LOC)
- [ ] Token pattern detection documentation

---

### Phase 4.3: Multi-File Generation (3-4 weeks)

**Goal:** Split flattened EDN into proper multi-file hierarchy

#### Modules to Build

**1. `forma.hierarchy.generator` (NEW)**

File generation logic:
```clojure
(ns forma.hierarchy.generator
  "Generate multi-file EDN structure from classified properties")

(defn generate-global-defaults
  "Generate default/global/defaults.edn from global properties

  Returns: {:tokens {...}
            :button {:background \"$colors.primary\"}}"
  [classified-properties token-registry]
  ...)

(defn generate-component-definitions
  "Generate default/components/*.edn files

  Returns: {\"button.edn\" {:button {:base [...]
                                     :variants {...}}}
            \"input.edn\" {...}}"
  [classified-properties]
  ...)

(defn generate-section-overrides
  "Generate default/sections/*.edn files (if sections detected)

  Returns: {\"header.edn\" {:header {...}}}"
  [classified-properties]
  ...)

(defn generate-template-definitions
  "Generate default/templates/*.edn files (if templates detected)

  Returns: {\"dashboard.edn\" {:dashboard {...}}}"
  [classified-properties]
  ...)

(defn generate-page-instances
  "Generate projects/{name}/pages/*.edn files

  Returns: {\"home.edn\" {:home {:content [...]}}}"
  [classified-properties project-name]
  ...)

(defn write-multi-file-project
  "Write all generated files to disk

  Side effect: Creates directory structure and .edn files"
  [generated-files base-path]
  ...)
```

**File Structure:**

```
default/
├── global/
│   └── defaults.edn          # Tokens + global defaults
├── components/
│   ├── button.edn            # Base + variants
│   ├── input.edn
│   └── heading.edn
├── sections/
│   ├── header.edn            # Section-specific overrides
│   └── footer.edn
└── templates/
    └── dashboard.edn         # Template-specific overrides

projects/
└── imported-site/
    ├── config.edn            # Project config
    └── pages/
        ├── home.edn          # Instance-level properties
        └── about.edn
```

**Generation Strategy:**

1. **Global Defaults:**
   - All token definitions
   - Properties classified as `:global`
   - Common defaults (>50% frequency)

2. **Component Definitions:**
   - Properties classified as `:components`
   - Group by component type (`:button`, `:input`)
   - Separate base classes from variants

3. **Section Overrides:**
   - Properties classified as `:sections`
   - Group by section context (detected from element paths)

4. **Template Definitions:**
   - Properties classified as `:templates`
   - Group by template patterns (detected from page structure)

5. **Page Instances:**
   - Properties classified as `:pages`
   - Instance-specific content and props
   - References to components/sections/templates

**Output Files:**

```clojure
;; default/global/defaults.edn
{:tokens
 {:colors {:primary "#4f46e5"
           :secondary "#64748b"}
  :spacing {:sm "0.5rem" :md "1rem"}}
 :button {:background "$colors.primary"
          :padding "$spacing.md"}}

;; default/components/button.edn
{:button
 {:base ["btn" "inline-flex" "items-center"]
  :variants {:primary ["bg-primary" "text-white"]
             :secondary ["bg-secondary" "text-gray-900"]}}}

;; projects/imported-site/pages/home.edn
{:home
 {:content [[:button {:variant :primary
                      :on-click "handleSubmit"}
             "Submit Order"]]}}
```

#### Tests

```clojure
(deftest test-generate-global-defaults
  ;; Global properties + tokens → defaults.edn
  ...)

(deftest test-generate-component-definitions
  ;; Component properties → component.edn files
  ...)

(deftest test-separate-base-from-variants
  ;; Base classes vs variant classes
  ...)

(deftest test-generate-page-instances
  ;; Page properties → page.edn files
  ...)

(deftest test-write-multi-file-project
  ;; Write all files to disk, verify structure
  ...)
```

**Deliverables:**
- [ ] `forma/src/forma/hierarchy/generator.clj` (~450 LOC)
- [ ] Phase 4.3 tests in `phase4_hierarchy_tests.clj` (~200 LOC)
- [ ] File structure documentation with examples

---

### Phase 4.4: Project Reconciliation (3-4 weeks)

**Goal:** Merge imported changes with existing project (3-way merge)

#### Modules to Build

**1. `forma.project.reconciliation` (NEW)**

Reconciliation and conflict resolution:
```clojure
(ns forma.project.reconciliation
  "Reconcile imported changes with existing project")

(defn detect-conflicts
  "Compare base, local, and imported versions, detect conflicts

  Returns: [{:type :property-conflict
             :path [:button :background]
             :base \"$colors.primary\"
             :local \"$colors.accent\"
             :imported \"#4f46e5\"
             :resolution-strategy :manual}]"
  [base-project local-project imported-project]
  ...)

(defn auto-resolve-conflicts
  "Auto-resolve conflicts using heuristics

  Strategies:
  - :accept-local - Keep local changes
  - :accept-imported - Use imported changes
  - :merge - Combine both (if possible)
  - :manual - Require user decision"
  [conflicts resolution-strategy]
  ...)

(defn three-way-merge
  "Perform 3-way merge: base + local + imported

  Returns: {:merged-project {...}
            :conflicts [...]
            :auto-resolved [...]
            :manual-review [...]}"
  [base-project local-project imported-project options]
  ...)

(defn generate-conflict-report
  "Generate human-readable conflict report

  Returns: Markdown report with conflicts + suggested resolutions"
  [conflicts]
  ...)

(defn apply-resolution
  "Apply conflict resolution decisions to project

  Returns: Reconciled project with conflicts resolved"
  [merged-project resolutions]
  ...)
```

**Reconciliation Workflow:**

```
1. User has existing Forma project (LOCAL)
   ↓
2. Export to WordPress with sync metadata
   ↓
3. Designer edits in WordPress Oxygen Builder
   ↓
4. Import changes back (IMPORTED)
   ↓
5. Reconciliation:
   - Compare BASE (last export) vs LOCAL (current) vs IMPORTED
   - Detect conflicts (both changed same property)
   - Auto-resolve where possible
   - Prompt user for manual conflicts
   ↓
6. Merged project with all changes
```

**Conflict Types:**

| Conflict Type | Example | Resolution Strategy |
|---------------|---------|---------------------|
| Property value changed | Base: `#fff`, Local: `#000`, Imported: `#4f46e5` | Manual (user choice) |
| Property added locally | Local adds `:new-prop`, Imported doesn't have it | Keep local (accept local) |
| Property added remotely | Imported adds `:new-prop`, Local doesn't have it | Accept imported |
| Property deleted locally | Base has `:prop`, Local deleted it, Imported changed it | Manual (restore or delete?) |
| Class conflict | Base: `btn`, Local: `btn custom`, Imported: `btn primary` | Merge (combine classes) |
| Token conflict | Base: `$colors.primary`, Local changed token, Imported changed value | Manual (which takes precedence?) |

**Auto-Resolution Strategies:**

```clojure
;; Non-conflicting changes → auto-merge
{:local-added [:new-prop]
 :imported-added [:other-prop]}
→ Merge both (accept all)

;; Same change on both sides → no conflict
{:local {:background "#fff"}
 :imported {:background "#fff"}}
→ Accept (no action needed)

;; Metadata hints precedence
{:local {:explicit? true}
 :imported {:inherited? true}}
→ Accept local (explicit wins over inherited)
```

**Output:**

```clojure
;; Reconciliation result
{:merged-project {:button {...}}  ; Merged result
 :conflicts []                     ; No conflicts (all auto-resolved)
 :auto-resolved [{:type :property-added-both-sides
                  :resolution :merged}]
 :manual-review []}                ; No manual review needed

;; Conflict report (Markdown)
"
# Reconciliation Report

## Summary
- Conflicts detected: 3
- Auto-resolved: 2
- Require manual review: 1

## Manual Review Required

### 1. Button background color (path: [:button :background])
- **Base**: `$colors.primary`
- **Local**: `$colors.accent` (changed locally)
- **Imported**: `#4f46e5` (changed in WordPress)

**Suggested resolution**: Accept local (explicit token reference)
**Alternative**: Accept imported (designer's choice)
"
```

#### Tests

```clojure
(deftest test-detect-conflicts-property-changed-both-sides
  ;; Both local and imported changed same property → conflict
  ...)

(deftest test-detect-no-conflict-same-change
  ;; Both made same change → no conflict
  ...)

(deftest test-auto-resolve-non-conflicting
  ;; Different properties changed → auto-merge
  ...)

(deftest test-auto-resolve-metadata-precedence
  ;; Use metadata hints to auto-resolve
  ...)

(deftest test-three-way-merge-success
  ;; Successful 3-way merge with auto-resolution
  ...)

(deftest test-three-way-merge-with-conflicts
  ;; Merge with conflicts requiring manual review
  ...)

(deftest test-generate-conflict-report
  ;; Generate human-readable Markdown report
  ...)

(deftest test-apply-resolution
  ;; Apply user resolutions to merged project
  ...)
```

**Deliverables:**
- [ ] `forma/src/forma/project/reconciliation.clj` (~500 LOC)
- [ ] Phase 4.4 tests in `phase4_hierarchy_tests.clj` (~250 LOC)
- [ ] Reconciliation workflow documentation
- [ ] Conflict resolution guide

---

## Testing Strategy

### Test Structure

**Main test file:** `forma/src/forma/dev/phase4_hierarchy_tests.clj`

Test organization:
```clojure
(ns forma.dev.phase4-hierarchy-tests
  "Phase 4: Hierarchy Reconstruction test suite"
  (:require [clojure.test :refer [deftest is testing]]
            [forma.hierarchy.classifier :as classifier]
            [forma.tokens.registry :as registry]
            [forma.hierarchy.generator :as generator]
            [forma.project.reconciliation :as reconcile]))

;; Phase 4.1: Property Classification (10 tests)
(deftest test-classify-property-by-frequency ...)
(deftest test-classify-property-by-variance ...)
(deftest test-classify-property-with-metadata ...)
;; ... 7 more tests

;; Phase 4.2: Token Reverse Lookup (8 tests)
(deftest test-build-token-registry-from-metadata ...)
(deftest test-reverse-lookup-token-unique ...)
;; ... 6 more tests

;; Phase 4.3: Multi-File Generation (10 tests)
(deftest test-generate-global-defaults ...)
(deftest test-generate-component-definitions ...)
;; ... 8 more tests

;; Phase 4.4: Project Reconciliation (12 tests)
(deftest test-detect-conflicts-property-changed-both-sides ...)
(deftest test-three-way-merge-success ...)
;; ... 10 more tests

;; Integration Tests (10 tests)
(deftest test-full-pipeline-html-to-project ...)
(deftest test-full-pipeline-jsx-to-project ...)
;; ... 8 more tests

;; Total: 50 tests expected
```

### Integration Testing

End-to-end tests for complete workflow:

```clojure
(deftest test-full-pipeline-html-to-project
  "Test complete pipeline: HTML → Flattened EDN → Multi-file project"
  (let [html "<button class='btn btn-primary' style='padding: 1rem'>Submit</button>"

        ;; 1. Parse HTML
        parsed (parsers/parse-html html {:mode :sync})

        ;; 2. Extract metadata
        metadata (sync/extract-metadata-from-attributes parsed)

        ;; 3. Classify properties
        classified (classifier/classify-element-properties parsed metadata)

        ;; 4. Build token registry
        tokens (registry/build-token-registry parsed metadata)

        ;; 5. Generate files
        files (generator/generate-multi-file-project classified tokens "imported-site")

        ;; 6. Verify output
        global-defaults (get files "default/global/defaults.edn")
        button-def (get files "default/components/button.edn")
        home-page (get files "projects/imported-site/pages/home.edn")]

    ;; Assertions
    (is (contains? global-defaults :tokens))
    (is (contains? (:tokens global-defaults) :spacing))
    (is (= "1rem" (get-in global-defaults [:tokens :spacing :md])))

    (is (contains? button-def :button))
    (is (contains? (:button button-def) :base))
    (is (some #(= "btn" %) (get-in button-def [:button :base])))

    (is (contains? home-page :home))
    (is (= "Submit" (get-in home-page [:home :content 0 2])))))
```

### Test Data

Create realistic test fixtures:
```clojure
;; test/forma/fixtures/phase4/sample-html.html
<div class="dashboard">
  <header class="header">
    <button class="btn btn-primary" style="padding: 1rem">Submit</button>
  </header>
  <main>
    <div class="card" style="background: #4f46e5; padding: 2rem">
      <h1 style="color: #fff">Dashboard</h1>
    </div>
  </main>
</div>

;; test/forma/fixtures/phase4/sample-project/ (for reconciliation tests)
default/
└── global/
    └── defaults.edn
```

---

## Success Criteria

### Phase 4.1 Complete When:
- [ ] Property classification heuristics implemented
- [ ] Metadata-driven classification working
- [ ] Usage statistics analysis working
- [ ] 10/10 tests passing
- [ ] Documentation with classification examples

### Phase 4.2 Complete When:
- [ ] Token registry built from metadata
- [ ] Token registry built from frequency analysis
- [ ] Reverse lookup working (unique + collisions)
- [ ] Token pattern detection working
- [ ] 8/8 tests passing
- [ ] Token reconstruction examples documented

### Phase 4.3 Complete When:
- [ ] Global defaults generation working
- [ ] Component definitions generation working
- [ ] Section/template generation working
- [ ] Page instances generation working
- [ ] Multi-file write to disk working
- [ ] 10/10 tests passing
- [ ] File structure documentation complete

### Phase 4.4 Complete When:
- [ ] Conflict detection working
- [ ] Auto-resolution strategies working
- [ ] 3-way merge working
- [ ] Conflict reports generated
- [ ] Resolution application working
- [ ] 12/12 tests passing
- [ ] Reconciliation workflow documented

### Phase 4 Complete When:
- [ ] All sub-phases complete (4.1-4.4)
- [ ] Integration tests passing (10/10)
- [ ] **Total: 50/50 tests passing**
- [ ] End-to-end examples documented
- [ ] Production-ready documentation
- [ ] Git committed with proper commit messages

---

## Timeline Estimate

| Phase | Duration | Dependencies |
|-------|----------|--------------|
| 4.1: Property Classification | 2-3 weeks | None (uses Phase 3 metadata) |
| 4.2: Token Registry | 2-3 weeks | 4.1 (classification informs tokens) |
| 4.3: Multi-File Generation | 3-4 weeks | 4.1 + 4.2 (classification + tokens) |
| 4.4: Project Reconciliation | 3-4 weeks | 4.3 (needs multi-file structure) |
| **Total** | **10-14 weeks (2.5-3.5 months)** | Sequential dependencies |

**Critical Path:** 4.1 → 4.2 → 4.3 → 4.4 (must be sequential)

**Parallel Opportunities:**
- Documentation can be written alongside development
- Test infrastructure can be built ahead of implementation

---

## Risks & Mitigations

### Risk 1: Classification Heuristics Inaccurate
**Impact:** Properties classified at wrong hierarchy level
**Mitigation:**
- Start with metadata-driven classification (high accuracy)
- Add heuristics incrementally, validate with real examples
- Provide manual override mechanism

### Risk 2: Token Collision (Multiple Tokens, Same Value)
**Impact:** Ambiguity in token reconstruction
**Mitigation:**
- Prefer metadata tokens (if available)
- Use semantic naming heuristics (color names, spacing scale)
- Provide alternatives, let user choose

### Risk 3: Reconciliation Conflicts Complex
**Impact:** Auto-resolution fails, too many manual conflicts
**Mitigation:**
- Start with conservative auto-resolution
- Provide clear conflict reports with context
- Build interactive resolution UI (future Phase 5?)

### Risk 4: Performance with Large Projects
**Impact:** Slow analysis of 1000+ element projects
**Mitigation:**
- Use streaming analysis where possible
- Cache intermediate results
- Add progress reporting for long operations

---

## Future Enhancements (Post-Phase 4)

### Phase 5 Ideas:
- **Interactive Resolution UI**: Web UI for conflict resolution
- **Smart Suggestions**: ML-based classification improvements
- **Pattern Detection**: Detect common component patterns automatically
- **Import Templates**: Pre-defined import rules for popular frameworks
- **Diff Visualization**: Visual diff tool for reconciliation

### Phase 6 Ideas:
- **Continuous Sync**: Watch for changes, auto-reconcile
- **Collaborative Editing**: Multi-user editing with conflict resolution
- **Version Control Integration**: Git-aware reconciliation
- **Import from Figma**: Design tool integration

---

## Related Documentation

- **[SESSION_STATE.md](../SESSION_STATE.md)** - Current project status
- **[CHANGELOG.md](../CHANGELOG.md)** - Development history
- **[Phase 3 Implementation](../CHANGELOG.md#L7-L210)** - Metadata system
- **[ARCHITECTURE.md](./ARCHITECTURE.md)** - Overall system architecture

---

**Last Updated:** 2025-01-12
**Status:** 🚧 Phase 4.1 starting
**Next Milestone:** Property classification heuristics implemented
