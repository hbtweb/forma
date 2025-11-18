# Dashboard Example Project

## Overview

This project demonstrates creating a complex website with HTML, CSS, and HTMX using Forma's declarative inheritance system.

## Files

- `original.html` - Hand-written HTML/CSS/HTMX reference
- `compiled.html` - Forma-compiled output (in progress)
- `build.clj` - Compilation script
- `config.edn` - Project configuration
- `global/tokens.edn` - Global tokens
- `components/` - Component definitions
- `sections/` - Section definitions
- `templates/` - Template definitions
- `pages/` - Page definitions

## Status

### ✅ Completed
- Original HTML/CSS/HTMX website created
- Project structure set up
- Global tokens defined
- Component definitions created
- Section definitions created
- Template definitions created
- Page definitions created

### ⚠️ In Progress
- Compilation script needs adjustment for nested element handling
- Need to verify full compilation output matches original

## Original Website Features

1. **Header Section**
   - Gradient background
   - Title and subtitle

2. **Stats Grid**
   - 4 stat cards in responsive grid
   - Hover effects

3. **Content Grid**
   - Main content area with table
   - Sidebar with quick actions

4. **HTMX Interactivity**
   - `hx-get="/api/refresh"` with target and swap
   - `hx-post="/api/create"` with target and swap
   - `hx-get="/api/export"`
   - `hx-delete="/api/clear"` with confirmation

5. **CSS Styling**
   - Modern gradient header
   - Card-based layout
   - Responsive grid
   - Hover transitions
   - Badge variants (success/warning/danger)

## Next Steps

1. Fix compilation script to handle nested elements correctly
2. Verify HTMX attributes are preserved
3. Compare compiled output with original
4. Document any differences

## Usage

```bash
# Build the project
clj -M projects/dashboard-example/build.clj

# Compare outputs
diff original.html compiled.html
```

