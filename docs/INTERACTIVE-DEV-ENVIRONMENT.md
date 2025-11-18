# Interactive Dev Environment Considerations

## Overview

This document outlines considerations and strategies for using Forma compilation in an interactive development environment, such as a Next.js-like framework built on top of Forma. It covers development vs production modes, hot module replacement, caching strategies, incremental compilation, and more.

**See also**: 
- [Architecture](./ARCHITECTURE.md) - Core compiler architecture
- [Output Formats and Minification](./OUTPUT-FORMATS-AND-MINIFICATION.md) - Compilation output and optimization
- [Inheritance, Components, and Resolution](./INHERITANCE-COMPONENTS-RESOLUTION-ANALYSIS.md) - How inheritance works

---

## 1. Development vs Production Modes

### Principle

**Different optimization strategies for different environments**

- **Development**: Fast feedback, skip optimizations, preserve debugging info
- **Production**: Full optimization, minification, tree-shaking

### Configuration

```clojure
;; projects/my-app/config.edn
{:defaults {
   :environment :development  ; or :production
   :optimization {
     :development {
       :pre-compilation-optimization false  ; Skip tree-shaking in dev
       :minification false                   ; No minification in dev
       :source-maps true                     ; Enable source maps
       :cache-strategy :aggressive          ; Cache everything
       :incremental-compilation true         ; Only recompile changed files
       :skip-pre-resolution false            ; Still use pre-resolution (fast)
       :preserve-structure true              ; Don't flatten for debugging
     }
     :production {
       :pre-compilation-optimization true    ; Full optimization
       :minification true                    ; Minify output
       :source-maps false                    ; No source maps
       :cache-strategy :conservative         ; Cache only stable outputs
       :incremental-compilation false        ; Full compilation
       :skip-pre-resolution false            ; Use pre-resolution
       :preserve-structure false             ; Flatten for optimization
     }
   }
 }}
```

### Implementation

```clojure
(defn should-optimize?
  "Determine if optimization should run based on environment"
  [context]
  (let [environment (get context :environment :development)
        optimize-config (get-in context [:optimization :pre-compilation])]
    (and (= environment :production)
         (not= optimize-config false))))

(defn compile-with-environment
  "Compile with environment-aware optimizations"
  [elements context]
  (let [environment (get context :environment :development)
        ;; Skip expensive operations in dev
        optimized-elements (if (should-optimize? context)
                            (tree-shake-elements elements context)
                            elements)
        ;; Skip minification in dev
        compiled (compile-to-html optimized-elements context)
        minified (if (and (= environment :production)
                         (get-in context [:optimization :minification] true))
                  (minify-html compiled)
                  compiled)]
    minified))
```

**Key Differences:**

| Feature | Development | Production |
|---------|------------|------------|
| Tree-shaking | ❌ Skip | ✅ Full |
| Minification | ❌ Skip | ✅ Full |
| Source maps | ✅ Enable | ❌ Disable |
| Flattening | ❌ Preserve structure | ✅ Flatten |
| Caching | ✅ Aggressive | ✅ Conservative |
| Incremental | ✅ Enable | ❌ Full compile |

---

## 2. Hot Module Replacement (HMR) / Fast Refresh

### Principle

**Incremental compilation on file changes for instant feedback**

- Watch EDN files for changes
- Determine affected pages/components
- Recompile only what changed
- Send updates to client

### Implementation

