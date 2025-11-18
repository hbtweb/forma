# Oxygen vs Breakdance Element Systems Comparison

**Status:** ✅ **FULLY ANALYZED**
**Date:** 2025-01-17
**Installation:** Breakdance 6.0.0-beta.3 (running in Oxygen mode)
**Location:** C:\laragon\www\hbtcomputers.com.au\wp-content\plugins\oxygen

---

## Executive Summary

**CRITICAL FINDING:** The "Oxygen" plugin installed is **NOT Classic Oxygen 4.5.1**. It is **Breakdance 6.0.0-beta.3 running in "Oxygen mode"** - a unified codebase that can operate as either Breakdance OR Oxygen depending on the `BREAKDANCE_MODE` constant.

### Key Insights

1. **Unified Codebase:** Both OxygenElements and EssentialElements use the **exact same infrastructure**
2. **Identical Tree Structure:** No difference in data storage or schema
3. **Same Rendering Engine:** Both use Twig templates + PHP classes
4. **Namespace Distinction Only:** The difference is element catalog, not architecture
5. **Mixed Elements Supported:** Both element types can coexist in the same tree

---

## Version Architecture

### Current Installation

```php
/**
 * Plugin Name: Oxygen
 * Version: 6.0.0-beta.3
 */
define('BREAKDANCE_MODE', 'oxygen');
const __BREAKDANCE_VERSION = '6.0.0-beta.3';
```

### Mode Switching

The plugin operates as a **single unified codebase**:
- `BREAKDANCE_MODE = 'breakdance'` - Full Breakdance functionality
- `BREAKDANCE_MODE = 'oxygen'` - Oxygen compatibility mode ✅ (current)

---

## Element Systems Comparison

### 1. OxygenElements (21 Elements)

**Namespace:** `OxygenElements\`
**Location:** `oxygen/subplugins/oxygen-elements/elements/`
**Purpose:** Oxygen-compatible elements for backward compatibility

#### Complete Element Catalog

| Element Class | Type | Purpose |
|---------------|------|---------|
| `Component` | Container | References reusable component by ID |
| `Container` | Layout | Generic container/div |
| `Container_Link` | Layout | Clickable container |
| `Container_Shortcode` | Layout | Container for WordPress shortcodes |
| `CSS_Code` | Code | Custom CSS injection |
| `Dynamic_Data_Loop` | Dynamic | Loop through dynamic data |
| `HTML_Code` | Code | Raw HTML injection |
| `HTML5_Video` | Media | HTML5 video player |
| `Image` | Media | Image element |
| `JavaScript_Code` | Code | Custom JavaScript injection |
| `oEmbed` | Media | oEmbed (YouTube, Vimeo, etc.) |
| `PHP_Code` | Code | Custom PHP execution |
| `Posts_Loop` | Dynamic | WordPress post query loop |
| `Rich_Text` | Content | Rich text editor (HTML support) |
| `Shortcode` | WordPress | WordPress shortcode execution |
| `SVG_Icon` | Media | SVG icon |
| `Template_Content_Area` | Layout | Template content placeholder |
| `Term_Loop_Builder` | Dynamic | Taxonomy term query loop |
| `Text` | Content | Basic text element |
| `Text_Link` | Content | Text with link |
| `WP_Widget` | WordPress | WordPress widget integration |

#### Characteristics

- ✅ Minimal, essential elements
- ✅ Basic UI building blocks
- ✅ Backward compatibility with Classic Oxygen workflows
- ✅ `availableIn()` returns `['oxygen']`
- ✅ CSS class prefix: `oxy-` (e.g., `oxy-text`)

---

### 2. EssentialElements (22+ Elements)

**Namespace:** `EssentialElements\`
**Location:** `oxygen/subplugins/breakdance-elements/elements/`
**Purpose:** Full-featured Breakdance elements available in Oxygen mode

#### Element Catalog

| Element Class | Type | Purpose |
|---------------|------|---------|
| `Atom_V1_Button` | Interactive | Advanced button with design options |
| `Atom_V1_Custom_Button_Design` | Preset | Custom button design preset |
| `contact-form-7` | Form | Contact Form 7 integration |
| `EffectsPreset` | Preset | Animation/effect presets |
| `FancyBackgroundPreset` | Preset | Advanced background styles |
| `FlexibleContent` | Layout | Flexible content areas |
| `FormDesignOptions` | Preset | Form styling options |
| `gravity-forms` | Form | Gravity Forms integration |
| `Hover_Swapper` | Interactive | Hover state image swapper |
| `icon-boxes` | Content | Icon box grid |
| `image` | Media | Advanced image element |
| `layoutPreset` | Preset | Layout configuration preset |
| `MenuCart` | WooCommerce | Shopping cart menu icon |
| `missing-element` | System | Placeholder for unknown elements |
| `ninja-forms` | Form | Ninja Forms integration |
| `Overlapped_Images` | Media | Overlapping image design |
| `Pagination` | Navigation | Page navigation |
| `SimpleLayoutPreset` | Preset | Simple layout preset |
| `SliderOptionsPreset` | Preset | Slider configuration |
| `WooGlobalStyler` | WooCommerce | Global WooCommerce styles |
| `WooPresetInputs` | WooCommerce | WooCommerce input styles |
| `WooPresetStock` | WooCommerce | Stock status styling |

#### Characteristics

- ✅ Advanced design elements
- ✅ Form integrations (Contact Form 7, Gravity Forms, Ninja Forms)
- ✅ WooCommerce integration (not active on current site)
- ✅ Design presets and layout systems
- ✅ `availableIn()` can return `['breakdance']` or both
- ✅ CSS class prefix: `bde-` (e.g., `bde-image`)

---

## Tree Structure (IDENTICAL for Both)

### Schema Definition

```php
/**
 * @psalm-type TreeNode = array{
 *   id: int,
 *   data: TreeNodeData,
 *   children: array
 * }
 *
 * @psalm-type Tree = array{
 *   root: array{
 *     id: int,
 *     data: TreeNodeData,
 *     children: TreeNode[]
 *   },
 *   _nextNodeId: int,
 *   exportedLookupTable: array<int, TreeNode>
 * }
 */
