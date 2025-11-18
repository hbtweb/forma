# Oxygen Deployment Guide

**Conversational design workflow: Describe → Generate EDN → Deploy to WordPress/Oxygen**

## What's Already Built ✅

Your infrastructure is **100% complete**:

- ✅ **Oxygen Compiler** - EDN → Oxygen JSON ([forma/src/forma/integrations/oxygen/compiler.clj](forma/src/forma/integrations/oxygen/compiler.clj))
- ✅ **Sync Client** - HTTP client with auth, retry, rate limiting ([forma/src/forma/sync/client.clj](forma/src/forma/sync/client.clj))
- ✅ **WordPress Config** - Oxygen REST API endpoints configured ([forma/default/sync/wordpress.edn](forma/default/sync/wordpress.edn))
- ✅ **Oxygen REST Bridge** - WordPress plugin exposing Oxygen API (C:\laragon\www\hbtcomputers.com.au\wp-content\plugins\oxygen-rest-bridge)

## Quick Start (5 minutes)

### 1. Get WordPress Application Password

1. Go to: `https://hbtcomputers.com.au/wp-admin/profile.php`
2. Scroll to "Application Passwords"
3. Enter name: "Forma CLI"
4. Click "Add New Application Password"
5. Copy the generated password (format: `xxxx xxxx xxxx xxxx xxxx xxxx`)

### 2. Start Clojure REPL

```powershell
cd c:\GitHub\ui\forma
clojure -M:repl
```

### 3. Set Credentials

```clojure
(System/setProperty "WORDPRESS_URL" "https://hbtcomputers.com.au")
(System/setProperty "WORDPRESS_USER" "your-username")
(System/setProperty "WORDPRESS_APP_PASSWORD" "xxxx xxxx xxxx xxxx xxxx xxxx")
```

### 4. Test Connection

```clojure
(load-file "test_oxygen_connection.clj")
(in-ns 'test-oxygen-connection)
(test-connection)
;; => {:success true, :message "Connected successfully", :oxygen-available true}
```

### 5. Deploy Your First Page

```clojure
(load-file "src/forma/oxygen_deploy_demo.clj")
(in-ns 'forma.oxygen-deploy-demo)

;; Deploy hero section
(deploy-hero-section)
;; => {:success true
;;     :data {:id 123
;;            :url "https://hbtcomputers.com.au/homepage-hero/"
;;            :edit_url "https://hbtcomputers.com.au/wp-admin/admin.php?page=oxygen_vsb_sign_shortcodes&action=edit&post=123"}}
```

### 6. View Result

Open the returned `:url` in your browser to see the live page!

---

## Conversational Design Workflow

### How It Works

```
You (Describe design)
    ↓
"Create a hero section with dark background, large heading, and CTA button"
    ↓
Claude (Generates Forma EDN)
    ↓
[[:section {:design/background "#1a202c"}
  [:heading {:text "Welcome"}]
  [:button {:text "Get Started"}]]]
    ↓
Forma Compiler (EDN → Oxygen JSON)
    ↓
HTTP POST to /wp-json/oxygen/v1/save
    ↓
Live on hbtcomputers.com.au!
```

### Example Session

**You:** "Create a services page with 3 cards showing computer repair, networking, and security"

**Claude:**
```clojure
(def services-page
  [{:type :section
    :props {:design/padding "60px 20px"}
    :children [{:type :heading
                :props {:level 2
                        :text "Our Services"}}
               {:type :div
                :props {:design/display "grid"
                        :design/grid-template-columns "repeat(3, 1fr)"
                        :design/gap "30px"}
                :children [{:type :card
                            :children [{:type :heading
                                       :props {:text "Computer Repair"}}]}
                           {:type :card
                            :children [{:type :heading
                                       :props {:text "Network Setup"}}]}
                           {:type :card
                            :children [{:type :heading
                                       :props {:text "Security"}}]}]}]}])

(deploy-page "Services" services-page {:status "draft"})
```