```clojure
(defn watch-and-recompile
  "Watch EDN files and recompile incrementally"
  [project-name]
  (let [watcher (create-file-watcher project-name)]
    (watch watcher
      (fn [changed-files]
        ;; Determine what needs recompiling
        (let [affected-pages (find-affected-pages changed-files)
              ;; Only recompile affected pages
              recompiled (mapv #(compile-page % {:incremental true})
                              affected-pages)]
          ;; Send HMR update to client
          (send-hmr-update recompiled))))))

(defn find-affected-pages
  "Find pages affected by file changes"
  [changed-files]
  (let [changed-components (extract-components changed-files)
        changed-tokens (extract-tokens changed-files)
        changed-styles (extract-styles changed-files)
        changed-templates (extract-templates changed-files)
        changed-sections (extract-sections changed-files)
        
        ;; Find pages using changed components
        pages-using-components (find-pages-using-components changed-components)
        
        ;; Find pages using changed tokens
        pages-using-tokens (find-pages-using-tokens changed-tokens)
        
        ;; Find pages using changed styles
        pages-using-styles (find-pages-using-styles changed-styles)
        
        ;; Find pages using changed templates
        pages-using-templates (find-pages-using-templates changed-templates)
        
        ;; Find pages using changed sections
        pages-using-sections (find-pages-using-sections changed-sections)]
    
    ;; Combine all affected pages
    (distinct (concat pages-using-components
                     pages-using-tokens
                     pages-using-styles
                     pages-using-templates
                     pages-using-sections))))

(defn send-hmr-update
  "Send HMR update to client via WebSocket or similar"
  [recompiled-pages]
  (doseq [page recompiled-pages]
    (broadcast-hmr-update {:type :page-update
                          :path (:path page)
                          :html (:html page)
                          :timestamp (System/currentTimeMillis)})))
```

### Dependency Graph

**Build dependency graph for efficient HMR:**

```clojure
(defn build-dependency-graph
  "Build graph of page → component → token/style dependencies"
  [project-name]
  (let [pages (load-pages project-name)
        graph (atom {})]
    (doseq [page pages]
      (let [components (extract-components-from-page page)
            tokens (extract-tokens-from-page page)
            styles (extract-styles-from-page page)]
        (swap! graph assoc
               (:name page)
               {:components (set components)
                :tokens (set tokens)
                :styles (set styles)})))
    @graph))

(defn find-affected-by-change
  "Find all pages affected by a file change using dependency graph"
  [dependency-graph changed-file]
  (let [changed-type (determine-file-type changed-file)
        changed-name (extract-name changed-file)]
    (case changed-type
      :component (find-pages-using-component dependency-graph changed-name)
      :token (find-pages-using-token dependency-graph changed-name)
      :style (find-pages-using-style dependency-graph changed-name)
      :template (find-pages-using-template dependency-graph changed-name)
      :section (find-pages-using-section dependency-graph changed-name)
      :page [changed-name])))
```

---

## 3. Caching Strategies

### Principle

**Multi-level caching with intelligent invalidation**

- File hash cache (fastest)
- Context hash cache (medium)
- Full compilation (slowest, only when needed)

### Implementation

```clojure
(defn build-with-cache
  "Build with intelligent multi-level caching"
  [page-name context]
  (let [;; Level 1: File hash cache (fastest)
        file-hash (hash-edn-files page-name)
        cached-output (get-cache [:file-hash file-hash])]
    (if cached-output
      (do (log-cache-hit :file-hash)
          cached-output)
      (let [;; Level 2: Context hash cache
            context-hash (hash-context context)
            cached-context-output (get-cache [:context-hash context-hash])]
        (if cached-context-output
          (do (log-cache-hit :context-hash)
              ;; Promote to file hash cache
              (set-cache [:file-hash file-hash] cached-context-output)
              cached-context-output)
          (let [;; Level 3: Full compilation
                compiled (compile-page page-name context)]
            (log-cache-miss)
            ;; Cache at both levels
            (set-cache [:file-hash file-hash] compiled)
            (set-cache [:context-hash context-hash] compiled)
            compiled))))))

(defn hash-edn-files
  "Hash all EDN files that affect a page"
  [page-name]
  (let [dependencies (get-page-dependencies page-name)
        file-hashes (mapv hash-file dependencies)]
    (hash (sort file-hashes))))

(defn hash-context
  "Hash context for cache key"
  [context]
  (hash {:platform-stack (:platform-stack context)
         :styling-system (:styling-system context)
         :project-name (:project-name context)
         :environment (:environment context)
         :optimization (:optimization context)}))
```

### Cache Invalidation

**Intelligent invalidation based on dependencies:**

