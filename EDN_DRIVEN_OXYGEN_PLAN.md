# EDN-Driven Oxygen Bidirectional Compilation Plan

## Problem Statement

Currently, Oxygen compilation has **hardcoded Clojure logic** in `oxygen_mapper.clj`:
- ❌ `tailwind->oxygen-properties` function (300+ lines)
- ❌ `oxygen->tailwind-properties` function (reverse)
- ❌ Property transformation logic (gradients, spacing, layout)
- ❌ All mapping rules embedded in code

**Goal**: Replace ALL platform-specific code with EDN configuration in `oxygen.edn`, just like `html.edn` does.

---

## Part 1: How HTML.edn Achieves Bidirectionality

### Forward Compilation (Forma EDN → HTML)

```clojure
;; html.edn :elements section
:button {:element "button"
         :attr-map {:url :href :on-click :onclick}}

;; Forma EDN
{:type :button :url "/submit" :on-click "handleClick()"}

;; Compiler reads :attr-map
;; → <button href="/submit" onclick="handleClick()">
```

### Reverse Compilation (HTML → Forma EDN)

```clojure
;; html.edn :parser section
:parser {
  :attribute-mappings {
    :href :url        ; HTML href → Forma :url
    :onclick :on-click
  }
}

;; HTML
;; <button href="/submit" onclick="handleClick()">

;; Parser reads :attribute-mappings in REVERSE
;; → {:type :button :url "/submit" :on-click "handleClick()"}
```

**Key Insight**: The SAME `:attr-map` is used bidirectionally!
- Forward: `:url` → `:href`
- Reverse: `:href` → `:url`

---

## Part 2: Oxygen's Property Schema Challenge

### Problem: Nested Property Structures

Oxygen doesn't use flat attributes like HTML. It uses **deeply nested property objects**:

```json
// Oxygen JSON
{
  "properties": {
    "design": {
      "layout_v2": {
        "layout": "grid",
        "grid": {
          "columnCount": 4,
          "columnGap": {"number": 24, "unit": "px"}
        }
      },
      "spacing": {
        "padding": {
          "top": {"number": 80, "unit": "px"},
          "bottom": {"number": 80, "unit": "px"}
        }
      },
      "typography": {
        "size": {"number": 48, "unit": "px"},
        "weight": "700",
        "color": "#ffffff"
      }
    }
  }
}
```

vs. Forma EDN (flat):

```clojure
{:display "grid"
 :grid-template-columns "repeat(4, 1fr)"
 :gap 24
 :padding "80px 20px"
 :font-size 48
 :font-weight 700
 :color "#ffffff"}
```

### Solution: Property Transform Rules in EDN

We need a **property mapping DSL** that handles:
1. **Path mapping**: Flat → Nested path
2. **Value transforms**: `"repeat(4, 1fr)"` → `{:columnCount 4}`
3. **Bidirectionality**: Same rules work both ways

---

## Part 3: Proposed oxygen.edn Structure

