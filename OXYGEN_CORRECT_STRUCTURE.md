# Oxygen Correct Tree Structure

**Status:** ✅ **VERIFIED WORKING**
**Date:** 2025-11-17
**Working Pages:** ID 21, 46 (deployed programmatically)
**Working Template:** ID 10
**Source:** Analyzed from existing working Oxygen pages and source code

---

## **Critical Structure Requirements**

### **1. Root Structure**

```json
{
  "_nextNodeId": 200,  // ✅ MUST be NUMBER (not string!)
  "root": {
    "id": 1,  // ✅ MUST be NUMBER (always 1)
    "data": {  // ✅ MUST be OBJECT (not array!)
      "type": "root",
      "properties": []  // ✅ Empty array
    },
    "children": [...]  // Array of element nodes
  }
}
```

**WRONG (causes IO-TS errors):**
```json
{
  "_nextNodeId": "node_123",  // ❌ String
  "root": {
    "id": "root",  // ❌ String
    "data": [],  // ❌ Array instead of object
    "children": [...]
  }
}
```

---

### **2. Element Node Structure**

```json
{
  "id": 100,  // ✅ NUMBER (not string)
  "data": {
    "type": "EssentialElements\\Section",  // ✅ Fully qualified class name
    "properties": {
      "design": {...},
      "content": {...}
    }
  },
  "children": [],  // ✅ Always array (even if empty)
  "_parentId": 1  // ✅ Optional but recommended
}
```

---

### **3. Content Nesting**

**Heading Content:**
```json
{
  "type": "EssentialElements\\Heading",
  "properties": {
    "content": {
      "content": {  // ✅ Nested "content"
        "text": "My Heading",
        "tags": "h1"  // ✅ For heading level
      }
    }
  }
}
```

**Text Content:**
```json
{
  "type": "EssentialElements\\Text",
  "properties": {
    "content": {
      "text": "My paragraph text"  // ✅ Direct text property
    }
  }
}
```

---

### **4. Section Design Properties**

```json
{
  "type": "EssentialElements\\Section",
  "properties": {
    "design": {
      "background": "#1a202c",
      "padding": "80px 20px",
      "layout_v2": {  // ✅ Layout structure
        "layout": "vertical"
      }
    }
  }
}
```

---

## **Complete Working Example**

```json
{
  "_nextNodeId": 200,
  "root": {
    "id": 1,
    "data": {
      "type": "root",
      "properties": []
    },
    "children": [
      {
        "id": 100,
        "data": {
          "type": "EssentialElements\\Section",
          "properties": {
            "design": {
              "background": "#1a202c",
              "padding": "80px 20px",
              "layout_v2": {
                "layout": "vertical"
              }
            }
          }
        },
        "children": [
          {
            "id": 101,
            "data": {
              "type": "EssentialElements\\Heading",
              "properties": {
                "content": {
                  "content": {
                    "text": "Hello from Forma!",
                    "tags": "h1"
                  }
                }
              }
            },
            "children": [],
            "_parentId": 100
          },
          {
            "id": 102,
            "data": {
              "type": "EssentialElements\\Text",
              "properties": {
                "content": {
                  "text": "This is paragraph text."
                }
              }
            },
            "children": [],
            "_parentId": 100
          }
        ],
        "_parentId": 1
      }
    ]
  }
}
```

---

## **Common IO-TS Validation Errors**

### **Error: Type `[]` at path `document.tree.root.0.data` is not assignable**

**Cause:** `root.data` is an array `[]` instead of object `{}`

**Fix:**
```json
"root": {
  "id": 1,
  "data": {  // ✅ Object
    "type": "root",
    "properties": []
  }
}
```

### **Error: Property `document.tree.status` is missing**

**Cause:** Missing `_nextNodeId` in tree root

**Fix:**
```json
{
  "_nextNodeId": 200,  // ✅ Add this
  "root": {...}
}
```

### **Error: Type is not assignable to number**

**Cause:** IDs are strings instead of numbers

**Fix:**
```json
{
  "id": 100,  // ✅ Number, not "100"
  ...
}
```

---

## **Element Types Reference**

Based on working pages (ID 21):

