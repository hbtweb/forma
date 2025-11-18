(ns forma.sync.metadata
  "Phase 3: Metadata embedding for sync mode compilation.

   Provides two compilation modes:
   - Export mode: Clean output for production (no metadata)
   - Sync mode: Metadata-enriched output for round-trip editing

   Metadata includes:
   - Token provenance (original token references)
   - Property sources (explicit vs inherited, hierarchy levels)
   - Class attribution (which styling system contributed what)
   - Full provenance tracking (compilation stages, overrides)

   Metadata can be embedded as:
   - data-forma-* attributes (HTML/JSX)
   - Sidecar files (.metadata.json)
   - Inline comments (CSS/code formats)"
  (:require [clojure.string :as str]
            [cheshire.core :as json]
            [forma.provenance.tracker :as provenance]
            [forma.inheritance.tracking :as tracking]))

;; ============================================================================
;; COMPILATION MODES
;; ============================================================================

(defn get-compilation-mode
  "Determine compilation mode from context.

   Modes:
   - :export - Clean output for production (default)
   - :sync - Metadata-enriched for round-trip editing

   Args:
     context - Compilation context

   Returns: :export or :sync"
  [context]
  (get context :compilation-mode :export))

(defn export-mode?
  "Check if compilation is in export mode (clean output)."
  [context]
  (= (get-compilation-mode context) :export))

(defn sync-mode?
  "Check if compilation is in sync mode (metadata embedded)."
  [context]
  (= (get-compilation-mode context) :sync))

;; ============================================================================
;; METADATA COLLECTION
;; ============================================================================

(defn collect-element-metadata
  "Collect all metadata for an element.

   Phase 3 Feature: Aggregates metadata from multiple sources.

   Args:
     element - Compiled element
     props - Resolved properties
     context - Compilation context with tracking data

   Returns: Metadata map with:
   {:token-provenance {...}
    :property-sources {...}
    :class-attribution {...}
    :provenance-entries [...]
    :element-type :button
    :variant :primary}"
  [element props context]
  (let [metadata (atom {})

        ;; Token provenance (from Phase 3 token tracking)
        token-prov (get props :_token-provenance)
        _ (when token-prov
            (swap! metadata assoc :token-provenance token-prov))

        ;; Property sources (explicit vs inherited)
        property-tracker (get context :property-tracker)
        _ (when property-tracker
            (let [sources (reduce (fn [acc src]
                                   (assoc acc (:property src)
                                          {:source-type (:source-type src)
                                           :source-level (:source-level src)}))
                                 {}
                                 (tracking/get-all-tracked property-tracker))]
              (swap! metadata assoc :property-sources sources)))

        ;; Class attribution (styling system sources)
        class-prov (get props :_class-provenance)
        _ (when class-prov
            (swap! metadata assoc :class-attribution class-prov))

        ;; Full provenance entries
        prov-tracker (get context :provenance-tracker)
        _ (when prov-tracker
            (let [entries (provenance/get-element-provenance
                          prov-tracker
                          (get context :element-path []))]
              (when (seq entries)
                (swap! metadata assoc :provenance-entries
                       (mapv provenance/entry->map entries)))))

        ;; Element metadata
        _ (when-let [elem-type (:type element)]
            (swap! metadata assoc :element-type elem-type))

        _ (when-let [variant (get props :variant)]
            (swap! metadata assoc :variant variant))]

    @metadata))

;; ============================================================================
;; METADATA EMBEDDING - DATA ATTRIBUTES
;; ============================================================================