```clojure
{:platform :oxygen
 :description "Oxygen Builder JSON compilation rules"

 ;; ========================================================================
 ;; PARSER CONFIGURATION (Oxygen JSON → Forma EDN)
 ;; ========================================================================
 :parser {
   ;; Oxygen element type → Forma element type
   :element-mappings {
     "EssentialElements\\Section" :section
     "EssentialElements\\Heading" :heading
     "EssentialElements\\Text" :text
     "EssentialElements\\Button" :button
     "EssentialElements\\Div" :div
     "EssentialElements\\Image" :image
     "OxygenElements\\HtmlCode" :html-code
     "OxygenElements\\CssCode" :css-code
     "OxygenElements\\JavaScriptCode" :js-code
   }

   ;; Oxygen property path → Forma property + transform
   :property-mappings [
     ;; Layout properties
     {:oxygen-path [:design :layout_v2 :layout]
      :forma-key :display
      :transform :oxygen-layout->display
      :reverse :display->oxygen-layout}

     {:oxygen-path [:design :layout_v2 :grid :columnCount]
      :forma-key :grid-template-columns
      :transform :oxygen-grid->columns
      :reverse :columns->oxygen-grid}

     {:oxygen-path [:design :layout_v2 :grid :columnGap]
      :forma-key :gap
      :transform :oxygen-unit->number
      :reverse :number->oxygen-unit}

     ;; Spacing properties
     {:oxygen-path [:design :spacing :padding]
      :forma-key :padding
      :transform :oxygen-spacing->shorthand
      :reverse :shorthand->oxygen-spacing}

     {:oxygen-path [:design :spacing :margin]
      :forma-key :margin
      :transform :oxygen-spacing->shorthand
      :reverse :shorthand->oxygen-spacing}

     ;; Sizing properties
     {:oxygen-path [:design :sizing :minHeight]
      :forma-key :min-height
      :transform :oxygen-unit->value
      :reverse :value->oxygen-unit}

     {:oxygen-path [:design :sizing :maxWidth]
      :forma-key :max-width
      :transform :oxygen-unit->value
      :reverse :value->oxygen-unit}

     ;; Typography properties
     {:oxygen-path [:design :typography :size]
      :forma-key :font-size
      :transform :oxygen-unit->number
      :reverse :number->oxygen-unit}

     {:oxygen-path [:design :typography :weight]
      :forma-key :font-weight
      :transform :identity
      :reverse :identity}

     {:oxygen-path [:design :typography :color]
      :forma-key :color
      :transform :identity
      :reverse :identity}

     ;; Background properties
     {:oxygen-path [:design :background]
      :forma-key :background
      :transform :oxygen-background->css
      :reverse :css->oxygen-background}

     ;; Border properties
     {:oxygen-path [:design :borders :radius]
      :forma-key :border-radius
      :transform :oxygen-unit->value
      :reverse :value->oxygen-unit}

     ;; Effects properties
     {:oxygen-path [:design :effects :boxShadow]
      :forma-key :box-shadow
      :transform :oxygen-shadow->css
      :reverse :css->oxygen-shadow}

     ;; Content properties
     {:oxygen-path [:content :content :text]
      :forma-key :text
      :transform :identity
      :reverse :identity}
   ]

   ;; Metadata extraction (for round-trip)
   :metadata {
     :preserve-oxygen-id? true
     :preserve-parent-id? true
     :preserve-next-node-id? true
     :track-source-file? true
   }
 }

 ;; ========================================================================
 ;; COMPILER CONFIGURATION (Forma EDN → Oxygen JSON)
 ;; ========================================================================
 :compiler {
   :output-format :oxygen-json

   ;; ID generation strategy
   :id-generation {
     :strategy :incremental  ; or :uuid, :hash
     :start-id 100
   }

   ;; Extractors (what goes where in Oxygen schema)
   :extractors {
     :design-properties {
       :type :property-mapper
       :keys [:display :grid-template-columns :gap :padding :margin
              :min-height :max-width :font-size :font-weight :color
              :background :border-radius :box-shadow]
       :output-path [:design]
     }
     :content-properties {
       :type :property-mapper
       :keys [:text :html :css :javascript]
       :output-path [:content :content]
     }
   }

   ;; Tree structure rules
   :tree-structure {
     :root-type "root"
     :root-id 1
     :track-next-node-id true
     :parent-id-key :_parentId
     :children-key :children
   }
 }

 ;; ========================================================================
 ;; TRANSFORM FUNCTIONS (EDN-Driven Value Transforms)
 ;; ========================================================================
 :transforms {
   ;; Layout transforms
   :oxygen-layout->display {
     :type :map-lookup
     :map {"grid" "grid"
           "vertical" "flex"
           "horizontal" "flex"}
   }
   :display->oxygen-layout {
     :type :conditional
     :rules [
       {:when {:key :display :value "grid"}
        :then "grid"}
       {:when {:key :flex-direction :value "column"}
        :then "vertical"}
       {:when {:key :flex-direction :value "row"}
        :then "horizontal"}
       {:else "vertical"}
     ]
   }

   ;; Grid transforms
   :oxygen-grid->columns {
     :type :template
     :template "repeat({{columnCount}}, 1fr)"
   }
   :columns->oxygen-grid {
     :type :regex-extract
     :pattern "repeat\\((\\d+),\\s*1fr\\)"
     :capture-group 1
     :parse-as :integer
     :default 4
   }

   ;; Unit transforms
   :oxygen-unit->number {
     :type :path-access
     :path [:number]
   }
   :number->oxygen-unit {
     :type :object-constructor
     :template {:number "{{value}}" :unit "px"}
   }

   :oxygen-unit->value {
     :type :function
     :fn :construct-unit-string
     :args [:number :unit]
     :template "{{number}}{{unit}}"
   }
   :value->oxygen-unit {
     :type :regex-extract
     :pattern "(\\d+)(px|em|rem|%|vh|vw)"
     :captures {:number 1 :unit 2}
     :parse-number true
     :output {:number "{{number}}" :unit "{{unit}}"}
   }

   ;; Spacing transforms
   :oxygen-spacing->shorthand {
     :type :function
     :fn :spacing-to-shorthand
     :description "Convert {:top 80 :right 20 :bottom 80 :left 20} → '80px 20px'"
   }
   :shorthand->oxygen-spacing {
     :type :function
     :fn :shorthand-to-spacing
     :description "Convert '80px 20px' → {:top 80 :right 20 :bottom 80 :left 20}"
   }

   ;; Background transforms
   :oxygen-background->css {
     :type :function
     :fn :oxygen-gradient-to-css
     :description "Convert Oxygen gradient object → CSS string"
   }
   :css->oxygen-background {
     :type :function
     :fn :css-gradient-to-oxygen
     :description "Convert CSS gradient string → Oxygen structured format"
   }

   ;; Shadow transforms
   :oxygen-shadow->css {
     :type :function
     :fn :oxygen-shadow-to-css
     :description "Convert Oxygen shadow array → CSS box-shadow string"
   }
   :css->oxygen-shadow {
     :type :function
     :fn :css-shadow-to-oxygen
     :description "Convert CSS box-shadow string → Oxygen shadow array"
   }
 }

 ;; ========================================================================
 ;; BUILT-IN TRANSFORM FUNCTIONS (Clojure implementations)
 ;; ========================================================================
 :transform-functions {
   :spacing-to-shorthand {
     :description "Convert Oxygen spacing object to CSS shorthand"
     :implementation "forma.transforms.oxygen/spacing-to-shorthand"
   }
   :shorthand-to-spacing {
     :description "Parse CSS spacing shorthand to Oxygen object"
     :implementation "forma.transforms.oxygen/shorthand-to-spacing"
   }
   :oxygen-gradient-to-css {
     :description "Convert Oxygen gradient to CSS linear-gradient string"
     :implementation "forma.transforms.oxygen/gradient-to-css"
   }
   :css-gradient-to-oxygen {
     :description "Parse CSS gradient to Oxygen structured format"
     :implementation "forma.transforms.oxygen/css-to-gradient"
   }
   :construct-unit-string {
     :description "Concatenate number and unit (e.g., 100, 'px' → '100px')"
     :implementation "forma.transforms.common/construct-unit-string"
   }
 }

 ;; ========================================================================
 ;; ELEMENT MAPPINGS
 ;; ========================================================================
 :elements {
   :section {
     :element "EssentialElements\\Section"
     :properties-key :properties
     :design-path [:design]
     :content-path [:content :content]
     :children-handling :compile-all
   }
   :heading {
     :element "EssentialElements\\Heading"
     :properties-key :properties
     :content-path [:content :content :text]
     :design-path [:design]
     :prop-map {:level [:content :content :tags]
                :text [:content :content :text]}
   }
   :text {
     :element "EssentialElements\\Text"
     :properties-key :properties
     :content-path [:content :content :text]
     :design-path [:design]
   }
   :div {
     :element "EssentialElements\\Div"
     :properties-key :properties
     :design-path [:design]
     :children-handling :compile-all
   }
   :button {
     :element "EssentialElements\\Button"
     :properties-key :properties
     :content-path [:content :content :text]
     :design-path [:design]
     :prop-map {:url [:content :content :link :url]}
   }
   :html-code {
     :element "OxygenElements\\HtmlCode"
     :properties-key :properties
     :content-path [:content :content :html_code]
   }
   :css-code {
     :element "OxygenElements\\CssCode"
     :properties-key :properties
     :content-path [:content :content :css_code]
   }
   :js-code {
     :element "OxygenElements\\JavaScriptCode"
     :properties-key :properties
     :content-path [:content :content :javascript_code]
   }
 }
}
```

