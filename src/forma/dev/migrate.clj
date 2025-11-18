(ns forma.dev.migrate
  "Migration script to move resources/forma/ to default/ directory structure"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(defn copy-directory
  "Recursively copy a directory"
  [source-dir dest-dir]
  (let [source-file (io/file source-dir)
        dest-file (io/file dest-dir)]
    (when (.exists source-file)
      (when (not (.exists dest-file))
        (.mkdirs dest-file))
      (doseq [file (.listFiles source-file)]
        (if (.isDirectory file)
          (copy-directory file (io/file dest-file (.getName file)))
          (io/copy file (io/file dest-file (.getName file))))))))

(defn migrate-to-default
  "Migrate resources/forma/ to default/ directory"
  []
  (println "Starting migration: resources/forma/ -> default/")
  (let [source-dir "resources/forma"
        dest-dir "default"]
    (if (.exists (io/file source-dir))
      (do
        (println (str "Copying " source-dir " to " dest-dir "..."))
        (copy-directory (io/file source-dir) (io/file dest-dir))
        (println "Migration complete!")
        (println "\nNext steps:")
        (println "1. Verify default/ directory structure")
        (println "2. Create library/ and projects/ directories")
        (println "3. Test compiler with new structure"))
      (println (str "Source directory " source-dir " does not exist")))))

(defn create-directory-structure
  "Create library/ and projects/ directory structures"
  []
  (println "Creating directory structure...")
  (let [dirs ["library/components" "library/platforms" "library/styles" 
              "library/sections" "library/templates"
              "projects"]]
    (doseq [dir dirs]
      (let [dir-file (io/file dir)]
        (when (not (.exists dir-file))
          (.mkdirs dir-file)
          (println (str "Created: " dir)))))
    (println "Directory structure created!")))

(defn -main
  "Run migration"
  [& args]
  (println "=== Forma Migration Script ===")
  (migrate-to-default)
  (println "\n")
  (create-directory-structure)
  (println "\n=== Migration Complete ==="))

