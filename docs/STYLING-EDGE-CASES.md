# Styling Edge Cases and Recommended Handling

This document enumerates edge cases for styling extension/stacking, inheritance, and CSS extraction, along with recommended behaviour and implementation notes.

---

## 1. Circular Styling Extension

**Problem**  
Styling systems can currently create a cycle (e.g., `tailwind` extends `shadcn-ui` and `shadcn-ui` extends `tailwind`), leading to infinite recursion.

**Recommendation**  
- Track the extension chain while loading styling systems.  
- Detect when a system name appears twice in the chain and throw a descriptive error.  
- Surface the chain to help the user resolve the cycle.

```clojure
(defn load-styling-system-with-cycle-detection [system-name visited ...]
  (when (contains? visited system-name)
    (throw (ex-info "Styling system cycle detected"
                    {:cycle (conj visited system-name)})))
  ...)
```

---

## 2. Empty Explicit Class (`:class ""`)

**Problem**  
An element may specify `{:class ""}`. Should this be interpreted as “explicitly no classes” or “no explicit override”?

**Recommendation**  
- Treat blank strings and whitespace-only strings as _no explicit override_.  
- After trimming, if the class string is empty, allow styling base/variant classes to apply.  
- Preserve explicit `[]` (vector) even if empty to distinguish explicit “no classes” once vector support exists.

Implementation detail: update the explicit-class check to use `clojure.string/blank?`.

---

## 3. Token Resolution Failures

**Problem**  
When a token reference (e.g., `"$typography.font-size.sm"`) cannot be resolved, the current behaviour is unclear.

**Recommendation**  
- Provide configurable fallback behaviour:
  - **Warn and remove** (default): log a warning and drop the property.
  - **Warn and passthrough**: keep the raw `$token.path` in output.
  - **Error**: throw with descriptive message.
- Expose fallback selection via project config (e.g., `:tokens {:on-missing :warn-remove}`).

---

## 4. Configuration Precedence (Project vs Styling System vs Component)

**Problem**  
Multiple layers can define flags such as `:apply-base-when-explicit`. We need deterministic precedence.

**Recommendation**  
1. **Element override** (future: per-instance overrides)  
2. **Project config** (`projects/my-app/config.edn`)  
3. **Styling system global config** (`styles/foo.edn :styling-config`)  
4. **Component-specific config** within styling system  
5. **Default behaviour** (compiler fallback)

Document this precedence and implement helper functions to resolve settings with fallback.

---

## 5. Stacking Extended Systems (Duplicate Classes)

**Problem**  
Stacking a system that already extends another (e.g., stack `[:tailwind :shadcn-ui]` when `shadcn-ui` extends `tailwind`) can duplicate classes.

**Recommendation**  
- **Default to extension** for composite styling systems; treat stacking as opt-in for utility layers only.  
- Detect when a stacked system extends one already in the stack and either:
  1. Emit a warning and skip the duplicated base system automatically, _or_
  2. Require an explicit `:allow-stacking? true` override for that entry.
- When stacking utilities (e.g., Tailwind + animation helpers), deduplicate classes after concatenation using `distinct` while preserving order.
- Provide conflict diagnostics (see §11) when stacking composite systems is unavoidable.

---

## 6. Style Merging Edge Cases (Whitespace / Empty Strings)

**Problem**  
Merging explicit styles with extracted styles can create double semicolons or stray whitespace.

**Recommendation**  
- Trim trailing semicolons from both explicit and extracted styles before concatenation.  
- Ignore empty or whitespace-only styles.  
- Optionally ensure final output always ends with a single semicolon for consistency.

```clojure
(defn merge-styles [explicit extracted]
  (let [trimmed-explicit (-> explicit (or "") str/trim (str/replace #";\s*$" ""))
        trimmed-extracted (-> extracted (or "") str/trim (str/replace #";\s*$" ""))]
    (->> [trimmed-explicit trimmed-extracted]
         (remove str/blank?)
         (str/join "; ")
         str/trim)))
```

---

## 7. Conflicting Classes in Stacking

**Problem**  
Different systems may emit conflicting classes (e.g., `bg-blue-500` vs `bg-red-500`).

**Recommendation**  
- Document that class conflicts are resolved by CSS specificity/order.  
- Provide optional tooling to detect conflicts (e.g., warn when multiple `bg-*` classes exist).  
- Allow project config to opt-in to conflict warnings for production builds.

---

## 8. Multiple Variant Dimensions (`:variant`, `:size`, `:tone`, etc.)