---

## Part 4: Generic Transform Engine

The compiler needs a **generic transform engine** that reads EDN transform rules and applies them:

### Transform Types (All EDN-Configurable)

1. **`:map-lookup`** - Simple value mapping
2. **`:conditional`** - Conditional logic
3. **`:template`** - String templating
4. **`:regex-extract`** - Pattern extraction
5. **`:path-access`** - Nested property access
6. **`:object-constructor`** - Build objects from templates
7. **`:function`** - Call pre-registered transform function

### Example: Gradient Transform

**EDN Configuration**:
```clojure
:css->oxygen-background {
  :type :function
  :fn :css-gradient-to-oxygen
  :description "Convert CSS gradient string → Oxygen structured format"
}
```

**Transform Function** (in `forma/src/forma/transforms/oxygen.clj`):
```clojure
(ns forma.transforms.oxygen
  "Oxygen-specific transform functions")

(defn css-gradient-to-oxygen
  "Parse CSS linear-gradient to Oxygen structured format

  Input: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)'
  Output: {:gradient {:points [...] :type 'linear' :degree 135 ...} :type 'gradient'}"
  [gradient-str]
  (let [degree (or (second (re-find #"(\\d+)deg" gradient-str)) 180)
        color-stops (re-seq #"#([0-9a-fA-F]{6})\\s+(\\d+)%" gradient-str)
        points (mapv parse-color-stop color-stops)
        svg-value (generate-svg-gradient points degree)]
    {:gradient {:points points
                :type "linear"
                :degree degree
                :svgValue svg-value
                :value gradient-str}
     :type "gradient"}))

(defn gradient-to-css
  "Convert Oxygen gradient object to CSS string

  Input: {:gradient {:points [...] :degree 135} :type 'gradient'}
  Output: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)'"
  [{:keys [gradient]}]
  (let [{:keys [points degree type]} gradient]
    (if (= type "linear")
      (str "linear-gradient(" degree "deg, "
           (str/join ", " (map point-to-css-stop points))
           ")")
      (str "radial-gradient("
           (str/join ", " (map point-to-css-stop points))
           ")"))))
```

