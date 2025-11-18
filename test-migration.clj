(require 'forma.compiler)
(require 'clojure.java.io)

(println "=== Forma Migration Verification ===\n")

;; Test 1: Config loading
(println "1. Testing config loading...")
(try
  (let [config ((resolve 'forma.compiler/load-config))]
    (if (map? config)
      (do
        (println "   ✓ Config loaded successfully")
        (println (str "   - Default path: " (get-in config [:paths :default])))
        (println (str "   - Resolution order: " (get-in config [:resolution-order]))))
      (println "   ✗ Config is not a map")))
  (catch Exception e
    (println (str "   ✗ Config loading failed: " (.getMessage e)))))

;; Test 2: Directory structure
(println "\n2. Testing directory structure...")
(let [dirs ["default" "library" "projects"]
      all-exist (every? #(.exists (clojure.java.io/file %)) dirs)]
  (if all-exist
    (do
      (println "   ✓ All directories exist")
      (doseq [dir dirs]
        (println (str "   - " dir "/"))))
    (do
      (println "   ✗ Some directories missing")
      (doseq [dir dirs]
        (println (str "   - " dir ": " (if (.exists (clojure.java.io/file dir)) "exists" "missing")))))))

;; Test 3: Resource loading
(println "\n3. Testing resource loading...")
(try
  (let [load-resource-fn (resolve 'forma.compiler/load-resource)
        global (load-resource-fn "global/defaults.edn")]
    (if (map? global)
      (do
        (println "   ✓ Global defaults loaded successfully")
        (println (str "   - Keys: " (take 5 (keys global)))))
      (println "   ✗ Global defaults is not a map")))
  (catch Exception e
    (println (str "   ✗ Resource loading failed: " (.getMessage e)))))

;; Test 4: Platform loading
(println "\n4. Testing platform loading...")
(try
  (let [load-platform-config-fn (resolve 'forma.compiler/load-platform-config)
        html-config (load-platform-config-fn :html)]
    (if (and (map? html-config) (contains? html-config :platform))
      (do
        (println "   ✓ HTML platform config loaded successfully")
        (println (str "   - Platform: " (:platform html-config)))
        (println (str "   - Elements: " (count (get html-config :elements {})))))
      (println "   ✗ Platform config is invalid")))
  (catch Exception e
    (println (str "   ✗ Platform loading failed: " (.getMessage e)))))

;; Test 5: Hierarchy loading
(println "\n5. Testing hierarchy loading...")
(try
  (let [load-hierarchy-data-fn (resolve 'forma.compiler/load-hierarchy-data)
        hierarchy (load-hierarchy-data-fn)]
    (if (map? hierarchy)
      (do
        (println "   ✓ Hierarchy data loaded successfully")
        (println (str "   - Components: " (count (get hierarchy :components {}))))
        (println (str "   - Sections: " (count (get hierarchy :sections {}))))
        (println (str "   - Templates: " (count (get hierarchy :templates {})))))
      (println "   ✗ Hierarchy data is not a map")))
  (catch Exception e
    (println (str "   ✗ Hierarchy loading failed: " (.getMessage e)))))

;; Test 6: Compilation
(println "\n6. Testing compilation...")
(try
  (let [element {:type :button :props {:text "Click me"} :children []}
        build-context-fn (resolve 'forma.compiler/build-context)
        context (build-context-fn {})
        forma-compiler-var (resolve 'forma.compiler/forma-compiler)
        compile-element-fn (resolve 'kora.core.compiler/compile-element)]
    (if (and build-context-fn context forma-compiler-var compile-element-fn)
      (let [compiled (compile-element-fn @forma-compiler-var element context)]
        (if (vector? compiled)
          (do
            (println "   ✓ Compilation successful")
            (println (str "   - Compiled element: " (pr-str (take 3 compiled)))))
          (println "   ✗ Compilation result is not a vector")))
      (println "   ✗ Could not resolve compiler functions")))
  (catch Exception e
    (println (str "   ✗ Compilation failed: " (.getMessage e)))))

(println "\n=== Verification Complete ===")

