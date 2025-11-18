(ns forma.hierarchy.reconciliation
  "3-way merge reconciliation for Forma project updates from external sources.

  This module enables intelligent merging of changes made to exported Forma
  projects in external platforms (WordPress, React apps, etc.) back into the
  original Forma project structure.

  Key concepts:
  - BASE: Original Forma export (snapshot at export time)
  - THEIRS: External changes (edited in WordPress/React/etc.)
  - OURS: Current Forma state (may have changed since export)
  - MERGED: Result of intelligent 3-way merge

  Merge strategies:
  1. Auto-merge: Non-conflicting changes applied automatically
  2. Manual: User resolves conflicts via callback
  3. Theirs-wins: External changes take precedence
  4. Ours-wins: Forma changes take precedence

  Change types:
  - Addition: Property/element added
  - Modification: Property/element changed
  - Deletion: Property/element removed
  - Conflict: Both sides changed the same property"
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [clojure.data :as data]))

;;
;; Change Detection
;; ============================================================================

(defn- detect-property-change
  "Detect what changed for a single property between base and target.

  Returns:
    {:type :added | :modified | :deleted | :unchanged
     :base-value value
     :target-value value}"
  [base-value target-value]
  (cond
    (and (nil? base-value) (some? target-value))
    {:type :added :base-value nil :target-value target-value}

    (and (some? base-value) (nil? target-value))
    {:type :deleted :base-value base-value :target-value nil}

    (not= base-value target-value)
    {:type :modified :base-value base-value :target-value target-value}

    :else
    {:type :unchanged :base-value base-value :target-value target-value}))

(defn detect-changes
  "Detect all changes between base and target versions.

  Args:
    base   - Base version (original export) - map of {:element-id {:props {...}}}
    target - Target version (external edits) - same structure

  Returns:
    {:element-id {:property-key {:type :added | :modified | :deleted | :unchanged
                                  :base-value ...
                                  :target-value ...}}}"
  [base target]
  (let [all-element-ids (set/union (set (keys base)) (set (keys target)))]
    (reduce
     (fn [changes element-id]
       (let [base-props (get base element-id {})
             target-props (get target element-id {})
             all-props (set/union (set (keys base-props)) (set (keys target-props)))
             prop-changes (reduce
                           (fn [pc prop-key]
                             (let [base-val (get base-props prop-key)
                                   target-val (get target-props prop-key)
                                   change (detect-property-change base-val target-val)]
                               (if (= :unchanged (:type change))
                                 pc
                                 (assoc pc prop-key change))))
                           {}
                           all-props)]
         (if (empty? prop-changes)
           changes
           (assoc changes element-id prop-changes))))
     {}
     all-element-ids)))