```clojure
(defn invalidate-cache
  "Invalidate cache when dependencies change"
  [changed-files]
  (let [affected-pages (find-affected-pages changed-files)]
    (doseq [page affected-pages]
      ;; Invalidate file hash cache
      (clear-cache [:file-hash (hash-edn-files page)])
      ;; Invalidate context hash cache for this page
      (clear-cache-pattern [:context-hash :* page]))))

(defn build-context-with-deps
  "Build context and track dependencies for cache invalidation"
  [context-options]
  (let [context (build-context context-options)
        ;; Track what this context depends on
        dependencies {:platform-stack (:platform-stack context)
                      :styling-system (:styling-system context)
                      :project-name (:project-name context)
                      :file-hashes (hash-project-files (:project-name context))}]
    (assoc context
           :dependencies dependencies
           :cache-key (hash dependencies))))
```

**Cache invalidation triggers:**
- File changes → Invalidate file hash cache
- Platform config changes → Invalidate context hash cache
- Styling system changes → Invalidate context hash cache
- Project config changes → Invalidate all caches
- Context options change → Invalidate context hash cache

---

## 4. Request-Time vs Build-Time Compilation

### Principle

**Different compilation strategies for different use cases**

- **Static Generation (SSG)**: Pre-compile at build time
- **Server-Side Rendering (SSR)**: Compile on each request
- **Incremental Static Regeneration (ISR)**: Compile on first request, cache, revalidate

### Static Site Generation (SSG)

```clojure
(defn generate-static-pages
  "Pre-compile pages at build time"
  [pages]
  (mapv (fn [page]
          (let [context (build-context {:environment :production
                                        :optimize true})
                html (compile-to-html (:elements page) context)]
            {:path (:path page)
             :html html
             :output-path (str "out/" (:path page) "/index.html")}))
        pages))

(defn build-static-site
  "Build complete static site"
  [project-name]
  (let [pages (load-pages project-name)
        static-pages (generate-static-pages pages)]
    ;; Write all pages to disk
    (doseq [page static-pages]
      (write-file (:output-path page) (:html page)))
    static-pages))
```

### Server-Side Rendering (SSR)

```clojure
(defn render-page-ssr
  "Compile page on each request"
  [page-name request]
  (let [context (build-context {:request request
                                :environment :production
                                :optimize true})
        compiled (compile-to-html (load-page page-name) context)]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body compiled}))

(defn ssr-handler
  "SSR handler for framework integration"
  [request]
  (let [page-name (extract-page-name request)]
    (render-page-ssr page-name request)))
```

### Incremental Static Regeneration (ISR)

```clojure
(defn render-page-isr
  "Compile on first request, cache, revalidate periodically"
  [page-name request]
  (let [cached (get-cache [:isr page-name])]
    (if (and cached (not-expired? cached))
      (do (log-cache-hit :isr)
          ;; Trigger background revalidation if needed
          (trigger-background-revalidation page-name)
          cached)
      (let [context (build-context {:request request
                                    :environment :production
                                    :optimize true})
            compiled (compile-to-html (load-page page-name) context)
            ;; Cache with expiration
            _ (set-cache [:isr page-name] compiled {:ttl 3600})]
        (log-cache-miss :isr)
        compiled))))

(defn trigger-background-revalidation
  "Trigger background revalidation for ISR"
  [page-name]
  (future
    (let [context (build-context {:environment :production
                                  :optimize true})
          recompiled (compile-to-html (load-page page-name) context)]
      (set-cache [:isr page-name] recompiled {:ttl 3600}))))
```

---

## 5. Source Maps for Debugging

### Principle

**Map compiled output back to EDN source for debugging**

- Map HTML elements → EDN source locations
- Map CSS classes → EDN style definitions
- Map tokens → EDN token definitions
- Enable "View Source" in dev tools

### Implementation

