# CRITICAL DISCOVERY: Oxygen Property Schema

**Date:** 2025-01-17
**Issue:** Styles not rendering correctly

---

## ❌ The Problem

I was sending **raw CSS properties** to Oxygen:
```clojure
:design {:background "linear-gradient(...)"
         :padding "80px 20px"
         :grid-template-columns "repeat(4, 1fr)"}
```

But Oxygen **has its own property schema** and doesn't accept raw CSS!

---

## ✅ The Solution: Oxygen's Property Format

### Working Footer (ID: 47) Uses:

```json
{
  "design": {
    "sizing": {
      "maxWidth": {"number": 1400, "unit": "px"}
    },
    "layout_v2": {
      "layout": "grid",    // ← "grid", not "display: grid"!
      "grid": {
        "columnCount": 4,  // ← columnCount, not grid-template-columns!
        "columnGap": {"number": 24, "unit": "px"},
        "rowGap": {"number": 16, "unit": "px"}
      }
    },
    "spacing": {
      "padding": {
        "left": {"number": 16, "unit": "px"},
        "right": {"number": 16, "unit": "px"},
        "top": {"number": 24, "unit": "px"},
        "bottom": {"number": 24, "unit": "px"}
      },
      "margin": {
        "left": "auto",
        "right": "auto"
      }
    }
  }
}
```

---

## 📋 Oxygen Property Schema

### Layout (`design.layout_v2`)

**Options:**
```json
{
  "layout": "vertical" | "horizontal" | "grid" | "columns",

  // If layout="grid":
  "grid": {
    "columnCount": 4,
    "columnGap": {"number": 24, "unit": "px"},
    "rowGap": {"number": 16, "unit": "px"}
  },

  // Alignment:
  "align-items": "flex-start" | "center" | "flex-end" | "stretch",
  "justify-content": "flex-start" | "center" | "flex-end" | "space-between"
}
```

### Spacing (`design.spacing`)

```json
{
  "padding": {
    "top": {"number": 16, "unit": "px"},
    "right": {"number": 16, "unit": "px"},
    "bottom": {"number": 16, "unit": "px"},
    "left": {"number": 16, "unit": "px"}
  },
  "margin": {
    "top": {"number": 16, "unit": "px"},
    // ... or "auto"
  }
}
```

### Sizing (`design.sizing`)

```json
{
  "maxWidth": {"number": 1400, "unit": "px"},
  "minHeight": {"number": 100, "unit": "vh"},
  "width": {"number": 100, "unit": "%"}
}
```

### Typography (`design.typography`)

```json
{
  "size": {"number": 16, "unit": "px"},
  "weight": "400" | "600" | "700",
  "color": "#1f2937",
  "text-align": "left" | "center" | "right"
}
```

### Backgrounds (`design.background`)

**Colors:**
```json
{
  "color": "#667eea"
}
```

**Gradients:** (Need to check if supported - may need custom CSS!)

### Borders (`design.borders`)

```json
{
  "border": {
    "top": {
      "width": {"number": 1, "unit": "px"},
      "style": "solid",
      "color": "#e5e7eb"
    }
  },
  "radius": {
    "topLeft": {"number": 8, "unit": "px"},
    // ...
  }
}
```

### Effects (`design.effects`)

```json
{
  "boxShadow": [
    {
      "x": {"number": 0, "unit": "px"},
      "y": {"number": 4, "unit": "px"},
      "blur": {"number": 12, "unit": "px"},
      "spread": {"number": 0, "unit": "px"},
      "color": "rgba(0,0,0,0.1)"
    }
  ],
  "opacity": {"number": 0.9}
}
```

### Hover States (`design.hover`)

**NOT SURE IF SUPPORTED IN PROPERTIES!**
May need to use Oxygen's selector system instead.

---

## 🔧 What Needs to Change

1. **Grid Layouts:**
   - ❌ `:grid-template-columns "repeat(4, 1fr)"`
   - ✅ `:layout "grid" :grid {:columnCount 4}`

2. **Padding (as string):**
   - ❌ `:padding "80px 20px"`
   - ✅ `:padding {:top {:number 80 :unit "px"} :left {:number 20 :unit "px"} ...}`

3. **Gradients:**
   - ❌ `:background "linear-gradient(...)"`
   - ✅ May need custom CSS or Oxygen selector

4. **Hover States:**
   - ❌ `:hover {:transform "translateY(-4px)"}`
   - ✅ Need to use Oxygen's selector/custom CSS system

---

## 🚀 Next Steps

1. Create correct property mapping (Tailwind → Oxygen schema)
2. Redeploy homepage with proper Oxygen properties
3. Verify rendering in browser
4. Document complete property schema

---

## 📝 Key Insight

**Oxygen is NOT a direct CSS compiler!**

It has its own **abstraction layer** with a structured property schema. We need to:
1. Parse Mesh/Tailwind properties
2. Map to Oxygen's schema
3. Send Oxygen-formatted properties
4. Let Oxygen generate the CSS

This is actually **better** for bidirectional sync - Oxygen's schema is more predictable than raw CSS!
