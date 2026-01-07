# HTML/HTMX Platform Guide

This guide covers using Forma to compile to HTML, CSS, and HTMX for web applications.

## Overview

**Status:** ✅ Production (100% complete)

**Output:** Semantic HTML5 + CSS + HTMX attributes

The HTML/HTMX platform is Forma's most mature target, producing clean, semantic HTML with optional CSS styling and HTMX interactivity.

## Quick Start

```clojure
(require '[forma.compiler :as compiler])

;; Compile to HTML
(compiler/compile-to-html
  [[:button {:text "Click me"}]]
  {:platform-stack [:html]})

;; Output: "<button>Click me</button>"
```

## Platform Stack

The HTML platform supports three layers:

```clojure
{:platform-stack [:html :css :htmx]}
```

- **`:html`** - Semantic HTML elements
- **`:css`** - Styling (inline styles or classes)
- **`:htmx`** - HTMX attributes for interactivity

## Element Mappings

### Layout Elements

```clojure
;; Section
[:section {:design/background "#1a202c"}
  [:heading {:text "Title"}]]
;; → <section style="background: #1a202c"><h1>Title</h1></section>

;; Div (generic container)
[:div {:class "container"}
  [:text "Content"]]
;; → <div class="container"><p>Content</p></div>

;; Header/Footer
[:header
  [:nav [:link {:href "/"} "Home"]]]
;; → <header><nav><a href="/">Home</a></nav></header>
```

### Typography

```clojure
;; Headings (h1-h6)
[:heading {:text "Title" :level 1}]
;; → <h1>Title</h1>

[:heading {:text "Subtitle" :level 2}]
;; → <h2>Subtitle</h2>

;; Text/Paragraph
[:text "This is a paragraph"]
;; → <p>This is a paragraph</p>

;; Rich text (with HTML)
[:rich-text "<strong>Bold</strong> text"]
;; → <div><strong>Bold</strong> text</div>
```

### Interactive Elements

```clojure
;; Button
[:button {:text "Click" :variant :primary}]
;; → <button class="btn btn-primary">Click</button>

;; Link
[:link {:href "/about" :text "About"}]
;; → <a href="/about">About</a>

;; Form
[:form {:action "/submit" :method "post"}
  [:text-input {:name "email" :placeholder "Email"}]
  [:button {:type :submit :text "Submit"}]]
;; → <form action="/submit" method="post">
;;     <input type="text" name="email" placeholder="Email">
;;     <button type="submit">Submit</button>
;;   </form>
```

## Styling

### Design Properties

Use `:design/*` properties for styling:

```clojure
[:section {:design/background "#1a202c"
           :design/padding "60px 20px"
           :design/color "#ffffff"
           :design/border-radius "8px"}
  [:heading {:text "Styled Section"}]]

;; Output:
;; <section style="background: #1a202c; padding: 60px 20px; color: #ffffff; border-radius: 8px">
;;   <h1>Styled Section</h1>
;; </section>
```

### Supported CSS Properties

**Layout:**
- `:design/display` - `block`, `flex`, `grid`, `inline-block`
- `:design/padding` - `20px`, `10px 20px`, etc.
- `:design/margin` - Same as padding
- `:design/width`, `:design/height` - `100%`, `500px`, `auto`
- `:design/max-width`, `:design/min-width`

**Flexbox:**
- `:design/flex-direction` - `row`, `column`
- `:design/justify-content` - `center`, `space-between`, `flex-start`, `flex-end`
- `:design/align-items` - `center`, `flex-start`, `flex-end`, `stretch`
- `:design/gap` - `16px`

**Colors:**
- `:design/background` - `#1a202c`, `rgb(26, 32, 44)`, `var(--color-primary)`
- `:design/color` - Text color
- `:design/border-color`

**Typography:**
- `:design/font-size` - `16px`, `1.5rem`
- `:design/font-weight` - `bold`, `400`, `700`
- `:design/font-family` - `"Arial, sans-serif"`
- `:design/line-height` - `1.5`, `24px`
- `:design/text-align` - `center`, `left`, `right`

**Effects:**
- `:design/border` - `1px solid #ccc`
- `:design/border-radius` - `8px`
- `:design/box-shadow` - `0 2px 4px rgba(0,0,0,0.1)`
- `:design/opacity` - `0.8`

### Responsive Design

```clojure
[:section {:design/padding {:base "20px"
                             :md "40px"
                             :lg "60px"}}
  [:heading {:text "Responsive"}]]

;; Generates media queries:
;; <section style="padding: 20px">
;;   <h1>Responsive</h1>
;; </section>
;;
;; @media (min-width: 768px) {
;;   section { padding: 40px; }
;; }
;; @media (min-width: 1024px) {
;;   section { padding: 60px; }
;; }
```

### CSS Classes

Use shadcn-ui component classes:

```clojure
[:button {:variant :primary :text "Primary"}]
;; → <button class="btn btn-primary">Primary</button>

[:button {:variant :secondary :text "Secondary"}]
;; → <button class="btn btn-secondary">Secondary</button>

[:button {:variant :destructive :text "Delete"}]
;; → <button class="btn btn-destructive">Delete</button>
```

## HTMX Integration

### Basic HTMX

```clojure
[:button {:text "Load Content"
          :htmx/get "/api/content"
          :htmx/target "#result"
          :htmx/swap "innerHTML"}]

;; Output:
;; <button hx-get="/api/content" hx-target="#result" hx-swap="innerHTML">
;;   Load Content
;; </button>
```

### HTMX Attributes

**HTTP Methods:**
- `:htmx/get` - GET request
- `:htmx/post` - POST request
- `:htmx/put` - PUT request
- `:htmx/delete` - DELETE request