```clojure
(defn compile-with-source-maps
  "Compile with source map generation"
  [elements context]
  (let [compiled (compile-to-html elements context)
        source-map (build-source-map elements compiled)]
    {:html compiled
     :source-map source-map}))

(defn build-source-map
  "Build source map from EDN to compiled output"
  [elements compiled]
  (let [mappings (atom [])]
    (walk-elements
     (fn [element compiled-output]
       (swap! mappings conj
              {:edn-location (:location element)
               :edn-source (:source element)
               :compiled-output compiled-output
               :line (:line element)
               :column (:column element)}))
     elements
     compiled)
    {:version "3"
     :sources (mapv :edn-source @mappings)
     :mappings (encode-mappings @mappings)}))

(defn inject-source-map
  "Inject source map into HTML for dev tools"
  [html source-map]
  (if source-map
    (str html "\n<!--# sourceMappingURL=data:application/json;base64,"
         (base64-encode (json-encode source-map))
         " -->")
    html))
```

**Source map structure:**
- **EDN location**: File path and line/column
- **Compiled output**: HTML/CSS output location
- **Mappings**: Bidirectional mapping between source and output

---

## 6. Error Handling and Reporting

### Principle

**Fast, clear error reporting with context**

- Detailed errors in development
- Generic errors in production
- Source location information
- Helpful suggestions

### Implementation

```clojure
(defn compile-with-error-handling
  "Compile with detailed error reporting"
  [elements context]
  (try
    (compile-to-html elements context)
    (catch Exception e
      (let [error-context (extract-error-context e elements context)
            environment (:environment context)]
        (if (= environment :development)
          ;; Dev: Show detailed error
          (throw (ex-info "Compilation error"
                         {:error e
                          :message (.getMessage e)
                          :source-location (:location error-context)
                          :element (:element error-context)
                          :suggestion (:suggestion error-context)
                          :stack-trace (get-stack-trace e)}))
          ;; Prod: Generic error
          (throw (ex-info "Compilation error"
                         {:error e
                          :message "An error occurred during compilation"})))))))

(defn extract-error-context
  "Extract context from error for better reporting"
  [error elements context]
  (let [error-element (find-element-at-error error elements)
        location (get-location error-element)
        suggestion (generate-suggestion error error-element)]
    {:element error-element
     :location location
     :suggestion suggestion}))

(defn generate-suggestion
  "Generate helpful suggestion based on error"
  [error element]
  (cond
    (token-resolution-error? error)
    "Check that token is defined in global/defaults.edn or project tokens"
    
    (component-not-found-error? error)
    "Check that component is defined in components/ directory"
    
    (styling-error? error)
    "Check that styling system includes styles for this component"
    
    :else
    "Check EDN syntax and component definition"))
```

---

## 7. Watch Mode and File System Monitoring

### Principle

**Watch EDN files for changes and trigger recompilation**

- Monitor all relevant directories
- Detect file changes
- Trigger incremental recompilation
- Support multiple watch patterns

### Implementation

```clojure
(defn setup-watch-mode
  "Watch EDN files and trigger recompilation"
  [project-name]
  (let [watch-paths [(str "projects/" project-name "/pages")
                     (str "projects/" project-name "/templates")
                     (str "projects/" project-name "/sections")
                     (str "projects/" project-name "/components")
                     (str "projects/" project-name "/config.edn")
                     "default/components"
                     "default/styles"
                     "default/platforms"
                     "default/global"]
        watcher (create-watcher watch-paths)]
    (watch watcher
      (fn [event]
        (let [changed-file (:file event)
              event-type (:type event)  ; :created, :modified, :deleted
              affected-pages (find-affected-pages [changed-file])]
          (case event-type
            :created (doseq [page affected-pages]
                      (compile-page page {:incremental true}))
            :modified (doseq [page affected-pages]
                       (recompile-page page {:incremental true}))
            :deleted (doseq [page affected-pages]
                      (invalidate-page page))))))))

(defn create-watcher
  "Create file system watcher"
  [paths]
  {:paths paths
   :patterns ["**/*.edn"]
   :recursive true
   :ignore [".git/**" "node_modules/**" ".next/**" "out/**"]})
```

---

## 8. Incremental Compilation