**Problem**  
Styling systems often support multiple variant dimensions. All relevant dimensions must be applied in a predictable order.

**Recommendation**  
- Extend component styling schema to include explicit variant dimensions, e.g.:
  ```clojure
  {:variants {:intent {:primary [...]}
              :tone {:success [...]}}
   :variant-order [:intent :tone]}  ; new key describing application order
  ```
- Apply variant classes in the declared order.  
- Document defaults (if `:variant-order` missing, use vector order from EDN map or predefined sequence).

---

## 9. CSS Extraction Mixed Explicit/Inherit

**Problem**  
Elements may mix explicit CSS properties (`{:font-size "16px"}`) with inherited ones (`global/defaults`). Need clear behaviour when `:only-extract-explicit true`.

**Recommendation**  
- Track which props were set explicitly during inheritance resolution.  
- When `:only-extract-explicit` is enabled, extract only properties marked as explicit.  
- Provide per-property overrides (e.g., `:always-extract [:font-weight]`).

---

## 10. Inheritance Hierarchy + Explicit Style

**Problem**  
How should inherited CSS behave when an explicit `:style` attribute is present?

**Recommendation**  
- Default: explicit `:style` blocks inherited CSS extraction.  
- Optional: allow merging via config (`:merge-with-explicit-style true`).  
- Ensure merging uses the trimmed-style helper (see §6) to avoid formatting issues.

---

## 11. Style Provenance Tracing

**Problem**  
With multi-level inheritance, stacked systems, and multiple stylesheets, developers struggle to identify where each class or CSS property originated.

**Recommendation**  
- Build a “style provenance” tracker in the compilation pipeline (inspired by Oxygen’s selector registry and flattening logic):
  - Record the source file, hierarchy level, and styling system responsible for every class and CSS property.
  - Preserve resolution order (inheritance → styling extension → stacking → explicit overrides).
  - Capture overrides (what was replaced or removed) to help debug conflicts.
- Maintain a revision history for selectors/styles so teams can diff changes across builds (similar to Oxygen’s `oxy_selectors` history).
- Provenance must span **component/property inheritance** (global → component → section → template → instance) **and styling-system resolution** (extension, stacking, variants, utilities) so the final output can be traced end-to-end.
- Expose the provenance data through:
  - A CLI/dev-mode report for a given element path.
  - Optional inspector output (e.g., JSON alongside compiled HTML/CSS).
  - Integrations with dev tooling so teams can inspect provenance without leaving the editor.
- Highlight potential issues (conflicting class families, redundant overrides) using the recorded provenance.
- Allow teams to export provenance snapshots to compare styling changes across builds.
- Attach provenance metadata to sync payloads so external systems can reconcile hierarchy-aware styling changes (bi-directional sync).

This tool is especially valuable when multiple stylesheets or styling systems coexist, providing traceability that most tooling currently lacks.

---

## 12. External Sync Considerations

**Problem**  
Projects often sync styling to external systems (design tokens services, CMS themes). Without provenance, conflicts and drift are hard to manage.

**Recommendation**  
- Include provenance metadata in sync exports so receivers know the origin (hierarchy level, styling system, explicit override).  
- When importing updates, compare incoming provenance with current state to determine merge strategy (auto-merge, warn, or block).  
- Surface provenance diffs in tooling so teams review changes before applying them.  
- All sync endpoints should respect ad-hoc styling policies (strict mode can reject inline overrides; flexible mode can accept but log/extract them).

---

## 13. Comparison with Existing Tooling

**Observation**  
Current market offerings cover pieces of the problem space but not the full stack (hierarchical inheritance + styling provenance + policy enforcement).

**Summary**
- **Oxygen / Breakdance**: Strong class registry and selector revisions, but minimal inheritance. Provenance limited to selectors; ergonomics rely on manual class stacking.
- **Webflow / Framer / Editor X**: Excellent live feedback on current styles, yet no provenance history and limited structural inheritance.
- **Utility frameworks (Tailwind, shadcn-ui)**: Emphasise stacking utilities; no concept of hierarchical inheritance or provenance diagnostics.
- **Design token pipelines (Style Dictionary, Theo, Tokens Studio)**: Great token inheritance + revision history; stop short of component-level styling provenance.

**Forma’s differentiators**
- Multi-level inheritance (global → component → section → template → instance) with provenance capturing each override.
- Styling systems default to extension with opt-in stacking and conflict diagnostics.
- Style provenance spans inheritance and styling layers, enabling sync diffs, conflict warnings, and policy enforcement.
- Ad-hoc styling policy controls (strict vs flexible) with optional extraction to shared stylesheets.

