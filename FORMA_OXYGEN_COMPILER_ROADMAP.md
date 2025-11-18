# Forma → Oxygen Compiler Roadmap

**Status:** 📋 **PLANNING PHASE**
**Date:** 2025-01-17
**Target:** Forma EDN → Oxygen/Breakdance JSON compilation
**Current Phase:** Phase 1 (Manual JSON generation)

---

## Vision

**Goal:** Enable conversational design where users describe what they want, and Forma generates deployable Oxygen/Breakdance pages.

```
User Request: "Create a landing page with hero section, features grid, and testimonials"
    ↓
Forma EDN Generation (AI-assisted)
    ↓
forma.compiler/compile-to-oxygen
    ↓
Oxygen JSON Tree Structure
    ↓
POST /oxygen/v1/save
    ↓
Live WordPress Page
```

---

## Architecture Overview

### Current Forma Pipeline

```
EDN Input
    ↓
Parse (forma.compiler/parse-element)
    ↓
Expand (property shortcuts)
    ↓
Resolve (inheritance + tokens)
    ↓
Apply Styling (design system)
    ↓
Compile (platform-specific)
    ↓
Output (HTML, Hiccup, CSS, etc.)
```

### New Oxygen Platform Extension

```
Forma EDN
    ↓
forma.compiler/compile-to-oxygen
    ├─ Parse Forma elements
    ├─ Resolve design tokens
    ├─ Map to Oxygen element types
    ├─ Generate node IDs
    ├─ Build tree structure
    └─ Add platform metadata
    ↓
Oxygen JSON Tree
    ↓
forma.sync.client/deploy
    ↓
WordPress via REST API
```

---

## Phase 1: Static Element Support ✅ (COMPLETE)

**Timeline:** Completed 2025-01-17

### Deliverables

- [x] Understand Oxygen tree structure
- [x] Document element types (OxygenElements + EssentialElements)
- [x] Identify critical fields (_nextNodeId, status: "exported")
- [x] Create deployment scripts (deploy_correct_structure.clj)
- [x] Successfully deploy test page (Page ID 46)
- [x] Verify builder can open deployed pages

### What Works

**Manual JSON generation:**
```clojure
(def tree
  {:_nextNodeId 200
   :status "exported"
   :root {:id 1
          :data {:type "root" :properties []}
          :children [{:id 100
                     :data {:type "OxygenElements\\Container"
                            :properties {...}}}]}})

(deploy-to-oxygen tree)
```

**Element types supported:**
- Container, Section, Div
- Text, Heading, RichText
- Image, Video
- Button, Link
- Accordion, IconList, Testimonial
- Code elements (HTML, CSS, JavaScript, PHP)
- WordPress widgets
- Component references

---

## Phase 2: Forma Compiler Integration (2-4 weeks)

**Timeline:** Q1 2025

### Goals

1. **Create oxygen.edn Platform Configuration**
2. **Implement forma.platforms.oxygen Namespace**
3. **Build Element Mapping System**
4. **Add Node ID Generation**
5. **Support Component References**

### 1. Platform Configuration

**File:** `forma/default/platforms/oxygen.edn`

```clojure
{:platform :oxygen
 :version "6.0.0-beta.3"
 :mode "oxygen"  ; or "breakdance"

 :elements {
   ;; OxygenElements (basic)
   :container {:element "OxygenElements\\Container"
              :tag "div"
              :nestable true
              :properties {
                :design {:background :color
                        :padding :spacing
                        :layout_v2 {:layout [:vertical :horizontal]}}}}

   :text {:element "OxygenElements\\Text"
         :tag "div"
         :properties {
           :content {:text :string}
           :design {:typography {:color :color
                                :size :font-size}}}}

   :heading {:element "OxygenElements\\Heading"
            :tag "h1"
            :tag-options ["h1" "h2" "h3" "h4" "h5" "h6"]
            :properties {
              :content {:content {:text :string
                                 :tags :heading-level}}}}

   ;; EssentialElements (advanced)
   :button {:element "EssentialElements\\Atom_V1_Button"
           :tag "button"
           :properties {
             :content {:text :string
                      :link :url}}}

   :menu-builder {:element "EssentialElements\\MenuBuilder"
                 :nestable true
                 :children [:menu-link :menu-dropdown]}

   :component {:element "OxygenElements\\Component"
              :properties {
                :content {:block {:componentId :integer
                                 :targets :array}}}}
 }

 :compiler {
   :extractors {
     :design {:type :property-selector
             :keys [:padding :margin :background :color :typography]}
     :content {:type :property-selector
              :keys [:text :image :video :link]}
     :settings {:type :property-selector
               :keys [:tag :class :id :attributes]}}

   :property-paths {
     ;; Map Forma properties to Oxygen paths
     :text [:properties :content :content :text]
     :color [:properties :design :typography :color]
     :background [:properties :design :background]
     :padding [:properties :design :padding]}

   :node-id-strategy :sequential  ; Start at 100, increment
   :root-id 1
   :initial-next-id 200
 }
}
```