### Principle

**Only recompile what changed for faster feedback**

- Track dependencies
- Determine affected pages
- Recompile incrementally
- Preserve cache for unchanged pages

### Implementation

```clojure
(defn compile-incremental
  "Incrementally compile only changed parts"
  [page-name changed-files]
  (let [;; Determine what changed
        changed-components (extract-components changed-files)
        changed-tokens (extract-tokens changed-files)
        changed-styles (extract-styles changed-files)
        changed-templates (extract-templates changed-files)
        
        ;; Find affected pages
        affected-pages (find-pages-using changed-components
                                        changed-tokens
                                        changed-styles
                                        changed-templates)
        
        ;; Recompile only affected pages
        recompiled (mapv #(compile-page % {:incremental true
                                         :changed-components changed-components
                                         :changed-tokens changed-tokens
                                         :changed-styles changed-styles
                                         :changed-templates changed-templates})
                        affected-pages)]
    recompiled))

(defn compile-page-incremental
  "Compile page with incremental optimization"
  [page-name options]
  (let [changed-components (:changed-components options)
        changed-tokens (:changed-tokens options)
        changed-styles (:changed-styles options)
        
        ;; Only re-resolve changed components/tokens/styles
        context (build-context {:incremental true
                               :changed-components changed-components
                               :changed-tokens changed-tokens
                               :changed-styles changed-styles})
        
        ;; Compile with incremental context
        compiled (compile-to-html (load-page page-name) context)]
    compiled))
```

---

## 9. Middleware Integration

### Principle

**Request-time compilation in middleware**

- Check cache first
- Compile on cache miss
- Cache result
- Support SSR and API routes

### Implementation

```clojure
(defn forma-middleware
  "Middleware for request-time compilation"
  [handler]
  (fn [request]
    (let [page-name (extract-page-name request)
          context (build-context {:request request
                                 :environment (get-env)})
          ;; Check cache first
          cache-key (hash-context context)
          cached (get-request-cache page-name cache-key)]
      (if cached
        (handler (assoc request :forma-output cached))
        (let [compiled (compile-to-html (load-page page-name) context)
              _ (set-request-cache page-name cache-key compiled)]
          (handler (assoc request :forma-output compiled)))))))

(defn extract-page-name
  "Extract page name from request"
  [request]
  (let [path (:path request)
        ;; Convert /about to :about
        page-name (keyword (subs path 1))]
    page-name))
```

---

## 10. Build Output Strategies

### Static Site Generation (SSG)

```clojure
(defn build-static
  "Build static HTML files"
  [pages]
  (mapv (fn [page]
          (let [context (build-context {:environment :production
                                       :optimize true})
                html (compile-to-html (:elements page) context)]
            {:path (:path page)
             :html html
             :output-path (str "out/" (:path page) "/index.html")}))
        pages))
```

### Server-Side Rendering (SSR)

```clojure
(defn build-ssr
  "Build SSR-ready pages"
  [pages]
  (mapv (fn [page]
          {:path (:path page)
           :compile-fn (fn [request]
                         (let [context (build-context {:request request
                                                      :environment :production
                                                      :optimize true})]
                           (compile-to-html (:elements page) context)))})
        pages))
```

### API Routes

```clojure
(defn build-api-route
  "Build API route handler"
  [route-name]
  (fn [request]
    (let [context (build-context {:request request
                                 :environment :production})
          compiled (compile-to-html (load-route route-name) context)]
      {:status 200
       :headers {"Content-Type" "text/html"}
       :body compiled})))
```

---

## 11. Performance Optimizations for Dev

### Principle

**Optimize for dev speed, not output size**

- Skip expensive operations
- Use aggressive caching
- Incremental compilation
- Fast error reporting

### Implementation