Maintaining this comparison ensures the implementation remains differentiated and on par (or ahead) of the broader ecosystem.

---

## 14. AI / MCP Designer Tooling

**Problem**  
AI agents and designer applications need structured operations to manipulate EDN safely and explain styling provenance.

**Recommendation**  
- Expose machine-readable provenance and diff outputs (e.g., JSON) so MCP tools and LLMs can consume them.  
- Provide operations such as `describe-style`, `list-conflicts`, `update-styling-stack`, `apply-sync-diff`, and `validate-policy` for conversational agents.  
- Ensure designer UIs (web/desktop/mobile) call the same APIs or MCP endpoints, surfacing provenance overlays, conflict warnings, and policy status.  
- Enforce strict vs flexible styling policies at the API layer so automated tools cannot bypass governance.

---

## 15. Mobile / Offline Projects

**Problem**  
Teams may need offline-first workflows on tablets/phones while retaining provenance and policy guarantees.

**Recommendation**  
- Bundle a lightweight compiler plus provenance engine (native/WASM) for offline previews and diagnostics.  
- Cache EDN/provenance locally (encrypted at rest), queuing sync operations until connectivity returns.  
- Use incremental compilation and provenance diffing to keep the on-device UX responsive.  
- Reconcile edits via provenance-aware merge when syncing with upstream Git or API sources.

---

## 16. Security & E2EE

**Problem**  
Styling data and sync payloads may require end-to-end encryption across devices and services.

**Recommendation**  
- Encrypt EDN, provenance logs, and sync payloads client-side before transmission or commit (e.g., AES-GCM, libsodium).  
- Support per-project key management, including hardware-backed secure enclaves on mobile.  
- Allow API endpoints to store/relay encrypted blobs without inspecting plaintext.  
- Document CI/CD workflows for decrypting during builds and sharing keys securely among collaborators.

---

## Implementation Checklist

1. **Cycle detection** in `load-styling-system` / `load-platform-config`.  
2. **Explicit class handling** using `blank?`.  
3. **Token fallback** strategy selected via config.  
4. **Configuration precedence resolver** utility.  
5. **Stacking deduplication or documentation** regarding extended systems.  
6. **Style merge helper** to normalize concatenation.  
7. **Optional class conflict warnings** (future).  
8. **Variant dimension order** support in styling configs.  
9. **Explicit prop tracking** for CSS extraction.  
10. **Configurable explicit style merging** behaviour.  
11. **Style provenance tracker** (record + surface styling sources).  
12. **Selector/stylesheet revision history** for diffs and rollbacks.  
13. **Ad-hoc styling policy controls** (strict vs flexible + extract-to-stylesheet tooling).  
14. **Provenance-aware sync tooling** (export/import metadata, conflict detection UI).  
15. **Provenance UI/reporting surface** (CLI, inspector, dashboards).  
16. **Competitive comparison kept current** to validate differentiation.  
17. **AI / MCP tooling endpoints** (machine-readable provenance, editing operations).  
18. **Offline/mobile support** (bundled compiler, queued sync, caching).  
19. **E2EE support** (client-side encryption, key management workflows).

Each item can be implemented incrementally; prioritise cycle detection, explicit class handling, style merging, and explicit prop tracking to address current bugs.

---

## Future Tooling (Next.js-like dev environment, designer/dev app scope)

**Vision**  
A dedicated designer/dev environment that behaves like a Next.js-style runtime—fast dev server, hot reload, provenance-aware diagnostics—and can export or sync changes directly into production repositories.

**Out of current scope**  
This tooling will be tackled in a separate designer/dev app project once the core Forma stack (compiler, provenance, policies, sync APIs) is in place.

**Key capabilities (planned)**  
- Instant feedback: hot reload and incremental compile hooked to provenance diffing so designers see which hierarchy/styling layer changed.  
- Export/sync: one-click export to target stacks (HTML/JSX/HTMX/etc.) with provenance metadata, plus Git/API sync workflows.  
- Designer/dev UI: touch-friendly controls, provenance overlays, conflict warnings, and policy enforcement in the UI.  
- Automation hooks: MCP/AI integration built-in for conversational editing, conflict resolution, and token management.  
- Offline collaboration: optional bundled compiler/provenance engine with queued sync for mobile or low-connectivity environments.

