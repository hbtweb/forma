# Oxygen Native Properties Strategy

**Date:** 2025-01-17
**Goal:** Compile Forma/Mesh → Pure Oxygen properties (no external CSS)

---

## ✅ What Oxygen Supports Natively

### Confirmed Working (from footer + test pages):

1. **Layout** ✅
   - Grid layouts (`layout_v2.layout = "grid"`)
   - Flexbox vertical/horizontal
   - Column counts, gaps

2. **Spacing** ✅
   - Padding (all sides, individual control)
   - Margin (including `"auto"`)

3. **Typography** ✅
   - Font size, weight, color
   - Text alignment

4. **Borders** ✅
   - Border width, style, color
   - Border radius (all corners)

5. **Colors** ✅
   - Solid background colors
   - Text colors
   - Border colors

6. **Shadows** ✅
   - Box shadow with full control (x, y, blur, spread, color)

7. **Sizing** ✅
   - Width, height, min/max
   - Units: px, %, vh, vw

---

## ❓ Unknown/Needs Testing

### Gradients
**Question:** Does Oxygen support gradient backgrounds natively?

**Options:**
1. Check if `background` property accepts gradient strings
2. Use Oxygen's class/selector system
3. Fall back to solid colors if unsupported

**Test needed:**
```clojure
{:design {:background {:gradient "linear-gradient(...)"}}}
;; OR
{:design {:background {:type "gradient" :value "..."}}}
```

### Hover States
**Question:** Does Oxygen support `:hover` in properties?

**Options:**
1. Use Oxygen's selector system (`design.hover`)
2. Use Oxygen's interaction states
3. Fall back to no hover effects

### Transitions/Animations
**Question:** Does Oxygen support transitions in properties?

**Options:**
1. Check `effects.transition`
2. Use Oxygen's animation system
3. Fall back to instant changes

---

## 🎯 Forma Compilation Strategy

### Phase 1: Core Properties (WORKING)
Compile to proven Oxygen properties:
- ✅ Layouts (grid, flex)
- ✅ Spacing (padding, margin)
- ✅ Typography (size, weight, color, align)
- ✅ Borders (width, style, color, radius)
- ✅ Shadows (box-shadow)
- ✅ Sizing (width, height, min/max)

### Phase 2: Advanced Features (TEST & FALLBACK)
Test Oxygen support, fallback if needed:
- 🔄 Gradients → Test native support, fallback to solid color
- 🔄 Hover states → Test native support, fallback to static
- 🔄 Transitions → Test native support, fallback to instant

### Phase 3: Platform Extensions (IF NEEDED)
Only if Oxygen doesn't support critical features:
- Use CssCode for unsupported CSS (last resort)
- Document what requires extension

---

## 📋 Implementation Plan

### 1. Test Oxygen's Advanced Features
Create test page with:
```clojure
;; Test 1: Gradient background
{:design {:background {:gradient "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"}}}

;; Test 2: Hover state
{:design {:hover {:transform "translateY(-4px)"}}}

;; Test 3: Transition
{:design {:effects {:transition "all 0.3s ease"}}}
```

### 2. Update oxygen_mapper.clj
Add support for discovered features:
```clojure
(defn map-background [bg]
  (cond
    (str/starts-with? bg "linear-gradient")
    {:gradient bg}  ; If Oxygen supports it

    :else
    {:color bg}))   ; Fallback to solid color
```

### 3. Deploy Pure Oxygen Homepage
Compile Mesh → Oxygen using ONLY native properties:
- No external CSS
- No Tailwind
- Pure Oxygen property schema

---

## 🚀 Benefits of This Approach

1. **Platform Native** - Uses Oxygen's visual editor features
2. **Maintainable** - Users can edit in Oxygen UI
3. **Predictable** - No CSS conflicts or specificity issues
4. **Forma's Vision** - Universal compilation to native formats

---

## 📝 Next Steps

1. **Test gradient support** - Deploy page with gradient in `background` property
2. **Test hover support** - Deploy page with hover effects in `design.hover`
3. **Test transition support** - Deploy page with transitions
4. **Update mapper** - Add discovered features to oxygen_mapper.clj
5. **Deploy Mesh homepage** - Using pure Oxygen properties

---

**Goal:** Forma should compile to each platform's native format, not inject external CSS unless absolutely necessary!