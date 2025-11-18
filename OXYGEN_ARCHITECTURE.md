# Oxygen/Breakdance Architecture

**Status:** ✅ **FULLY ANALYZED**
**Date:** 2025-01-17
**Version:** Breakdance 6.0.0-beta.3 (running in Oxygen mode)
**Source:** Analyzed from hbtcomputers.com.au WordPress site + plugin source code

---

## Important Version Clarification

**CRITICAL:** The installed "Oxygen" plugin is **NOT Classic Oxygen 4.5.1**. It is **Breakdance 6.0.0-beta.3 running in "Oxygen mode"** - a unified codebase that supports both:
- `OxygenElements\*` (21 elements) - Oxygen-compatible elements
- `EssentialElements\*` (22+ elements) - Full Breakdance elements

See [OXYGEN_BREAKDANCE_COMPARISON.md](OXYGEN_BREAKDANCE_COMPARISON.md) for detailed comparison.

---

## Table of Contents

1. [Overview](#overview)
2. [Content Types](#content-types)
3. [Headers & Footers](#headers--footers)
4. [Global Blocks (Components)](#global-blocks-components)
5. [Design Library](#design-library)
6. [Node Structure](#node-structure)
7. [Template System](#template-system)
8. [REST API](#rest-api)

---

## Overview

Oxygen/Breakdance uses a **tree-based architecture** where everything is a **node** (element). The system has several content types that work together:

```
WordPress Site (hbtcomputers.com.au)
├── Pages (post_type: page)
│   └── Each page has its own tree structure
├── Templates (post_type: oxygen_template)
│   └── Reusable page layouts with conditional rendering
├── Headers (post_type: oxygen_header)
│   └── Site-wide headers (can be conditional)
├── Footers (post_type: oxygen_footer)
│   └── Site-wide footers (can be conditional)
├── Global Blocks (post_type: oxygen_block)
│   └── Reusable components that can be referenced
└── Design Library
    ├── Selectors (CSS classes)
    ├── Variables (design tokens)
    └── Presets (saved element configurations)
```

---

## Content Types

### 1. Pages (`post_type: "page"`)

**Purpose:** Individual pages (About, Contact, etc.)

**Structure:**
```json
{
  "id": 46,
  "title": "Forma Test Page",
  "post_type": "page",
  "status": "publish",
  "tree": {
    "_nextNodeId": 200,
    "status": "exported",
    "root": {
      "id": 1,
      "data": {"type": "root", "properties": []},
      "children": [...]
    }
  }
}
```

**Example:** Test Page (ID: 21), Front Page (ID: 12)

---

### 2. Templates (`post_type: "oxygen_template"`)

**Purpose:** Reusable page layouts that apply conditionally based on rules

**Structure:**
```json
{
  "id": 10,
  "title": "Template",
  "post_type": "oxygen_template",
  "status": "publish",
  "tree": {...},
  "template_settings": {
    "type": "front-page",  // Where this template applies
    "ruleGroups": [],      // Conditional rules
    "priority": 20,        // Higher priority = overrides others
    "triggers": []
  }
}
```

**Template Types:**
- `"front-page"` - Homepage
- `"all-singles"` - All single posts/pages
- `"post-type-archive"` - Archive pages
- `"taxonomy-archive"` - Category/tag pages
- `"everywhere"` - Global template

**Example:** Template (ID: 10) - Front page layout

---

### 3. Headers (`post_type: "oxygen_header"`)

**Purpose:** Site-wide headers (navigation, logo, etc.)

**Structure:**
```json
{
  "id": 11,
  "title": "Header",
  "post_type": "oxygen_header",
  "tree": {
    "root": {
      "children": [
        {
          "id": 111,
          "data": {
            "type": "EssentialElements\\Div",
            "properties": {
              "design": {
                "layout_v2": {
                  "layout": "horizontal",
                  "h_align": {"breakpoint_base": "center"}
                }
              }
            }
          },
          "children": [
            {
              "id": 102,
              "data": {
                "type": "EssentialElements\\MenuBuilder",
                "properties": null
              },
              "children": [
                {"id": 103, "data": {"type": "EssentialElements\\MenuLink", ...}},
                {"id": 104, "data": {"type": "EssentialElements\\MenuDropdown", ...}}
              ]
            }
          ]
        }
      ]
    }
  },
  "settings": {
    "type": "everywhere",  // Where header appears
    "priority": 1
  }
}
```

**Key Element Types:**
- `EssentialElements\MenuBuilder` - Menu container
- `EssentialElements\MenuLink` - Menu item
- `EssentialElements\MenuDropdown` - Dropdown menu
- `EssentialElements\MenuCustomDropdown` - Custom dropdown with any elements

**Example:** Header (ID: 11) - Navigation menu with dropdowns

---

### 4. Footers (`post_type: "oxygen_footer"`)

**Purpose:** Site-wide footers (copyright, links, etc.)

**Structure:**
```json
{
  "id": 26,
  "title": "Footer",
  "post_type": "oxygen_footer",
  "tree": {
    "root": {
      "children": [
        {"id": 101, "data": {"type": "EssentialElements\\Heading", ...}},
        {"id": 102, "data": {"type": "OxygenElements\\TextLink", ...}},
        {"id": 100, "data": {"type": "OxygenElements\\Component", ...}}
      ]
    }
  },
  "settings": {
    "type": "everywhere",
    "priority": 1
  }
}
```

**Example:** Footer (ID: 26) - Simple footer with links and component reference

---

## Global Blocks (Components)

**Post Type:** `oxygen_block`

**Purpose:** Reusable components that can be referenced from any page/template/header/footer

### How They Work

1. **Create a Global Block:**
```json
{
  "id": 24,
  "title": "New Component",
  "post_type": "oxygen_block",
  "tree": {
    "root": {
      "children": [
        {
          "id": 100,
          "data": {"type": "EssentialElements\\Div"},
          "children": [
            {"id": 107, "data": {"type": "EssentialElements\\Heading", ...}},
            {"id": 102, "data": {"type": "OxygenElements\\WpWidget", ...}}
          ]
        }
      ]
    }
  }
}
```

2. **Reference it anywhere:**
```json
{
  "id": 100,
  "data": {
    "type": "OxygenElements\\Component",
    "properties": {
      "content": {
        "content": {
          "block": {
            "componentId": 24,  // ✅ References Global Block ID 24
            "targets": []        // Optional: Pass data to component
          }
        }
      }
    }
  }
}
```

### Component Nesting

**Components can reference other components!**

Example from our analysis:
- Component 24 ("New Component") references Component 28 ("Comments")
- Component 28 contains a CommentForm element
- Footer (ID: 26) references Component 24

```
Footer (ID: 26)
  └─ OxygenElements\Component (references 24)
       └─ Component 24 ("New Component")
            ├─ Heading
            ├─ WpWidget
            └─ OxygenElements\Component (references 28)
                 └─ Component 28 ("Comments")
                      └─ CommentForm
```

### Available via REST API

```bash
GET /oxygen/v1/blocks
```

Returns all global blocks with their complete tree structures.

**Example from hbtcomputers.com.au:**
- Component 28: "Comments (saved as component from within oxy)"
- Component 24: "New Component" (references Component 28)

---

## Design Library

The Design Library provides reusable styles, variables, and presets across the entire site.

### 1. Selectors (CSS Classes)

**Purpose:** Define reusable CSS classes

**Endpoint:** `GET /oxygen/v1/selectors`

**Structure:**
```json
{
  "selectors": [
    {
      "name": "button-primary",
      "type": "class",
      "properties": {
        "design": {
          "background": "#1a202c",
          "padding": "12px 24px",
          "typography": {
            "color": "#ffffff"
          }
        }
      }
    }
  ]
}
```

**Usage in elements:**
```json
{
  "id": 100,
  "data": {
    "type": "EssentialElements\\Button",
    "properties": {
      "meta": {
        "classes": ["button-primary"]  // ✅ Applies selector styles
      }
    }
  }
}
```

---

### 2. Variables (Design Tokens)

**Purpose:** Define reusable values (colors, spacing, typography)

**Endpoint:** `POST /oxygen/v1/variables`

**Structure:**
```json
{
  "variables": {
    "colors": {
      "primary": "#1a202c",
      "secondary": "#E2D8FF",
      "accent": "#F5E9FF"
    },
    "spacing": {
      "small": "8px",
      "medium": "16px",
      "large": "32px"
    },
    "typography": {
      "heading-font": "gfont-abeezee",
      "body-font": "system-ui"
    }
  }
}
```

**Usage:**
```json
{
  "properties": {
    "design": {
      "background": "var(--colors-primary)",  // ✅ Uses variable
      "padding": "var(--spacing-large)"
    }
  }
}
```

---

### 3. Presets

**Purpose:** Save element configurations for reuse

**Endpoint:** `POST /oxygen/v1/presets`

**Structure:**
```json
{
  "presets": [
    {
      "slug": "hero-section",
      "section": {
        "slug": "design",
        "label": "Design"
      },
      "availableInElementStudio": true,
      "properties": {
        "design": {
          "background": "#1a202c",
          "padding": "80px 20px",
          "layout_v2": {
            "layout": "vertical"
          }
        }
      }
    }
  ]
}
```

**Usage:** Apply saved presets to elements via Oxygen builder UI

---

## Node Structure

Every element in Oxygen is a **node**. Nodes have IDs that are **scoped per page/template**.

### Basic Node Structure

```json
{
  "id": 100,                              // Unique within this page/template
  "data": {
    "type": "EssentialElements\\Section", // Fully qualified class name
    "properties": {                       // Element configuration
      "content": {},                      // Content properties
      "design": {},                       // Style properties
      "settings": {},                     // Advanced settings
      "meta": {                           // Metadata
        "classes": []                     // CSS classes from Design Library
      }
    }
  },
  "children": [],                         // Child nodes
  "_parentId": 1                          // Parent node ID
}
```

### Node ID Rules

1. **Root always has ID 1** (every page/template)
2. **IDs are scoped per page/template** - Different pages can reuse the same IDs
3. **IDs must be numbers** (not strings)
4. **_nextNodeId tracks next available ID** (formula: `MAX(all IDs) + 1`)

**Example:**
```
Page 21:     root (1) → Section (100) → Heading (101)
Template 10: root (1) → Section (103) → Heading (100)  // ✅ Same ID 100!
Page 46:     root (1) → Section (100) → Heading (101)  // ✅ Reuses IDs!
```

---

## Template System

### Template Application Order

1. **Most specific template wins** (e.g., "Page ID 21" beats "All Pages")
2. **Higher priority wins** (if same specificity)
3. **Headers/Footers apply site-wide** (unless conditional rules exclude them)

### Conditional Rules

Templates, headers, and footers support conditional rules:

```json
{
  "template_settings": {
    "type": "everywhere",
    "ruleGroups": [
      {
        "conditions": [
          {
            "slug": "post-type",
            "operand": "is",
            "value": "page"
          },
          {
            "slug": "user-logged-in-status",
            "operand": "is",
            "value": "logged in"
          }
        ]
      }
    ],
    "priority": 20
  }
}
```

**Available condition types:**
- Post type, Post ID, Post status
- Taxonomy, Category, Tag
- User role, User logged in status
- Date & time conditions
- Custom PHP
- Browser, Operating system
- And many more (see header API response for full list)

---

## REST API

### Available Endpoints

**Pages & Templates:**
- `POST /oxygen/v1/save` - Create/update page/template/header/footer/block
- `GET /oxygen/v1/page/{id}` - Get page/template tree
- `DELETE /oxygen/v1/page/{id}` - Delete page/template
- `GET /oxygen/v1/templates/list/{type}` - List templates by type (template, header, footer, block)

**Global Blocks:**
- `GET /oxygen/v1/blocks` - Get all global blocks with tree structures

**Design Library:**
- `GET /oxygen/v1/selectors` - Get all CSS selectors
- `POST /oxygen/v1/selectors` - Update selectors
- `POST /oxygen/v1/variables` - Update design variables
- `POST /oxygen/v1/presets` - Update presets

**Template Conditions:**
- `GET /oxygen/v1/template/{id}/conditions` - Get template conditions
- `POST /oxygen/v1/template/{id}/conditions` - Update template conditions

**Global Settings:**
- `GET /oxygen/v1/global-settings` - Get site-wide Oxygen settings
- `POST /oxygen/v1/global-settings` - Update global settings

**Cache & Maintenance:**
- `POST /oxygen/v1/cache/regenerate/{id}` - Regenerate CSS for page
- `POST /oxygen/v1/cache/regenerate-all` - Regenerate all CSS
- `POST /oxygen/v1/cache/clear` - Clear all caches
- `POST /oxygen/v1/repair/{id}` - Repair broken page tree

**Builder:**
- `POST /oxygen/v1/builder/load/{id}` - Load page in builder
- `POST /oxygen/v1/validate-tree` - Validate tree structure
- `GET /oxygen/v1/elements` - Get available element types
- `GET /oxygen/v1/icons` - Get icon library
- `POST /oxygen/v1/icons/upload` - Upload custom icons

**WordPress Integration:**
- `GET /oxygen/v1/wp/post-types` - Get WordPress post types
- `GET /oxygen/v1/wp/taxonomies` - Get WordPress taxonomies
- `GET /oxygen/v1/wp/menus` - Get WordPress menus

**Revisions:**
- `GET /oxygen/v1/revisions/{id}` - Get page revisions
- `POST /oxygen/v1/revisions/{id}/restore/{revision_id}` - Restore revision

---

## Element Types

### EssentialElements (Modern Breakdance)

**Containers:**
- `EssentialElements\Section` - Layout section
- `EssentialElements\Div` - Generic container

**Content:**
- `EssentialElements\Heading` - Headings (h1-h6)
- `EssentialElements\Text` - Paragraph text
- `EssentialElements\RichText` - Rich formatted text (HTML)
- `EssentialElements\TextLink` - Text with link
- `EssentialElements\Button` - Button element

**Media:**
- `EssentialElements\Image` - Image element
- `EssentialElements\Video` - Video embed (YouTube, etc.)

**Interactive:**
- `EssentialElements\IconList` - List with icons
- `EssentialElements\AdvancedAccordion` - Accordion wrapper
- `EssentialElements\AccordionContent` - Accordion item
- `EssentialElements\SimpleTestimonial` - Testimonial card

**Navigation:**
- `EssentialElements\MenuBuilder` - Menu container
- `EssentialElements\MenuLink` - Menu item
- `EssentialElements\MenuDropdown` - Dropdown menu
- `EssentialElements\MenuCustomDropdown` - Custom dropdown

**WordPress:**
- `EssentialElements\CommentForm` - WordPress comments

---

### OxygenElements (Legacy Oxygen)

**Code:**
- `OxygenElements\HtmlCode` - Raw HTML
- `OxygenElements\CssCode` - CSS styles
- `OxygenElements\JavaScriptCode` - JavaScript code
- `OxygenElements\PhpCode` - PHP code
- `OxygenElements\Shortcode` - WordPress shortcode
- `OxygenElements\ContainerShortcode` - Shortcode container

**Content:**
- `OxygenElements\Text` - Text with HTML support
- `OxygenElements\TextLink` - Text link
- `OxygenElements\RichText` - Rich text editor

**WordPress:**
- `OxygenElements\WpWidget` - WordPress widget

**Components:**
- `OxygenElements\Component` - Reference to global block

---

## Example: Complete Page Structure

```json
{
  "title": "About Us",
  "post_type": "page",
  "status": "publish",
  "tree": {
    "_nextNodeId": 200,
    "status": "exported",
    "root": {
      "id": 1,
      "data": {"type": "root", "properties": []},
      "children": [
        {
          "id": 100,
          "data": {
            "type": "EssentialElements\\Section",
            "properties": {
              "design": {
                "background": "var(--colors-primary)",
                "padding": "var(--spacing-large)",
                "layout_v2": {"layout": "vertical"}
              },
              "meta": {
                "classes": ["hero-section"]
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
                      "text": "About Our Company",
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
                "type": "OxygenElements\\Component",
                "properties": {
                  "content": {
                    "content": {
                      "block": {"componentId": 24, "targets": []}
                    }
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
}
```

---

## Summary

**Oxygen/Breakdance Architecture:**

1. **Tree-based** - Everything is a node with ID, type, properties, children
2. **Modular** - Headers, footers, templates, and blocks work together
3. **Reusable** - Global blocks (components) can be referenced anywhere
4. **Conditional** - Templates/headers/footers apply based on rules
5. **Design System** - Variables, selectors, presets provide consistency
6. **RESTful** - Full CRUD operations via REST API
7. **Node IDs** - Scoped per page/template, not global

**Key Insight:** To build pages programmatically, you don't need to create everything from scratch. Reference existing headers (ID: 11), footers (ID: 26), and global blocks (IDs: 24, 28) in your page trees!

---

**Last Updated:** 2025-01-17
**Analyzed From:** hbtcomputers.com.au WordPress site + plugin source code
**Version:** Breakdance 6.0.0-beta.3 (Oxygen mode)
**REST API Version:** oxygen/v1

---

## Related Documentation

- **[OXYGEN_BREAKDANCE_COMPARISON.md](OXYGEN_BREAKDANCE_COMPARISON.md)** - Element systems comparison (OxygenElements vs EssentialElements)
- **[WORDPRESS_INTEGRATION.md](WORDPRESS_INTEGRATION.md)** - WordPress integration guide, REST API access, dynamic content gaps
- **[FORMA_OXYGEN_COMPILER_ROADMAP.md](FORMA_OXYGEN_COMPILER_ROADMAP.md)** - Forma compiler development plan
- **[OXYGEN_CORRECT_STRUCTURE.md](OXYGEN_CORRECT_STRUCTURE.md)** - Tree structure requirements and validation
