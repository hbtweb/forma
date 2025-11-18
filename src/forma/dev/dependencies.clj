(ns forma.dev.dependencies
  "Dependency tracking for incremental compilation
  
   Builds and maintains dependency graphs to determine which pages
   are affected by file changes for efficient incremental compilation."
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.walk :as walk]))

(defn extract-components-from-page
  "Extract component types from a page
  
   Args:
     page - Page EDN content
   
   Returns:
     Set of component type keywords"
  [page]
  (let [components (atom #{})]
    (clojure.walk/postwalk
     (fn [form]
       (when (and (vector? form) (keyword? (first form)))
         (swap! components conj (first form)))
       form)
     page)
    @components))

(defn extract-tokens-from-page
  "Extract token references from a page
  
   Args:
     page - Page EDN content
   
   Returns:
     Set of token path strings"
  [page]
  (let [tokens (atom #{})]
    (clojure.walk/postwalk
     (fn [form]
       (when (and (string? form) (str/starts-with? form "$"))
         (swap! tokens conj (subs form 1)))
       form)
     page)
    @tokens))

(defn build-dependency-graph
  "Build graph of page → component → token/style dependencies
  
   Args:
     project-name - Name of the project
   
   Returns:
     Map with :pages key containing dependency graph"
  [project-name]
  (let [pages-dir (io/file (str "projects/" project-name "/pages"))
        graph (atom {})]
    (when (.exists pages-dir)
      (doseq [file (.listFiles pages-dir)
              :when (.endsWith (.getName file) ".edn")]
        (try
          (let [page-name (keyword (.replace (.getName file) ".edn" ""))
                page-content (edn/read-string (slurp file))
                components (extract-components-from-page page-content)
                tokens (extract-tokens-from-page page-content)]
            (swap! graph assoc page-name
                   {:components components
                    :tokens tokens
                    :file (.getPath file)}))
          (catch Exception e
            (log/warn (str "Could not parse page " (.getName file) ": " (.getMessage e)))))))
    {:pages @graph}))

(defn find-affected-pages
  "Find pages affected by file changes
  
   Args:
     changed-files - Vector of changed file paths
     dependency-graph - Dependency graph from build-dependency-graph
   
   Returns:
     Set of page names that are affected"
  [changed-files dependency-graph]
  (let [affected (atom #{})]
    (doseq [changed-file changed-files]
      (let [file-name (.getName (io/file changed-file))
            file-type (cond
                       (str/includes? changed-file "components/") :component
                       (str/includes? changed-file "styles/") :style
                       (str/includes? changed-file "tokens/") :token
                       (str/includes? changed-file "pages/") :page
                       :else :unknown)]
        (case file-type
          :page (swap! affected conj (keyword (.replace file-name ".edn" "")))
          :component (doseq [[page-name page-data] (:pages dependency-graph)]
                      (when (contains? (:components page-data) (keyword (.replace file-name ".edn" "")))
                        (swap! affected conj page-name)))
          :style (swap! affected into (keys (:pages dependency-graph))) ; All pages potentially affected
          :token (doseq [[page-name page-data] (:pages dependency-graph)]
                  (when (some #(str/includes? % (.replace file-name ".edn" "")) (:tokens page-data))
                    (swap! affected conj page-name)))
          :unknown nil)))
    @affected))

