(ns forma.dev.incremental
  "Incremental compilation support
  
   Provides functions for compiling only changed parts
   of a project for fast development feedback."
  (:require [forma.compiler :as compiler]
            [forma.dev.dependencies :as deps]
            [forma.dev.cache :as cache]
            [clojure.edn :as edn]
            [clojure.tools.logging :as log]))

(defn compile-incremental
  "Incrementally compile only changed parts
  
   Args:
     page-name - Name of page to compile
     changed-files - Vector of changed file paths
     context - Compilation context
   
   Returns:
     Compiled result"
  [page-name changed-files context]
  (let [project-name (get context :project-name)
        ;; Build dependency graph if not already built
        dependency-graph (deps/build-dependency-graph project-name)
        ;; Check if this page is affected
        affected-pages (deps/find-affected-pages changed-files dependency-graph)]
    (if (contains? affected-pages page-name)
      ;; Page is affected, recompile
      (let [page-path (str "projects/" project-name "/pages/" (name page-name) ".edn")
            page-content (try
                          (edn/read-string (slurp page-path))
                          (catch Exception e
                            (log/error e (str "Could not load page " page-name))
                            []))
            compiled (compiler/compile-to-html page-content context)]
        ;; Cache result
        (cache/set-cache [:page page-name] compiled)
        compiled)
      ;; Page not affected, return cached result
      (or (cache/get-cache [:page page-name])
          ;; Fallback: compile anyway if not cached
          (let [page-path (str "projects/" project-name "/pages/" (name page-name) ".edn")
                page-content (try
                              (edn/read-string (slurp page-path))
                              (catch Exception e
                                (log/error e (str "Could not load page " page-name))
                                []))
                compiled (compiler/compile-to-html page-content context)]
            (cache/set-cache [:page page-name] compiled)
            compiled)))))

(defn recompile-affected
  "Recompile all affected pages
  
   Args:
     changed-files - Vector of changed file paths
     context - Compilation context
   
   Returns:
     Map of page-name -> compiled result"
  [changed-files context]
  (let [project-name (get context :project-name)
        dependency-graph (deps/build-dependency-graph project-name)
        affected-pages (deps/find-affected-pages changed-files dependency-graph)
        results (atom {})]
    (doseq [page-name affected-pages]
      (try
        (let [compiled (compile-incremental page-name changed-files context)]
          (swap! results assoc page-name compiled))
        (catch Exception e
          (log/error e (str "Error compiling page " page-name)))))
    @results))