### 2. Compiler Implementation

**File:** `forma/src/forma/platforms/oxygen.clj`

```clojure
(ns forma.platforms.oxygen
  (:require [forma.compiler :as compiler]
            [cheshire.core :as json]))

(defn load-oxygen-config []
  "Load oxygen.edn platform configuration"
  (read-string (slurp "forma/default/platforms/oxygen.edn")))

(defn generate-node-id [id-counter]
  "Generate sequential node ID starting at 100"
  (swap! id-counter inc))

(defn map-element-type [forma-type]
  "Map Forma element type to Oxygen element class"
  (let [config (load-oxygen-config)
        elements (:elements config)]
    (get-in elements [forma-type :element])))

(defn build-properties [forma-props oxygen-config]
  "Build Oxygen properties from Forma properties"
  (let [property-paths (get-in oxygen-config [:compiler :property-paths])]
    (->> forma-props
         (map (fn [[k v]]
                (let [path (get property-paths k)]
                  (when path
                    [path v]))))
         (filter some?)
         (reduce (fn [acc [path value]]
                   (assoc-in acc path value))
                 {}))))

(defn compile-element [forma-element id-counter parent-id]
  "Compile single Forma element to Oxygen node"
  (let [[[tag props & children]] forma-element
        node-id (generate-node-id id-counter)
        oxygen-type (map-element-type tag)
        oxygen-props (build-properties props (load-oxygen-config))
        compiled-children (mapv #(compile-element % id-counter node-id) children)]
    {:id node-id
     :data {:type oxygen-type
            :properties oxygen-props}
     :children compiled-children
     :_parentId parent-id}))

(defn compile-to-oxygen [forma-edn options]
  "Compile Forma EDN to Oxygen tree structure"
  (let [id-counter (atom 99)
        root-children (mapv #(compile-element % id-counter 1) forma-edn)
        max-id @id-counter
        next-id (inc max-id)]
    {:tree {:_nextNodeId next-id
            :status "exported"
            :root {:id 1
                   :data {:type "root"
                          :properties []}
                   :children root-children}}}))

;; Example usage
(comment
  (compile-to-oxygen
    [[:container {:bg "#1a202c" :padding "20px"}
      [:heading {:text "Hello World" :level 1}]
      [:text {:text "This is a paragraph."}]]]
    {:project-name "dashboard-example"})

  ;; => {:tree {:_nextNodeId 103
  ;;            :status "exported"
  ;;            :root {:id 1 ...}}}
)
```

### 3. Integration with Existing Compiler

**File:** `forma/src/forma/compiler.clj` (update)

```clojure
(ns forma.compiler
  (:require [forma.platforms.html :as html]
            [forma.platforms.css :as css]
            [forma.platforms.oxygen :as oxygen]))

(defn compile-to-platform [forma-edn platform options]
  "Compile Forma EDN to target platform"
  (case platform
    :html (html/compile forma-edn options)
    :css (css/compile forma-edn options)
    :oxygen (oxygen/compile-to-oxygen forma-edn options)
    :htmx (html/compile-with-htmx forma-edn options)
    (throw (ex-info "Unknown platform" {:platform platform}))))

;; New function for Oxygen deployment
(defn compile-and-deploy-to-oxygen [forma-edn options]
  "Compile Forma EDN and deploy to WordPress/Oxygen"
  (let [oxygen-tree (compile-to-platform forma-edn :oxygen options)
        deploy-result (forma.sync.client/deploy-page oxygen-tree options)]
    deploy-result))
```

### 4. Property Mapping Examples

**Forma → Oxygen property transformations:**

```clojure
;; Forma EDN
[:text {:content "Hello"
        :color "$colors.primary"
        :font-size "18px"}]

;; After token resolution
[:text {:content "Hello"
        :color "#0066cc"
        :font-size "18px"}]

;; Compiled to Oxygen
{:id 100
 :data {:type "OxygenElements\\Text"
        :properties {:content {:content {:text "Hello"}}
                    :design {:typography {:color "#0066cc"
                                        :size {:number 18
                                              :unit "px"
                                              :style "18px"}}}}}}
```

### 5. Deliverables

