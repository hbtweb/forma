(ns forma.oxygen-deploy-demo
  "Quick demo of conversational Oxygen design deployment.

   This namespace shows how to:
   1. Generate Forma EDN from natural language descriptions
   2. Compile to Oxygen JSON
   3. Deploy to WordPress via REST API

   Usage (REPL):
     ;; Set environment variables first
     (System/setProperty \"WORDPRESS_URL\" \"https://hbtcomputers.com.au\")
     (System/setProperty \"WORDPRESS_USER\" \"your-username\")
     (System/setProperty \"WORDPRESS_APP_PASSWORD\" \"your-app-password\")

     ;; Deploy a page
     (deploy-hero-section)

     ;; Or deploy custom design
     (deploy-page \"My Page\" my-elements)"
  (:require [forma.sync.client :as sync]
            [forma.integrations.oxygen.compiler :as oxygen]))

;; =============================================================================
;; Example: Hero Section
;; =============================================================================

(def hero-section
  "Example: Professional hero section with heading, text, and CTA button"
  [{:type :section
    :props {:design/background "#1a202c"
            :design/padding "80px 20px"
            :design/text-align "center"}
    :children [{:type :heading
                :props {:level 1
                        :text "Professional IT Solutions"
                        :design/color "#ffffff"
                        :design/font-size "48px"
                        :design/margin-bottom "20px"}}
               {:type :text
                :props {:text "Expert computer services for your business"
                        :design/color "#e2e8f0"
                        :design/font-size "20px"
                        :design/margin-bottom "40px"}}
               {:type :button
                :props {:text "Get Started"
                        :url "/contact"
                        :design/background "#3182ce"
                        :design/color "#ffffff"
                        :design/padding "15px 40px"
                        :design/border-radius "8px"}}]}])

;; =============================================================================
;; Example: Services Section
;; =============================================================================

(def services-section
  "Example: Services grid with 3 cards"
  [{:type :section
    :props {:design/padding "60px 20px"
            :design/background "#f7fafc"}
    :children [{:type :heading
                :props {:level 2
                        :text "Our Services"
                        :design/text-align "center"
                        :design/margin-bottom "40px"}}
               {:type :div
                :props {:design/display "grid"
                        :design/grid-template-columns "repeat(3, 1fr)"
                        :design/gap "30px"}
                :children [{:type :card
                            :props {:design/padding "30px"
                                    :design/background "#ffffff"
                                    :design/border-radius "12px"}
                            :children [{:type :heading
                                       :props {:level 3
                                               :text "Computer Repair"}}
                                      {:type :text
                                       :props {:text "Fast and reliable repairs"}}]}
                           {:type :card
                            :props {:design/padding "30px"
                                    :design/background "#ffffff"
                                    :design/border-radius "12px"}
                            :children [{:type :heading
                                       :props {:level 3
                                               :text "Network Setup"}}
                                      {:type :text
                                       :props {:text "Professional network configuration"}}]}
                           {:type :card
                            :props {:design/padding "30px"
                                    :design/background "#ffffff"
                                    :design/border-radius "12px"}
                            :children [{:type :heading
                                       :props {:level 3
                                               :text "Security Solutions"}}
                                      {:type :text
                                       :props {:text "Protect your business"}}]}]}]}])

;; =============================================================================
;; Deployment Functions
;; =============================================================================

(defn deploy-page
  "Deploy a page to WordPress/Oxygen.

   Args:
     title - Page title
     elements - Vector of Forma EDN elements
     opts - Options map (optional)
       :status - \"publish\" or \"draft\" (default: \"draft\")

   Returns:
     {:success true/false
      :data {...}
      :error \"...\"}"
  ([title elements]
   (deploy-page title elements {}))
  ([title elements opts]
   (let [status (get opts :status "draft")
         ;; Compile EDN to Oxygen JSON
         oxygen-tree (oxygen/compile-to-oxygen elements)

         ;; Deploy via sync client
         result (sync/publish :wordpress
                             title
                             elements
                             {:project-name "hbt-computers"
                              :metadata {:post_type "page"
                                        :status status
                                        :tree oxygen-tree}})]
     result)))

(defn deploy-hero-section
  "Quick demo: Deploy hero section to WordPress.

   Returns:
     {:success true/false
      :data {:id 123 :url \"...\" :edit_url \"...\"}
      :error \"...\"}"
  []
  (deploy-page "Homepage Hero" hero-section {:status "draft"}))

(defn deploy-services-page
  "Quick demo: Deploy full services page with hero and services sections.

   Returns:
     {:success true/false
      :data {...}
      :error \"...\"}"
  []
  (deploy-page "Services"
               (vec (concat hero-section services-section))
               {:status "draft"}))

;; =============================================================================
;; Conversational Design Workflow
;; =============================================================================

(comment
  ;; STEP 1: Set your WordPress credentials
  (System/setProperty "WORDPRESS_URL" "https://hbtcomputers.com.au")
  (System/setProperty "WORDPRESS_USER" "your-username")
  (System/setProperty "WORDPRESS_APP_PASSWORD" "xxxx xxxx xxxx xxxx xxxx xxxx")

  ;; STEP 2: Deploy a test page
  (deploy-hero-section)
  ;; => {:success true
  ;;     :data {:id 123
  ;;            :url "https://hbtcomputers.com.au/homepage-hero/"
  ;;            :edit_url "https://hbtcomputers.com.au/wp-admin/admin.php?page=oxygen_vsb_sign_shortcodes&action=edit&post=123"}}

  ;; STEP 3: Check the result in your browser
  ;; Open the :url to see the live page
  ;; Open the :edit_url to edit in Oxygen Builder

  ;; STEP 4: Update the design
  (deploy-page "Homepage Hero"
               [{:type :section
                 :props {:design/background "#2d3748"  ; Darker background
                         :design/padding "100px 20px"}  ; Taller section
                 :children [{:type :heading
                            :props {:level 1
                                    :text "Updated: Professional IT Solutions"
                                    :design/color "#ffffff"
                                    :design/font-size "60px"}}]}]  ; Bigger heading
               {:status "draft"})

  ;; STEP 5: Publish when ready
  (deploy-page "Homepage Hero" hero-section {:status "publish"})

  ;; STEP 6: Deploy full services page
  (deploy-services-page)

  ;; ======================================================================
  ;; CONVERSATIONAL WORKFLOW EXAMPLE
  ;; ======================================================================

  ;; You: "Create a contact form with name, email, message, and submit button"
  ;; Me: "Let me create that for you..."

  (def contact-form
    [{:type :section
      :props {:design/padding "60px 20px"
              :design/background "#ffffff"}
      :children [{:type :heading
                  :props {:level 2
                          :text "Get In Touch"
                          :design/margin-bottom "40px"}}
                 {:type :form
                  :props {:action "/submit-contact"}
                  :children [{:type :input
                             :props {:name "name"
                                     :placeholder "Your Name"
                                     :design/margin-bottom "20px"}}
                            {:type :input
                             :props {:name "email"
                                     :type "email"
                                     :placeholder "Your Email"
                                     :design/margin-bottom "20px"}}
                            {:type :textarea
                             :props {:name "message"
                                     :placeholder "Your Message"
                                     :design/margin-bottom "20px"}}
                            {:type :button
                             :props {:text "Send Message"
                                     :design/background "#3182ce"
                                     :design/color "#ffffff"}}]}]}])

  (deploy-page "Contact Us" contact-form {:status "draft"})

  ;; You: "Make the form narrower and center it"
  ;; Me: "I'll update the design..."

  (def contact-form-v2
    [{:type :section
      :props {:design/padding "60px 20px"
              :design/background "#ffffff"
              :design/display "flex"
              :design/justify-content "center"}
      :children [{:type :div
                  :props {:design/max-width "600px"
                          :design/width "100%"}
                  :children [{:type :heading
                             :props {:level 2
                                     :text "Get In Touch"
                                     :design/margin-bottom "40px"}}
                            {:type :form
                             :props {:action "/submit-contact"}
                             :children [{:type :input
                                        :props {:name "name"
                                                :placeholder "Your Name"
                                                :design/margin-bottom "20px"}}
                                       {:type :input
                                        :props {:name "email"
                                                :type "email"
                                                :placeholder "Your Email"
                                                :design/margin-bottom "20px"}}
                                       {:type :textarea
                                        :props {:name "message"
                                                :placeholder "Your Message"
                                                :design/margin-bottom "20px"}}
                                       {:type :button
                                        :props {:text "Send Message"
                                                :design/background "#3182ce"
                                                :design/color "#ffffff"}}]}]}]}])

  (deploy-page "Contact Us" contact-form-v2 {:status "draft"})

  ;; That's the workflow! Describe changes → I update EDN → Redeploy → See result
)