**Key Principle**: The EDN config references the function by `:fn` keyword. The compiler looks it up in a registry and calls it.

---

## Part 5: Compiler Implementation (Generic, EDN-Driven)

### Current Code (Platform-Specific):
```clojure
;; oxygen_mapper.clj (WRONG - hardcoded)
(defn tailwind->oxygen-properties [props]
  (let [layout (map-layout props)
        spacing (map-spacing props)
        ...]
    {:layout_v2 layout
     :spacing spacing
     ...}))
```

### New Code (Generic, Reads EDN):
```clojure
;; forma/compiler.clj (RIGHT - generic)
(defn compile-properties
  "Generic property compiler - reads platform EDN config"
  [forma-props platform-config]
  (let [property-mappings (get-in platform-config [:parser :property-mappings])
        transforms (get platform-config :transforms)]
    (reduce (fn [oxygen-props mapping]
              (let [forma-key (:forma-key mapping)
                    forma-value (get forma-props forma-key)
                    oxygen-path (:oxygen-path mapping)
                    reverse-transform-key (:reverse mapping)
                    transform-config (get transforms reverse-transform-key)
                    oxygen-value (apply-transform forma-value transform-config transforms)]
                (assoc-in oxygen-props oxygen-path oxygen-value)))
            {}
            property-mappings)))

(defn apply-transform
  "Generic transform applicator - reads transform config and applies it"
  [value transform-config all-transforms]
  (case (:type transform-config)
    :map-lookup (get (:map transform-config) value)
    :template (apply-template (:template transform-config) value)
    :regex-extract (regex-extract value (:pattern transform-config) (:capture-group transform-config))
    :function (call-transform-function (:fn transform-config) value)
    :identity value
    value))
```

---

## Part 6: Bidirectional Workflow

### Forward (Forma EDN → Oxygen JSON)

```clojure
;; 1. Load oxygen.edn
(def oxygen-config (load-platform-config :oxygen))

;; 2. Forma EDN input
(def forma-edn
  {:type :section
   :display "grid"
   :grid-template-columns "repeat(4, 1fr)"
   :gap 24
   :padding "80px 20px"
   :background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
   :children [...]})

;; 3. Compile properties
(def oxygen-props
  (compile-properties forma-edn oxygen-config))

;; Result:
{:design {
  :layout_v2 {
    :layout "grid"
    :grid {:columnCount 4 :columnGap {:number 24 :unit "px"}}
  }
  :spacing {
    :padding {:top {:number 80 :unit "px"}
              :bottom {:number 80 :unit "px"}
              :left {:number 20 :unit "px"}
              :right {:number 20 :unit "px"}}
  }
  :background {
    :gradient {
      :points [{:left 0 :red 102 :green 126 :blue 234 :alpha 1}
               {:left 100 :red 118 :green 75 :blue 162 :alpha 1}]
      :type "linear"
      :degree 135
      :svgValue "<linearGradient...>"
      :value "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
    }
    :type "gradient"
  }
}}
```

### Reverse (Oxygen JSON → Forma EDN)

