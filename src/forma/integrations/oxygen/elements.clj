(ns forma.integrations.oxygen.elements
  "Pre-built Oxygen element definitions and compositions
   
   Provides ready-to-use elements with sensible defaults for common use cases."
  (:require [forma.integrations.oxygen.dsl :as dsl]))

;; ============================================================================
;; NAVIGATION ELEMENTS
;; ============================================================================

(defn navbar
  "Create a navigation bar
   
   Args:
     opts - Map with keys:
       :logo-text - Logo text
       :logo-url - Logo link
       :links - Vector of {:text :url} maps
       :background - Background color
       :sticky - Make navbar sticky (true/false)
   
   Example:
     (navbar {:logo-text \"Brand\"
              :links [{:text \"Home\" :url \"/\"}
                      {:text \"About\" :url \"/about\"}]
              :sticky true})"
  [{:keys [logo-text links background sticky]}]
  (dsl/section {:background (or background "#ffffff")
                :padding "20px"
                :position (when sticky "sticky")
                :top (when sticky "0")
                :z-index (when sticky "1000")}
    (dsl/container {}
      (dsl/div {:display "flex"
                :justify-content "space-between"
                :align-items "center"}
        (dsl/heading {:text logo-text
                      :level "h2"
                      :font-size "24px"
                      :font-weight "700"})
        (dsl/div {:display "flex"
                  :gap "32px"}
          (for [link links]
            (dsl/text {:text (:text link)
                       :font-size "16px"})))))))

;; ============================================================================
;; HERO SECTIONS
;; ============================================================================

(defn split-hero
  "Create a split hero section (image + content side-by-side)
   
   Args:
     opts - Map with keys:
       :title - Hero title
       :subtitle - Hero subtitle
       :cta-text - Button text
       :cta-url - Button URL
       :image - Image URL
       :image-side - \"left\" or \"right\" (default: \"right\")"
  [{:keys [title subtitle cta-text cta-url image image-side]}]
  (let [reverse? (= image-side "left")
        content (dsl/div {:display "flex"
                          :flex-direction "column"
                          :justify-content "center"
                          :gap "24px"}
                  (dsl/heading {:text title
                                :level "h1"
                                :font-size "48px"})
                  (when subtitle
                    (dsl/text {:text subtitle
                               :font-size "20px"
                               :color "#6b7280"}))
                  (when cta-text
                    (dsl/button {:text cta-text
                                 :url cta-url
                                 :background "#4f46e5"
                                 :color "#ffffff"
                                 :padding "16px 32px"})))
        image-el (dsl/image {:src image
                             :alt title
                             :width "100%"})]
    (dsl/section {:padding "80px 20px"}
      (dsl/container {}
        (dsl/columns {:columns 2 :gap "48px"}
          (if reverse?
            [image-el content]
            [content image-el]))))))

(defn video-hero
  "Create a hero section with background video
   
   Args:
     opts - Map with keys:
       :title - Hero title
       :subtitle - Hero subtitle
       :cta-text - Button text
       :cta-url - Button URL
       :video-url - Video URL"
  [{:keys [title subtitle cta-text cta-url]}]
  (dsl/section {:position "relative"
                :min-height "700px"
                :padding "100px 20px"}
    ;; Background video (simplified - would need custom Oxygen video element)
    (dsl/container {}
      (dsl/div {:display "flex"
                :flex-direction "column"
                :align-items "center"
                :text-align "center"
                :gap "24px"
                :position "relative"
                :z-index "10"}
        (dsl/heading {:text title
                      :level "h1"
                      :font-size "56px"
                      :color "#ffffff"})
        (when subtitle
          (dsl/text {:text subtitle
                     :font-size "20px"
                     :color "#ffffff"}))
        (when cta-text
          (dsl/button {:text cta-text
                       :url cta-url
                       :background "#ffffff"
                       :color "#000000"
                       :padding "16px 32px"}))))))

;; ============================================================================
;; CONTENT SECTIONS
;; ============================================================================

(defn testimonial-section
  "Create a testimonial section
   
   Args:
     testimonials - Vector of maps:
       :quote - Testimonial text
       :author - Author name
       :title - Author title
       :image - Author image URL"
  [testimonials]
  (dsl/section {:background "#f9fafb"
                :padding "80px 20px"}
    (dsl/container {}
      (dsl/heading {:text "What Our Customers Say"
                    :level "h2"
                    :font-size "36px"
                    :text-align "center"
                    :margin {:bottom "48px"}})
      (dsl/columns {:columns (min 3 (count testimonials))
                    :gap "32px"}
        (for [t testimonials]
          (dsl/div {:background "#ffffff"
                    :padding "32px"
                    :border-radius "12px"}
            (dsl/text {:text (str "\"" (:quote t) "\"")
                       :font-size "16px"
                       :margin {:bottom "16px"}})
            (dsl/div {}
              (dsl/text {:text (:author t)
                         :font-weight "600"})
              (dsl/text {:text (:title t)
                         :font-size "14px"
                         :color "#6b7280"}))))))))

(defn stats-section
  "Create a stats/numbers section
   
   Args:
     stats - Vector of maps:
       :number - Statistic number
       :label - Statistic label
       :suffix - Optional suffix (%, K, M, etc.)"
  [stats]
  (dsl/section {:background "#1a1a1a"
                :padding "60px 20px"}
    (dsl/container {}
      (dsl/columns {:columns (count stats)
                    :gap "48px"}
        (for [stat stats]
          (dsl/div {:text-align "center"}
            (dsl/heading {:text (str (:number stat) (or (:suffix stat) ""))
                          :level "h2"
                          :font-size "48px"
                          :font-weight "700"
                          :color "#ffffff"})
            (dsl/text {:text (:label stat)
                       :font-size "16px"
                       :color "#d1d5db"})))))))

(defn image-text-section
  "Create an image+text section
   
   Args:
     opts - Map with keys:
       :title - Section title
       :text - Section text
       :image - Image URL
       :image-side - \"left\" or \"right\" (default: \"left\")
       :cta-text - Optional button text
       :cta-url - Optional button URL"
  [{:keys [title text image image-side cta-text cta-url]}]
  (let [reverse? (= image-side "right")
        image-el (dsl/image {:src image
                             :alt title
                             :width "100%"
                             :border-radius "12px"})
        content (dsl/div {:display "flex"
                          :flex-direction "column"
                          :justify-content "center"
                          :gap "20px"}
                  (dsl/heading {:text title
                                :level "h2"
                                :font-size "36px"})
                  (dsl/text {:text text
                             :font-size "16px"
                             :color "#6b7280"})
                  (when cta-text
                    (dsl/button {:text cta-text
                                 :url cta-url
                                 :background "#4f46e5"
                                 :color "#ffffff"})))]
    (dsl/section {:padding "80px 20px"}
      (dsl/container {}
        (dsl/columns {:columns 2 :gap "48px"}
          (if reverse?
            [content image-el]
            [image-el content]))))))

;; ============================================================================
;; CALL-TO-ACTION SECTIONS
;; ============================================================================

(defn cta-banner
  "Create a full-width CTA banner
   
   Args:
     opts - Map with keys:
       :title - CTA title
       :subtitle - CTA subtitle
       :cta-text - Button text
       :cta-url - Button URL
       :background - Background color"
  [{:keys [title subtitle cta-text cta-url background]}]
  (dsl/section {:background (or background "#4f46e5")
                :padding "60px 20px"}
    (dsl/container {}
      (dsl/div {:display "flex"
                :flex-direction "column"
                :align-items "center"
                :text-align "center"
                :gap "24px"}
        (dsl/heading {:text title
                      :level "h2"
                      :font-size "36px"
                      :color "#ffffff"})
        (when subtitle
          (dsl/text {:text subtitle
                     :font-size "18px"
                     :color "#ffffff"}))
        (dsl/button {:text cta-text
                     :url cta-url
                     :background "#ffffff"
                     :color (or background "#4f46e5")
                     :padding "16px 32px"
                     :border-radius "8px"})))))

(defn cta-box
  "Create a bordered CTA box
   
   Args:
     opts - Map with keys:
       :title - CTA title
       :text - CTA text
       :cta-text - Button text
       :cta-url - Button URL"
  [{:keys [title text cta-text cta-url]}]
  (dsl/section {:padding "80px 20px"}
    (dsl/container {:max-width "800px"}
      (dsl/div {:background "#f9fafb"
                :padding "48px"
                :border-radius "12px"
                :text-align "center"}
        (dsl/heading {:text title
                      :level "h2"
                      :font-size "32px"
                      :margin {:bottom "16px"}})
        (dsl/text {:text text
                   :font-size "16px"
                   :color "#6b7280"
                   :margin {:bottom "24px"}})
        (dsl/button {:text cta-text
                     :url cta-url
                     :background "#4f46e5"
                     :color "#ffffff"
                     :padding "14px 28px"})))))

;; ============================================================================
;; PRICING SECTIONS
;; ============================================================================

(defn pricing-card
  "Create a single pricing card
   
   Args:
     opts - Map with keys:
       :name - Plan name
       :price - Price (e.g., \"$29\")
       :period - Period (e.g., \"/month\")
       :features - Vector of feature strings
       :cta-text - Button text
       :cta-url - Button URL
       :featured - Highlight this plan (true/false)"
  [{:keys [name price period features cta-text cta-url featured]}]
  (dsl/div {:background (if featured "#4f46e5" "#ffffff")
            :padding "32px"
            :border-radius "12px"
            :border (when-not featured "1px solid #e5e7eb")}
    (dsl/heading {:text name
                  :level "h3"
                  :font-size "20px"
                  :color (if featured "#ffffff" "#111827")
                  :margin {:bottom "8px"}})
    (dsl/div {:display "flex"
              :align-items "baseline"
              :margin {:bottom "24px"}}
      (dsl/heading {:text price
                    :level "h2"
                    :font-size "36px"
                    :color (if featured "#ffffff" "#111827")})
      (dsl/text {:text period
                 :font-size "16px"
                 :color (if featured "#e0e7ff" "#6b7280")}))
    (dsl/div {:margin {:bottom "24px"}}
      (for [feature features]
        (dsl/text {:text (str "✓ " feature)
                   :font-size "14px"
                   :color (if featured "#ffffff" "#111827")
                   :margin {:bottom "8px"}})))
    (dsl/button {:text cta-text
                 :url cta-url
                 :background (if featured "#ffffff" "#4f46e5")
                 :color (if featured "#4f46e5" "#ffffff")
                 :padding "12px 24px"
                 :width "100%"})))

(defn pricing-section
  "Create a pricing section with multiple plans
   
   Args:
     opts - Map with keys:
       :title - Section title
       :subtitle - Section subtitle
       :plans - Vector of pricing card options"
  [{:keys [title subtitle plans]}]
  (dsl/section {:background "#f9fafb"
                :padding "80px 20px"}
    (dsl/container {}
      (dsl/div {:text-align "center"
                :margin {:bottom "48px"}}
        (dsl/heading {:text title
                      :level "h2"
                      :font-size "36px"})
        (when subtitle
          (dsl/text {:text subtitle
                     :font-size "18px"
                     :color "#6b7280"})))
      (dsl/columns {:columns (min 3 (count plans))
                    :gap "32px"}
        (for [plan plans]
          (pricing-card plan))))))

;; ============================================================================
;; FOOTER ELEMENTS
;; ============================================================================

(defn footer
  "Create a footer section
   
   Args:
     opts - Map with keys:
       :logo-text - Logo text
       :tagline - Company tagline
       :links - Vector of link groups:
                {:title \"Group Title\"
                 :links [{:text \"Link\" :url \"/url\"}]}
       :social - Vector of social links:
                {:platform \"twitter\" :url \"...\"}
       :copyright - Copyright text"
  [{:keys [logo-text tagline links copyright]}]
  (dsl/section {:background "#1a1a1a"
                :padding "60px 20px"}
    (dsl/container {}
      (dsl/columns {:columns 4 :gap "48px"}
        ;; Logo column
        (dsl/div {}
          (dsl/heading {:text logo-text
                        :level "h3"
                        :font-size "24px"
                        :color "#ffffff"
                        :margin {:bottom "8px"}})
          (when tagline
            (dsl/text {:text tagline
                       :font-size "14px"
                       :color "#9ca3af"})))
        
        ;; Link columns
        (for [group links]
          (dsl/div {}
            (dsl/heading {:text (:title group)
                          :level "h4"
                          :font-size "14px"
                          :color "#ffffff"
                          :margin {:bottom "16px"}})
            (for [link (:links group)]
              (dsl/text {:text (:text link)
                         :font-size "14px"
                         :color "#9ca3af"
                         :margin {:bottom "8px"}})))))
      
      ;; Copyright
      (dsl/div {:text-align "center"
                :margin {:top "48px"}
                :padding {:top "24px"}
                :border-top "1px solid #374151"}
        (dsl/text {:text copyright
                   :font-size "14px"
                   :color "#6b7280"})))))

(comment
  ;; Example usage
  
  ;; Create a landing page with pre-built elements
  
  (dsl/create-page! {:title "Product Landing Page"}
    (navbar {:logo-text "MyBrand"
             :links [{:text "Home" :url "/"}
                     {:text "Features" :url "/#features"}
                     {:text "Pricing" :url "/#pricing"}]
             :sticky true})
    
    (split-hero {:title "Revolutionary Product"
                 :subtitle "Transform your workflow"
                 :cta-text "Get Started Free"
                 :cta-url "/signup"
                 :image "/images/hero.jpg"})
    
    (stats-section [{:number "10K" :label "Happy Customers"}
                    {:number "99.9" :suffix "%" :label "Uptime"}
                    {:number "24/7" :label "Support"}])
    
    (pricing-section {:title "Simple Pricing"
                      :subtitle "Choose the plan that fits your needs"
                      :plans [{:name "Starter"
                               :price "$29"
                               :period "/mo"
                               :features ["10 Projects" "1GB Storage" "Email Support"]
                               :cta-text "Get Started"
                               :cta-url "/signup?plan=starter"}
                              {:name "Pro"
                               :price "$99"
                               :period "/mo"
                               :features ["Unlimited Projects" "10GB Storage" "Priority Support"]
                               :cta-text "Get Started"
                               :cta-url "/signup?plan=pro"
                               :featured true}]})
    
    (cta-banner {:title "Ready to Get Started?"
                 :subtitle "Join thousands of happy customers"
                 :cta-text "Sign Up Free"
                 :cta-url "/signup"})
    
    (footer {:logo-text "MyBrand"
             :tagline "Building the future"
             :links [{:title "Product"
                      :links [{:text "Features" :url "/features"}
                              {:text "Pricing" :url "/pricing"}]}]
             :copyright "© 2025 MyBrand. All rights reserved."}))
  
  )

