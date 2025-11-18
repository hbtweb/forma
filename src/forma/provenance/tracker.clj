(ns forma.provenance.tracker
  "Style provenance tracking for Forma compiler

   Implements Edge Case #11: Style Provenance Tracing

   Records the source of every class and CSS property throughout compilation:
   - Hierarchy level (global, component, section, template, page)
   - Styling system (tailwind, shadcn-ui, etc.)
   - Resolution stage (inheritance, extension, stacking, explicit override)
   - Source file path
   - Override history (what was replaced)

   Provenance data enables:
   - Debugging: 'Where did this class come from?'
   - Conflict detection: 'Why are bg-blue-500 and bg-red-500 both present?'
   - Diffs: 'What changed between builds?'
   - Sync: 'Which hierarchy level should this change apply to?'
   - Tooling: 'Show me all styles from this component'"
  (:require [clojure.string :as str]
            [clojure.set :as set]))

;; ============================================================================
;; PROVENANCE DATA STRUCTURES
;; ============================================================================

(defrecord ProvenanceEntry
  [property      ;; :class or CSS property name (e.g., :background, :padding)
   value         ;; The actual value (e.g., "btn primary" or "#ffffff")
   source-type   ;; :hierarchy-level, :styling-system, :explicit
   source-name   ;; Hierarchy level keyword, system name, or :explicit
   source-file   ;; Path to source EDN file (if available)
   stage         ;; :parse, :expand, :resolve-inheritance, :resolve-tokens, :apply-styling, :compile-platform, :explicit-override
   element-type  ;; Element type (:button, :div, etc.)
   element-path  ;; Path in element tree (e.g., [:page :header :nav :button])
   replaced      ;; Previous value that was replaced (if any)
   replaced-by]) ;; ProvenanceEntry that replaced this one (if overridden)

(defn make-provenance-entry
  "Create a new provenance entry with required fields.

   Optional fields default to nil if not provided."
  [property value source-type source-name stage element-type & {:as opts}]
  (map->ProvenanceEntry
   (merge
    {:property property
     :value value
     :source-type source-type
     :source-name source-name
     :stage stage
     :element-type element-type
     :source-file nil
     :element-path []
     :replaced nil
     :replaced-by nil}
    opts)))

;; ============================================================================
;; PROVENANCE TRACKER
;; ============================================================================

(defprotocol ProvenanceTracker
  "Protocol for tracking style provenance during compilation"
  (record-property [this entry]
    "Record a provenance entry for a property or class")
  (record-override [this property old-entry new-entry]
    "Record when a property is overridden")
  (get-provenance [this]
    "Get all provenance entries")
  (get-property-provenance [this property]
    "Get provenance entries for a specific property")
  (get-element-provenance [this element-path]
    "Get all provenance for an element at given path")
  (get-source-provenance [this source-type source-name]
    "Get all provenance from a specific source"))

(deftype MapProvenanceTracker [state-atom]
  ProvenanceTracker
  (record-property [this entry]
    (swap! state-atom update :entries conj entry)
    this)

  (record-override [this property old-entry new-entry]
    (let [updated-old (assoc old-entry :replaced-by new-entry)
          updated-new (assoc new-entry :replaced updated-old)]
      (swap! state-atom
             (fn [state]
               (-> state
                   (update :entries
                           (fn [entries]
                             (mapv #(if (= % old-entry) updated-old %)
                                   entries)))
                   (update :entries conj updated-new)
                   (update :overrides conj {:property property
                                            :old updated-old
                                            :new updated-new}))))
      this))

  (get-provenance [this]
    (:entries @state-atom))

  (get-property-provenance [this property]
    (filter #(= (:property %) property)
            (:entries @state-atom)))

  (get-element-provenance [this element-path]
    (filter #(= (:element-path %) element-path)
            (:entries @state-atom)))

  (get-source-provenance [this source-type source-name]
    (filter #(and (= (:source-type %) source-type)
                  (= (:source-name %) source-name))
            (:entries @state-atom))))

(defn create-tracker
  "Create a new provenance tracker instance"
  []
  (MapProvenanceTracker. (atom {:entries []
                                 :overrides []})))

;; ============================================================================
;; PROVENANCE UTILITIES
;; ============================================================================

(defn entry->map
  "Convert ProvenanceEntry to plain map for serialization"
  [entry]
  (when entry
    (into {} (remove (comp nil? val) entry))))

(defn provenance->json-compatible
  "Convert provenance entries to JSON-compatible format"
  [entries]
  (mapv entry->map entries))

(defn filter-active-entries
  "Filter provenance to only active entries (not replaced by something else)"
  [entries]
  (remove :replaced-by entries))

(defn group-by-property
  "Group provenance entries by property"
  [entries]
  (group-by :property entries))

(defn group-by-source
  "Group provenance entries by source (type + name)"
  [entries]
  (group-by (fn [e] [(:source-type e) (:source-name e)]) entries))

(defn group-by-element
  "Group provenance entries by element path"
  [entries]
  (group-by :element-path entries))

;; ============================================================================
;; CONFLICT DETECTION
;; ============================================================================

