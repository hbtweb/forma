(ns forma.build.assets
  "Phase 5.4: Asset Pipeline - Static file handling and optimization

  Provides:
  - Static file copying (images, fonts, JavaScript, CSS)
  - Asset fingerprinting for cache busting
  - Asset manifest generation
  - Image optimization (optional)"
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.security MessageDigest]
           [java.io File]))

;; =============================================================================
;; Asset Configuration
;; =============================================================================

(def ^:private default-asset-config
  "Default asset pipeline configuration"
  {:copy-static? true
   :static-dirs ["assets/" "public/" "static/"]
   :output-dir "build/assets/"
   :fingerprint? true
   :manifest-file "asset-manifest.edn"
   :optimize-images? false
   :extensions {:copy #{"png" "jpg" "jpeg" "gif" "svg" "webp" "ico"
                        "woff" "woff2" "ttf" "otf" "eot"
                        "js" "css" "json" "xml" "txt"}
                :optimize #{"png" "jpg" "jpeg"}}})

;; =============================================================================
;; File Utilities
;; =============================================================================

(defn- file-extension
  "Get file extension without dot"
  [file]
  (let [name (.getName file)
        idx (.lastIndexOf name ".")]
    (when (pos? idx)
      (subs name (inc idx)))))

(defn- file-checksum
  "Calculate MD5 checksum of file"
  [file]
  (let [digest (MessageDigest/getInstance "MD5")
        buffer (byte-array 8192)]
    (with-open [is (io/input-stream file)]
      (loop []
        (let [n (.read is buffer)]
          (when (pos? n)
            (.update digest buffer 0 n)
            (recur)))))
    (let [bytes (.digest digest)]
      (apply str (map #(format "%02x" %) bytes)))))

(defn- fingerprint-filename
  "Add fingerprint to filename

  Example: logo.png -> logo.a3d5f9c2.png"
  [filename checksum]
  (let [idx (.lastIndexOf filename ".")]
    (if (pos? idx)
      (str (subs filename 0 idx)
           "."
           (subs checksum 0 8)  ; Use first 8 chars
           (subs filename idx))
      (str filename "." (subs checksum 0 8)))))

(defn- copy-file
  "Copy file with optional fingerprinting

  Returns map with :source, :dest, :fingerprint"
  [source dest-dir fingerprint?]
  (let [filename (.getName source)
        checksum (when fingerprint? (file-checksum source))
        dest-filename (if fingerprint?
                        (fingerprint-filename filename checksum)
                        filename)
        dest-file (io/file dest-dir dest-filename)]

    ;; Create parent directories
    (io/make-parents dest-file)

    ;; Copy file
    (io/copy source dest-file)

    {:source (.getPath source)
     :dest (.getPath dest-file)
     :original-name filename
     :fingerprinted-name dest-filename
     :checksum checksum}))

;; =============================================================================
;; Asset Discovery
;; =============================================================================

(defn- discover-assets
  "Discover all asset files in static directories

  Parameters:
  - static-dirs: Vector of directory paths
  - extensions: Set of file extensions to include

  Returns sequence of File objects"
  [static-dirs extensions]
  (for [dir static-dirs
        :when (.exists (io/file dir))
        file (file-seq (io/file dir))
        :when (and (.isFile file)
                   (contains? extensions (file-extension file)))]
    file))

;; =============================================================================
;; Asset Processing
;; =============================================================================

(defn process-assets
  "Process and copy asset files

  Parameters:
  - config: Asset configuration map

  Returns map with:
  - :files - Vector of processed file maps
  - :manifest - Asset manifest map (original -> fingerprinted)"
  [config]
  (let [asset-config (merge default-asset-config config)
        static-dirs (:static-dirs asset-config)
        output-dir (:output-dir asset-config)
        fingerprint? (:fingerprint? asset-config)
        extensions (get-in asset-config [:extensions :copy])]

    ;; Discover assets
    (let [assets (discover-assets static-dirs extensions)]

      ;; Process each asset
      (let [processed (mapv #(copy-file % output-dir fingerprint?) assets)
            manifest (into {}
                       (map (fn [{:keys [original-name fingerprinted-name]}]
                              [original-name fingerprinted-name])
                            processed))]

        {:files processed
         :manifest manifest
         :total (count processed)
         :bytes (reduce + (map #(.length (io/file (:source %))) processed))}))))

(defn write-asset-manifest
  "Write asset manifest to file

  Parameters:
  - manifest: Asset manifest map
  - output-path: Path to manifest file"
  [manifest output-path]
  (io/make-parents output-path)
  (spit output-path (pr-str manifest)))

;; =============================================================================
;; Public API
;; =============================================================================

(defn copy-static-assets
  "Copy static assets with optional fingerprinting

  Parameters:
  - config: Asset configuration map

  Options:
  - :static-dirs - Vector of source directories
  - :output-dir - Destination directory
  - :fingerprint? - Add content-based fingerprints to filenames
  - :manifest-file - Path to manifest file (optional)

  Returns processing result map"
  [config]
  (let [result (process-assets config)]

    ;; Write manifest if requested
    (when-let [manifest-file (:manifest-file config)]
      (write-asset-manifest
        (:manifest result)
        (str (:output-dir config) "/" manifest-file)))

    result))

(defn resolve-asset-url
  "Resolve asset URL using manifest

  Parameters:
  - manifest: Asset manifest map
  - original-name: Original asset filename

  Returns fingerprinted filename or original if not in manifest"
  [manifest original-name]
  (get manifest original-name original-name))

(comment
  ;; Copy assets with fingerprinting
  (def result
    (copy-static-assets
      {:static-dirs ["forma/assets/" "forma/public/"]
       :output-dir "build/assets/"
       :fingerprint? true
       :manifest-file "asset-manifest.edn"}))

  (:total result)
  ;; => 42

  (:manifest result)
  ;; => {"logo.png" "logo.a3d5f9c2.png"
  ;;     "main.css" "main.7f8e2b1d.css"}

  ;; Resolve asset URL
  (resolve-asset-url (:manifest result) "logo.png")
  ;; => "logo.a3d5f9c2.png"
  )