```

### Storage

- **WordPress Meta Key:** `_oxygen_data` or `_breakdance_data` (depending on mode)
- **Format:** JSON-encoded tree structure
- **Access Function:** `\Breakdance\Data\get_tree($post_id)`

### Example Tree with Mixed Elements

```json
{
  "root": {
    "id": 1,
    "data": {
      "type": "OxygenElements\\Container",
      "properties": {
        "design": {
          "background": "#1a202c",
          "padding": "20px"
        }
      }
    },
    "children": [
      {
        "id": 2,
        "data": {
          "type": "OxygenElements\\Text",
          "properties": {
            "content": {
              "content": {
                "text": "Hello from OxygenElements"
              }
            }
          }
        },
        "children": []
      },
      {
        "id": 3,
        "data": {
          "type": "EssentialElements\\Atom_V1_Button",
          "properties": {
            "content": {
              "content": {
                "text": "Click Me (Breakdance Button)"
              }
            }
          }
        },
        "children": []
      }
    ]
  },
  "_nextNodeId": 4,
  "status": "exported"
}
```

**✅ Mixed elements work perfectly!** Both namespaces can coexist in the same tree.

---

## Element Class Architecture (IDENTICAL)

Both OxygenElements and EssentialElements extend the same base class with identical structure:

### PHP Class Template

```php
namespace OxygenElements; // or EssentialElements

class Text extends \Breakdance\Elements\Element
{
    // UI Metadata
    static function uiIcon() { return 'TextIcon'; }
    static function tag() { return 'div'; }
    static function tagOptions() { return ['span', 'p', 'h1', 'h2', ...]; }
    static function name() { return 'Text'; }
    static function className() { return 'oxy-text'; } // or 'bde-text'
    static function category() { return 'basic'; }
    static function slug() { return __CLASS__; }

    // Rendering Templates (Twig)
    static function template() {
        return file_get_contents(__DIR__ . '/html.twig');
    }
    static function cssTemplate() {
        return file_get_contents(__DIR__ . '/css.twig');
    }
    static function defaultCss() {
        return file_get_contents(__DIR__ . '/default.css');
    }

    // Property Controls (Builder UI)
    static function contentControls() { return [...]; }
    static function designControls() { return [...]; }
    static function settingsControls() { return [...]; }

    // Element Metadata
    static function availableIn() {
        return ['oxygen']; // or ['breakdance'] or both
    }
    static function defaultProperties() { return [...]; }
    static function attributes() { return [...]; }
    static function nestingRule() {
        return ["type" => "final"]; // or "container" or "parent-only"
    }
}
```

### File Structure Per Element

```
Text/
  ├── element.php      # PHP class definition
  ├── html.twig        # HTML rendering template
  ├── css.twig         # CSS generation template
  └── default.css      # Default element styles