| Element | Type | Content Path |
|---------|------|--------------|
| **Section** | `EssentialElements\\Section` | N/A |
| **Heading** | `EssentialElements\\Heading` | `content.content.text` + `content.content.tags` |
| **Text** | `EssentialElements\\Text` | `content.text` |
| **Icon List** | (need to analyze) | - |
| **Accordion** | (need to analyze) | - |

---

## **ID Management**

### **Rules:**
1. **Root ID:** Always `1` (number) - Same on every page/template
2. **_nextNodeId:** Next available ID (formula: `MAX(all node IDs) + 1`)
3. **Element IDs:** Start at 100, increment for each element
4. **No string IDs:** All IDs must be numbers
5. **IDs are page/template-specific:** Different pages can reuse the same IDs

### **ID Scoping:**
```
✅ CORRECT - IDs are scoped per page:
  Page 21:     root (1) → Section (100) → Heading (101)
  Template 10: root (1) → Section (103) → Heading (100)  // Same ID 100!
  Page 46:     root (1) → Section (100) → Heading (101)  // Reuses IDs!

❌ WRONG - IDs are NOT global across all pages
```

**Evidence from WordPress:**
- Page 21 has node ID 100 (Section with many children)
- Template 10 has node ID 100 (Heading element)
- Page 46 has node ID 100 (Section)
- All stored separately in post meta - no conflicts!

### **_nextNodeId Formula:**
```clojure
;; Oxygen uses: _nextNodeId = MAX(all element IDs in tree) + 1

;; Example:
;; Tree has IDs: 1, 100, 101, 102, 128
;; _nextNodeId = 129  (next available)

;; For deployment:
(defn calculate-next-node-id [tree]
  (let [all-ids (collect-all-node-ids tree)
        max-id (apply max all-ids)]
    (inc max-id)))

;; Simple approach: Use high number (200+) to avoid conflicts
{:_nextNodeId 200  ; Safe: higher than typical element count
 :root {...}}
```

### **ID Counter (for building trees):**
```clojure
(def id-counter (atom 99))

(defn next-id []
  (swap! id-counter inc))

;; Usage
(def element-id (next-id))  ;; => 100
(def next-element-id (next-id))  ;; => 101

;; After deployment, set _nextNodeId higher than last used ID
```

---

## **Status Field**

### **Required for Builder:**
```json
{
  "_nextNodeId": 200,
  "status": "exported",  // ✅ REQUIRED for Oxygen builder to open page!
  "root": {...}
}
```

**Without `status: "exported"`:**
- ❌ Page preview loads (frontend works)
- ❌ Oxygen builder cannot open page (editor fails)
- ❌ No IO-TS errors, but builder shows "cannot load document"

**Discovery:** Page 21 (working) has `status: "exported"` in tree JSON. Page 43 (our first deploy) was missing this field, causing builder to fail despite preview working.

---

##Human: can you see how all the pages/templates/components are built in oxygen at hbtcomputers? can you infer the schema?---

## Related Documentation

- **[OXYGEN_ARCHITECTURE.md](OXYGEN_ARCHITECTURE.md)** - Complete Oxygen/Breakdance architecture guide
  - Headers, footers, templates, global blocks (components)
  - Design library (selectors, variables, presets)
  - Template system and conditional rendering
  - Complete REST API reference
  - Element types catalog (EssentialElements + OxygenElements)

---

## Quick Reference

### Deploy a Page with Header/Footer

```clojure
;; Reference existing header (ID: 11) and footer (ID: 26)
;; Oxygen will automatically include them based on their settings

{:title "My New Page"
 :post_type "page"
 :status "publish"
 :tree {:_nextNodeId 200
        :status "exported"
        :root {:id 1
               :data {:type "root" :properties []}
               :children [
                 {:id 100
                  :data {:type "EssentialElements\Section"
                         :properties {:design {...}}}
                  :children [...]
                  :_parentId 1}]}}}
```

### Use a Global Block (Component)

```clojure
;; Reference Component 24 in your page
{:id 100
 :data {:type "OxygenElements\Component"
        :properties {:content {:content {:block {:componentId 24
                                                  :targets []}}}}}
 :children []
 :_parentId 1}
```

### Available Components (hbtcomputers.com.au)

- **Component 24** - "New Component" (Heading + WpWidget + nested Component 28)
- **Component 28** - "Comments" (CommentForm)

### Available Headers/Footers

- **Header 11** - Navigation menu with dropdowns
- **Footer 26** - Simple footer with links + Component 24

---