**You:** "Make the cards have more padding and add shadows"

**Claude:** *Updates the EDN, redeploys in 10 seconds*

---

## Available Design Elements

### Layout
- `:section` - Container sections
- `:div` - Generic containers
- `:container` - Max-width containers

### Typography
- `:heading` - H1-H6 headings (`:level 1-6`)
- `:text` - Paragraphs and text
- `:rich-text` - Rich text content

### Forms
- `:form` - Form container
- `:input` - Text inputs
- `:textarea` - Multi-line text
- `:button` - Buttons
- `:select` - Dropdowns
- `:checkbox` - Checkboxes
- `:radio` - Radio buttons

### Interactive
- `:button` - Call-to-action buttons
- `:modal` - Modal dialogs
- `:tabs` - Tab interfaces
- `:accordion` - Collapsible sections
- `:dropdown` - Dropdown menus

### Content
- `:card` - Card components
- `:badge` - Badges
- `:alert` - Alert messages
- `:tooltip` - Tooltips
- `:progress` - Progress bars

### Data Display
- `:data-table` - Data tables
- `:stat-card` - Statistics cards
- `:timeline` - Timeline views
- `:calendar` - Calendar views
- `:kanban-board` - Kanban boards

### Media
- `:image` - Images
- `:video` - Videos
- `:icon` - Icons

---

## Design Properties

### Layout
```clojure
:design/display "flex"
:design/flex-direction "column"
:design/justify-content "center"
:design/align-items "center"
:design/grid-template-columns "repeat(3, 1fr)"
:design/gap "20px"
```

### Spacing
```clojure
:design/padding "20px"
:design/padding-top "40px"
:design/margin "20px auto"
:design/margin-bottom "30px"
```

### Colors
```clojure
:design/background "#ffffff"
:design/color "#1a202c"
:design/border-color "#e2e8f0"
```

### Typography
```clojure
:design/font-size "18px"
:design/font-weight "bold"
:design/line-height "1.6"
:design/text-align "center"
```

### Borders
```clojure
:design/border "1px solid #e2e8f0"
:design/border-radius "8px"
```

### Sizing
```clojure
:design/width "100%"
:design/max-width "1200px"
:design/height "500px"
:design/min-height "300px"
```

---

## Advanced Features

### Animations
```clojure
{:type :section
 :props {:anim :fade-in  ; Shorthand
         ;; Or full control:
         :animations {:type :fade-in
                     :duration "1s"
                     :delay "0.2s"}}}
```

### Conditional Visibility
```clojure
{:type :section
 :props {:cond {:role :admin}  ; Only show to admins
         ;; Or:
         :conditions {:type :user-role
                     :op :eq
                     :val "admin"}}}
```

### Dynamic Data
```clojure
{:type :heading
 :props {:text "{{post.title}}"  ; WordPress post title
         :dynamicData "{{post.title}}"}}
```

### Interactions
```clojure
{:type :button
 :props {:text "Toggle Menu"
         :interact {:on :click
                   :action :toggle-class
                   :target "#mobile-menu"
                   :class "open"}}}
```

### Sticky Positioning
```clojure
{:type :section
 :props {:sticky "0px"  ; Stick to top
         ;; Or:
         :sticky {:top "0px"
                 :z-index 100}}}
```

### Responsive Design
```clojure
{:type :section
 :props {:hide-on :mobile   ; Hide on mobile
         ;; Or:
         :show-on :desktop}} ; Only show on desktop
```

---

## Deployment Functions

### Deploy Page
```clojure
(deploy-page "Page Title" elements {:status "draft"})
;; status: "draft" or "publish"
```

### Deploy Services Page (Example)
```clojure
(deploy-services-page)
;; Deploys pre-built services page with hero + 3 cards
```