(defn detect-conflicts
  "Detect conflicts between THEIRS and OURS changes.

  A conflict occurs when both THEIRS and OURS modified the same property
  from the BASE value.

  Args:
    base-changes   - Changes from BASE → THEIRS
    ours-changes   - Changes from BASE → OURS

  Returns:
    {:element-id {:property-key {:type :conflict
                                  :base-value ...
                                  :theirs-value ...
                                  :ours-value ...
                                  :conflict-type :both-modified | :theirs-added-ours-modified | ...}}}"
  [base-changes ours-changes]
  (let [all-element-ids (set/union (set (keys base-changes)) (set (keys ours-changes)))]
    (reduce
     (fn [conflicts element-id]
       (let [base-props (get base-changes element-id {})
             ours-props (get ours-changes element-id {})
             all-props (set/union (set (keys base-props)) (set (keys ours-props)))
             prop-conflicts (reduce
                             (fn [pc prop-key]
                               (let [base-change (get base-props prop-key)
                                     ours-change (get ours-props prop-key)]
                                 (cond
                                   ;; Both modified (true conflict)
                                   (and (= :modified (:type base-change))
                                        (= :modified (:type ours-change)))
                                   (assoc pc prop-key
                                          {:type :conflict
                                           :conflict-type :both-modified
                                           :base-value (:base-value base-change)
                                           :theirs-value (:target-value base-change)
                                           :ours-value (:target-value ours-change)})

                                   ;; Both deleted (not a conflict, same intent)
                                   (and (= :deleted (:type base-change))
                                        (= :deleted (:type ours-change)))
                                   pc

                                   ;; One added, one modified/deleted (conflict)
                                   (and (= :added (:type base-change))
                                        (#{:modified :deleted} (:type ours-change)))
                                   (assoc pc prop-key
                                          {:type :conflict
                                           :conflict-type :theirs-added-ours-changed
                                           :base-value nil
                                           :theirs-value (:target-value base-change)
                                           :ours-value (:target-value ours-change)})

                                   (and (= :added (:type ours-change))
                                        (#{:modified :deleted} (:type base-change)))
                                   (assoc pc prop-key
                                          {:type :conflict
                                           :conflict-type :ours-added-theirs-changed
                                           :base-value nil
                                           :theirs-value (:target-value base-change)
                                           :ours-value (:target-value ours-change)})

                                   ;; One deleted, one modified (conflict)
                                   (and (= :deleted (:type base-change))
                                        (= :modified (:type ours-change)))
                                   (assoc pc prop-key
                                          {:type :conflict
                                           :conflict-type :theirs-deleted-ours-modified
                                           :base-value (:base-value base-change)
                                           :theirs-value nil
                                           :ours-value (:target-value ours-change)})

                                   (and (= :deleted (:type ours-change))
                                        (= :modified (:type base-change)))
                                   (assoc pc prop-key
                                          {:type :conflict
                                           :conflict-type :ours-deleted-theirs-modified
                                           :base-value (:base-value ours-change)
                                           :theirs-value (:target-value base-change)
                                           :ours-value nil})

                                   ;; No conflict
                                   :else pc)))
                             {}
                             all-props)]
         (if (empty? prop-conflicts)
           conflicts
           (assoc conflicts element-id prop-conflicts))))
     {}
     all-element-ids)))

;;
;; Merge Strategies
;; ============================================================================

(defn merge-auto
  "Automatically merge non-conflicting changes.

  Strategy:
  - If only THEIRS changed → Accept THEIRS
  - If only OURS changed → Accept OURS
  - If both unchanged → Keep BASE
  - If conflict → Return conflict for manual resolution

  Args:
    base        - Base version (original export)
    theirs      - Their version (external edits)
    ours        - Our version (current Forma state)
    theirs-changes - Changes from BASE → THEIRS
    ours-changes   - Changes from BASE → OURS
    conflicts   - Detected conflicts

  Returns:
    {:merged {...}              ; Merged result
     :conflicts {...}           ; Unresolved conflicts
     :stats {:auto-merged-count N
             :conflict-count N
             :theirs-accepted N
             :ours-accepted N}}"
  [base theirs ours theirs-changes ours-changes conflicts]
  (let [all-element-ids (set/union (set (keys base))
                                   (set (keys theirs))
                                   (set (keys ours)))
        stats (atom {:auto-merged-count 0
                     :conflict-count 0
                     :theirs-accepted 0
                     :ours-accepted 0})
        merged (reduce
                (fn [result element-id]
                  (let [base-props (get base element-id {})
                        theirs-props (get theirs element-id {})
                        ours-props (get ours element-id {})
                        elem-conflicts (get conflicts element-id {})
                        all-props (set/union (set (keys base-props))
                                             (set (keys theirs-props))
                                             (set (keys ours-props)))
                        merged-props (reduce
                                      (fn [mp prop-key]
                                        (if (contains? elem-conflicts prop-key)
                                          ;; Conflict - skip (will be in :conflicts)
                                          (do
                                            (swap! stats update :conflict-count inc)
                                            mp)
                                          ;; No conflict - auto-merge
                                          (let [base-val (get base-props prop-key)
                                                theirs-val (get theirs-props prop-key)
                                                ours-val (get ours-props prop-key)
                                                theirs-change (get-in theirs-changes [element-id prop-key])
                                                ours-change (get-in ours-changes [element-id prop-key])]
                                            (cond
                                              ;; Only THEIRS changed
                                              (and theirs-change
                                                   (not ours-change))
                                              (do
                                                (swap! stats update :auto-merged-count inc)
                                                (swap! stats update :theirs-accepted inc)
                                                (assoc mp prop-key theirs-val))

                                              ;; Only OURS changed
                                              (and ours-change
                                                   (not theirs-change))
                                              (do
                                                (swap! stats update :auto-merged-count inc)
                                                (swap! stats update :ours-accepted inc)
                                                (assoc mp prop-key ours-val))

                                              ;; Neither changed (keep base)
                                              :else
                                              (assoc mp prop-key base-val)))))
                                      {}
                                      all-props)]
                    (assoc result element-id merged-props)))
                {}
                all-element-ids)]
    {:merged merged
     :conflicts conflicts
     :stats @stats}))

(defn merge-theirs-wins
  "Merge strategy where THEIRS always wins conflicts.

  Args:
    base   - Base version
    theirs - Their version (external edits)
    ours   - Our version (current Forma state)

  Returns:
    {:merged {...}
     :stats {:theirs-count N
             :ours-count N}}"
  [base theirs ours]
  (let [theirs-changes (detect-changes base theirs)
        ours-changes (detect-changes base ours)
        stats (atom {:theirs-count 0 :ours-count 0})
        all-element-ids (set/union (set (keys base))
                                   (set (keys theirs))
                                   (set (keys ours)))
        merged (reduce
                (fn [result element-id]
                  (let [theirs-props (get theirs element-id {})
                        ours-props (get ours element-id {})
                        base-props (get base element-id {})
                        all-props (set/union (set (keys base-props))
                                             (set (keys theirs-props))
                                             (set (keys ours-props)))
                        merged-props (reduce
                                      (fn [mp prop-key]
                                        (let [theirs-change (get-in theirs-changes [element-id prop-key])
                                              ours-change (get-in ours-changes [element-id prop-key])
                                              theirs-val (get theirs-props prop-key)
                                              ours-val (get ours-props prop-key)
                                              base-val (get base-props prop-key)]
                                          (cond
                                            ;; THEIRS changed (wins)
                                            theirs-change
                                            (do
                                              (swap! stats update :theirs-count inc)
                                              (assoc mp prop-key theirs-val))

                                            ;; Only OURS changed
                                            ours-change
                                            (do
                                              (swap! stats update :ours-count inc)
                                              (assoc mp prop-key ours-val))

                                            ;; Neither changed
                                            :else
                                            (assoc mp prop-key base-val))))
                                      {}
                                      all-props)]
                    (assoc result element-id merged-props)))
                {}
                all-element-ids)]
    {:merged merged
     :stats @stats}))

(defn merge-ours-wins
  "Merge strategy where OURS always wins conflicts.

  Args:
    base   - Base version
    theirs - Their version (external edits)
    ours   - Our version (current Forma state)

  Returns:
    {:merged {...}
     :stats {:theirs-count N
             :ours-count N}}"
  [base theirs ours]
  (let [theirs-changes (detect-changes base theirs)
        ours-changes (detect-changes base ours)
        stats (atom {:theirs-count 0 :ours-count 0})
        all-element-ids (set/union (set (keys base))
                                   (set (keys theirs))
                                   (set (keys ours)))
        merged (reduce
                (fn [result element-id]
                  (let [theirs-props (get theirs element-id {})
                        ours-props (get ours element-id {})
                        base-props (get base element-id {})
                        all-props (set/union (set (keys base-props))
                                             (set (keys theirs-props))
                                             (set (keys ours-props)))
                        merged-props (reduce
                                      (fn [mp prop-key]
                                        (let [theirs-change (get-in theirs-changes [element-id prop-key])
                                              ours-change (get-in ours-changes [element-id prop-key])
                                              theirs-val (get theirs-props prop-key)
                                              ours-val (get ours-props prop-key)
                                              base-val (get base-props prop-key)]
                                          (cond
                                            ;; OURS changed (wins)
                                            ours-change
                                            (do
                                              (swap! stats update :ours-count inc)
                                              (assoc mp prop-key ours-val))

                                            ;; Only THEIRS changed
                                            theirs-change
                                            (do
                                              (swap! stats update :theirs-count inc)
                                              (assoc mp prop-key theirs-val))

                                            ;; Neither changed
                                            :else
                                            (assoc mp prop-key base-val))))
                                      {}
                                      all-props)]
                    (assoc result element-id merged-props)))
                {}
                all-element-ids)]
    {:merged merged
     :stats @stats}))

(defn merge-manual
  "Merge with manual conflict resolution via callback.

  Args:
    base        - Base version
    theirs      - Their version (external edits)
    ours        - Our version (current Forma state)
    resolve-fn  - Conflict resolution function:
                  (fn [element-id property-key conflict] → resolved-value)
                  where conflict = {:type :conflict
                                    :base-value ...
                                    :theirs-value ...
                                    :ours-value ...}

  Returns:
    {:merged {...}
     :stats {:auto-merged-count N
             :manual-resolved-count N
             :conflict-count N}}"
  [base theirs ours resolve-fn]
  (let [theirs-changes (detect-changes base theirs)
        ours-changes (detect-changes base ours)
        conflicts (detect-conflicts theirs-changes ours-changes)
        stats (atom {:auto-merged-count 0
                     :manual-resolved-count 0
                     :conflict-count (count conflicts)})
        all-element-ids (set/union (set (keys base))
                                   (set (keys theirs))
                                   (set (keys ours)))
        merged (reduce
                (fn [result element-id]
                  (let [base-props (get base element-id {})
                        theirs-props (get theirs element-id {})
                        ours-props (get ours element-id {})
                        elem-conflicts (get conflicts element-id {})
                        all-props (set/union (set (keys base-props))
                                             (set (keys theirs-props))
                                             (set (keys ours-props)))
                        merged-props (reduce
                                      (fn [mp prop-key]
                                        (if-let [conflict (get elem-conflicts prop-key)]
                                          ;; Manual resolution
                                          (let [resolved-value (resolve-fn element-id prop-key conflict)]
                                            (swap! stats update :manual-resolved-count inc)
                                            (assoc mp prop-key resolved-value))
                                          ;; Auto-merge
                                          (let [theirs-change (get-in theirs-changes [element-id prop-key])
                                                ours-change (get-in ours-changes [element-id prop-key])
                                                theirs-val (get theirs-props prop-key)
                                                ours-val (get ours-props prop-key)
                                                base-val (get base-props prop-key)]
                                            (cond
                                              theirs-change
                                              (do
                                                (swap! stats update :auto-merged-count inc)
                                                (assoc mp prop-key theirs-val))

                                              ours-change
                                              (do
                                                (swap! stats update :auto-merged-count inc)
                                                (assoc mp prop-key ours-val))

                                              :else
                                              (assoc mp prop-key base-val)))))
                                      {}
                                      all-props)]
                    (assoc result element-id merged-props)))
                {}
                all-element-ids)]
    {:merged merged
     :stats @stats}))

;;
;; High-Level Reconciliation API
;; ============================================================================

(defn reconcile
  "Reconcile external changes back into Forma project (3-way merge).

  Args:
    base    - Base version (original export snapshot)
    theirs  - Their version (external edits in WordPress/React/etc.)
    ours    - Our version (current Forma project state)
    options - {:strategy :auto | :theirs-wins | :ours-wins | :manual
               :resolve-fn (fn [elem-id prop-key conflict] → value)  ; for :manual
               :preserve-tokens? true | false  ; try to preserve token references
               :metadata {...}}  ; optional metadata for context

  Returns:
    {:merged {...}              ; Merged result
     :conflicts {...}           ; Unresolved conflicts (if any)
     :stats {...}               ; Merge statistics
     :change-summary {...}}     ; Summary of what changed"
  [base theirs ours {:keys [strategy resolve-fn preserve-tokens? metadata]
                     :or {strategy :auto
                          preserve-tokens? true}}]
  (let [theirs-changes (detect-changes base theirs)
        ours-changes (detect-changes base ours)
        conflicts (detect-conflicts theirs-changes ours-changes)
        result (case strategy
                 :auto
                 (merge-auto base theirs ours theirs-changes ours-changes conflicts)

                 :theirs-wins
                 (merge-theirs-wins base theirs ours)

                 :ours-wins
                 (merge-ours-wins base theirs ours)

                 :manual
                 (if resolve-fn
                   (merge-manual base theirs ours resolve-fn)
                   (throw (ex-info "Manual merge requires :resolve-fn"
                                   {:strategy :manual})))

                 (throw (ex-info "Unknown merge strategy"
                                 {:strategy strategy
                                  :valid-strategies [:auto :theirs-wins :ours-wins :manual]})))
        change-summary {:theirs-changes theirs-changes
                        :ours-changes ours-changes
                        :conflicts conflicts
                        :total-changes (+ (count theirs-changes) (count ours-changes))
                        :conflict-count (count conflicts)}]
    (assoc result :change-summary change-summary)))

;;
;; Diff Reporting
;; ============================================================================

(defn generate-diff-report
  "Generate human-readable diff report for reconciliation.

  Args:
    reconciliation-result - Result from reconcile function

  Returns:
    String report with:
    - Summary statistics
    - Detailed change listing
    - Conflict details (if any)
    - Recommendations"
  [{:keys [merged conflicts stats change-summary]}]
  (let [theirs-changes (:theirs-changes change-summary)
        ours-changes (:ours-changes change-summary)
        conflict-count (:conflict-count change-summary)]
    (str
     "=== RECONCILIATION REPORT ===\n\n"

     "SUMMARY:\n"
     "  Auto-merged: " (get stats :auto-merged-count 0) " properties\n"
     "  THEIRS accepted: " (get stats :theirs-accepted (get stats :theirs-count 0)) " changes\n"
     "  OURS accepted: " (get stats :ours-accepted (get stats :ours-count 0)) " changes\n"
     "  Conflicts: " conflict-count "\n\n"

     (when (pos? conflict-count)
       (str "CONFLICTS DETECTED:\n"
            (apply str
                   (for [[elem-id prop-conflicts] conflicts
                         [prop-key conflict] prop-conflicts]
                     (str "  - " elem-id " → " prop-key "\n"
                          "    Conflict type: " (:conflict-type conflict) "\n"
                          "    BASE:   " (pr-str (:base-value conflict)) "\n"
                          "    THEIRS: " (pr-str (:theirs-value conflict)) "\n"
                          "    OURS:   " (pr-str (:ours-value conflict)) "\n\n")))
            "\n"))

     "THEIRS CHANGES:\n"
     (if (empty? theirs-changes)
       "  (none)\n"
       (apply str
              (for [[elem-id prop-changes] theirs-changes
                    [prop-key change] prop-changes]
                (str "  " (:type change) " - " elem-id " → " prop-key
                     " = " (pr-str (:target-value change)) "\n"))))
     "\n"

     "OURS CHANGES:\n"
     (if (empty? ours-changes)
       "  (none)\n"
       (apply str
              (for [[elem-id prop-changes] ours-changes
                    [prop-key change] prop-changes]
                (str "  " (:type change) " - " elem-id " → " prop-key
                     " = " (pr-str (:target-value change)) "\n"))))
     "\n"

     "=== END REPORT ===\n")))

(defn preview-reconciliation
  "Preview reconciliation without applying changes (dry-run).

  Args:
    base    - Base version
    theirs  - Their version
    ours    - Our version
    options - Reconciliation options

  Returns:
    Reconciliation result + diff report (no files written)"
  [base theirs ours options]
  (let [result (reconcile base theirs ours options)
        report (generate-diff-report result)]
    (assoc result :diff-report report)))
