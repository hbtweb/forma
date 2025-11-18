(ns forma.hierarchy.generator
  "Generate multi-file Forma project structure from classified properties.

  This module takes classified properties (from forma.hierarchy.classifier) and
  token registry (from forma.tokens.registry) to generate a complete multi-file
  Forma project with proper hierarchy:

  - default/global/defaults.edn - Token definitions + global defaults
  - default/components/{name}.edn - Component base definitions + variants
  - default/sections/{name}.edn - Section overrides (optional)
  - default/templates/{name}.edn - Template definitions (optional)
  - projects/{project}/pages/{name}.edn - Page instances"
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [forma.hierarchy.classifier :as classifier]
            [forma.tokens.registry :as registry]))

;;
;; File Path Generation
;;

(defn- level->directory
  "Map hierarchy level to directory path."
  [level project-name]
  (case level
    :global "default/global"
    :components "default/components"
    :sections "default/sections"
    :templates "default/templates"
    :pages (str "projects/" project-name "/pages")
    "default/global"))

(defn- generate-file-path
  "Generate file path for a given level and name."
  [level element-name project-name]
  (let [dir (level->directory level project-name)
        filename (if (= level :global)
                   "defaults.edn"
                   (str (name element-name) ".edn"))]
    (str dir "/" filename)))

;;
;; Component Extraction
;;

