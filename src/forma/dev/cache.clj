(ns forma.dev.cache
  "Caching for dev environment
  
   Provides multi-level caching with intelligent invalidation
   for fast development feedback."
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]))

(def cache-store (atom {}))

(defn hash-file
  "Hash a file for cache key
  
   Args:
     file-path - Path to file
   
   Returns:
     Hash string or nil if file doesn't exist"
  [file-path]
  (try
    (when (.exists (io/file file-path))
      (let [content (slurp file-path)]
        (str (hash content))))
    (catch Exception e
      (log/warn (str "Could not hash file " file-path ": " (.getMessage e)))
      nil)))

(defn hash-context
  "Hash context for cache key
  
   Args:
     context - Compilation context
   
   Returns:
     Hash string"
  [context]
  (str (hash (select-keys context [:platform-stack :styling-system :styling-stack :environment :project-name]))))

(defn get-cache
  "Get from cache
  
   Args:
     key - Cache key (vector or keyword)
   
   Returns:
     Cached value or nil"
  [key]
  (get @cache-store key))

(defn set-cache
  "Set cache value
  
   Args:
     key - Cache key (vector or keyword)
     value - Value to cache
     options - Optional map with :ttl (time to live in seconds)
   
   Returns:
     nil"
  [key value & [options]]
  (let [entry (merge {:value value
                     :timestamp (System/currentTimeMillis)}
                    options)]
    (swap! cache-store assoc key entry)))

(defn invalidate-cache
  "Invalidate cache for changed files
  
   Args:
     changed-files - Vector of changed file paths
   
   Returns:
     Number of cache entries invalidated"
  [changed-files]
  (let [invalidated (atom 0)]
    (swap! cache-store
           (fn [cache]
             (reduce-kv
              (fn [acc key _entry]
                (if (and (vector? key)
                        (= (first key) :file-hash)
                        (some #(= (hash-file %) (second key)) changed-files))
                  (do (swap! invalidated inc)
                      (dissoc acc key))
                  acc))
              cache
              cache)))
    @invalidated))

(defn clear-cache
  "Clear all cache entries
  
   Returns:
     nil"
  []
  (reset! cache-store {}))

(defn matches-pattern?
  "Check if key matches pattern
  
   Args:
     key - Cache key
     pattern - Pattern to match
   
   Returns:
     true if matches, false otherwise"
  [key pattern]
  (if (not= (count key) (count pattern))
    false
    (every? (fn [[k p]]
              (or (= p :*)
                  (= k p)))
            (map vector key pattern))))

(defn clear-cache-pattern
  "Clear cache entries matching a pattern
  
   Args:
     pattern - Pattern to match (vector with :* for wildcards)
   
   Returns:
     Number of entries cleared"
  [pattern]
  (let [cleared (atom 0)]
    (swap! cache-store
           (fn [cache]
             (reduce-kv
              (fn [acc key _entry]
                (if (matches-pattern? key pattern)
                  (do (swap! cleared inc)
                      (dissoc acc key))
                  acc))
              cache
              cache)))
    @cleared))

(defn get-cache-stats
  "Get cache statistics
  
   Returns:
     Map with :size, :entries keys"
  []
  {:size (count @cache-store)
   :entries (keys @cache-store)})

