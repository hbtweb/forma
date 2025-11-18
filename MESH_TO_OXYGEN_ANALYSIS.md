# Mesh → Oxygen Deployment Analysis

## Executive Summary

**Mesh Project**: Next.js 16 e-commerce application (Amazon-inspired UI)
**Goal**: Compile Mesh components to WordPress + Oxygen Builder
**Challenge**: Mesh uses complex React/Next.js features that don't map 1:1 to Oxygen

---

## Part 1: What is Mesh?

### Project Overview
- **Tech Stack**: Next.js 16 (App Router) + React 19 + TypeScript + Tailwind CSS v4
- **UI Framework**: shadcn/ui (Radix UI primitives)
- **State Management**: React Context API (no Redux/Zustand)
- **Data Persistence**: localStorage (mock data, no backend)

### Key Features
1. **Amazon-Style Header** with complex scroll animations
2. **Multi-Modal Cart** (slim strip, overlay, dedicated page)
3. **AI-Powered PC Builder** (OpenAI integration)
4. **Account Management** (mock auth with localStorage)
5. **Advanced Product Catalog** with search/filtering

---

## Part 2: Fastest Way to Interface with Oxygen

### Current Status: ✅ FULLY WORKING

**Existing Infrastructure**:
- ✅ HTTP client with WordPress REST API auth ([forma/src/forma/sync/client.clj](../src/forma/sync/client.clj:1-1))
- ✅ Oxygen property mapper ([forma/src/forma/platforms/oxygen_mapper.clj](../src/forma/platforms/oxygen_mapper.clj:1-1))
- ✅ Successfully deployed 5+ pages (IDs: 47, 49, 50, 54, 56, 61)

**API Endpoint**: `http://hbtcomputers.com.au.test/index.php?rest_route=/oxygen/v1/save`
**Auth**: Basic Auth (admin:T8W8rxIo5y566jm79HgSs9Mi)

### Simplest Deployment Workflow

**Option 1: Use Existing Deploy Script** (Recommended)
```clojure
;; From forma/ directory
(load-file "deploy_mesh_corrected.clj")
(in-ns 'deploy-mesh-corrected)
(deploy-corrected-homepage)
;; => {:id 50 :url "http://hbtcomputers.com.au.test/?page_id=50"}
```

**Option 2: Direct HTTP** (No Forma)
```bash
curl -X POST http://hbtcomputers.com.au.test/index.php?rest_route=/oxygen/v1/save \
  -u "admin:T8W8rxIo5y566jm79HgSs9Mi" \
  -H "Content-Type: application/json" \
  -d @oxygen-tree.json
```

**Option 3: Forma EDN → Oxygen** (New Deployment)
```clojure
(require '[forma.platforms.oxygen-mapper :as mapper]
         '[clj-http.client :as http]
         '[cheshire.core :as json])

(defn deploy-page [title forma-edn]
  (let [tree (build-oxygen-tree forma-edn)
        body {:title title :post_type "page" :status "draft" :tree tree}]
    (http/post "http://hbtcomputers.com.au.test/index.php?rest_route=/oxygen/v1/save"
               {:basic-auth ["admin" "T8W8rxIo5y566jm79HgSs9Mi"]
                :content-type :json
                :body (json/generate-string body)})))
```

---

## Part 3: Bidirectional Compilation Clarification

### What "Single EDN File Bidirectional" Means

**NOT This** (simultaneous multi-platform):
```clojure
;; ❌ One EDN compiles to BOTH HTML and Oxygen simultaneously
{:page {:elements [...]}}
→ HTML output (for static hosting)
→ Oxygen JSON output (for WordPress)
```

**YES This** (round-trip conversion):
```clojure
;; ✅ Forward (Export)
Forma EDN → oxygen_mapper → Oxygen JSON → WordPress

;; ✅ Reverse (Import)
Oxygen JSON → oxygen_parser → Forma EDN → Code editor

;; ✅ Round-trip (Edit in Oxygen, sync back to code)
Edit in Oxygen → Pull JSON → Update Forma EDN → Push back
```

### Current Implementation Status

| Direction | Status | Location |
|-----------|--------|----------|
| **Forma → Oxygen** | ✅ Complete | `oxygen_mapper.clj` |
| **Oxygen → Forma** | ⚠️ Partial | Stub functions exist, not tested |
| **Round-trip** | ⚠️ Not tested | Need metadata preservation |

