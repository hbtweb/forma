# Foundation Implementation Plan

## Overview

Before exposing a public API or building designer/dev tooling, we need to finish the core Forma runtime. This plan covers the foundational modules and behaviours that must be implemented next. Once these are complete, the stack will provide reliable provenance, policy enforcement, diagnostics, and sync capabilities for higher-level tooling.

## Modules to Implement

### 1. Provenance (`src/forma/provenance/`)
- Record hierarchy + styling lineage for every element.
- Maintain revision history (snapshots/diffs) for auditing.
- Expose machine-readable reports (EDN/JSON) and CLI helpers.

### 2. Policy (`src/forma/policy/`)
- Strict vs flexible mode enforcement for ad-hoc styling.
- Inline extraction tooling (move inline styles into the styling system when allowed).
- Central policy configuration lookups.

### 3. Diagnostics (`src/forma/diagnostics/`)
- Stacking conflict detection (duplicate class families, extended stacks).
- Variant dimension validator (ensure order and completeness).
- Warning generators that feed provenance/uI tooling.

### 4. Configuration Precedence (`src/forma/config/resolver.clj`)
- Deterministic override resolution (element > project > styling global > component > default).
- Helpers reused by compiler, policy, diagnostics, and sync.

### 5. Styling Updates
- Style merge helper (trim semicolons/whitespace).
- Explicit prop tracking for inheritance vs inline values.
- Token fallback strategies (warn/remove, passthrough, error).
- Variant dimension ordering support in styling configs.

### 6. Sync (`src/forma/sync/`)
- Export/import workflows including provenance metadata.
- Conflict resolution hooks (provenance-aware merges).
- Optional E2EE wrappers for payloads (planning groundwork even if full E2EE comes later).

## Supporting Tasks
- Update `forma/compiler` to integrate provenance recording, policy enforcement, diagnostics, and improved style merging.
- Extend optimisation pipeline (`forma/optimization`) to generate explicit prop tracking data.
- Provide machine-readable provenance outputs for future MCP tooling.
- Update documentation (`IMPLEMENTATION-STATUS.md`, `STYLING-EDGE-CASES.md`) as modules ship.

## Testing Strategy
- Unit tests for each new namespace (provenance, policy, diagnostics, config resolver, style merge helper).
- Integration tests covering strict vs flexible policies, stacking warnings, provenance snapshots.
- Golden tests for sync export/import to keep payloads stable.

## Deferred Work (Future Tooling)
- MCP/AI tooling endpoints.
- Offline/mobile bundling (incremental compiler, caching, queued sync).
- Full E2EE workflows and key management docs.
- Next.js-like designer/dev environment.