### Custom Deployment
```clojure
(require '[forma.sync.client :as sync])
(require '[forma.integrations.oxygen.compiler :as oxygen])

(def my-elements [...])

(sync/publish :wordpress
              "My Page"
              my-elements
              {:project-name "hbt-computers"
               :metadata {:post_type "page"
                         :status "publish"
                         :tree (oxygen/compile-to-oxygen my-elements)}})
```

---

## Troubleshooting

### Connection Fails

**Problem:** `Connection failed - 401 Unauthorized`

**Solution:**
1. Check Application Password is correct
2. Verify username matches WordPress admin username
3. Ensure Oxygen REST Bridge plugin is activated

### Element Not Rendering

**Problem:** Element shows as empty box in Oxygen

**Solution:**
1. Check element type is valid (see "Available Design Elements")
2. Verify properties are correctly formatted
3. Check Oxygen Builder has the element available

### Page Not Found

**Problem:** Can't access deployed page URL

**Solution:**
1. Check WordPress permalinks are configured
2. Verify page status is "publish" (not "draft")
3. Check page ID in WordPress admin

---

## Next Steps

### 1. Start with Simple Pages

Deploy basic pages first (hero sections, contact forms) to validate workflow.

### 2. Build Component Library

Create reusable components for common patterns:
```clojure
(defn hero [title subtitle cta-text cta-url]
  [{:type :section
    :props {:design/background "#1a202c"
            :design/padding "80px 20px"}
    :children [{:type :heading
                :props {:level 1 :text title}}
               {:type :text
                :props {:text subtitle}}
               {:type :button
                :props {:text cta-text
                        :url cta-url}}]}])
```

### 3. Integrate with Mesh (Optional)

If you want visual design in Next.js:
- Parse Mesh React components → Forma EDN
- Use `forma.parsers.jsx` (already exists!)
- Deploy to Oxygen

### 4. Automate Common Tasks

Create shell scripts or aliases:
```powershell
# deploy-hero.ps1
clojure -M -e "(load-file \"src/forma/oxygen_deploy_demo.clj\") (in-ns 'forma.oxygen-deploy-demo) (deploy-hero-section)"
```

---

## Architecture Overview

```
Conversational Input (Natural Language)
    ↓
Forma EDN Generation
    ↓
forma.integrations.oxygen.compiler
    ↓
Oxygen JSON Tree Structure
    ↓
forma.sync.client (HTTP Client)
    ↓
/wp-json/oxygen/v1/save (REST API)
    ↓
Oxygen REST Bridge Plugin
    ↓
WordPress Database
    ↓
Live Website (hbtcomputers.com.au)
```

---

## Resources

- **Oxygen Compiler:** [forma/src/forma/integrations/oxygen/compiler.clj](forma/src/forma/integrations/oxygen/compiler.clj)
- **Sync Client:** [forma/src/forma/sync/client.clj](forma/src/forma/sync/client.clj)
- **WordPress Config:** [forma/default/sync/wordpress.edn](forma/default/sync/wordpress.edn)
- **Demo Examples:** [forma/src/forma/oxygen_deploy_demo.clj](forma/src/forma/oxygen_deploy_demo.clj)
- **Connection Test:** [forma/test_oxygen_connection.clj](forma/test_oxygen_connection.clj)
- **Oxygen REST Bridge:** [C:\laragon\www\hbtcomputers.com.au\wp-content\plugins\oxygen-rest-bridge\README.md](C:\laragon\www\hbtcomputers.com.au\wp-content\plugins\oxygen-rest-bridge\README.md)

---

## Support

For issues or questions:
1. Check [SESSION_STATE.md](SESSION_STATE.md) for current capabilities
2. Review [CHANGELOG.md](CHANGELOG.md) for recent changes
3. Test connection with `test_oxygen_connection.clj`
4. Verify Oxygen REST Bridge is working: `https://hbtcomputers.com.au/wp-json/oxygen/v1/info`

---

**Last Updated:** 2025-01-17
**Status:** ✅ Production Ready
**Timeline to First Deploy:** 5 minutes