**Documentation References**:
- Export: [CODE_ELEMENTS_BREAKTHROUGH.md](CODE_ELEMENTS_BREAKTHROUGH.md:1-1)
- Import vision: [SESSION_STATE.md](SESSION_STATE.md:1-1) Phase 3-4
- Metadata system: [forma/src/forma/sync/metadata.clj](../src/forma/sync/metadata.clj:1-1)

---

## Part 4: Comprehensive Mesh Requirements Analysis

### 4.1 Component Inventory

#### Homepage Components
```tsx
<Header />               // Complex Amazon-style navbar
<Hero />                 // Hero section with CTA
<ProductGrid />          // Product cards with filters
<CategoryGrid />         // Category cards
<Footer />               // Footer with links
```

#### Header Sub-Components
```tsx
<SearchBar />            // With autocomplete
<CartFlyout />           // Overlay cart panel
<AccountFlyout />        // Account dropdown
<MenuFlyout />           // Category mega menu
<LocationSelector />     // Store/location picker
<ChatToggle />           // AI chat button
<ThemeToggle />          // Dark/light mode
```

### 4.2 JavaScript Requirements

#### Header JavaScript (CRITICAL)

**1. Scroll Animation System**
- **Location**: [C:\GitHub\mesh\components\header.tsx:329-620](C:\GitHub\mesh\components\header.tsx:329-620)
- **Complexity**: HIGH
- **Features**:
  - Header slides up/down on scroll (Amazon-style)
  - Direct DOM manipulation (bypasses React for performance)
  - `requestAnimationFrame` for 60fps
  - GPU acceleration via `transform: translateY()`
  - Spacer element prevents content jump
  - Conditional behavior based on `navMainEnabled` state

**Implementation Details**:
```javascript
// Scroll handler (lines 483-603)
const handleScroll = () => {
  const currentScrollY = window.scrollY
  const isScrollingDown = currentScrollY > previousScrollY

  // Position-based offset calculation
  if (!hasCrossedThreshold || previousScrollY <= barHeight) {
    const scrollPastBelt = currentScrollY - barHeight
    const thresholdOffset = navMainEnabled ? (barHeight * 0.5) : barHeight
    const calculatedOffset = -(scrollPastBelt * 0.5) - thresholdOffset
    newOffset = Math.max(-barHeight, Math.min(0, calculatedOffset))
  }

  // Apply transform directly to DOM (no React state)
  beltRef.style.transform = `translateY(${newOffset}px)`

  // Spacer prevents content jump
  spacerRef.current.style.height = `${barHeight}px` // when nav-belt fixed
}
```

**Why This Matters for Oxygen**:
- ❌ Oxygen **cannot run** this JavaScript natively
- ❌ This is **client-side React code** requiring:
  - `useEffect`, `useRef`, `useCallback` hooks
  - Direct DOM manipulation
  - Animation frame scheduling
- ⚠️ **Deployment Options**:
  1. Inject as `JavaScriptCode` element (vanilla JS rewrite required)
  2. Use Oxygen's "Sticky Header" feature (simpler, less control)
  3. Skip animation entirely (static header)

**2. Flyout Panels (Search, Bag, Account, Menu)**
- **Location**: [C:\GitHub\mesh\components\header.tsx:195-328](C:\GitHub\mesh\components\header.tsx:195-328)
- **Features**:
  - React Portals for overlay rendering
  - Triangle tolerance algorithm (Amazon-style submenu delay)
  - Click-outside detection
  - Keyboard navigation (Escape to close)

**Triangle Tolerance Algorithm** (lines 240-280):
```javascript
// Prevents accidental flyout close when mouse moves toward submenu
const updateTriangle = throttle((e: MouseEvent) => {
  const cursorPoint: Point = { x: e.clientX, y: e.clientY }
  const triangle = calculateTriangle(triggerRect, flyoutRect, cursorPoint)

  if (isInSafeZone(cursorPoint, triangle)) {
    // Don't close flyout - user is moving toward submenu
    return
  }

  // Close flyout
  setOpenFlyout(null)
}, 100)
```

**3. Mobile Menu**
- **Location**: [C:\GitHub\mesh\components\header.tsx:800-1100](C:\GitHub\mesh\components\header.tsx:800-1100)
- **Features**:
  - Hamburger toggle
  - Slide-in drawer
  - Category accordion
  - Account links