- [ ] Create `forma/default/platforms/oxygen.edn`
- [ ] Implement `forma/src/forma/platforms/oxygen.clj`
- [ ] Add Oxygen compilation to `forma.compiler`
- [ ] Write tests for element mapping
- [ ] Document property paths
- [ ] Create examples for common patterns
- [ ] Test deployment to WordPress

---

## Phase 3: Dynamic WordPress Integration (4-8 weeks)

**Timeline:** Q2 2025

### Goals

1. **Dynamic Menu Support**
2. **Post Loop Elements**
3. **Dynamic Data Tags**
4. **Component Caching**
5. **Query Builder**

### 1. Dynamic Menus

**Forma EDN:**
```clojure
[:menu-builder {:menu-id 123
                :fallback [:menu-link {:text "Home" :url "/"}]}]
```

**Compilation:**
```clojure
(defn compile-menu-builder [props id-counter parent-id]
  (if-let [menu-id (:menu-id props)]
    ;; Dynamic menu reference
    {:id (generate-node-id id-counter)
     :data {:type "EssentialElements\\MenuBuilder"
            :properties {:menu {:id menu-id
                               :source "wp_nav_menu"}}}}
    ;; Static menu (fallback)
    (compile-static-menu (:fallback props) id-counter parent-id)))
```

### 2. Post Loops

**Forma EDN:**
```clojure
[:post-loop {:query {:post-type "post"
                    :posts-per-page 10
                    :category "news"}
            :template [:card
                      [:heading {:text "{{post.title}}"}]
                      [:text {:text "{{post.excerpt}}"}]]}]
```

**Compilation:**
```clojure
(defn compile-post-loop [props id-counter parent-id]
  (let [query (:query props)
        template (:template props)
        compiled-template (compile-element template id-counter parent-id)]
    {:id (generate-node-id id-counter)
     :data {:type "OxygenElements\\Posts_Loop"
            :properties {:query {:post_type (get query :post-type "post")
                                :posts_per_page (get query :posts-per-page 10)
                                :tax_query (build-tax-query query)}
                        :template compiled-template}}}))
```

### 3. Dynamic Data Tags

**Preserve dynamic tags in output:**
```clojure
(defn compile-text-with-dynamic-data [text]
  "Preserve {{tags}} for Oxygen runtime resolution"
  (if (re-find #"\{\{([^}]+)\}\}" text)
    text  ; Keep as-is
    text))

[:heading {:text "{{post.title}}"}]
;; => {:properties {:content {:content {:text "{{post.title}}"}}}}
```

### 4. Component Caching

**Fetch and cache components:**
```clojure
(defonce component-cache (atom {}))

(defn fetch-components []
  "Fetch all global blocks from Oxygen API"
  (let [response (http/get "http://site/index.php?rest_route=/oxygen/v1/blocks"
                           {:basic-auth [user pass]})
        blocks (get-in (json/parse-string (:body response) true)
                       [:blocks :blocks])]
    (reduce (fn [cache block]
              (assoc cache (:id block) (:tree block)))
            {}
            blocks)))

(defn get-component [component-id]
  (when (empty? @component-cache)
    (swap! component-cache (constantly (fetch-components))))
  (get @component-cache component-id))

;; Usage in compilation
[:component {:component-id 24}]
;; => Check if component 24 exists, warn if not
```

### 5. Deliverables

- [ ] Add dynamic menu compilation
- [ ] Implement post-loop element
- [ ] Support dynamic data tag preservation
- [ ] Build component cache system
- [ ] Add query builder support
- [ ] Test with real WordPress data
- [ ] Document dynamic patterns

---

## Phase 4: Advanced Features (2-3 months)

**Timeline:** Q3 2025

### Goals

1. **Custom Field Integration (ACF)**
2. **Conditional Visibility**
3. **Form Integration**
4. **WooCommerce Elements**
5. **Template System**

### 1. ACF Integration

```clojure
[:text {:text "{{acf.company_name}}"
        :field-source :acf
        :field-name "company_name"
        :fallback "Company Name"}]
```

### 2. Conditional Visibility

```clojure
[:section {:conditions {:type :user-role
                       :op :eq
                       :val "admin"}}
 [:text "Admin only content"]]
```

### 3. Form Integration

```clojure
[:contact-form-7 {:form-id 123}]
[:gravity-form {:form-id 456
               :ajax true}]
```

### 4. WooCommerce

```clojure
[:products-loop {:category "electronics"
                :per-page 12}]
[:add-to-cart-button {:product-id 789}]
```

### 5. Deliverables

- [ ] ACF field resolution
- [ ] Conditional rendering support
- [ ] Form plugin integration
- [ ] WooCommerce element catalog
- [ ] Template inheritance system
- [ ] Full documentation