```clojure
;; 1. Load oxygen.edn
(def oxygen-config (load-platform-config :oxygen))

;; 2. Oxygen JSON input (from REST API)
(def oxygen-json
  {:id 200
   :data {:type "EssentialElements\\Section"
          :properties {:design {:layout_v2 {...}
                                :spacing {...}
                                :background {...}}}}
   :children [...]})

;; 3. Parse properties (SAME config, REVERSE direction)
(def forma-edn
  (parse-properties (:properties (:data oxygen-json)) oxygen-config))

;; Result:
{:type :section
 :display "grid"
 :grid-template-columns "repeat(4, 1fr)"
 :gap 24
 :padding "80px 20px"
 :background "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"
 :_oxygen-metadata {:id 200 :parent-id 1}}
```

---

## Part 7: Implementation Plan

### Phase 1: Create Complete oxygen.edn (1 week)
- [ ] Add `:parser` section with all property mappings
- [ ] Add `:transforms` section with all transform definitions
- [ ] Add `:transform-functions` registry
- [ ] Document all mapping rules

### Phase 2: Build Generic Transform Engine (1 week)
- [ ] Create `forma/src/forma/transforms/engine.clj`
- [ ] Implement `apply-transform` multimethod (dispatch on `:type`)
- [ ] Implement all transform types (map-lookup, template, regex-extract, etc.)
- [ ] Create `forma/src/forma/transforms/oxygen.clj` with Oxygen-specific functions

### Phase 3: Update Compiler to Use EDN Config (1 week)
- [ ] Modify `forma/compiler.clj` to read property mappings from EDN
- [ ] Remove hardcoded logic from `oxygen_mapper.clj`
- [ ] Add `compile-properties` generic function
- [ ] Add `parse-properties` generic function (reverse)

### Phase 4: Test Bidirectionality (3 days)
- [ ] Round-trip tests: Forma → Oxygen → Forma
- [ ] Verify 95%+ property preservation
- [ ] Test with real Mesh components
- [ ] Test gradient parsing both ways

### Phase 5: Documentation (2 days)
- [ ] Update CLAUDE.md with EDN-driven approach
- [ ] Document transform DSL
- [ ] Create examples for common transforms
- [ ] Migration guide from oxygen_mapper.clj

**Total Estimated Time**: 3-4 weeks

---

## Part 8: Benefits of EDN-Driven Approach

1. **Platform Agnostic**: Add new platforms by creating new EDN files (no code changes)
2. **Bidirectionality for Free**: Same config works both ways
3. **Testable**: Transform rules are data, easy to test
4. **Extensible**: Users can add custom transforms
5. **Debuggable**: Clear mapping of Forma → Platform properties
6. **Maintainable**: Changes to Oxygen schema = update EDN, not Clojure code

---

## Part 9: Mesh Deployment Revisited

With EDN-driven Oxygen compilation:

```clojure
;; 1. Define Mesh header in Forma EDN
(def mesh-header
  {:type :header
   :display "flex"
   :justify-content "space-between"
   :padding "20px"
   :background "#ffffff"
   :box-shadow "0 2px 4px rgba(0,0,0,0.1)"
   :children [
     {:type :div :class "logo" :text "HBT Computers"}
     {:type :div :class "nav" :children [...]}
   ]})

;; 2. Compile to Oxygen (reads oxygen.edn automatically)
(def oxygen-tree
  (compile-to-oxygen mesh-header {:platform-stack [:oxygen]}))

;; 3. Deploy to WordPress
(deploy-to-wordpress oxygen-tree)

;; 4. Pull from Oxygen (bidirectional!)
(def fetched-oxygen (fetch-from-wordpress page-id))

;; 5. Parse back to Forma EDN
(def forma-edn-reconstructed
  (parse-from-oxygen fetched-oxygen {:platform-stack [:oxygen]}))

;; 6. Verify round-trip
(= mesh-header forma-edn-reconstructed)  ; => true (with metadata stripped)
```

**No platform-specific Clojure code required!** Everything is driven by `oxygen.edn`.

---

## Conclusion

The path forward is clear:

1. **Complete oxygen.edn** with full bidirectional property mappings
2. **Build generic transform engine** that reads EDN transform rules
3. **Update compiler** to be platform-agnostic (reads EDN configs)
4. **Delete oxygen_mapper.clj** (replace with EDN config)
5. **Test bidirectionality** with real Mesh components

This achieves the Forma vision: **"completely generic, informed entirely by EDN files, no platform code"**.