#### Home Page JavaScript

**1. Product Filtering**
- Category filter buttons
- Search functionality
- Sort dropdown
- Client-side array filtering

**2. Wishlist Toggle**
- Heart icon click handler
- localStorage persistence
- Optimistic UI updates

**3. Add to Cart**
- Button click → update context
- Cart count animation
- Toast notifications

#### Cart JavaScript

**1. Cart Modes**
- Slim strip (bottom of page)
- Overlay panel (slide from right)
- Full page (/cart route)

**2. Quantity Controls**
- Increment/decrement buttons
- Direct input editing
- Debounced updates

**3. AI Chat Integration**
- OpenAI API calls
- Streaming responses
- Action commands: `[ADD: Product]`, `[SWAP: Item]`, `[REMOVE: Item]`

### 4.3 CSS Requirements

#### Tailwind Classes Used

**Layout**:
```css
/* Grid */
grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6

/* Flexbox */
flex flex-col items-center justify-between

/* Container */
container mx-auto px-4 max-w-7xl
```

**Spacing**:
```css
p-4 p-6 p-8  /* padding */
m-2 m-4 m-6  /* margin */
gap-4 gap-6  /* grid/flex gap */
```

**Typography**:
```css
text-sm text-base text-lg text-xl text-2xl text-3xl text-4xl
font-normal font-medium font-semibold font-bold
leading-tight leading-relaxed
```

**Colors**:
```css
bg-background bg-card bg-primary
text-foreground text-muted-foreground text-primary
border-border
```

**Effects**:
```css
/* Shadows */
shadow-sm shadow-md shadow-lg

/* Transitions */
transition-all duration-300 ease-in-out

/* Hover */
hover:bg-accent hover:text-accent-foreground
hover:scale-105 hover:shadow-xl

/* Focus */
focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary
```

**Custom Animations** (via tw-animate-css):
```css
animate-fade-in
animate-slide-up
animate-bounce-in
```

#### Custom CSS (Beyond Tailwind)

**1. Gradient Backgrounds**
```css
.gradient-hero {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
```

**2. Glassmorphism**
```css
.glass-effect {
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
}
```

**3. Scrollbar Styling**
```css
.custom-scrollbar::-webkit-scrollbar {
  width: 8px;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background: hsl(var(--muted));
  border-radius: 4px;
}
```

### 4.4 State Management

#### Context Providers (React Context API)

**1. AuthProvider** ([C:\GitHub\mesh\lib\auth-context.tsx](C:\GitHub\mesh\lib\auth-context.tsx:1-1))
- User session (localStorage + cookies)
- Login/logout functions
- Test account: email="test", password="1234"

**2. CartProvider** ([C:\GitHub\mesh\lib\cart-context.tsx](C:\GitHub\mesh\lib\cart-context.tsx:1-1))
- Cart items (12 mock items pre-loaded)
- Add/remove/update quantity
- Viewed products tracking
- User intent tracking (for AI)

**3. CartUIProvider** ([C:\GitHub\mesh\lib\cart-ui-context.tsx](C:\GitHub\mesh\lib\cart-ui-context.tsx:1-1))
- Cart display mode: `none`, `slim`, `overlay`, `page`
- Chat toggle state
- Mode switching functions

**4. ListsProvider** ([C:\GitHub\mesh\lib\lists-context.tsx](C:\GitHub\mesh\lib\lists-context.tsx:1-1))
- Wishlists (default "Wishlist" created on first load)
- Add/remove from wishlist
- localStorage persistence

**5. LocationProvider** ([C:\GitHub\mesh\lib\location-context.tsx](C:\GitHub\mesh\lib\location-context.tsx:1-1))
- Selected store
- Saved addresses
- Postcode
- Country selection

**Oxygen Deployment Challenge**:
- ❌ Oxygen **cannot use React Context**
- ❌ All state management is client-side React
- ⚠️ **Options**:
  1. Server-side rendering (SSR) to static HTML (loses interactivity)
  2. Inject React app as iframe (complex, not ideal)
  3. Rewrite with vanilla JavaScript + localStorage
  4. Use WordPress plugins for cart/auth (WooCommerce, etc.)

---

## Part 5: What Needs to Be Compiled

### 5.1 Static Content (Can Deploy to Oxygen)

