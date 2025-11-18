(ns forma.dev.test-migration
  "Test script to verify migration and compiler functionality"
  (:require [forma.compiler :as compiler]
            [clojure.java.io :as io]
            [clojure.edn :as edn]))

(defn test-config-loading
  "Test that config.edn loads correctly"
  []
  (println "Testing config loading...")
  (try
    ;; Access the private var via the namespace
    (let [load-config-fn (resolve 'forma.compiler/load-config)
          config (load-config-fn)]
      (if (map? config)
        (do
          (println "✓ Config loaded successfully")
          (println (str "  - Default path: " (get-in config [:paths :default])))
          (println (str "  - Resolution order: " (get-in config [:resolution-order])))
          true)
        (do
          (println "✗ Config is not a map")
          false)))
    (catch Exception e
      (println (str "✗ Config loading failed: " (.getMessage e)))
      (.printStackTrace e)
      false)))

(defn test-resource-loading
  "Test that resources can be loaded from default/"
  []
  (println "\nTesting resource loading...")
  (try
    (let [load-resource-fn (resolve 'forma.compiler/load-resource)
          global (load-resource-fn "global/defaults.edn")]
      (if (map? global)
        (do
          (println "✓ Global defaults loaded successfully")
          (println (str "  - Keys: " (keys global)))
          true)
        (do
          (println "✗ Global defaults is not a map")
          false)))
    (catch Exception e
      (println (str "✗ Resource loading failed: " (.getMessage e)))
      (.printStackTrace e)
      false)))

(defn test-platform-loading
  "Test that platform configs can be loaded"
  []
  (println "\nTesting platform loading...")
  (try
    (let [load-platform-config-fn (resolve 'forma.compiler/load-platform-config)
          html-config (load-platform-config-fn :html)]
      (if (and (map? html-config) (contains? html-config :platform))
        (do
          (println "✓ HTML platform config loaded successfully")
          (println (str "  - Platform: " (:platform html-config)))
          (println (str "  - Elements: " (count (get html-config :elements {}))))
          true)
        (do
          (println "✗ Platform config is invalid")
          false)))
    (catch Exception e
      (println (str "✗ Platform loading failed: " (.getMessage e)))
      (.printStackTrace e)
      false)))

(defn test-hierarchy-loading
  "Test that hierarchy data can be loaded"
  []
  (println "\nTesting hierarchy loading...")
  (try
    (let [load-hierarchy-data-fn (resolve 'forma.compiler/load-hierarchy-data)
          hierarchy (load-hierarchy-data-fn)]
      (if (map? hierarchy)
        (do
          (println "✓ Hierarchy data loaded successfully")
          (println (str "  - Global: " (if (:global hierarchy) "present" "missing")))
          (println (str "  - Components: " (count (get hierarchy :components {}))))
          (println (str "  - Sections: " (count (get hierarchy :sections {}))))
          (println (str "  - Templates: " (count (get hierarchy :templates {}))))
          true)
        (do
          (println "✗ Hierarchy data is not a map")
          false)))
    (catch Exception e
      (println (str "✗ Hierarchy loading failed: " (.getMessage e)))
      (.printStackTrace e)
      false)))

(defn test-compilation
  "Test that basic compilation works"
  []
  (println "\nTesting compilation...")
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
              (println "✓ Compilation successful")
              (println (str "  - Compiled: " (pr-str (take 3 compiled))))
              true)
            (do
              (println "✗ Compilation result is not a vector")
              false)))
        (do
          (println "✗ Could not resolve compiler functions")
          false)))
    (catch Exception e
      (println (str "✗ Compilation failed: " (.getMessage e)))
      (.printStackTrace e)
      false)))

(defn test-directory-structure
  "Test that directory structure exists"
  []
  (println "\nTesting directory structure...")
  (let [dirs ["default" "library" "projects"]
        all-exist (every? #(.exists (io/file %)) dirs)]
    (if all-exist
      (do
        (println "✓ All directories exist")
        (doseq [dir dirs]
          (println (str "  - " dir "/")))
        true)
      (do
        (println "✗ Some directories are missing")
        (doseq [dir dirs]
          (println (str "  - " dir ": " (if (.exists (io/file dir)) "exists" "missing"))))
        false))))

(defn -main
  "Run all tests"
  [& args]
  (println "=== Forma Migration Verification ===\n")
  (let [results [(test-directory-structure)
                 (test-config-loading)
                 (test-resource-loading)
                 (test-platform-loading)
                 (test-hierarchy-loading)
                 (test-compilation)]]
    (println "\n=== Test Results ===")
    (let [passed (count (filter true? results))
          total (count results)]
      (println (str "Passed: " passed "/" total))
      (if (= passed total)
        (do
          (println "✓ All tests passed!")
          (System/exit 0))
        (do
          (println "✗ Some tests failed")
          (System/exit 1))))))

