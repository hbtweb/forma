# Quick Start - Deploy to WordPress/Oxygen in 60 Seconds

## ✅ Setup Complete!

Your WordPress site is **ready to receive deployments**!

### **WordPress Credentials:**

```
Site URL: http://hbtcomputers.com.au.test
Username: admin
Application Password: T8W8 rxIo 5y56 6jm7 9HgS s9Mi
```

---

## **Deploy Your First Page (4 Steps)**

### **Step 1: Start REPL (10 seconds)**

```powershell
cd c:\GitHub\ui\forma
clojure -M:repl
```

### **Step 2: Set Credentials (10 seconds)**

```clojure
(System/setProperty "WORDPRESS_URL" "http://hbtcomputers.com.au.test")
(System/setProperty "WORDPRESS_USER" "admin")
(System/setProperty "WORDPRESS_APP_PASSWORD" "T8W8 rxIo 5y56 6jm7 9HgS s9Mi")
```

### **Step 3: Test Connection (10 seconds)**

```clojure
(load-file "test_oxygen_connection.clj")
(in-ns 'test-oxygen-connection)
(test-connection)
```

**Expected output:**
```clojure
✓ Config loaded successfully
✓ Connection successful!
Oxygen REST Bridge Info: {...}
=> {:success true, :message "Connected successfully", :oxygen-available true}
```

### **Step 4: Deploy Hero Section (30 seconds)**

```clojure
(load-file "src/forma/oxygen_deploy_demo.clj")
(in-ns 'forma.oxygen-deploy-demo)

(deploy-hero-section)
```

**Expected output:**
```clojure
{:success true
 :data {:id 687
        :url "http://hbtcomputers.com.au.test/homepage-hero/"
        :edit_url "http://hbtcomputers.com.au.test/wp-admin/admin.php?page=oxygen_vsb_sign_shortcodes&action=edit&post=687"}}
```

---

## **View Your Page!**

Open the returned `:url` in your browser:

```
http://hbtcomputers.com.au.test/homepage-hero/
```

You should see:
- Dark background (`#1a202c`)
- Large heading: "Professional IT Solutions"
- Subheading: "Expert computer services for your business"
- Blue CTA button: "Get Started"

---

## **What Just Happened?**

```
Your Description
    ↓
Forma EDN Generated
    ↓
[[:section {:design/background "#1a202c"}
  [:heading {:text "Professional IT Solutions"}]
  [:button {:text "Get Started"}]]]
    ↓
Compiled to Oxygen JSON
    ↓
POST /wp-json/oxygen/v1/save
    ↓
Live on WordPress!
```

---

## **Next: Conversational Design**

Now you can describe any design and I'll deploy it instantly!

### **Example Session:**

**You:** "Create a pricing page with 3 tiers"

**Me:**
```clojure
(def pricing-page
  [{:type :section
    :props {:design/padding "60px 20px"}
    :children [{:type :heading
                :props {:level 2 :text "Pricing Plans"}}
               {:type :div
                :props {:design/display "grid"
                        :design/grid-template-columns "repeat(3, 1fr)"
                        :design/gap "30px"}
                :children [{:type :card
                            :children [{:type :heading {:text "Basic - $99/mo"}}]}
                           {:type :card
                            :children [{:type :heading {:text "Pro - $199/mo"}}]}
                           {:type :card
                            :children [{:type :heading {:text "Enterprise - Contact"}}]}]}]}])

(deploy-page "Pricing" pricing-page {:status "draft"})
```

**Result:** Live at `http://hbtcomputers.com.au.test/pricing/`

**You:** "Make the Pro tier highlighted with blue background"

**Me:** *Updates EDN, redeploys in 10 seconds*

---

## **Available Commands**

```clojure
;; Deploy hero section (pre-built example)
(deploy-hero-section)

;; Deploy services page (hero + 3 service cards)
(deploy-services-page)

;; Deploy custom page
(deploy-page "My Page Title" my-elements {:status "draft"})

;; Publish (make live)
(deploy-page "My Page Title" my-elements {:status "publish"})
```

---

## **Design Elements Available**

### **Layout**
- `:section` - Container sections
- `:div` - Generic containers
- `:container` - Max-width containers

### **Typography**
- `:heading` - Headings (H1-H6)
- `:text` - Paragraphs

### **Interactive**
- `:button` - Buttons
- `:card` - Cards
- `:form` - Forms
- `:input` - Input fields
- `:textarea` - Text areas

### **More**
See [OXYGEN_DEPLOYMENT_GUIDE.md](OXYGEN_DEPLOYMENT_GUIDE.md) for complete list.

---

## **Troubleshooting**

### Connection Test Fails

```clojure
(test-connection)
;; => {:success false, :error "..."}
```

**Fix:**
1. Verify credentials are set correctly
2. Check Laragon is running
3. Test manually:
   ```bash
   curl -u "admin:T8W8 rxIo 5y56 6jm7 9HgS s9Mi" "http://hbtcomputers.com.au.test/index.php?rest_route=/oxygen/v1/info"
   ```

### Deployment Fails

**Error:** `No route found`

**Fix:** Use the correct REST API path format:
```
http://hbtcomputers.com.au.test/index.php?rest_route=/oxygen/v1/save
```

Not:
```
http://hbtcomputers.com.au.test/wp-json/oxygen/v1/save  ❌
```

---

## **What's Next?**

1. ✅ **Deploy test page** (Step 4 above)
2. 🎨 **Start designing** - Tell me what you want to build!
3. 🚀 **Iterate fast** - 10-second redeploys
4. 📦 **Build component library** - Reusable patterns
5. 🔄 **(Optional) Add Mesh** - Visual design in Next.js

---

**Ready to design?** Run the 4 steps above and let's build something! 🚀