(defn- extract-component-definition
  "Extract component base definition from classified properties.

  Returns: {:button {:base {...} :variants {...}}}"
  [element-type classified-elements]
  (let [;; Get all elements of this type
        elements (filter #(= (:type %) element-type) classified-elements)

        ;; Extract base properties (common to all instances)
        base-props
        (reduce
         (fn [acc elem]
           (let [component-props (get-in elem [:classified :components] {})]
             (merge acc component-props)))
         {}
         elements)

        ;; Extract variants (differing properties with patterns)
        variants
        (reduce
         (fn [acc elem]
           (when-let [variant (get-in elem [:properties :variant])]
             (let [variant-props (get-in elem [:classified :components] {})]
               (assoc acc variant variant-props))))
         {}
         elements)]

    {element-type
     (cond-> {:base base-props}
       (seq variants) (assoc :variants variants))}))

(defn extract-components
  "Extract all component definitions from classified elements.

  Args:
    classified-elements - [{:type :button :properties {...} :classified {...}} ...]

  Returns: {:button {:base {...} :variants {...}}
            :input {:base {...}}
            ...}"
  [classified-elements]
  (let [element-types (distinct (map :type classified-elements))]
    (reduce
     (fn [acc elem-type]
       (merge acc (extract-component-definition elem-type classified-elements)))
     {}
     element-types)))

;;
;; Global Defaults Extraction
;;

(defn extract-global-defaults
  "Extract global defaults from token registry and global properties.

  Args:
    token-registry - Token definitions (from forma.tokens.registry)
    classified-elements - Elements with classified properties

  Returns: {:tokens {...} :defaults {...}}"
  [token-registry classified-elements]
  (let [;; Collect all global properties
        global-props
        (reduce
         (fn [acc elem]
           (let [elem-type (:type elem)
                 global (get-in elem [:classified :global] {})]
             (if (seq global)
               (update acc elem-type #(merge % global))
               acc)))
         {}
         classified-elements)]

    {:tokens token-registry
     :defaults global-props}))

;;
;; Page Instance Extraction
;;

(defn- extract-page-instance
  "Extract page-specific properties (instance overrides).

  Returns: {:home {:content [[:button {:text \"Submit\"}] ...]}}}"
  [page-name classified-elements]
  (let [;; Extract page-level properties
        page-content
        (mapv
         (fn [elem]
           (let [elem-type (:type elem)
                 page-props (get-in elem [:classified :pages] {})
                 ;; Include variant if specified at component level
                 variant (get-in elem [:properties :variant])]
             (if (seq page-props)
               [elem-type (cond-> page-props
                            variant (assoc :variant variant))]
               [elem-type])))
         classified-elements)]

    {page-name {:content page-content}}))

(defn extract-pages
  "Extract page instances from classified elements.

  Args:
    page-definitions - [{:name :home :elements [...]} ...]

  Returns: {:home {:content [...]} :about {:content [...]}}"
  [page-definitions]
  (reduce
   (fn [acc page-def]
     (let [page-name (:name page-def)
           elements (:elements page-def)]
       (merge acc (extract-page-instance page-name elements))))
   {}
   page-definitions))

;;
;; EDN Serialization
;;

(defn- format-edn
  "Format Clojure data structure as pretty-printed EDN."
  [data]
  (binding [*print-namespace-maps* false
            *print-length* nil
            *print-level* nil]
    (with-out-str
      (pprint/pprint data))))

(defn serialize-global-defaults
  "Serialize global defaults to EDN string."
  [global-data]
  (format-edn global-data))

(defn serialize-component
  "Serialize component definition to EDN string."
  [component-name component-def]
  (format-edn {component-name component-def}))

(defn serialize-page
  "Serialize page definition to EDN string."
  [page-name page-def]
  (format-edn {page-name page-def}))

;;
;; File Writing
;;

(defn- ensure-directory
  "Ensure directory exists, creating parent directories as needed."
  [file-path]
  (let [file (io/file file-path)
        parent (.getParentFile file)]
    (when parent
      (.mkdirs parent))))

(defn write-file
  "Write EDN content to file.

  Args:
    file-path - Relative file path (e.g., \"default/global/defaults.edn\")
    content   - EDN string
    base-dir  - Base directory (default: current directory)"
  ([file-path content]
   (write-file file-path content "."))
  ([file-path content base-dir]
   (let [full-path (str base-dir "/" file-path)]
     (ensure-directory full-path)
     (spit full-path content)
     {:path file-path
      :bytes (count (.getBytes content))
      :status :success})))

;;
;; High-Level Generation API
;;

(defn generate-file-structure
  "Generate complete multi-file Forma project structure.

  Args:
    classified-elements - Elements with classified properties
    token-registry      - Token definitions
    project-name        - Project name (default: \"imported-project\")
    base-dir            - Base directory (default: current directory)

  Returns:
    {:files [{:path \"default/global/defaults.edn\" :bytes 1234 :status :success} ...]
     :summary {:total-files 5 :total-bytes 12345}}

  Example:
    (generate-file-structure
      classified-elements
      token-registry
      \"my-imported-site\"
      \"forma/\")"
  ([classified-elements token-registry]
   (generate-file-structure classified-elements token-registry "imported-project" "."))
  ([classified-elements token-registry project-name]
   (generate-file-structure classified-elements token-registry project-name "."))
  ([classified-elements token-registry project-name base-dir]
   (let [;; Extract all file contents
         global-data (extract-global-defaults token-registry classified-elements)
         components (extract-components classified-elements)

         ;; Group elements by page (simplified: single page for now)
         page-defs [{:name :index :elements classified-elements}]
         pages (extract-pages page-defs)

         ;; Generate file contents
         files (atom [])

         ;; Write global defaults
         _ (swap! files conj
                  (write-file
                   (generate-file-path :global nil project-name)
                   (serialize-global-defaults global-data)
                   base-dir))

         ;; Write component definitions
         _ (doseq [[comp-name comp-def] components]
             (swap! files conj
                    (write-file
                     (generate-file-path :components comp-name project-name)
                     (serialize-component comp-name comp-def)
                     base-dir)))

         ;; Write page definitions
         _ (doseq [[page-name page-def] pages]
             (swap! files conj
                    (write-file
                     (generate-file-path :pages page-name project-name)
                     (serialize-page page-name page-def)
                     base-dir)))

         result @files
         total-bytes (reduce + (map :bytes result))]

     {:files result
      :summary {:total-files (count result)
                :total-bytes total-bytes
                :project-name project-name}})))

(defn preview-file-structure
  "Preview file structure without writing files (dry-run).

  Returns: Same structure as generate-file-structure but with :status :preview"
  [classified-elements token-registry project-name]
  (let [global-data (extract-global-defaults token-registry classified-elements)
        components (extract-components classified-elements)
        page-defs [{:name :index :elements classified-elements}]
        pages (extract-pages page-defs)

        files
        (concat
         ;; Global defaults
         [{:path (generate-file-path :global nil project-name)
           :content (serialize-global-defaults global-data)
           :status :preview}]

         ;; Components
         (map (fn [[comp-name comp-def]]
                {:path (generate-file-path :components comp-name project-name)
                 :content (serialize-component comp-name comp-def)
                 :status :preview})
              components)

         ;; Pages
         (map (fn [[page-name page-def]]
                {:path (generate-file-path :pages page-name project-name)
                 :content (serialize-page page-name page-def)
                 :status :preview})
              pages))

        total-bytes (reduce + (map #(count (:content %)) files))]

    {:files files
     :summary {:total-files (count files)
               :total-bytes total-bytes
               :project-name project-name}}))

;;
;; Convenience API
;;

(defn generate-from-flattened
  "Complete pipeline: Classify → Extract tokens → Generate files.

  This is the high-level API for importing external projects.

  Args:
    flattened-edn - Flattened Forma EDN (from parser)
    metadata      - Optional metadata (from sync mode)
    project-name  - Project name
    base-dir      - Base directory

  Returns: File generation result

  Example:
    (generate-from-flattened
      parsed-html
      nil
      \"my-wordpress-site\"
      \"forma/\")"
  ([flattened-edn]
   (generate-from-flattened flattened-edn nil "imported-project" "."))
  ([flattened-edn metadata]
   (generate-from-flattened flattened-edn metadata "imported-project" "."))
  ([flattened-edn metadata project-name]
   (generate-from-flattened flattened-edn metadata project-name "."))
  ([flattened-edn metadata project-name base-dir]
   (let [;; Build token registry
         token-registry (registry/build-token-registry flattened-edn metadata)

         ;; Build usage statistics
         usage-stats (classifier/build-usage-statistics flattened-edn)

         ;; Extract and classify all elements
         elements (classifier/extract-elements flattened-edn)

         classified-elements
         (mapv
          (fn [elem]
            (let [elem-metadata (when metadata
                                 (get metadata (:id elem)))
                  classified (classifier/classify-element-properties
                              elem usage-stats elem-metadata)]
              {:type (:type elem)
               :properties elem
               :classified classified}))
          elements)

         ;; Generate file structure
         result (generate-file-structure
                 classified-elements
                 token-registry
                 project-name
                 base-dir)]

     result)))

;;
;; Public API Summary
;;

(comment
  ;; Full pipeline (recommended)
  (generate-from-flattened
   parsed-html-edn
   optional-metadata
   "my-wordpress-site"
   "forma/")

  ;; Preview without writing files
  (preview-file-structure classified-elements token-registry "my-site")

  ;; Manual step-by-step
  (let [token-registry (registry/build-token-registry edn)
        usage-stats (classifier/build-usage-statistics edn)
        elements (classifier/extract-elements edn)
        classified (map #(classifier/classify-element-properties % usage-stats {}) elements)]
    (generate-file-structure classified token-registry "my-site" "forma/"))
  )