(defn detect-class-conflicts
  "Detect conflicting classes in provenance entries.

   Conflicts are classes that affect the same CSS property.
   Examples:
   - bg-blue-500 and bg-red-500 (both set background)
   - p-4 and padding-2 (both set padding)
   - text-sm and text-lg (both set font-size)

   Returns: vector of conflict maps with:
   {:property-affected :background
    :classes ['bg-blue-500' 'bg-red-500']
    :entries [ProvenanceEntry ...]}"
  [entries]
  ;; Simplified implementation - looks for common class prefixes
  ;; Full implementation would parse Tailwind/system configs
  (let [class-entries (filter #(= (:property %) :class) entries)
        conflict-patterns {
          #"^bg-" :background
          #"^text-" :text-properties
          #"^p-|^px-|^py-|^pt-|^pb-|^pl-|^pr-|^padding-" :padding
          #"^m-|^mx-|^my-|^mt-|^mb-|^ml-|^mr-|^margin-" :margin
          #"^w-|^width-" :width
          #"^h-|^height-" :height}

        classified (for [entry class-entries
                        :let [classes (str/split (:value entry) #"\s+")]
                        cls classes
                        [pattern prop] conflict-patterns
                        :when (re-find pattern cls)]
                    {:class cls
                     :property-affected prop
                     :entry entry})

        by-prop (group-by :property-affected classified)]

    (->> by-prop
         (keep (fn [[prop class-entries]]
                 (when (> (count class-entries) 1)
                   {:property-affected prop
                    :classes (mapv :class class-entries)
                    :entries (mapv :entry class-entries)})))
         vec)))

(defn detect-duplicate-properties
  "Detect when the same CSS property is set multiple times.

   Returns: vector of duplicate maps with:
   {:property :background
    :values ['#fff' '#000']
    :entries [ProvenanceEntry ...]}"
  [entries]
  (let [css-entries (remove #(= (:property %) :class) entries)
        by-prop (group-by :property css-entries)]
    (->> by-prop
         (keep (fn [[prop prop-entries]]
                 (when (> (count prop-entries) 1)
                   {:property prop
                    :values (mapv :value prop-entries)
                    :entries prop-entries})))
         vec)))

;; ============================================================================
;; PROVENANCE REPORTING
;; ============================================================================

(defn provenance-summary
  "Generate human-readable summary of provenance entries"
  [entries]
  (let [active (filter-active-entries entries)
        by-source (group-by-source active)
        by-property (group-by-property active)]
    {:total-entries (count entries)
     :active-entries (count active)
     :overridden-entries (- (count entries) (count active))
     :sources (count by-source)
     :properties (keys by-property)
     :property-count (count by-property)}))

(defn format-provenance-entry
  "Format a single provenance entry for display"
  [entry]
  (let [{:keys [property value source-type source-name stage element-type
                source-file replaced]} entry]
    (str (name property) " = " value "\n"
         "  Source: " (name source-type) "/" (name source-name) "\n"
         "  Stage: " (name stage) "\n"
         "  Element: " (name element-type) "\n"
         (when source-file (str "  File: " source-file "\n"))
         (when replaced (str "  Replaced: " (:value replaced) "\n")))))

(defn format-provenance-report
  "Format full provenance report for an element or property"
  [entries]
  (let [summary (provenance-summary entries)
        conflicts (detect-class-conflicts entries)
        duplicates (detect-duplicate-properties entries)]
    (str "=== PROVENANCE REPORT ===\n\n"
         "Summary:\n"
         "  Total entries: " (:total-entries summary) "\n"
         "  Active: " (:active-entries summary) "\n"
         "  Overridden: " (:overridden-entries summary) "\n"
         "  Properties: " (str/join ", " (map name (:properties summary))) "\n\n"

         (when (seq conflicts)
           (str "CLASS CONFLICTS:\n"
                (str/join "\n"
                  (map (fn [{:keys [property-affected classes]}]
                         (str "  " (name property-affected) ": "
                              (str/join " vs " classes)))
                       conflicts))
                "\n\n"))

         (when (seq duplicates)
           (str "DUPLICATE PROPERTIES:\n"
                (str/join "\n"
                  (map (fn [{:keys [property values]}]
                         (str "  " (name property) ": "
                              (str/join " → " values)))
                       duplicates))
                "\n\n"))

         "ENTRIES:\n"
         (str/join "\n"
           (map format-provenance-entry (filter-active-entries entries))))))

;; ============================================================================
;; DIFF SUPPORT
;; ============================================================================

(defn diff-provenance
  "Compare two sets of provenance entries (e.g., between builds).

   Returns: {:added [entries] :removed [entries] :changed [entry-pairs]}"
  [old-entries new-entries]
  (let [old-by-key (group-by (juxt :property :element-path) old-entries)
        new-by-key (group-by (juxt :property :element-path) new-entries)
        old-keys (set (keys old-by-key))
        new-keys (set (keys new-by-key))

        added-keys (set/difference new-keys old-keys)
        removed-keys (set/difference old-keys new-keys)
        common-keys (set/intersection old-keys new-keys)

        changed-keys (filter (fn [k]
                               (not= (map :value (get old-by-key k))
                                     (map :value (get new-by-key k))))
                             common-keys)]

    {:added (mapcat new-by-key added-keys)
     :removed (mapcat old-by-key removed-keys)
     :changed (map (fn [k]
                     {:old (get old-by-key k)
                      :new (get new-by-key k)})
                   changed-keys)}))

(defn format-diff-report
  "Format provenance diff for display"
  [diff]
  (let [{:keys [added removed changed]} diff]
    (str "=== PROVENANCE DIFF ===\n\n"
         "Added (" (count added) "):\n"
         (str/join "\n" (map format-provenance-entry added))
         "\n\n"
         "Removed (" (count removed) "):\n"
         (str/join "\n" (map format-provenance-entry removed))
         "\n\n"
         "Changed (" (count changed) "):\n"
         (str/join "\n"
           (map (fn [{:keys [old new]}]
                  (str "Property: " (:property (first old)) "\n"
                       "  Old: " (str/join ", " (map :value old)) "\n"
                       "  New: " (str/join ", " (map :value new))))
                changed)))))
