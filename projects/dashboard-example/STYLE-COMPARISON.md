# Style Transfer Comparison - Original vs Compiled

## Major Differences Found

### 1. ❌ Header Element Structure
**Original:**
```html
<header class="header">
    <h1>Dashboard</h1>
    <p>Welcome back! Here's what's happening today.</p>
</header>
```

**Compiled:**
```html
<div class=""><div><h1>Dashboard</h1><p>Welcome back! Here's what's happening today.</p></div>
```

**Issues:**
- ❌ `<header>` element is compiled as `<div>` (semantic HTML lost)
- ❌ Missing `class="header"` on the header element
- ❌ Extra wrapper div added

### 2. ❌ Table Structure Completely Missing
**Original:**
```html
<table class="table">
    <thead>
        <tr>
            <th>User</th>
            <th>Action</th>
            <th>Status</th>
            <th>Time</th>
        </tr>
    </thead>
    <tbody>
        <tr>
            <td>John Doe</td>
            <td>Order #1234</td>
            <td><span class="badge badge-success">Completed</span></td>
            <td>2 min ago</td>
        </tr>
        ...
    </tbody>
</table>
```

**Compiled:**
```html
<div><div><div><div>User</div><div>Action</div><div>Status</div><div>Time</div></div></div><div><div><div>John Doe</div><div>Order #1234</div><div><span class="badge badge-success">Completed</span></div><div>2 min ago</div></div>...
```

**Issues:**
- ❌ `<table>` element is compiled as `<div>` (semantic HTML lost)
- ❌ Missing `class="table"` on table element
- ❌ `<thead>`, `<tbody>`, `<tr>`, `<th>`, `<td>` all compiled as `<div>` (semantic HTML lost)
- ❌ Table structure completely flattened

### 3. ❌ Missing Inline Styles on Buttons
**Original:**
```html
<button class="btn" style="width: 100%; margin-bottom: 0.5rem;" 
        hx-post="/api/create" hx-target="body" hx-swap="beforeend">
    Create New
</button>
<button class="btn btn-secondary" style="width: 100%; margin-bottom: 0.5rem;"
        hx-get="/api/export" hx-trigger="click">
    Export Data
</button>
<button class="btn btn-secondary" style="width: 100%;"
        hx-delete="/api/clear" hx-confirm="Are you sure?">
    Clear Cache
</button>
```

**Compiled:**
```html
<button class="inline-flex items-center justify-center rounded-md text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 btn" hx-post="/api/create" hx-swap="beforeend" hx-target="body" style="font-size:14px; font-weight:500; padding:; border-radius:4px; transition:all 0.2s">Create New</button>
```

**Issues:**
- ❌ Missing `style="width: 100%; margin-bottom: 0.5rem;"` inline styles
- ❌ Missing `hx-trigger="click"` on Export Data button
- ❌ Buttons have shadcn-ui classes added (which may conflict with original `.btn` styles)
- ❌ Inline styles from token resolution have empty values: `padding:;` (should be removed or filled)

### 4. ⚠️ Button Classes - Shadcn-UI Applied
**Original:**
```html
<button class="btn" ...>
<button class="btn btn-secondary" ...>
```

**Compiled:**
```html
<button class="inline-flex items-center justify-center rounded-md text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 btn" ...>
```

**Issues:**
- ⚠️ Shadcn-ui base classes are being automatically applied to buttons
- ⚠️ This may conflict with the original `.btn` CSS styles
- ⚠️ Original buttons use simple classes, compiled adds Tailwind classes

### 5. ⚠️ Inline Styles from Token Resolution
**Compiled buttons have:**
```html
style="font-size:14px; font-weight:500; padding:; border-radius:4px; transition:all 0.2s"
```

**Issues:**
- ⚠️ `padding:;` has empty value (should be removed or have a value)
- ⚠️ These styles come from token resolution, but original had no inline styles on buttons (except width/margin)

## Root Causes

### 1. Missing HTML Element Definitions
- `:header` element may not be defined in `html.edn`, so it defaults to `div`
- `:table`, `:thead`, `:tbody`, `:tr`, `:th`, `:td` elements may not be defined, so they default to `div`

### 2. Inline Styles Not Preserved
- Inline `style` attributes from the original EDN are not being preserved through compilation
- Token resolution is adding inline styles, but original inline styles are being lost

### 3. Styling System Auto-Application
- `apply-styling` is automatically applying shadcn-ui classes to buttons
- This may not be desired when the original design uses custom CSS classes

### 4. Token Resolution Issues
- Token resolution is creating inline styles with empty values (`padding:`)
- These should either be removed or filled with actual values

## Recommendations

1. **Add missing HTML element definitions** to `html.edn`:
   - `:header` → `<header>` element
   - `:table` → `<table>` element
   - `:thead` → `<thead>` element
   - `:tbody` → `<tbody>` element
   - `:tr` → `<tr>` element
   - `:th` → `<th>` element
   - `:td` → `<td>` element

2. **Preserve inline styles** from original props:
   - Inline `style` attributes should be preserved through compilation
   - Merge with token-resolved styles, don't replace

3. **Make styling system optional**:
   - Allow disabling auto-application of styling system classes
   - Or make it conditional based on context/styling-system setting

4. **Fix token resolution**:
   - Remove empty CSS properties (`padding:` → remove or fill)
   - Only add inline styles if tokens actually resolve to values

5. **Preserve original classes**:
   - Don't override original classes with styling system classes
   - Merge them instead