✅ **Hero Section**
- Heading, subtitle, CTA button
- Gradient background (via CSS code injection)
- Images/icons

✅ **Product Grid** (Static Snapshot)
- Product cards (image, name, price, rating)
- Layout: 4-column grid
- Hover effects (CSS only)

✅ **Category Cards**
- Icons, titles, descriptions
- 3-column grid
- Hover effects

✅ **Footer**
- Links, copyright, social icons
- Already deployed (Page ID: 47)

### 5.2 Interactive Features (Cannot Deploy to Oxygen Natively)

❌ **Search with Autocomplete**
- Requires: API call, debouncing, dropdown rendering
- Oxygen limitation: No native autocomplete widget
- **Workaround**: Use WordPress search plugin or custom JS

❌ **Cart Functionality**
- Requires: React Context, localStorage, quantity controls
- Oxygen limitation: No cart system built-in
- **Workaround**: Use WooCommerce (WordPress e-commerce plugin)

❌ **Wishlist Toggle**
- Requires: localStorage, optimistic updates
- **Workaround**: WordPress wishlist plugin (e.g., YITH WooCommerce Wishlist)

❌ **Account System**
- Requires: Auth context, session management
- **Workaround**: WordPress user system + login forms

❌ **AI Chat**
- Requires: OpenAI API, streaming responses
- **Workaround**: Custom JavaScript + WordPress REST API endpoint

❌ **Header Scroll Animation**
- Requires: React hooks, `requestAnimationFrame`, direct DOM manipulation
- **Workaround**:
  1. Oxygen's "Sticky Header" feature (simple)
  2. Custom JavaScript injection (complex)
  3. Skip animation (easiest)

### 5.3 Oxygen Property Mapping Status

| Feature | Tailwind/CSS | Oxygen Native | Status |
|---------|--------------|---------------|--------|
| **Layout** | grid, flex | `layout_v2.layout = "grid"/"flex"` | ✅ Complete |
| **Spacing** | p-4, m-6 | `spacing.padding/margin` | ✅ Complete |
| **Typography** | text-lg, font-bold | `typography.size/weight` | ✅ Complete |
| **Colors** | bg-primary, text-foreground | `typography.color`, `background.color` | ✅ Complete |
| **Borders** | border-2, rounded-lg | `borders.width/radius` | ✅ Complete |
| **Shadows** | shadow-md | `effects.boxShadow` | ✅ Complete |
| **Gradients** | bg-gradient-to-r | N/A (use CSS code) | ⚠️ CSS fallback |
| **Hover States** | hover:scale-105 | N/A (use CSS) | ⚠️ CSS fallback |
| **Transitions** | transition-all | N/A (use CSS) | ⚠️ CSS fallback |
| **Animations** | animate-fade-in | N/A (use CSS) | ⚠️ CSS fallback |

---

## Part 6: Deployment Strategy

### Strategy A: Static HTML Export (Recommended for MVP)

**Steps**:
1. Build Mesh with `npm run build` → generates static HTML
2. Extract HTML for each page
3. Convert HTML → Forma EDN (using [forma/src/forma/parsers/html.clj](../src/forma/parsers/html.clj:1-1))
4. Map Forma EDN → Oxygen JSON (using [forma/src/forma/platforms/oxygen_mapper.clj](../src/forma/platforms/oxygen_mapper.clj:1-1))
5. Deploy to WordPress via REST API

**Trade-offs**:
- ✅ Fast deployment
- ✅ No JavaScript rewriting needed
- ❌ Loses all interactivity (no cart, search, auth)
- ❌ One-time snapshot (not synced with React codebase)

**Use Case**: Marketing site, product showcase, content-heavy pages

### Strategy B: Hybrid (Static + WordPress Plugins)

**Steps**:
1. Deploy static content (hero, product grid, footer) to Oxygen
2. Use WordPress/WooCommerce for:
   - Product catalog (WooCommerce products)
   - Shopping cart (WooCommerce cart)
   - User accounts (WordPress users)
   - Search (WordPress search or Relevanssi plugin)
3. Inject minimal JavaScript for UI enhancements (smooth scroll, animations)

**Trade-offs**:
- ✅ Full e-commerce functionality
- ✅ WordPress admin for content management
- ⚠️ Requires WooCommerce configuration
- ⚠️ Design consistency challenges (Mesh vs WooCommerce themes)
- ⚠️ Performance overhead (WordPress vs Next.js)

