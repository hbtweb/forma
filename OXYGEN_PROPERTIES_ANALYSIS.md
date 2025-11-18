# Oxygen Native Properties - Complete Analysis

**Date:** 2025-01-17
**Source:** Page 59 (corrected by user)

---

## 🎨 Gradient Backgrounds (4 Variations)

### Test 1a: Simple String Format (ID: 10)
```json
{
  "design": {
    "background": {
      "gradient": "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
    }
  }
}
```
**Format:** String-based gradient
**Status:** ✅ Works (simplest format)

---

### Test 1b: String + Type Flag (ID: 100)
```json
{
  "design": {
    "background": {
      "gradient": "linear-gradient(135deg, #667eea 0%, #764ba2 100%)",
      "type": "gradient"
    }
  }
}
```
**Format:** String + explicit type declaration
**Status:** ✅ Works (more explicit)

---

### Test 1c: Structured Linear Gradient (ID: 102)
```json
{
  "design": {
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
        "type": "linear",
        "degree": 90,
        "svgValue": "<linearGradient x1=\"0\" y1=\"0.5\" x2=\"1\" y2=\"0.5\" id=\"%%GRADIENTID%%\">...",
        "value": "linear-gradient(90deg,rgba(0, 0, 0, 1) 0%,rgba(255, 0, 0, 1) 100%)"
      },
      "type": "gradient"
    }
  }
}
```
**Format:** Full structured format with points, degree, SVG value
**Status:** ✅ Works (Oxygen visual editor format)
**Use Case:** When using Oxygen's gradient picker UI

---

### Test 1d: Structured Radial Gradient (ID: 104)
```json
{
  "design": {
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
        "svgValue": "<radialGradient id=\"%%GRADIENTID%%\">...",
        "value": "radial-gradient(rgba(0, 0, 0, 1) 0%,rgba(255, 0, 0, 1) 100%)"
      },
      "type": "gradient"
    }
  }
}
```
**Format:** Structured radial gradient
**Status:** ✅ Works
**Use Case:** Radial gradients via Oxygen editor

---

## 🎯 Hover States (2 Variations)

### Test 2a: Hover with Transform + Shadow (ID: 20)
```json
{
  "design": {
    "background": {
      "color": "#ffffff"
    },
    "borders": {
      "radius": {
        "topLeft": {"number": 8, "unit": "px"},
        "topRight": {"number": 8, "unit": "px"},
        "bottomLeft": {"number": 8, "unit": "px"},
        "bottomRight": {"number": 8, "unit": "px"}
      }
    },
    "hover": {
      "transform": "translateY(-4px)",
      "boxShadow": [
        {
          "x": {"number": 0, "unit": "px"},
          "y": {"number": 10, "unit": "px"},
          "blur": {"number": 30, "unit": "px"},
          "spread": {"number": 0, "unit": "px"},
          "color": "rgba(0,0,0,0.15)"
        }
      ]
    }
  }
}
```
**Format:** Hover as nested object with transform + boxShadow
**Status:** ✅ Works
**Use Case:** Lift effect on hover

---

### Test 2b: Hover Background Color (ID: 106)
```json
{
  "design": {
    "background": {
      "color": "#ffffff",
      "color_hover": "#642EDAFF"
    },
    "hover": {
      "transform": "translateY(-4px)",
      "boxShadow": [...]
    }
  }
}
```
**Format:** `color_hover` property alongside `color`
**Status:** ✅ Works
**Use Case:** Background color change on hover

---

## ⚡ Transitions (2 Variations)

### Test 3a: Transition in Effects (ID: 30)
```json
{
  "design": {
    "background": {
      "color": "#667eea"
    },
    "effects": {
      "transition": {
        "property": "all",
        "duration": "0.3s",
        "timing": "ease"
      }
    }
  }
}
```
**Format:** Transition in `effects` object
**Status:** ✅ Works
**Use Case:** Generic transition for all properties

---

### Test 3b: Transition Duration in Background (ID: 107)
```json
{
  "design": {
    "background": {
      "color": "#667eea",
      "transition_duration": {
        "number": 1000,
        "unit": "ms",
        "style": "1000ms"
      },
      "type": null,
      "overlay": {
        "color": "#100"
      },
      "color_hover": "#583D92FF"
    },
    "effects": {
      "transition": {
        "property": "all",
        "duration": "0.3s",
        "timing": "ease"
      }
    }
  }
}
```
**Format:** `transition_duration` directly in background object
**Status:** ✅ Works
**Use Case:** Specific transition for background color changes

---

## 📊 Summary Table

| Feature | Format | Status | Best For |
|---------|--------|--------|----------|
| **Gradients** | | | |
| String | `{gradient: "linear-gradient(...)"}` | ✅ | Simple, from CSS |
| String + Type | `{gradient: "...", type: "gradient"}` | ✅ | Explicit |
| Structured Linear | `{gradient: {points: [...], type: "linear"}}` | ✅ | Oxygen editor |
| Structured Radial | `{gradient: {points: [...], type: "radial"}}` | ✅ | Oxygen editor |
| **Hover States** | | | |
| Transform + Shadow | `{hover: {transform: "...", boxShadow: [...]}}` | ✅ | Lift effects |
| Color Change | `{background: {color_hover: "..."}}` | ✅ | Color transitions |
| **Transitions** | | | |
| In Effects | `{effects: {transition: {...}}}` | ✅ | Generic |
| In Background | `{background: {transition_duration: {...}}}` | ✅ | Specific to bg |

---

## 🎯 Recommendations for oxygen_mapper.clj

### 1. Gradient Mapping
```clojure
(defn map-background [bg]
  (cond
    ;; Linear gradient (string format - simplest)
    (str/starts-with? bg "linear-gradient")
    {:gradient bg
     :type "gradient"}

    ;; Radial gradient
    (str/starts-with? bg "radial-gradient")
    {:gradient bg
     :type "gradient"}

    ;; Solid color
    :else
    {:color bg}))
```

### 2. Hover State Mapping
```clojure
(defn map-hover [hover-props]
  (when hover-props
    (cond-> {}
      (:transform hover-props)
      (assoc :transform (:transform hover-props))

      (:box-shadow hover-props)
      (assoc :boxShadow (map-box-shadow (:box-shadow hover-props))))))

(defn map-background-with-hover [bg hover-color]
  {:color bg
   :color_hover hover-color})
```

### 3. Transition Mapping
```clojure
(defn map-transition [transition-props]
  (when transition-props
    {:transition {:property (or (:property transition-props) "all")
                  :duration (or (:duration transition-props) "0.3s")
                  :timing (or (:timing transition-props) "ease")}}))
```

---

## ✅ What We Learned

1. **Gradients ARE supported natively** - Both string and structured formats work
2. **Hover states ARE supported** - via `design.hover` object
3. **Transitions ARE supported** - via `design.effects.transition`
4. **Background hover colors** - via `color_hover` property
5. **NO external CSS needed** - Everything can be pure Oxygen properties!

---

## 🚀 Next Steps

1. ✅ Update `oxygen_mapper.clj` with gradient support
2. ✅ Add hover state mapping
3. ✅ Add transition mapping
4. ✅ Deploy full Mesh homepage using ONLY Oxygen properties
5. ✅ No Tailwind needed, no external CSS needed!

**Forma's vision realized:** Universal compilation to platform-native format! 🎉