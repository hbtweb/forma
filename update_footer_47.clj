(require '[clj-http.client :as http]
         '[cheshire.core :as json])

(println "=== Updating Footer 47 with Fixed Structure ===\n")

(def wp-url "http://hbtcomputers.com.au.test")
(def wp-user "admin")
(def wp-pass "T8W8rxIo5y566jm79HgSs9Mi")
(def footer-id 47)

;; Helper to create node IDs
(def id-counter (atom 99))
(defn next-id [] (swap! id-counter inc))

;; Helper to create a text link element (no nil parent!)
(defn create-link [text url parent-id]
  {:id (next-id)
   :data {:type "EssentialElements\\TextLink"
          :properties {:content {:content {:text text
                                           :link {:type "url"
                                                  :url url
                                                  :opensInNewTab false}}}
                      :design {:typography {:size {:number 12 :unit "px"}
                                          :color "hsl(var(--muted-foreground))"}
                              :hover {:typography {:color "hsl(var(--primary))"}}}}}
   :children []
   :_parentId parent-id})

;; Helper to create a section heading
(defn create-section-heading [text parent-id]
  {:id (next-id)
   :data {:type "EssentialElements\\Heading"
          :properties {:content {:content {:text text
                                           :tags "h3"}}
                      :design {:typography {:size {:number 12 :unit "px"}
                                          :weight "600"
                                          :color "hsl(var(--foreground))"}}}}
   :children []
   :_parentId parent-id})

;; Helper to create a link list
(defn create-link-list [links parent-id]
  (let [list-id (next-id)]
    {:id list-id
     :data {:type "EssentialElements\\Div"
            :properties {:design {:spacing {:margin {:top {:number 4 :unit "px"}}}}}}
     :children (mapv #(create-link (:text %) (:url %) list-id) links)
     :_parentId parent-id}))

;; Helper to create a footer column
(defn create-footer-column [sections parent-id]
  (let [col-id (next-id)]
    {:id col-id
     :data {:type "EssentialElements\\Div"
            :properties {:design {:spacing {:padding {:top {:number 8 :unit "px"}
                                                     :bottom {:number 8 :unit "px"}}}}}}
     :children (mapv (fn [section]
                      (let [section-id (next-id)
                            heading (create-section-heading (:title section) section-id)
                            link-list (create-link-list (:links section) section-id)]
                        {:id section-id
                         :data {:type "EssentialElements\\Div"
                                :properties {:design {:spacing {:margin {:bottom {:number 12 :unit "px"}}}}}}
                         :children [heading link-list]
                         :_parentId col-id}))
                    sections)
     :_parentId parent-id}))

;; Footer data structure (based on Mesh footer)
(def footer-sections
  {:shop [{:title "Shop"
           :links [{:text "Departments" :url "/departments"}
                  {:text "Hot Deals" :url "/deals"}
                  {:text "New Arrivals" :url "/new-arrivals"}
                  {:text "Gift Cards" :url "/gift-cards"}
                  {:text "Product Availability" :url "/availability"}]}
          {:title "Services"
           :links [{:text "Repairs and Upgrades" :url "/services/repairs"}
                  {:text "Custom PCs and Workstations" :url "/services/custom-pcs"}
                  {:text "Managed IT and MSP" :url "/services/business"}
                  {:text "Installations and Smart Home" :url "/services/installations"}
                  {:text "Book a Service" :url "/services/book"}]}]
   :accounts [{:title "Accounts"
              :links [{:text "Manage Account" :url "/account"}
                     {:text "Track Orders" :url "/orders/track"}
                     {:text "Order History" :url "/orders/history"}
                     {:text "Wishlists and Builds" :url "/wishlist"}]}
             {:title "Support"
              :links [{:text "Help Centre" :url "/help"}
                     {:text "Returns and Exchanges" :url "/returns"}
                     {:text "Shipping and Click and Collect" :url "/shipping"}
                     {:text "Warranty and Protection Plans" :url "/warranty"}
                     {:text "Payment Options" :url "/payments"}
                     {:text "Recycling and E-Waste" :url "/recycling"}]}]
   :community [{:title "Community"
               :links [{:text "News and Updates" :url "/news"}
                      {:text "Events and Workshops" :url "/events"}
                      {:text "Careers in Western Australia" :url "/careers"}
                      {:text "Supplier Partnerships" :url "/partners/suppliers"}
                      {:text "Small Business Hub" :url "/business/small-business"}
                      {:text "Local Sponsorships" :url "/community/sponsorships"}
                      {:text "Creator and Student Programs" :url "/community/creators"}
                      {:text "Contact Us" :url "/contact"}]}]
   :programs [{:title "Business"
              :links [{:text "Business Solutions" :url "/business"}
                     {:text "Commercial Accounts" :url "/business/accounts"}
                     {:text "Partner and Reseller Portal" :url "/partners/resellers"}]}
             {:title "Education"
              :links [{:text "Education Programs" :url "/education"}
                     {:text "Campus and Student Offers" :url "/education/campus"}
                     {:text "Schools and TAFE Services" :url "/education/schools"}]}
             {:title "Government"
              :links [{:text "Government Procurement" :url "/government"}
                     {:text "Industry Compliance" :url "/government/compliance"}
                     {:text "Public Sector Support" :url "/government/support"}]}]})

;; Build the footer tree
(defn build-footer-tree []
  (let [;; Main container
        main-container-id (next-id)

        ;; Grid container
        grid-id (next-id)

        ;; Create 4 columns
        col1 (create-footer-column (:shop footer-sections) grid-id)
        col2 (create-footer-column (:accounts footer-sections) grid-id)
        col3 (create-footer-column (:community footer-sections) grid-id)
        col4 (create-footer-column (:programs footer-sections) grid-id)

        ;; Grid container
        grid-container {:id grid-id
                       :data {:type "EssentialElements\\Div"
                              :properties {:design {:layout_v2 {:layout "grid"
                                                               :grid {:columnCount 4
                                                                     :columnGap {:number 24 :unit "px"}
                                                                     :rowGap {:number 16 :unit "px"}}}
                                                   :spacing {:margin {:bottom {:number 16 :unit "px"}}}}}}
                       :children [col1 col2 col3 col4]
                       :_parentId main-container-id}

        ;; Lower footer with copyright
        lower-footer-id (next-id)
        copyright-id (next-id)
        legal-links-id (next-id)

        lower-footer {:id lower-footer-id
                     :data {:type "EssentialElements\\Div"
                            :properties {:design {:border {:top {:width {:number 1 :unit "px"}
                                                                :style "solid"
                                                                :color "hsl(var(--border))"}}
                                                 :spacing {:padding {:top {:number 16 :unit "px"}}}
                                                 :layout_v2 {:layout "horizontal"
                                                           :h_align {:breakpoint_base "space-between"}
                                                           :v_align {:breakpoint_base "center"}}}}}
                     :children [{:id copyright-id
                                :data {:type "EssentialElements\\Text"
                                       :properties {:content {:text (str "© " (.getYear (java.time.LocalDate/now)) " HBT Computers. All rights reserved.")}
                                                   :design {:typography {:size {:number 12 :unit "px"}
                                                                       :color "hsl(var(--muted-foreground))"}}}}
                                :children []
                                :_parentId lower-footer-id}
                               {:id legal-links-id
                                :data {:type "EssentialElements\\Div"
                                       :properties {:design {:layout_v2 {:layout "horizontal"
                                                                        :gap {:number 12 :unit "px"}}}}}
                                :children [(create-link "Privacy" "/privacy" legal-links-id)
                                          (create-link "Terms" "/terms-site" legal-links-id)
                                          (create-link "Legal" "/legal" legal-links-id)
                                          (create-link "Sitemap" "/sitemap" legal-links-id)]
                                :_parentId lower-footer-id}]
                     :_parentId main-container-id}

        ;; Main container
        main-container {:id main-container-id
                       :data {:type "EssentialElements\\Div"
                              :properties {:design {:sizing {:maxWidth {:number 1400 :unit "px"}}
                                                   :layout_v2 {:layout "vertical"}
                                                   :spacing {:margin {:left "auto" :right "auto"}
                                                            :padding {:left {:number 16 :unit "px"}
                                                                    :right {:number 16 :unit "px"}
                                                                    :top {:number 24 :unit "px"}
                                                                    :bottom {:number 24 :unit "px"}}}}}}
                       :children [grid-container lower-footer]
                       :_parentId 1}

        ;; Root
        root {:id 1
              :data {:type "root"
                     :properties []}
              :children [main-container]}]

    {:_nextNodeId (inc @id-counter)
     :status "exported"
     :root root}))

;; Create new footer tree
(println "1. Creating corrected footer tree...")
(def new-footer-tree (build-footer-tree))

(println (str "  ✓ Tree created with " @id-counter " nodes"))
(println (str "  ✓ _nextNodeId: " (:_nextNodeId new-footer-tree)))

;; Update footer 47
(println "\n2. Updating footer 47 with corrected tree...")
(let [payload {:id footer-id
              :title "HBT Computers Footer"
              :post_type "oxygen_footer"
              :status "publish"
              :tree new-footer-tree
              :settings {:type "everywhere"
                        :priority 1}}
      response (http/post (str wp-url "/index.php?rest_route=/oxygen/v1/save")
                         {:basic-auth [wp-user wp-pass]
                          :content-type :json
                          :accept :json
                          :body (json/generate-string payload)
                          :throw-exceptions false})]

  (if (= 200 (:status response))
    (let [result (json/parse-string (:body response) true)]
      (println "  ✓ Footer updated successfully!")
      (println (str "  ✓ ID: " (:id result)))
      (println (str "  ✓ URL: " (:url result))))
    (do
      (println "  ✗ Failed to update footer")
      (println (str "  Status: " (:status response)))
      (println (str "  Body: " (:body response))))))

(println "\n=== Update Complete ===")
(println "\nFooter 47 now has correct _parentId values!")
(println "Open in builder: http://hbtcomputers.com.au.test/wp-admin/admin.php?page=oxygen_vsb_sign_shortcodes&action=edit&post=47")