```

### Property Controls Structure

```php
static function contentControls() {
    return [
        [
            'slug' => 'text',
            'label' => 'Text',
            'type' => 'text',
            'options' => ['multiline' => true]
        ],
        [
            'slug' => 'tag',
            'label' => 'HTML Tag',
            'type' => 'dropdown',
            'options' => ['items' => [
                ['text' => 'Default (Div)', 'value' => ''],
                ['text' => 'Paragraph', 'value' => 'p'],
                ['text' => 'Span', 'value' => 'span']
            ]]
        ]
    ];
}
```

---

## Key Differences Summary

| Feature | OxygenElements | EssentialElements |
|---------|----------------|-------------------|
| **Namespace** | `OxygenElements\` | `EssentialElements\` |
| **Element Count** | 21 | 22+ |
| **Complexity** | Basic/Essential | Advanced/Full-featured |
| **CSS Prefix** | `oxy-` | `bde-` |
| **Form Integration** | None | CF7, Gravity, Ninja |
| **WooCommerce** | None | Full integration |
| **Design Presets** | Minimal | Extensive |
| **Available In** | `['oxygen']` | `['breakdance']` or both |
| **Purpose** | Backward compatibility | Modern features |
| **Tree Structure** | ✅ Identical | ✅ Identical |
| **Rendering Engine** | ✅ Identical | ✅ Identical |
| **Property Schema** | ✅ Identical | ✅ Identical |

---

## Forma Compiler Implications

### Single Compiler Strategy (RECOMMENDED)

Since both systems share:
- ✅ Identical tree structure
- ✅ Identical element class architecture
- ✅ Identical rendering pipeline
- ✅ Identical storage mechanism

**Forma should build ONE compiler** that outputs Oxygen/Breakdance tree structure.

### Element Mapping

```clojure
;; Forma EDN
[:text {:content "Hello World"}]

;; Can compile to EITHER:
{:type "OxygenElements\\Text"
 :properties {:content {:content {:text "Hello World"}}}}

;; OR:
{:type "EssentialElements\\Text"
 :properties {:content {:content {:text "Hello World"}}}}
```

### Element Catalog Configuration

```clojure
{:oxygen-elements
  {:text "OxygenElements\\Text"
   :container "OxygenElements\\Container"
   :image "OxygenElements\\Image"
   :component "OxygenElements\\Component"
   :posts-loop "OxygenElements\\Posts_Loop"}

 :essential-elements
  {:button "EssentialElements\\Atom_V1_Button"
   :image-advanced "EssentialElements\\image"
   :icon-boxes "EssentialElements\\icon-boxes"
   :contact-form "EssentialElements\\contact-form-7"
   :woo-cart "EssentialElements\\MenuCart"}}
```

### Mixed Element Support

```clojure
;; Forma can output mixed trees
[:container {:type :oxygen}  ; OxygenElements\Container
  [:text "Basic text"]        ; OxygenElements\Text
  [:button {:type :breakdance ; EssentialElements\Atom_V1_Button
            :text "Click Me"}]]
```

---

## Detection & Version Strategy

### Runtime Detection

```clojure
(defn detect-oxygen-version []
  "Detects Oxygen/Breakdance mode and available element systems"
  {:type :breakdance-in-oxygen-mode
   :version "6.0.0-beta.3"
   :mode "oxygen"
   :available-elements [:oxygen-elements :essential-elements]
   :can-mix-elements true})
```

### Compatibility Matrix

| Version | Tree Schema | OxygenElements | EssentialElements | Mixed Support |
|---------|-------------|----------------|-------------------|---------------|
| Classic Oxygen 4.x | Legacy | ✅ | ❌ | ❌ |
| Breakdance (Oxygen mode) 6.x | Modern | ✅ | ✅ | ✅ |
| Breakdance (Native) 6.x | Modern | ✅ | ✅ | ✅ |

---

## Recommendations

### For Forma Development

1. **Build single unified compiler** that outputs Oxygen tree structure
2. **Support both element namespaces** via configuration
3. **Use element catalog discovery** for extensibility
4. **Leverage Twig templates** as source of truth for property schemas
5. **Enable mixed element trees** by default

### For Deployment

1. **Use `/oxygen/v1/save` endpoint** (works for both element types)
2. **Include `status: "exported"`** in tree (required for builder)
3. **Set `_nextNodeId` correctly** (MAX(all IDs) + 1)
4. **Test with both namespaces** to ensure compatibility

### For Documentation

1. **Clarify version situation** (not Classic Oxygen 4.5.1)
2. **Document both element catalogs** with examples
3. **Provide property mapping guides** for each element type
4. **Show mixed element tree examples**

---

## Conclusion

There is **no architectural difference** between OxygenElements and EssentialElements. They are simply different **element catalogs** using the **same underlying infrastructure**.

**Forma needs ONE compiler** that:
- Outputs Oxygen/Breakdance tree structure
- Supports both element namespaces
- Uses element catalog configuration for mapping
- Enables mixed element trees

This provides maximum compatibility with minimal code complexity.

---

**Last Updated:** 2025-01-17
**Source:** Plugin analysis at C:\laragon\www\hbtcomputers.com.au\wp-content\plugins\oxygen
**Version Analyzed:** Breakdance 6.0.0-beta.3 (Oxygen mode)
