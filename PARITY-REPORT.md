# Forma Compiler Parity Report

## Summary
**35/40 tests passing (87.5%)**

## Test Results

### ✅ Passing (35 tests)
- **Element Parsing**: 3/4 (75%)
  - ✓ Vector syntax parsing
  - ✓ Map syntax parsing  
  - ✓ String element parsing
  - ✗ Tag with classes/id parsing (needs enhancement)

- **Property Expansion**: 3/3 (100%)
  - ✓ Basic property shortcuts (:bg, :pd, :mg)
  - ✓ Text shortcut (:txt)
  - ✓ URL shortcut (:url → :href)

- **Element Compilation**: 23/24 (96%)
  - ✓ All standard HTML elements (h1-h6, p, div, span, button, btn, link, input, textarea, select, img, video)
  - ✓ All legacy elements (container, text, image, divider, spacer, columns)
  - ✓ Heading with level
  - ✗ One element type (needs investigation)

- **Nested Structures**: 3/3 (100%)
  - ✓ Simple nested structure
  - ✓ Deep nested structure
  - ✓ Multiple children

- **Missing Features Check**: 3/3 (100%)
  - ✓ Tag with classes (basic support)
  - ✓ Oxygen element: :section
  - ✓ Oxygen element: :container

### ❌ Failing (5 tests)

1. **Tag Parsing with Classes/ID** (1 test)
   - **Issue**: Old compiler supports `:div.card#main` syntax that parses classes and ID from tag
   - **Current**: Forma only supports basic tag parsing
   - **Fix Needed**: Enhance `parse-element` to support tag.class#id syntax

2. **Template Resolution** (1 test)
   - **Issue**: Old compiler uses `{{variable.name}}` syntax for template variables
   - **Current**: Forma has `resolve-vars` but may not be fully integrated
   - **Fix Needed**: Verify template resolution works with `{{}}` syntax

3. **Style Conversion** (2 tests)
   - **Issue 1**: Inline styles not appearing in HTML output
     - **Current**: `element-styles` function exists but styles may not be applied correctly
     - **Fix Needed**: Ensure styles are merged into element attributes
   
   - **Issue 2**: HTMX attributes not appearing in HTML output
     - **Current**: `element-styles` extracts HTMX attributes but they may be lost
     - **Fix Needed**: Ensure HTMX attributes pass through compilation pipeline

## Key Differences from Old Compiler

### Architecture Changes
1. **Universal Compiler Pipeline**: Forma uses `kora.core.compiler` with standardized pipeline
2. **Inheritance System**: Forma uses `kora.core.inheritance` for property resolution
3. **Token System**: Forma uses `kora.core.tokens` for token resolution
4. **Resource Structure**: Resources moved from `resources/corebase/dsl/ui/` to `forma/resources/forma/`

### Feature Parity Status

| Feature | Old Compiler | Forma Compiler | Status |
|---------|-------------|----------------|--------|
| Vector syntax | ✅ | ✅ | ✅ |
| Map syntax | ✅ | ✅ | ✅ |
| Property shortcuts | ✅ | ✅ | ✅ |
| HTMX attributes | ✅ | ⚠️ | Needs fix |
| Inline styles | ✅ | ⚠️ | Needs fix |
| Template variables | ✅ | ⚠️ | Needs verification |
| Tag.class#id syntax | ✅ | ❌ | Missing |
| Element registry | ✅ | ✅ | ✅ |
| Custom elements | ✅ | ⚠️ | Partial |

## Recommended Fixes

### Priority 1: Critical Features
1. **HTMX Attributes**: Ensure HTMX attributes pass through compilation pipeline
2. **Inline Styles**: Fix style conversion to properly output CSS in HTML

### Priority 2: Important Features  
3. **Template Resolution**: Verify and fix `{{variable}}` syntax support
4. **Tag Parsing**: Add support for `:tag.class#id` syntax

### Priority 3: Nice to Have
5. **Custom Elements**: Verify all RepairShopr custom elements work
6. **Oxygen Registry**: Ensure all Oxygen registry elements are supported

## Next Steps

1. Fix HTMX attribute preservation
2. Fix inline style output
3. Add tag.class#id parsing
4. Verify template resolution
5. Re-run parity tests