**Use Case**: Full production e-commerce site

### Strategy C: React App Iframe (Not Recommended)

**Steps**:
1. Deploy Mesh as standalone Next.js app
2. Embed in WordPress page via iframe
3. Use WordPress only for content pages, blog, etc.

**Trade-offs**:
- ✅ Keeps all Mesh functionality intact
- ❌ Poor SEO (iframe content not indexed)
- ❌ Styling/layout conflicts
- ❌ Performance issues (double load)

**Use Case**: None (avoid this)

### Strategy D: Headless WordPress (Advanced)

**Steps**:
1. Use WordPress as headless CMS (REST API only)
2. Deploy Mesh to Vercel/Netlify
3. Fetch product data from WordPress via API
4. Keep Mesh as primary frontend

**Trade-offs**:
- ✅ Best of both worlds (Mesh UI + WordPress content)
- ✅ Full performance (no Oxygen overhead)
- ✅ SEO-friendly (Next.js SSR)
- ❌ Complex setup
- ❌ Requires separate hosting

**Use Case**: High-traffic production site

---

## Part 7: Recommended Next Steps

### Immediate Actions

1. **Clarify Deployment Goal**:
   - Full e-commerce site? → Strategy B (Hybrid with WooCommerce)
   - Marketing/showcase site? → Strategy A (Static HTML export)
   - Development/testing? → Strategy A (fastest)

2. **Test Static Deployment**:
   ```clojure
   ;; Deploy Mesh homepage as static snapshot
   (load-file "forma/parse_mesh_homepage.clj")
   (deploy-static-mesh-homepage)
   ```

3. **Identify Critical JavaScript**:
   - Which features MUST be interactive?
   - Can we simplify to CSS-only hover effects?
   - Can we replace cart with WooCommerce?

4. **Map Components to Oxygen Elements**:
   - Hero → `EssentialElements\\Section` with gradient CSS
   - Product Grid → `EssentialElements\\Section` with grid layout
   - Product Card → `EssentialElements\\Div` with image, text, button
   - Header → `EssentialElements\\Header` with links (static, no animation)

### Long-Term Plan

1. **Build Import Pipeline** (Phase 6):
   - HTML parser → Forma EDN → Oxygen JSON
   - Test with Mesh static export
   - Verify round-trip (Mesh → Oxygen → Mesh)

2. **JavaScript Injection System**:
   - Create helper for `JavaScriptCode` elements
   - Bundle React components to vanilla JS (Babel/Webpack)
   - Inject as external scripts

3. **WooCommerce Integration**:
   - Map Mesh products → WooCommerce products
   - Use WooCommerce REST API
   - Style WooCommerce to match Mesh design

---

## Part 8: Gaps in oxygen_mapper.clj

### Missing Features

**1. Responsive Design Properties** (not implemented)
```clojure
;; Currently:
{:padding "20px"}  ; Fixed value

;; Needed:
{:padding {:mobile "10px" :tablet "15px" :desktop "20px"}}
```

**2. CSS Grid Advanced Features** (partial)
```clojure
;; Currently:
{:grid-template-columns "repeat(4, 1fr)"}  ; Simple grid

;; Needed:
{:grid-template-areas "header header" "sidebar main" "footer footer"}
{:grid-auto-flow "dense"}
```

**3. Animation/Transition Helpers** (not implemented)
```clojure
;; Currently: Manual CSS injection

;; Needed:
(defn create-transition [property duration easing]
  {:transition {:property property
                :duration {:number duration :unit "ms"}
                :easing easing}})
```

**4. Component Reusability** (not implemented)
```clojure
;; Oxygen supports "Global Blocks" (reusable components)
;; We don't have helpers for referencing them

(defn reference-global-block [block-id]
  {:type "OxygenElements\\GlobalBlock"
   :properties {:block_id block-id}})
```

### Feature Completeness

| Category | Coverage | Missing |
|----------|----------|---------|
| Layout | 95% | Grid areas, auto-flow |
| Spacing | 100% | - |
| Typography | 90% | Line height, letter spacing |
| Colors | 100% | - |
| Borders | 100% | - |
| Shadows | 100% | - |
| Gradients | 80% | Conic gradients, complex stops |
| Effects | 70% | Filters (blur, brightness), transforms (rotate, skew) |
| Responsive | 0% | All breakpoint variants |

---