(defn metadata->data-attributes
  "Convert metadata map to data-forma-* attributes.

   Phase 3 Feature: Embeds metadata as HTML/JSX data attributes.

   Args:
     metadata - Metadata map from collect-element-metadata

   Returns: Map of data attribute keys to values
   {:data-forma-token-provenance \"{...}\"
    :data-forma-element-type \"button\"
    :data-forma-variant \"primary\"}"
  [metadata]
  (let [attrs (atom {})]

    ;; Token provenance (JSON)
    (when-let [token-prov (:token-provenance metadata)]
      (swap! attrs assoc :data-forma-token-provenance
             (json/generate-string token-prov)))

    ;; Property sources (JSON)
    (when-let [prop-sources (:property-sources metadata)]
      (swap! attrs assoc :data-forma-property-sources
             (json/generate-string prop-sources)))

    ;; Class attribution (JSON)
    (when-let [class-attr (:class-attribution metadata)]
      (swap! attrs assoc :data-forma-class-attribution
             (json/generate-string class-attr)))

    ;; Provenance entries (JSON, optional - can be large)
    (when-let [prov-entries (:provenance-entries metadata)]
      (when (get metadata :include-full-provenance? false)
        (swap! attrs assoc :data-forma-provenance
               (json/generate-string prov-entries))))

    ;; Element type (simple string)
    (when-let [elem-type (:element-type metadata)]
      (swap! attrs assoc :data-forma-type (name elem-type)))

    ;; Variant (simple string)
    (when-let [variant (:variant metadata)]
      (swap! attrs assoc :data-forma-variant (name variant)))

    @attrs))

(defn should-embed-metadata?
  "Determine if metadata should be embedded for this element.

   Phase 3 Feature: Configurable metadata embedding rules.

   Args:
     element - Element being compiled
     context - Compilation context

   Returns: boolean"
  [element context]
  (and (sync-mode? context)
       ;; Check element-specific rules
       (let [skip-for (get-in context [:metadata-options :skip-metadata-for] #{})]
         (not (contains? skip-for (:type element))))))

;; ============================================================================
;; METADATA EMBEDDING - SIDECAR FILES
;; ============================================================================

(defn generate-sidecar-filename
  "Generate sidecar metadata filename.

   Args:
     source-file - Source file path (e.g., \"output.html\")

   Returns: Sidecar filename (e.g., \"output.html.metadata.json\")"
  [source-file]
  (str source-file ".metadata.json"))

(defn write-sidecar-metadata
  "Write metadata to sidecar file.

   Phase 3 Feature: Alternative to inline data attributes.

   Args:
     metadata - Metadata map
     output-file - Output file path

   Returns: nil (writes file)"
  [metadata output-file]
  (let [sidecar-file (generate-sidecar-filename output-file)
        json-content (json/generate-string metadata {:pretty true})]
    (spit sidecar-file json-content)))

;; ============================================================================
;; METADATA EXTRACTION (Import/Parse)
;; ============================================================================

(defn extract-metadata-from-attributes
  "Extract forma metadata from data-forma-* attributes.

   Phase 3 Feature: Reverse of metadata->data-attributes.

   Args:
     attrs - Attribute map with data-forma-* keys

   Returns: Metadata map"
  [attrs]
  (let [metadata (atom {})]

    ;; Token provenance
    (when-let [token-prov (:data-forma-token-provenance attrs)]
      (swap! metadata assoc :token-provenance
             (json/parse-string token-prov true)))

    ;; Property sources
    (when-let [prop-sources (:data-forma-property-sources attrs)]
      (swap! metadata assoc :property-sources
             (json/parse-string prop-sources true)))

    ;; Class attribution
    (when-let [class-attr (:data-forma-class-attribution attrs)]
      (swap! metadata assoc :class-attribution
             (json/parse-string class-attr true)))

    ;; Full provenance
    (when-let [prov (:data-forma-provenance attrs)]
      (swap! metadata assoc :provenance-entries
             (json/parse-string prov true)))

    ;; Element type
    (when-let [elem-type (:data-forma-type attrs)]
      (swap! metadata assoc :element-type (keyword elem-type)))

    ;; Variant
    (when-let [variant (:data-forma-variant attrs)]
      (swap! metadata assoc :variant (keyword variant)))

    @metadata))

(defn read-sidecar-metadata
  "Read metadata from sidecar file.

   Args:
     output-file - Original output file path

   Returns: Metadata map or nil if file doesn't exist"
  [output-file]
  (let [sidecar-file (generate-sidecar-filename output-file)]
    (when (.exists (clojure.java.io/file sidecar-file))
      (json/parse-string (slurp sidecar-file) true))))

;; ============================================================================
;; INTEGRATION HELPERS
;; ============================================================================

(defn embed-metadata-in-element
  "Embed metadata in compiled element based on mode.

   Phase 3 Feature: Main integration point for metadata embedding.

   Args:
     element - Compiled element (Hiccup vector)
     props - Resolved properties
     context - Compilation context

   Returns: Element with metadata embedded (if sync mode)"
  [element props context]
  (if (should-embed-metadata? element context)
    (let [metadata (collect-element-metadata element props context)
          data-attrs (metadata->data-attributes metadata)
          ;; Element is Hiccup vector: [tag attrs children...]
          [tag attrs-map & children] element
          ;; Merge data attributes into element attributes
          updated-attrs (merge attrs-map data-attrs)]
      (into [tag updated-attrs] children))
    ;; Export mode or metadata skipped - return unchanged
    element))

(defn create-metadata-context
  "Create or update context with metadata tracking enabled.

   Phase 3 Feature: Initialize tracking infrastructure.

   Args:
     context - Base compilation context

   Returns: Context with metadata tracking enabled"
  [context]
  (cond-> context
    ;; Enable token tracking
    (sync-mode? context)
    (assoc :track-tokens? true)

    ;; Create property tracker if needed
    (and (sync-mode? context)
         (not (:property-tracker context)))
    (assoc :property-tracker (tracking/create-property-tracker))

    ;; Create provenance tracker if needed
    (and (sync-mode? context)
         (not (:provenance-tracker context)))
    (assoc :provenance-tracker (provenance/create-tracker))))

;; ============================================================================
;; PUBLIC API
;; ============================================================================

(defn enable-sync-mode
  "Enable sync mode with metadata embedding.

   Phase 3 Feature: Public API for sync mode.

   Args:
     context - Base compilation context
     opts - Optional configuration map:
            :metadata-format - :data-attributes (default) or :sidecar
            :include-full-provenance? - Include complete provenance data
            :skip-metadata-for - Set of element types to skip

   Returns: Context configured for sync mode"
  [context & [opts]]
  (-> context
      (assoc :compilation-mode :sync)
      (assoc :metadata-options opts)
      (create-metadata-context)))

(defn enable-export-mode
  "Enable export mode (clean output, no metadata).

   Args:
     context - Base compilation context

   Returns: Context configured for export mode"
  [context]
  (assoc context :compilation-mode :export))
