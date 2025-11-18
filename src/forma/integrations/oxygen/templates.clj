(ns forma.integrations.oxygen.templates
  "Pre-built page templates for common use cases
   
   Provides complete page templates that can be customized with data."
  (:require [forma.integrations.oxygen.dsl :as dsl]
            [forma.integrations.oxygen.elements :as el]))

;; ============================================================================
;; LANDING PAGE TEMPLATES
;; ============================================================================

(defn landing-page-template
  "Create a complete landing page
   
   Args:
     opts - Map with keys:
       ;; Hero section
       :hero-title - Main headline
       :hero-subtitle - Supporting text
       :hero-cta-text - Hero button text
       :hero-cta-url - Hero button URL
       :hero-image - Hero image URL
       
       ;; Features section
       :features - Vector of {:icon :title :description}
       
       ;; Social proof
       :stats - Vector of {:number :label :suffix}
       
       ;; Testimonials
       :testimonials - Vector of {:quote :author :title}
       
       ;; Pricing
       :pricing-title - Pricing section title
       :pricing-plans - Vector of pricing plans
       
       ;; Final CTA
       :cta-title - Final CTA title
       :cta-text - Final CTA button text
       :cta-url - Final CTA button URL
   
   Example:
     (landing-page-template
       {:hero-title \"Revolutionary Product\"
        :hero-subtitle \"Transform your workflow\"
        :hero-cta-text \"Get Started\"
        :hero-cta-url \"/signup\"
        :features [{:icon \"zap\" :title \"Fast\" :description \"Lightning speed\"}]
        :stats [{:number \"10K\" :label \"Customers\"}]
        :testimonials [{:quote \"Amazing!\" :author \"John Doe\" :title \"CEO\"}]
        :pricing-plans [{:name \"Pro\" :price \"$99\" :features [...]}]
        :cta-title \"Ready to start?\"
        :cta-text \"Sign Up Free\"
        :cta-url \"/signup\"})"
  [{:keys [hero-title hero-subtitle hero-cta-text hero-cta-url
           features stats testimonials
           pricing-title pricing-plans
           cta-title cta-text cta-url]}]
  [;; Hero section
   (dsl/hero-section {:title hero-title
                      :subtitle hero-subtitle
                      :cta-text hero-cta-text
                      :cta-url hero-cta-url
                      :background "#1a1a1a"})
   
   ;; Features (if provided)
   (when (seq features)
     (dsl/feature-grid features))
   
   ;; Stats (if provided)
   (when (seq stats)
     (el/stats-section stats))
   
   ;; Testimonials (if provided)
   (when (seq testimonials)
     (el/testimonial-section testimonials))
   
   ;; Pricing (if provided)
   (when (seq pricing-plans)
     (el/pricing-section {:title (or pricing-title "Pricing")
                          :plans pricing-plans}))
   
   ;; Final CTA
   (when cta-title
     (el/cta-banner {:title cta-title
                     :cta-text cta-text
                     :cta-url cta-url}))])

(defn create-landing-page!
  "Create a landing page from template
   
   Args:
     title - Page title
     opts - Template options (see landing-page-template)
   
   Returns:
     API response with page ID and URL"
  [title opts]
  (apply dsl/create-page!
         {:title title}
         (landing-page-template opts)))

;; ============================================================================
;; PRODUCT PAGE TEMPLATES
;; ============================================================================

(defn product-page-template
  "Create a product page
   
   Args:
     product - Product data:
       :name - Product name
       :description - Product description
       :price - Product price
       :images - Vector of image URLs
       :features - Vector of feature strings
       :specs - Map of specification key/values
       :cta-text - Add to cart button text
       :cta-url - Product URL"
  [{:keys [name description price images features specs cta-text cta-url]}]
  [(dsl/section {:padding "40px 20px"}
     (dsl/container {}
       (dsl/columns {:columns 2 :gap "48px"}
         ;; Product images
         (dsl/div {}
           (when (first images)
             (dsl/image {:src (first images)
                         :alt name
                         :width "100%"
                         :border-radius "12px"})))
         
         ;; Product info
         (dsl/div {}
           (dsl/heading {:text name
                         :level "h1"
                         :font-size "36px"
                         :margin {:bottom "16px"}})
           (dsl/heading {:text price
                         :level "h2"
                         :font-size "32px"
                         :color "#4f46e5"
                         :margin {:bottom "24px"}})
           (dsl/text {:text description
                      :font-size "16px"
                      :color "#6b7280"
                      :margin {:bottom "32px"}})
           (dsl/button {:text (or cta-text "Add to Cart")
                        :url (or cta-url "#")
                        :background "#4f46e5"
                        :color "#ffffff"
                        :padding "16px 48px"
                        :font-size "18px"
                        :width "100%"})))))
   
   ;; Features section
   (when (seq features)
     (dsl/section {:background "#f9fafb"
                   :padding "60px 20px"}
       (dsl/container {}
         (dsl/heading {:text "Features"
                       :level "h2"
                       :font-size "28px"
                       :margin {:bottom "32px"}})
         (dsl/div {}
           (for [feature features]
             (dsl/text {:text (str "✓ " feature)
                        :font-size "16px"
                        :margin {:bottom "12px"}}))))))
   
   ;; Specifications
   (when (seq specs)
     (dsl/section {:padding "60px 20px"}
       (dsl/container {}
         (dsl/heading {:text "Specifications"
                       :level "h2"
                       :font-size "28px"
                       :margin {:bottom "32px"}})
         (dsl/div {}
           (for [[key value] specs]
             (dsl/div {:display "flex"
                       :justify-content "space-between"
                       :padding "12px 0"
                       :border-bottom "1px solid #e5e7eb"}
               (dsl/text {:text (name key)
                          :font-weight "600"})
               (dsl/text {:text (str value)
                          :color "#6b7280"})))))))])

(defn create-product-page!
  "Create a product page from template
   
   Args:
     product - Product data (see product-page-template)
   
   Returns:
     API response with page ID and URL"
  [product]
  (apply dsl/create-page!
         {:title (:name product)}
         (product-page-template product)))

;; ============================================================================
;; ABOUT PAGE TEMPLATES
;; ============================================================================

(defn about-page-template
  "Create an about page
   
   Args:
     opts - Map with keys:
       :company-name - Company name
       :tagline - Company tagline
       :story - Company story text
       :mission - Mission statement
       :vision - Vision statement
       :values - Vector of value strings
       :team - Vector of team members:
               {:name :title :image :bio}
       :cta-title - Final CTA
       :cta-text - CTA button text
       :cta-url - CTA button URL"
  [{:keys [company-name tagline story mission vision values team
           cta-title cta-text cta-url]}]
  [;; Hero
   (dsl/section {:background "#f9fafb"
                 :padding "80px 20px"
                 :text-align "center"}
     (dsl/container {}
       (dsl/heading {:text company-name
                     :level "h1"
                     :font-size "48px"
                     :margin {:bottom "16px"}})
       (when tagline
         (dsl/text {:text tagline
                    :font-size "20px"
                    :color "#6b7280"}))))
   
   ;; Story
   (when story
     (dsl/section {:padding "80px 20px"}
       (dsl/container {:max-width "800px"}
         (dsl/heading {:text "Our Story"
                       :level "h2"
                       :font-size "36px"
                       :margin {:bottom "24px"}})
         (dsl/text {:text story
                    :font-size "18px"
                    :color "#374151"}))))
   
   ;; Mission & Vision
   (when (or mission vision)
     (dsl/section {:background "#f9fafb"
                   :padding "80px 20px"}
       (dsl/container {}
         (dsl/columns {:columns 2 :gap "48px"}
           (when mission
             (dsl/div {}
               (dsl/heading {:text "Mission"
                             :level "h3"
                             :font-size "24px"
                             :margin {:bottom "16px"}})
               (dsl/text {:text mission
                          :font-size "16px"
                          :color "#6b7280"})))
           (when vision
             (dsl/div {}
               (dsl/heading {:text "Vision"
                             :level "h3"
                             :font-size "24px"
                             :margin {:bottom "16px"}})
               (dsl/text {:text vision
                          :font-size "16px"
                          :color "#6b7280"})))))))
   
   ;; Values
   (when (seq values)
     (dsl/section {:padding "80px 20px"}
       (dsl/container {}
         (dsl/heading {:text "Our Values"
                       :level "h2"
                       :font-size "36px"
                       :text-align "center"
                       :margin {:bottom "48px"}})
         (dsl/columns {:columns (min 3 (count values))
                       :gap "32px"}
           (for [value values]
             (dsl/div {:text-align "center"
                       :padding "24px"}
               (dsl/text {:text value
                          :font-size "16px"})))))))
   
   ;; Team
   (when (seq team)
     (dsl/section {:background "#f9fafb"
                   :padding "80px 20px"}
       (dsl/container {}
         (dsl/heading {:text "Our Team"
                       :level "h2"
                       :font-size "36px"
                       :text-align "center"
                       :margin {:bottom "48px"}})
         (dsl/columns {:columns (min 4 (count team))
                       :gap "32px"}
           (for [member team]
             (dsl/div {:text-align "center"}
               (when (:image member)
                 (dsl/image {:src (:image member)
                             :alt (:name member)
                             :width "100%"
                             :border-radius "50%"}))
               (dsl/heading {:text (:name member)
                             :level "h4"
                             :font-size "18px"
                             :margin {:top "16px" :bottom "4px"}})
               (dsl/text {:text (:title member)
                          :font-size "14px"
                          :color "#6b7280"})))))))
   
   ;; CTA
   (when cta-title
     (el/cta-banner {:title cta-title
                     :cta-text cta-text
                     :cta-url cta-url}))])

(defn create-about-page!
  "Create an about page from template
   
   Args:
     opts - Template options (see about-page-template)
   
   Returns:
     API response with page ID and URL"
  [opts]
  (apply dsl/create-page!
         {:title "About Us"}
         (about-page-template opts)))

;; ============================================================================
;; CONTACT PAGE TEMPLATES
;; ============================================================================

(defn contact-page-template
  "Create a contact page
   
   Args:
     opts - Map with keys:
       :title - Page title
       :subtitle - Page subtitle
       :email - Contact email
       :phone - Contact phone
       :address - Physical address
       :map-embed - Map embed URL (optional)"
  [{:keys [title subtitle email phone address]}]
  [(dsl/section {:padding "80px 20px"}
     (dsl/container {}
       (dsl/div {:text-align "center"
                 :margin {:bottom "48px"}}
         (dsl/heading {:text (or title "Contact Us")
                       :level "h1"
                       :font-size "48px"
                       :margin {:bottom "16px"}})
         (when subtitle
           (dsl/text {:text subtitle
                      :font-size "20px"
                      :color "#6b7280"})))
       
       (dsl/columns {:columns 2 :gap "48px"}
         ;; Contact info
         (dsl/div {}
           (dsl/heading {:text "Get in Touch"
                         :level "h3"
                         :font-size "24px"
                         :margin {:bottom "24px"}})
           (when email
             (dsl/div {:margin {:bottom "16px"}}
               (dsl/text {:text "Email"
                          :font-weight "600"
                          :margin {:bottom "4px"}})
               (dsl/text {:text email
                          :color "#4f46e5"})))
           (when phone
             (dsl/div {:margin {:bottom "16px"}}
               (dsl/text {:text "Phone"
                          :font-weight "600"
                          :margin {:bottom "4px"}})
               (dsl/text {:text phone
                          :color "#4f46e5"})))
           (when address
             (dsl/div {}
               (dsl/text {:text "Address"
                          :font-weight "600"
                          :margin {:bottom "4px"}})
               (dsl/text {:text address
                          :color "#6b7280"}))))
         
         ;; Contact form placeholder
         (dsl/div {:background "#f9fafb"
                   :padding "32px"
                   :border-radius "12px"}
           (dsl/text {:text "Contact form would go here"
                      :text-align "center"
                      :color "#6b7280"})))))]
)

(defn create-contact-page!
  "Create a contact page from template
   
   Args:
     opts - Template options (see contact-page-template)
   
   Returns:
     API response with page ID and URL"
  [opts]
  (apply dsl/create-page!
         {:title "Contact"}
         (contact-page-template opts)))

(comment
  ;; Example: Create a complete landing page
  (create-landing-page! "Product Launch 2025"
    {:hero-title "Revolutionary Product"
     :hero-subtitle "Transform your workflow with AI"
     :hero-cta-text "Get Started Free"
     :hero-cta-url "/signup"
     
     :features [{:icon "zap" :title "Lightning Fast" :description "10x faster than competitors"}
                {:icon "lock" :title "Secure" :description "Bank-grade encryption"}
                {:icon "check" :title "Reliable" :description "99.9% uptime SLA"}]
     
     :stats [{:number "10K" :label "Happy Customers"}
             {:number "99.9" :suffix "%" :label "Uptime"}
             {:number "24/7" :label "Support"}]
     
     :testimonials [{:quote "This product changed everything for our team!"
                     :author "Jane Smith"
                     :title "CEO, TechCorp"}]
     
     :pricing-plans [{:name "Pro"
                      :price "$99"
                      :period "/month"
                      :features ["Unlimited projects" "10GB storage" "Priority support"]
                      :cta-text "Get Started"
                      :cta-url "/signup?plan=pro"
                      :featured true}]
     
     :cta-title "Ready to Transform Your Workflow?"
     :cta-text "Start Free Trial"
     :cta-url "/signup"})
  
  ;; Example: Create a product page
  (create-product-page!
    {:name "Premium Widget"
     :description "The best widget on the market"
     :price "$199.99"
     :images ["/images/product.jpg"]
     :features ["Durable construction" "2-year warranty" "Free shipping"]
     :specs {:weight "2.5 lbs" :dimensions "10x8x6 inches" :material "Aluminum"}
     :cta-text "Add to Cart"
     :cta-url "/cart?product=premium-widget"})
  
  )

