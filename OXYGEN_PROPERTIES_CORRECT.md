# Oxygen Native Properties - CORRECT Analysis

**Date:** 2025-01-17
**Source:** Page 59 (user-verified)

---

## ✅ WHAT WORKS

### Gradients: Structured Format ONLY (1c, 1d)

**❌ Test 1a: Simple String - DOES NOT WORK**
```json
{
  "background": {
    "gradient": "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
  }
}
```
**Status:** ❌ Does not render

---

**⚠️ Test 1b: String + Type - RENDERS BUT NOT EDITABLE**
```json
{
  "background": {
    "gradient": "linear-gradient(135deg, #667eea 0%, #764ba2 100%)",
    "type": "gradient"
  }
}
```
**Status:** ⚠️ Renders but breaks in Oxygen editor (overwrites on edit)
**Use Case:** Read-only deployment only

---

**✅ Test 1c: Structured Linear Gradient - WORKS**
```json
{
  "background": {
    "gradient": {
      "points": [
        {
          "left": 0,
          "red": 102,
          "green": 126,
          "blue": 234,
          "alpha": 1
        },
        {
          "left": 100,
          "red": 118,
          "green": 75,
          "blue": 162,
          "alpha": 1
        }
      ],
      "type": "linear",
      "degree": 135,
      "svgValue": "<linearGradient x1=\"0.146\" y1=\"0.146\" x2=\"0.854\" y2=\"0.854\" id=\"%%GRADIENTID%%\"><stop stop-opacity=\"1\" stop-color=\"#667eea\" offset=\"0\"></stop><stop stop-opacity=\"1\" stop-color=\"#764ba2\" offset=\"1\"></stop></linearGradient>",
      "value": "linear-gradient(135deg,rgba(102, 126, 234, 1) 0%,rgba(118, 75, 162, 1) 100%)"
    },
    "type": "gradient"
  }
}
```
**Status:** ✅ WORKS - Renders AND editable in Oxygen
**Required Format:** Full structured object with points array

---

**✅ Test 1d: Structured Radial Gradient - WORKS**
```json
{
  "background": {
    "gradient": {
      "points": [
        {
          "left": 0,
          "red": 0,
          "green": 0,
          "blue": 0,
          "alpha": 1
        },
        {
          "left": 100,
          "red": 255,
          "green": 0,
          "blue": 0,
          "alpha": 1
        }
      ],
      "type": "radial",
      "degree": 0,
      "svgValue": "<radialGradient id=\"%%GRADIENTID%%\"><stop stop-opacity=\"1\" stop-color=\"#000000\" offset=\"0\"></stop><stop stop-opacity=\"1\" stop-color=\"#ff0000\" offset=\"1\"></stop></radialGradient>",
      "value": "radial-gradient(rgba(0, 0, 0, 1) 0%,rgba(255, 0, 0, 1) 100%)"
    },
    "type": "gradient"
  }
}
```
**Status:** ✅ WORKS - Renders AND editable in Oxygen

---

### Hover States: Only Background Color Works

**❌ Test 2a: Hover Transform + Shadow - DOES NOT WORK**
```json
{
  "hover": {
    "transform": "translateY(-4px)",
    "boxShadow": [...]
  }
}
```
**Status:** ❌ Does not work
**Conclusion:** Oxygen doesn't support `design.hover` object

---

**✅ Test 2b: Background Color Hover - WORKS**
```json
{
  "background": {
    "color": "#ffffff",
    "color_hover": "#642EDAFF"
  }
}
```
**Status:** ✅ WORKS
**Limitation:** Only background color changes, no transform/shadow

---

### Transitions: Only Background Duration Works

**❌ Test 3a: Transition in Effects - DOES NOT WORK**
```json
{
  "effects": {
    "transition": {
      "property": "all",
      "duration": "0.3s",
      "timing": "ease"
    }
  }
}
```
**Status:** ❌ Does not work

---

**✅ Test 3b: Background Transition Duration - WORKS**
```json
{
  "background": {
    "color": "#667eea",
    "transition_duration": {
      "number": 1000,
      "unit": "ms",
      "style": "1000ms"
    },
    "color_hover": "#583D92FF"
  }
}
```
**Status:** ✅ WORKS
**Use Case:** Smooth color transitions on hover

---

## 📊 CORRECT Summary