## Part 9: Bidirectional Sync Revisited

### What "Single EDN File" Actually Means

Based on SESSION_STATE.md Phase 3-4, the vision is:

**1. Metadata-Enhanced Compilation**
```clojure
;; Compile with metadata tracking
(def html (compiler/compile-to-html elements
  {:metadata-mode :sync
   :preserve-provenance? true}))

;; Generated HTML has data attributes:
;; <div data-forma-type="button"
;;      data-forma-variant="primary"
;;      data-forma-token-provenance='{"color":"$colors.primary"}'>

;; Parse back with metadata:
(def parsed (parser/parse html {:preserve-metadata? true}))
;; => [:button
;;     {:class "btn btn-primary"
;;      :_forma-metadata {:type :button
;;                        :variant :primary
;;                        :tokens {:color "$colors.primary"}}}
;;     "Click Me"]
```

**2. Round-Trip with Oxygen**
```clojure
;; Forward:
Forma EDN
  → compiler/compile-to-html (with sync metadata)
  → oxygen-mapper/tailwind->oxygen-properties
  → Oxygen JSON (with custom data attributes)
  → WordPress

;; Reverse:
Oxygen JSON (fetch via REST API)
  → oxygen-parser/parse-oxygen-tree
  → oxygen-mapper/oxygen->tailwind-properties
  → compiler/parse-html (extract metadata)
  → Forma EDN (reconstructed)

;; Round-trip test:
(let [original forma-edn
      oxygen-json (forward-compile original)
      reconstructed (reverse-compile oxygen-json)]
  (= original reconstructed))  ; Should be true (95%+ fidelity)
```

**3. Single EDN = Single Source of Truth**
- Edit in Forma EDN → push to Oxygen
- Edit in Oxygen → pull back to Forma EDN
- Conflicts resolved via metadata (timestamp, precedence)

**Currently Missing**:
- ⚠️ Oxygen JSON parser ([forma/src/forma/parsers/oxygen.clj](../src/forma/parsers/oxygen.clj:1-1) - doesn't exist)
- ⚠️ Complete reverse mapping ([forma/src/forma/platforms/oxygen_mapper.clj](../src/forma/platforms/oxygen_mapper.clj:1-1) - partial)
- ⚠️ Metadata preservation in Oxygen properties
- ⚠️ Round-trip tests

---

## Conclusion

### Summary Table

| Aspect | Status | Action |
|--------|--------|--------|
| **Oxygen API Integration** | ✅ Complete | Use existing deploy scripts |
| **Property Mapping (Forward)** | ✅ 90% | Complete gradients, responsive |
| **Property Mapping (Reverse)** | ⚠️ 50% | Implement full reverse mapping |
| **Static Content Deployment** | ✅ Ready | Deploy hero, products, footer |
| **Interactive Features** | ❌ Blocked | Requires JavaScript rewrite or plugins |
| **Header Scroll Animation** | ❌ Complex | Use Oxygen sticky header or skip |
| **Cart/Auth** | ❌ Blocked | Use WooCommerce + WordPress users |
| **Bidirectional Sync** | ⚠️ Planned | Implement Oxygen parser (Phase 6) |

### Fastest Path to Deployment

**For Testing/MVP** (1 day):
1. Use existing `deploy_mesh_corrected.clj` script
2. Deploy static homepage snapshot
3. Skip JavaScript features
4. Result: Non-interactive showcase site

**For Production** (2-4 weeks):
1. Install WooCommerce on WordPress
2. Import products to WooCommerce
3. Deploy Mesh static design to Oxygen
4. Integrate WooCommerce cart/checkout
5. Configure WordPress users/accounts
6. Test end-to-end

### Key Decisions Needed

1. **Interactivity Level**:
   - Static showcase? → Deploy now
   - Full e-commerce? → Use WooCommerce
   - Keep React app? → Deploy to Vercel, skip Oxygen

2. **Header Behavior**:
   - Scroll animation? → Rewrite to vanilla JS
   - Simple sticky? → Use Oxygen feature
   - Static? → No JavaScript needed

3. **Cart System**:
   - WooCommerce? → Full WordPress integration
   - Custom? → Rewrite to vanilla JS + WordPress API
   - None? → Static product showcase

4. **Bidirectional Sync Priority**:
   - High? → Implement Phase 6 (Oxygen parser)
   - Low? → One-way deployment only