```clojure
(defn compile-dev
  "Fast compilation for dev mode"
  [elements context]
  (let [environment (:environment context)]
    (if (= environment :development)
      ;; Dev mode: Skip expensive operations
      (let [;; Skip pre-resolution (faster, but less optimized)
            context (dissoc context :pre-resolved-components)
            ;; Skip tree-shaking
            elements elements
            ;; Compile
            compiled (compile-to-html elements context)]
        compiled)
      ;; Prod mode: Full optimization
      (let [;; Use pre-resolution
            context (pre-resolve-context context)
            ;; Tree-shake
            elements (tree-shake-elements elements context)
            ;; Compile
            compiled (compile-to-html elements context)
            ;; Minify
            minified (minify-html compiled)]
        minified))))
```

---

## 12. Recommended Architecture

### Suggested Structure

```
forma-next/
├── pages/              # Next.js pages (EDN files)
│   ├── index.edn
│   ├── about.edn
│   └── api/
│       └── hello.edn
├── api/                # API routes (EDN files)
├── components/         # Shared components
├── templates/          # Page templates
├── middleware/         # Request-time compilation
├── build/              # Build-time compilation
│   ├── static.clj      # SSG
│   ├── ssr.clj         # SSR
│   └── api.clj         # API routes
├── dev/                # Dev server with HMR
│   ├── server.clj      # Dev server
│   ├── watch.clj       # File watching
│   └── hmr.clj         # Hot module replacement
└── cache/              # Compilation cache
    ├── file-hash/      # File hash cache
    ├── context-hash/   # Context hash cache
    └── isr/            # ISR cache
```

### Key Functions

```clojure
;; Dev server
(defn dev-server
  "Development server with HMR"
  [project-name port]
  (let [watcher (setup-watch-mode project-name)
        server (start-server port (forma-middleware identity))]
    {:server server
     :watcher watcher}))

;; Build functions
(defn build-static
  "Static site generation"
  [project-name]
  (let [pages (load-pages project-name)]
    (generate-static-pages pages)))

(defn build-ssr
  "Server-side rendering setup"
  [project-name]
  (let [pages (load-pages project-name)]
    (build-ssr-pages pages)))

;; Watch mode
(defn watch-mode
  "File watching and incremental compilation"
  [project-name]
  (setup-watch-mode project-name))

;; Cache manager
(defn cache-manager
  "Intelligent caching with invalidation"
  [project-name]
  {:get (fn [key] (get-cache key))
   :set (fn [key value] (set-cache key value))
   :invalidate (fn [pattern] (invalidate-cache pattern))})
```

---

## 13. Summary

### Critical Considerations

1. **Environment-aware compilation** - Different strategies for dev vs prod
2. **Incremental compilation** - Only recompile what changed
3. **Intelligent caching** - Multi-level with invalidation
4. **Source maps** - For debugging in dev
5. **Fast error reporting** - Detailed errors in dev, generic in prod
6. **File watching** - Monitor changes and trigger recompilation
7. **Request-time vs build-time** - Support SSG, SSR, ISR
8. **Dependency tracking** - For cache invalidation
9. **Skip optimizations in dev** - For faster feedback
10. **Middleware integration** - For SSR and API routes

### Performance Trade-offs

| Strategy | Dev Speed | Prod Size | Complexity |
|----------|-----------|-----------|------------|
| Skip tree-shaking | ✅ Faster | ❌ Larger | ✅ Simple |
| Aggressive caching | ✅ Faster | ⚠️ Memory | ⚠️ Medium |
| Incremental compile | ✅ Faster | ⚠️ Partial | ⚠️ Medium |
| Source maps | ⚠️ Slower | ❌ Larger | ✅ Simple |
| Full optimization | ❌ Slower | ✅ Smaller | ✅ Simple |

### Best Practices

1. **Dev mode**: Skip optimizations, use aggressive caching, enable source maps
2. **Prod mode**: Full optimization, conservative caching, no source maps
3. **HMR**: Track dependencies, only recompile affected pages
4. **Caching**: Multi-level with intelligent invalidation
5. **Error handling**: Detailed in dev, generic in prod
6. **Watch mode**: Monitor all relevant directories
7. **Incremental**: Only recompile what changed

This architecture provides maximum efficiency and flexibility for interactive development while maintaining production-ready output.