| Feature | Status | Notes |
|---------|--------|-------|
| **Gradients (String)** | ❌ | Does not render |
| **Gradients (String + Type)** | ⚠️ | Renders but not editable |
| **Gradients (Structured)** | ✅ | **ONLY format that works properly** |
| **Hover Transform/Shadow** | ❌ | Not supported |
| **Hover Background Color** | ✅ | Works via `color_hover` |
| **Transitions (Generic)** | ❌ | Not supported |
| **Transitions (Background)** | ✅ | Works via `transition_duration` |

---

## 🎯 What Oxygen ACTUALLY Supports

### ✅ SUPPORTED (Use These!)
1. **Gradients** - Structured format only
2. **Background color hover** - Via `color_hover`
3. **Background transition** - Via `transition_duration`
4. **Box shadows** - Standard format (not in hover)
5. **Borders, spacing, typography** - All standard

### ❌ NOT SUPPORTED (Need Fallback)
1. **Hover transforms** - Cannot lift elements on hover
2. **Hover box shadows** - Cannot add shadow on hover
3. **Generic transitions** - Only background transitions work

---

## 🔧 Implications for oxygen_mapper.clj

### Gradient Conversion (CSS → Oxygen)

Must parse CSS gradient and convert to structured format:

```clojure
(defn parse-linear-gradient
  "Parse 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' → Oxygen format"
  [gradient-str]
  (let [;; Extract angle
        angle (re-find #"(\d+)deg" gradient-str)
        degree (if angle (Integer/parseInt (second angle)) 90)

        ;; Extract color stops
        color-stops (re-seq #"#([0-9a-fA-F]{6})\s+(\d+)%" gradient-str)

        points (mapv (fn [[_ hex percent]]
                      (let [r (Integer/parseInt (subs hex 0 2) 16)
                            g (Integer/parseInt (subs hex 2 4) 16)
                            b (Integer/parseInt (subs hex 4 6) 16)
                            pos (Integer/parseInt percent)]
                        {:left pos
                         :red r
                         :green g
                         :blue b
                         :alpha 1}))
                    color-stops)]

    {:gradient {:points points
                :type "linear"
                :degree degree
                :value gradient-str}
     :type "gradient"}))
```

### Hover States (Limited Support)

```clojure
(defn map-hover [base-color hover-props]
  ;; Can ONLY do background color changes
  (when-let [hover-color (:background-color hover-props)]
    {:color base-color
     :color_hover hover-color
     :transition_duration {:number 300 :unit "ms" :style "300ms"}}))

;; ❌ CANNOT map:
;; - transform (no Oxygen support)
;; - box-shadow on hover (no Oxygen support)
```

### Fallback Strategy

```clojure
(defn map-card-with-hover [props]
  (let [base (map-oxygen-properties props)]
    (if (:hover props)
      ;; Has hover effects requested
      (if (hover-supported-by-oxygen? (:hover props))
        ;; Only color hover supported
        (assoc-in base [:design :background]
                  (map-hover (get-in props [:background])
                            (:hover props)))
        ;; Transform/shadow not supported - need CssCode fallback
        (merge base
               {:_requires_css_fallback true
                :_hover_effects (:hover props)}))
      base)))
```

---

## 🚀 Deployment Strategy

### Option A: Pure Oxygen (Limited Features)
- ✅ Gradients (structured format)
- ✅ Background color hover
- ✅ Background transitions
- ❌ No hover lift effects
- ❌ No hover shadows

### Option B: Hybrid (Full Features)
- ✅ All Oxygen native properties
- ✅ CssCode fallback for unsupported features:
  - Hover transforms (`.card-hover:hover { transform: ... }`)
  - Hover shadows (`.card-hover:hover { box-shadow: ... }`)

### Recommendation: **Hybrid Approach**

Use Oxygen native properties wherever possible, CssCode fallback for unsupported features.

```clojure
(defn compile-to-oxygen [forma-edn]
  (let [base (map-oxygen-properties forma-edn)
        unsupported (filter-unsupported-features forma-edn)]

    (if (seq unsupported)
      ;; Add CssCode element for fallback
      {:oxygen-tree base
       :css-fallback (generate-css-fallback unsupported)}
      ;; Pure Oxygen
      {:oxygen-tree base})))
```

---

## ✅ Corrected Implementation Plan

1. **Parse CSS gradients** → Convert to Oxygen structured format
2. **Map hover colors** → Use `color_hover` + `transition_duration`
3. **Fallback for transforms/shadows** → Generate CssCode element
4. **Update oxygen_mapper.clj** with correct formats
5. **Deploy Mesh homepage** using hybrid approach

---

**Key Insight:** Oxygen has LIMITED native support. Hybrid approach (Oxygen + CssCode fallback) needed for full features.