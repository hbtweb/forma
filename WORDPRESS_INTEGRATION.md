# WordPress Integration Guide

**Status:** ✅ **RESEARCHED & DOCUMENTED**
**Date:** 2025-01-17
**WordPress Site:** http://hbtcomputers.com.au.test
**Oxygen Version:** Breakdance 6.0.0-beta.3 (Oxygen mode)

---

## Table of Contents

1. [Overview](#overview)
2. [Current Capabilities](#current-capabilities)
3. [REST API Access](#rest-api-access)
4. [WordPress Data Available](#wordpress-data-available)
5. [Integration Gaps](#integration-gaps)
6. [Roadmap](#roadmap)

---

## Overview

This document outlines WordPress integration for the Forma → Oxygen deployment pipeline, including what's currently accessible via REST API and what features require additional development.

### Current Status

✅ **Working:** Static content deployment via REST API
❌ **Missing:** Dynamic WordPress integration (menus, post loops, custom fields)

---

## Current Capabilities

### What Works Today

1. **Static Page Deployment**
   - Create pages with Oxygen tree structure
   - Headers and footers apply automatically
   - Component references work (componentId)
   - WordPress widgets supported

2. **Element Types Supported**
   - All OxygenElements (21 elements)
   - All EssentialElements (22+ elements)
   - Mixed element trees

3. **REST API Access**
   - Page/template CRUD operations
   - WordPress post types, taxonomies, menus
   - Global blocks (components)
   - Settings and configuration

---

## REST API Access

### WordPress Core Endpoints

```bash
# Posts & Pages
GET  /wp/v2/posts              # Query posts
GET  /wp/v2/pages              # Query pages
GET  /wp/v2/types              # List post types
POST /wp/v2/posts              # Create post
POST /wp/v2/pages              # Create page

# Taxonomies
GET  /wp/v2/taxonomies         # List taxonomies
GET  /wp/v2/categories         # Query categories
GET  /wp/v2/tags               # Query tags
POST /wp/v2/categories         # Create category

# Menus (currently empty)
GET  /wp/v2/menus              # Query menus
GET  /wp/v2/menu-items         # Query menu items
POST /wp/v2/menus              # Create menu

# Media
GET  /wp/v2/media              # Query media library
POST /wp/v2/media              # Upload media

# Settings (requires authentication)
GET  /wp/v2/settings           # Site settings
POST /wp/v2/settings           # Update settings
```

### Oxygen REST Bridge Endpoints

```bash
# Pages & Templates
POST   /oxygen/v1/save                    # Create/update page/template/header/footer
GET    /oxygen/v1/page/{id}               # Get page tree
DELETE /oxygen/v1/page/{id}               # Delete page
GET    /oxygen/v1/templates/list/{type}   # List templates (template, header, footer, block)

# Global Blocks (Components)
GET    /oxygen/v1/blocks                  # Get all components

# Design Library
GET    /oxygen/v1/selectors               # Get CSS selectors
POST   /oxygen/v1/selectors               # Update selectors
POST   /oxygen/v1/variables               # Update design variables
POST   /oxygen/v1/presets                 # Update presets

# Global Settings
GET    /oxygen/v1/global-settings         # Get Oxygen global settings
POST   /oxygen/v1/global-settings         # Update global settings

# Template Conditions
GET    /oxygen/v1/template/{id}/conditions   # Get template rules
POST   /oxygen/v1/template/{id}/conditions   # Update template rules

# Cache & Maintenance
POST   /oxygen/v1/cache/regenerate/{id}      # Regenerate CSS for page
POST   /oxygen/v1/cache/regenerate-all       # Regenerate all CSS
POST   /oxygen/v1/cache/clear                # Clear all caches
POST   /oxygen/v1/repair/{id}                # Repair broken tree

# Builder & Validation
POST   /oxygen/v1/builder/load/{id}          # Load page in builder
POST   /oxygen/v1/validate-tree              # Validate tree structure
GET    /oxygen/v1/elements                   # Get available elements

# WordPress Data
GET    /oxygen/v1/wp/post-types              # Get WordPress post types
GET    /oxygen/v1/wp/taxonomies              # Get WordPress taxonomies
GET    /oxygen/v1/wp/menus                   # Get WordPress menus
```

---

## WordPress Data Available

### Post Types

```json
{
  "post": {
    "name": "Posts",
    "taxonomies": ["category", "post_tag"]
  },
  "page": {
    "name": "Pages",
    "taxonomies": []
  },
  "nav_menu_item": {
    "name": "Navigation Menu Items",
    "taxonomies": ["nav_menu"]
  },
  "wp_block": {
    "name": "Patterns",
    "taxonomies": ["wp_pattern_category"]
  }
}
```

### Taxonomies

```json
{
  "category": {
    "name": "Categories",
    "hierarchical": true,
    "types": ["post"]
  },
  "post_tag": {
    "name": "Tags",
    "hierarchical": false,
    "types": ["post"]
  },
  "nav_menu": {
    "name": "Navigation Menus",
    "hierarchical": false,
    "types": ["nav_menu_item"]
  }
}
```

### Menus (Currently Empty)

**Status:** No menus configured on test site

**Available Operations:**
- Create menus via `/wp/v2/menus`
- Add menu items via `/wp/v2/menu-items`
- Reference menus in MenuBuilder elements

**Current Approach:** Hardcoded menu structure in Header 11

### Site Settings

```json
{
  "title": "Site Title",
  "description": "Site Description",
  "timezone_string": "America/New_York",
  "date_format": "F j, Y",
  "time_format": "g:i a",
  "start_of_week": 1,
  "language": "en_US",
  "posts_per_page": 10,
  "default_category": 1,
  "default_post_format": "standard"
}
```

---

## Integration Gaps

### Critical Gaps

#### 1. Dynamic WordPress Menus

**Current State:** Hardcoded menu structure in JSON
```json
{
  "type": "EssentialElements\\MenuBuilder",
  "children": [
    {
      "type": "EssentialElements\\MenuLink",
      "properties": {
        "content": {
          "content": {
            "text": "Home",
            "link": {"type": "url", "url": "#"}
          }
        }
      }
    }
  ]
}
```

**Desired State:** Reference WordPress menu by ID
```clojure
;; Forma EDN
[:menu-builder {:menu-id 123
                :theme-location "primary"}]

;; Compiles to Oxygen element that references WP menu
{:type "EssentialElements\\Menu"  ; or MenuBuilder with dynamic source
 :properties {:menu-id 123}}
```

**Impact:** Menu changes require page redeployment

**Solution:** Add `:dynamic-menu-id` property support in Forma compiler

---

#### 2. Post Loops / Query Builder

**Current State:** No dynamic post queries
**Gap:** Cannot display lists of posts, categories, or custom post types

**Desired State:**
```clojure
;; Forma EDN
[:post-loop {:query {:post-type "post"
                    :posts-per-page 10
                    :category "news"
                    :orderby "date"}
            :template [:card
                      [:heading {:text "{{post.title}}"}]
                      [:text {:text "{{post.excerpt}}"}]]}]

;; Compiles to Oxygen Posts_Loop element
{:type "OxygenElements\\Posts_Loop"
 :properties {:query {:post_type "post"
                     :posts_per_page 10
                     :tax_query [{:taxonomy "category"
                                 :field "slug"
                                 :terms ["news"]}]}
             :template {:children [...]}}}
```

**Available Element:** `OxygenElements\Posts_Loop`

**Impact:** No blog archives, category pages, or dynamic lists

**Solution:** Add `:post-loop` element type to Forma compiler

---

#### 3. Dynamic Data Tags

**Current State:** Static text values only

**Gap:** Cannot use dynamic content like `{{post.title}}` or `{{acf.field_name}}`

**Desired State:**
```clojure
;; Forma EDN with dynamic data
[:heading {:text "{{post.title}}"
          :dynamic true}]

;; Compiles to Oxygen element with dynamic data preserved
{:type "OxygenElements\\Text"
 :properties {:content {:content {:text "{{post.title}}"}}}}
```

**Available Tags** (from Oxygen/Breakdance):
- `{{post.title}}`, `{{post.content}}`, `{{post.excerpt}}`
- `{{post.date}}`, `{{post.author}}`
- `{{post.featured_image}}`
- `{{acf.field_name}}` (if ACF installed)
- `{{woo.price}}`, `{{woo.add_to_cart}}` (if WooCommerce installed)

**Impact:** Templates cannot adapt to current post context

**Solution:** Support dynamic data syntax in Forma text fields

---

#### 4. Component Definition Retrieval

**Current State:** Component references work (`componentId: 24`)

**Gap:** Cannot fetch component tree structure via API

**Available Components:**
- Component 24: "New Component" (Heading + WpWidget + nested Component 28)
- Component 28: "Comments" (CommentForm)

**Workaround:** Cache component definitions or manually include

**Solution:** Add component caching system in Forma

---

#### 5. Custom Field Integration

**Current State:** No ACF, Meta Box, or custom field support

**Gap:** Cannot access custom fields programmatically

**Desired State:**
```clojure
[:text {:text "{{acf.company_name}}"
        :field-source :acf
        :field-name "company_name"}]
```

**Impact:** Limited to core WordPress data

**Solution:** Add `:dynamic-field` property type for ACF/Meta Box fields

---

### Minor Gaps

#### 6. WooCommerce Elements

**Status:** WooCommerce NOT installed on test site

**Available Elements:**
- `EssentialElements\MenuCart` - Shopping cart icon
- `EssentialElements\WooGlobalStyler` - Global WooCommerce styles
- `EssentialElements\WooPresetInputs` - Input styling
- `EssentialElements\WooPresetStock` - Stock status

**Impact:** N/A (not installed)

**Solution:** Add when WooCommerce is installed

---

#### 7. Form Builder Elements

**Status:** Form plugins not detected

**Available Elements:**
- `EssentialElements\contact-form-7` - Contact Form 7
- `EssentialElements\gravity-forms` - Gravity Forms
- `EssentialElements\ninja-forms` - Ninja Forms

**Impact:** No forms currently

**Solution:** Add when form plugins are installed

---

#### 8. Conditional Visibility

**Current State:** No conditional rendering support

**Gap:** Cannot show/hide elements based on user role, post type, etc.

**Desired State:**
```clojure
[:section {:conditions {:type :user-role
                       :op :eq
                       :val "admin"}}
 [:text "Admin only content"]]
```

**Solution:** Add `:conditions` property support

---

## Roadmap

### Phase 1: Static Content (COMPLETE ✅)

**Timeline:** Completed 2025-01-17

**Features:**
- ✅ Deploy static pages via REST API
- ✅ Support OxygenElements and EssentialElements
- ✅ Reference global blocks (components)
- ✅ Include WordPress widgets
- ✅ Correct tree structure with `status: "exported"`

**Status:** Working and documented

---

### Phase 2: Dynamic WordPress Integration (1-2 weeks)

**Timeline:** Next sprint

**Features:**
1. **Dynamic Menu Support**
   ```clojure
   [:menu-builder {:menu-id 123}]
   ```

2. **Post Loop Implementation**
   ```clojure
   [:post-loop {:query {...}
               :template [...]}]
   ```

3. **Dynamic Data Tags**
   ```clojure
   [:heading {:text "{{post.title}}"}]
   ```

4. **Component Caching**
   - Fetch component definitions from `/oxygen/v1/blocks`
   - Cache component trees locally
   - Inline expand or reference by ID

**Deliverables:**
- Update `forma.compiler` with dynamic element support
- Add `oxygen-dynamic.edn` platform extension
- Document dynamic data tag syntax
- Create examples for common patterns

---

### Phase 3: Advanced Features (1-2 months)

**Timeline:** Q2 2025

**Features:**
1. **Query Builder Integration**
   ```clojure
   [:query-builder {:type :custom
                   :tax-query [{:taxonomy "category"
                               :field "slug"
                               :terms ["news"]}]}]
   ```

2. **Custom Field Support (ACF)**
   ```clojure
   [:text {:text "{{acf.company_name}}"
          :field-source :acf}]
   ```

3. **Conditional Visibility**
   ```clojure
   [:section {:conditions {:type :user-role
                          :op :eq
                          :val "admin"}}]
   ```

4. **Form Integration**
   ```clojure
   [:contact-form-7 {:form-id 123}]
   [:gravity-form {:form-id 456}]
   ```

5. **WordPress Menu Creation**
   - Create menus programmatically
   - Add menu items via API
   - Reference in pages

**Deliverables:**
- Advanced query builder compiler
- ACF field resolution
- Conditional rendering engine
- Form builder integration
- Menu management API wrapper

---

### Phase 4: WooCommerce & E-commerce (3-6 months)

**Timeline:** Q3 2025 (if needed)

**Features:**
1. **Product Catalog**
   ```clojure
   [:products-loop {:category "electronics"
                   :per-page 12}]
   ```

2. **Shopping Cart Elements**
   ```clojure
   [:woo-cart {:icon "cart"}]
   [:add-to-cart-button {:product-id 123}]
   ```

3. **Checkout Flow**
   ```clojure
   [:woo-checkout]
   [:woo-my-account]
   ```

**Prerequisites:**
- Install WooCommerce on hbtcomputers.com.au
- Configure products, categories
- Test WooCommerce elements

**Deliverables:**
- WooCommerce element catalog
- Product query support
- Cart/checkout page templates
- Order tracking integration

---

## Example: Full Integration Workflow

### Current (Phase 1 - Static)

```clojure
;; Forma EDN
[:page {:title "About Us"}
 [:section {:bg "$colors.primary"}
  [:heading {:text "About Our Company" :level 1}]
  [:text {:text "We are a great company..."}]]]

;; Deploy
(deploy-to-oxygen forma-edn)
;; => Creates page with static content
```

### Future (Phase 2 - Dynamic)

```clojure
;; Forma EDN with dynamic content
[:page {:title "Blog"
        :template :blog-archive}
 [:section {:bg "$colors.primary"}
  [:heading {:text "Latest Posts" :level 1}]
  [:post-loop {:query {:posts-per-page 10
                      :category "news"}
              :template
               [:card
                [:heading {:text "{{post.title}}" :level 2}]
                [:image {:src "{{post.featured_image}}"}]
                [:text {:text "{{post.excerpt}}"}]
                [:link {:text "Read More" :url "{{post.permalink}}"}]]}]]]

;; Deploy
(deploy-to-oxygen forma-edn)
;; => Creates page with dynamic post loop
;; => Oxygen/Breakdance resolves {{tags}} at runtime
```

---

## Integration Best Practices

### 1. Use WordPress Menus

**Create menus in WordPress admin:**
```bash
# Via WP Admin UI
Appearance → Menus → Create New Menu

# Or via REST API
POST /wp/v2/menus
{
  "name": "Primary Menu",
  "locations": ["primary"]
}

POST /wp/v2/menu-items
{
  "title": "Home",
  "url": "/",
  "menu_order": 1,
  "menus": [123]  # Menu ID
}
```

**Reference in Forma:**
```clojure
[:header
 [:menu-builder {:menu-id 123}]]
```

### 2. Define Categories First

**Create categories before querying:**
```bash
POST /wp/v2/categories
{
  "name": "News",
  "slug": "news"
}
```

**Use in post loops:**
```clojure
[:post-loop {:query {:category "news"}}]
```

### 3. Cache Component Definitions

**Fetch components once:**
```clojure
(defonce components-cache
  (atom (fetch-components)))

(defn get-component [id]
  (get @components-cache id))
```

### 4. Validate Dynamic Tags

**Before deployment:**
```clojure
(defn validate-dynamic-tags [element]
  (when (re-find #"\{\{([^}]+)\}\}" (:text element))
    (validate-tag-exists (:text element))))
```

---

## WordPress Configuration Checklist

Before deploying dynamic content:

- [ ] Create WordPress menus (if using dynamic menus)
- [ ] Configure post categories and tags
- [ ] Install form plugins (Contact Form 7, Gravity Forms)
- [ ] Install ACF if using custom fields
- [ ] Install WooCommerce if using e-commerce
- [ ] Configure permalinks (Settings → Permalinks)
- [ ] Set reading settings (Posts per page, front page)
- [ ] Configure timezone and date formats
- [ ] Test REST API authentication

---

## Troubleshooting

### Menu Not Showing

**Problem:** Menu elements render but show no items

**Solutions:**
1. Check if WordPress menu exists: `GET /wp/v2/menus`
2. Verify menu has items: `GET /wp/v2/menu-items`
3. Confirm menu-id in element properties
4. Fallback to hardcoded menu structure

### Post Loop Returns Empty

**Problem:** Posts_Loop element shows no posts

**Solutions:**
1. Verify posts exist: `GET /wp/v2/posts`
2. Check query parameters (category slug, post_type)
3. Test query in WordPress directly
4. Check user permissions (authentication)

### Dynamic Tags Not Resolving

**Problem:** `{{post.title}}` shows literally in output

**Solutions:**
1. Verify Oxygen/Breakdance runtime is active
2. Check element is in post context (single post page)
3. Confirm dynamic data syntax is correct
4. Test in Oxygen builder first

---

## Resources

### Documentation

- [WordPress REST API Handbook](https://developer.wordpress.org/rest-api/)
- [Oxygen Builder Documentation](https://oxygenbuilder.com/documentation/)
- [Breakdance Documentation](https://breakdance.com/documentation/)

### API References

- [WordPress REST API Reference](https://developer.wordpress.org/rest-api/reference/)
- [Oxygen REST Bridge GitHub](https://github.com/oxygenbui/oxygen-rest-bridge) (if available)

### Forma Documents

- [OXYGEN_CORRECT_STRUCTURE.md](OXYGEN_CORRECT_STRUCTURE.md) - Tree structure requirements
- [OXYGEN_ARCHITECTURE.md](OXYGEN_ARCHITECTURE.md) - Complete architecture guide
- [OXYGEN_BREAKDANCE_COMPARISON.md](OXYGEN_BREAKDANCE_COMPARISON.md) - Element systems comparison
- [FORMA_OXYGEN_COMPILER_ROADMAP.md](FORMA_OXYGEN_COMPILER_ROADMAP.md) - Compiler development plan

---

**Last Updated:** 2025-01-17
**WordPress Version:** 6.7.1
**Oxygen Version:** Breakdance 6.0.0-beta.3 (Oxygen mode)
**Test Site:** http://hbtcomputers.com.au.test
