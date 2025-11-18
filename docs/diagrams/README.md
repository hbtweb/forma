# Architecture Diagrams

This directory contains D2 diagrams documenting the compiler, inheritance, and token systems for both the new Kora Core architecture and the old Corebase UI compiler.

## Diagrams

### Compiler Systems

1. **`kora-core-compiler.d2`** - Universal compiler pipeline architecture
   - Shows the `CompilerPipeline` protocol
   - 6-step pipeline: Translate → Parse → Expand → Resolve → Transform → Output
   - Application implementations (FormaCompiler, InterludeCompiler)
   - Dependencies on inheritance, tokens, and translation systems

2. **`old-compiler.d2`** - Legacy Corebase UI compiler architecture
   - Direct compilation flow without formal pipeline
   - Property expansion (shortcuts and features)
   - Template variable resolution
   - Element registry system
   - Legacy and custom elements

### Inheritance Systems

3. **`kora-inheritance.d2`** - Universal inheritance resolution system
   - Multi-level hierarchy resolution (Global → Components → Sections → Templates → Pages)
   - Deep merge algorithm
   - Priority-based property merging
   - Works for any hierarchy (Forma, Interlude, etc.)

4. **`old-inheritance.d2`** - Old compiler's approach
   - **No formal inheritance system**
   - Direct property expansion
   - Element registry as closest equivalent to defaults
   - Simple property processing

### Token Systems

5. **`kora-tokens.d2`** - Token reference system
   - `$token.path` syntax for property references
   - Fallback support: `$token.path || fallback`
   - Recursive resolution for nested structures
   - Token validation
   - Works with any property type

6. **`old-tokens.d2`** - Template variable system
   - `{{variable.name}}` syntax for text interpolation only
   - Context flattening
   - String replacement approach
   - Limited to text content

## Viewing the Diagrams

### Using D2 CLI

```bash
# Install D2 (if not already installed)
# See: https://d2lang.com/tour/install

# Generate SVG
d2 forma/docs/diagrams/kora-core-compiler.d2 forma/docs/diagrams/kora-core-compiler.svg

# Generate PNG
d2 forma/docs/diagrams/kora-core-compiler.d2 forma/docs/diagrams/kora-core-compiler.png

# Generate PDF
d2 forma/docs/diagrams/kora-core-compiler.d2 forma/docs/diagrams/kora-core-compiler.pdf
```

### Using D2 Playground

1. Copy the contents of any `.d2` file
2. Paste into [D2 Playground](https://play.d2lang.com/)
3. View and export the diagram

### Using VS Code Extension

1. Install the "D2" extension for VS Code
2. Open any `.d2` file
3. Use the preview command to view the diagram

## Key Architectural Differences

### Compiler Architecture

| Aspect | Old Compiler | Kora Core Compiler |
|--------|-------------|-------------------|
| **Structure** | Monolithic functions | Protocol-based pipeline |
| **Extensibility** | Hard to extend | Easy to extend via protocol |
| **Pipeline** | Direct compilation | 6-step universal pipeline |
| **Reusability** | UI-specific | Universal (works for any domain) |

### Inheritance

| Aspect | Old Compiler | Kora Core Inheritance |
|--------|-------------|---------------------|
| **System** | None (direct expansion) | Multi-level hierarchy |
| **Levels** | N/A | Configurable (e.g., [:global :components :sections :templates :pages]) |
| **Priority** | N/A | Explicit priority ordering |
| **Merge** | N/A | Deep merge algorithm |

### Tokens/Templates

| Aspect | Old System | Kora Core Tokens |
|--------|-----------|-----------------|
| **Syntax** | `{{variable.name}}` | `$token.path` |
| **Scope** | Text only | Any property type |
| **Fallback** | No | Yes (`$token.path \|\| fallback`) |
| **Nested** | Limited | Full recursive support |
| **Validation** | No | Yes (validate-tokens) |

## Diagram Structure

Each diagram includes:
- **Title**: Clear identification
- **Main Components**: Functions, classes, protocols
- **Data Flow**: Input → Processing → Output
- **Connections**: Relationships between components
- **Styling**: Color coding for different types of components

## Notes

- Diagrams use D2 syntax for maximum compatibility
- All diagrams are self-contained and can be viewed independently
- Colors are used to distinguish different component types:
  - Blue: Core functions/protocols
  - Green: Data/context
  - Purple: Registry/database
  - Orange: Processing steps
  - Yellow: Notes/comparisons