---

## Technical Considerations

### 1. Node ID Management

**Strategy:** Sequential IDs starting at 100

```clojure
(defn calculate-next-node-id [tree]
  "Calculate next available node ID from tree"
  (let [all-ids (collect-all-node-ids tree)
        max-id (apply max (conj all-ids 1))]
    (inc max-id)))
```

**Rules:**
- Root always has ID 1
- Elements start at 100
- Increment sequentially
- Set `_nextNodeId` to MAX + 1

### 2. Property Path Resolution

**Use EDN configuration for property mappings:**

```clojure
{:property-paths
  {:text [:properties :content :content :text]
   :color [:properties :design :typography :color]
   :background [:properties :design :background]
   :padding [:properties :design :padding]
   :margin [:properties :design :spacing :margin]}}
```

### 3. Element Catalog Discovery

**Scan plugin directories for available elements:**

```clojure
(defn discover-elements []
  "Discover all available Oxygen/Breakdance elements"
  (let [oxygen-elements (scan-directory "oxygen/subplugins/oxygen-elements/elements/")
        essential-elements (scan-directory "oxygen/subplugins/breakdance-elements/elements/")]
    {:oxygen-elements oxygen-elements
     :essential-elements essential-elements}))
```

### 4. Twig Template Integration

**Parse Twig templates for property structure:**

```clojure
(defn parse-twig-template [element-path]
  "Parse Twig template to extract property paths"
  (let [html-twig (slurp (str element-path "/html.twig"))
        css-twig (slurp (str element-path "/css.twig"))]
    (extract-property-paths html-twig css-twig)))
```

---

## Testing Strategy

### Unit Tests

```clojure
(deftest test-element-compilation
  (is (= (compile-element [:text {:content "Hello"}] (atom 99) 1)
         {:id 100
          :data {:type "OxygenElements\\Text"
                 :properties {:content {:content {:text "Hello"}}}}
          :children []
          :_parentId 1})))

(deftest test-tree-structure
  (let [tree (compile-to-oxygen [[:container [:text {:content "Test"}]]])]
    (is (= (get-in tree [:tree :_nextNodeId]) 102))
    (is (= (get-in tree [:tree :status]) "exported"))
    (is (= (get-in tree [:tree :root :id]) 1))))
```

### Integration Tests

```clojure
(deftest test-deploy-to-wordpress
  (let [forma-edn [[:heading {:text "Test Page"}]]
        result (compile-and-deploy-to-oxygen forma-edn {:title "Test"})]
    (is (:success result))
    (is (number? (:id result)))))
```

### End-to-End Tests

1. Compile Forma EDN
2. Deploy to WordPress
3. Fetch page via REST API
4. Verify tree structure
5. Open in Oxygen builder
6. Confirm preview renders correctly

---

## Documentation Plan

### Developer Documentation

- [ ] `OXYGEN_COMPILER_GUIDE.md` - How to use the compiler
- [ ] `ELEMENT_MAPPING_REFERENCE.md` - Element type mappings
- [ ] `PROPERTY_PATHS_GUIDE.md` - Property transformation rules
- [ ] API documentation for public functions

### User Documentation

- [ ] Forma → Oxygen quick start
- [ ] Element catalog with examples
- [ ] Common patterns cookbook
- [ ] Troubleshooting guide

---

## Success Metrics

### Phase 2

- ✅ Compile 20+ element types
- ✅ Pass all unit tests (100% coverage)
- ✅ Deploy 10+ test pages successfully
- ✅ Pages open in Oxygen builder without errors

### Phase 3

- ✅ Dynamic menus work on 5+ sites
- ✅ Post loops display correctly
- ✅ Dynamic data tags resolve at runtime
- ✅ Component caching reduces API calls by 80%

### Phase 4

- ✅ ACF fields integrate seamlessly
- ✅ Conditional visibility works for 10+ use cases
- ✅ Form integrations tested with 3+ plugins
- ✅ WooCommerce catalog displays 100+ products

---

## Conclusion

The Forma → Oxygen compiler will enable **conversational design** where users describe what they want, and Forma generates deployable WordPress pages. The roadmap progresses from:

1. **Phase 1:** Manual JSON (COMPLETE ✅)
2. **Phase 2:** Automated compilation (2-4 weeks)
3. **Phase 3:** Dynamic WordPress integration (4-8 weeks)
4. **Phase 4:** Advanced features (2-3 months)

By following this roadmap, Forma will become a powerful tool for programmatic WordPress design.

---

**Last Updated:** 2025-01-17
**Current Phase:** Phase 1 Complete, Starting Phase 2
**Target Completion:** Q3 2025