**Targeting:**
- `:htmx/target` - CSS selector for target element
- `:htmx/swap` - How to swap content (`innerHTML`, `outerHTML`, `beforebegin`, `afterbegin`, `beforeend`, `afterend`)

**Triggers:**
- `:htmx/trigger` - Event to trigger request (`click`, `change`, `submit`)

**Indicators:**
- `:htmx/indicator` - Loading indicator selector

### HTMX Examples

**Form submission:**
```clojure
[:form {:htmx/post "/api/submit"
        :htmx/target "#result"}
  [:text-input {:name "email"}]
  [:button {:type :submit :text "Submit"}]]
```

**Lazy loading:**
```clojure
[:div {:htmx/get "/api/lazy-content"
       :htmx/trigger "revealed"
       :htmx/swap "outerHTML"}
  [:text "Loading..."]]
```

**Polling:**
```clojure
[:div {:htmx/get "/api/stats"
       :htmx/trigger "every 5s"
       :htmx/swap "innerHTML"}
  [:text "Stats will update every 5 seconds"]]
```

## Component Examples

### Hero Section

```clojure
[:section {:design/background "#1a202c"
           :design/padding "80px 20px"
           :design/text-align "center"
           :design/color "#ffffff"}
  [:heading {:text "Professional IT Solutions" :level 1}]
  [:text "Expert computer services for your business"]
  [:button {:text "Get Started"
            :variant :primary
            :design/margin-top "20px"}]]
```

### Card

```clojure
[:card
  [:card-header
    [:card-title "Product Name"]]
  [:card-content
    [:text "Product description goes here"]]
  [:card-footer
    [:button {:text "Learn More"}]]]
```

### Navigation

```clojure
[:header
  [:nav {:design/display "flex"
         :design/justify-content "space-between"
         :design/padding "20px"}
    [:div {:class "logo"}
      [:link {:href "/" :text "Brand"}]]
    [:div {:class "nav-links"}
      [:link {:href "/about" :text "About"}]
      [:link {:href "/services" :text "Services"}]
      [:link {:href "/contact" :text "Contact"}]]]]
```

### Form

```clojure
[:form {:action "/submit" :method "post"
        :design/max-width "500px"
        :design/margin "0 auto"}
  [:text-input {:label "Name"
                :name "name"
                :placeholder "Enter your name"
                :required true}]
  [:text-input {:label "Email"
                :name "email"
                :type "email"
                :placeholder "Enter your email"
                :required true}]
  [:textarea {:label "Message"
              :name "message"
              :rows 5}]
  [:button {:type :submit
            :text "Send Message"
            :variant :primary}]]
```

## Output Formats

### HTML String (default)

```clojure
(compiler/compile-to-html elements {:platform-stack [:html]})
;; Returns: "<section><h1>Title</h1></section>"
```

### HTML with CSS

```clojure
(compiler/compile-to-html elements {:platform-stack [:html :css]})
;; Returns: HTML with inline styles or <style> tag
```

### HTML File

```clojure
(compiler/compile-to-html
  elements
  {:platform-stack [:html :css]
   :output {:format :file :path "output.html"}})
;; Writes complete HTML file with <!DOCTYPE>, <html>, <head>, <body>
```

### Hiccup (Clojure data structure)

```clojure
(compiler/compile-to-html
  elements
  {:platform-stack [:html]
   :output {:format :hiccup}})
;; Returns: [[:section [[:h1 "Title"]]]]
```

## Best Practices

### 1. Use Semantic HTML

```clojure
;; Good: Semantic elements
[:header
  [:nav
    [:link {:href "/"} "Home"]]]

;; Avoid: Divs for everything
[:div {:class "header"}
  [:div {:class "nav"}
    [:link {:href "/"} "Home"]]]
```

### 2. Design Tokens for Consistency

```clojure
;; Define tokens
(def tokens
  {:colors {:primary "#1a202c"
            :secondary "#E2D8FF"}
   :spacing {:md "16px" :lg "32px"}})

;; Use tokens
[:section {:design/background "var(--colors-primary)"
           :design/padding "var(--spacing-lg)"}]
```

### 3. Component Composition

```clojure
;; Reusable card component
(defn card [title content]
  [:card
    [:card-header [:card-title title]]
    [:card-content content]])

;; Use component
(card "Product" [:text "Description"])
```

### 4. HTMX for Interactivity

```clojure
;; Use HTMX instead of JavaScript for simple interactions
[:button {:text "Load More"
          :htmx/get "/api/more"
          :htmx/target "#content"
          :htmx/swap "beforeend"}]
```

## Performance Optimization

### Minification

```clojure
(compiler/compile-to-html
  elements
  {:platform-stack [:html]
   :minify {:enabled true
            :remove-whitespace true
            :remove-comments true}})
```

### CSS Extraction

```clojure
;; Extract CSS to separate file
(compiler/compile-to-html
  elements
  {:platform-stack [:html :css]
   :output {:css-extract true
            :css-path "styles.css"}})
```

## Testing

```clojure
;; Test HTML output
(require '[forma.platforms.html :as html])

(def result (html/to-html-string [[:button "Click"]]))
(assert (string? result))
(assert (re-find #"<button" result))
```

## Resources

- **Platform Config:** [default/platforms/html.edn](../../default/platforms/html.edn)
- **Compiler:** [src/forma/platforms/html.clj](../../src/forma/platforms/html.clj)
- **HTMX Docs:** [htmx.org](https://htmx.org)
- **HTML5 Reference:** [MDN Web Docs](https://developer.mozilla.org/en-US/docs/Web/HTML)
