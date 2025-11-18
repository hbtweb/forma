(ns forma.dev.phase4-4-tests
  "Test suite for Phase 4.4: Project Reconciliation (3-way merge).

  Tests cover:
  - Change detection (added, modified, deleted)
  - Conflict detection (both-modified, add/delete conflicts)
  - Merge strategies (auto, theirs-wins, ours-wins, manual)
  - Diff reporting
  - Edge cases (empty states, identical versions, all-conflict scenarios)"
  (:require [forma.hierarchy.reconciliation :as recon]))

;;
;; Test Utilities
;; ============================================================================

(defn assert-test
  "Simple assertion helper for testing."
  [test-name condition message]
  (if condition
    (do
      (println (str "✅ PASS: " test-name))
      true)
    (do
      (println (str "❌ FAIL: " test-name))
      (println (str "    " message))
      false)))

(defn count-changes
  "Count number of changes in change map."
  [changes]
  (reduce + (map (comp count second) changes)))

;;
;; Test 1: Change Detection - Basic Cases
;; ============================================================================

(defn test-change-detection-basic
  "Test basic change detection (added, modified, deleted, unchanged)."
  []
  (let [base {:elem-1 {:color "red" :padding "1rem"}}
        target {:elem-1 {:color "blue"           ; modified
                         :padding "1rem"         ; unchanged
                         :margin "2rem"}}        ; added
        changes (recon/detect-changes base target)
        color-change (get-in changes [:elem-1 :color])
        padding-change (get-in changes [:elem-1 :padding])
        margin-change (get-in changes [:elem-1 :margin])]
    (and
     (assert-test "Change detection - modified"
                  (= :modified (:type color-change))
                  (str "Expected :modified, got " (:type color-change)))
     (assert-test "Change detection - modified values"
                  (and (= "red" (:base-value color-change))
                       (= "blue" (:target-value color-change)))
                  (str "Values don't match: base=" (:base-value color-change)
                       " target=" (:target-value color-change)))
     (assert-test "Change detection - unchanged not returned"
                  (nil? padding-change)
                  (str "Unchanged property should not be in changes: " padding-change))
     (assert-test "Change detection - added"
                  (= :added (:type margin-change))
                  (str "Expected :added, got " (:type margin-change)))
     (assert-test "Change detection - added values"
                  (and (nil? (:base-value margin-change))
                       (= "2rem" (:target-value margin-change)))
                  (str "Added value incorrect: " margin-change)))))

;;
;; Test 2: Change Detection - Deletions
;; ============================================================================

(defn test-change-detection-deletions
  "Test deletion detection."
  []
  (let [base {:elem-1 {:color "red" :padding "1rem" :margin "2rem"}}
        target {:elem-1 {:color "red" :padding "1rem"}}  ; margin deleted
        changes (recon/detect-changes base target)
        margin-change (get-in changes [:elem-1 :margin])]
    (and
     (assert-test "Change detection - deleted"
                  (= :deleted (:type margin-change))
                  (str "Expected :deleted, got " (:type margin-change)))
     (assert-test "Change detection - deleted values"
                  (and (= "2rem" (:base-value margin-change))
                       (nil? (:target-value margin-change)))
                  (str "Deleted values incorrect: " margin-change)))))

;;
;; Test 3: Conflict Detection - Both Modified
;; ============================================================================

(defn test-conflict-detection-both-modified
  "Test conflict when both sides modify the same property."
  []
  (let [base {:elem-1 {:color "red"}}
        theirs {:elem-1 {:color "blue"}}   ; THEIRS changed to blue
        ours {:elem-1 {:color "green"}}    ; OURS changed to green
        theirs-changes (recon/detect-changes base theirs)
        ours-changes (recon/detect-changes base ours)
        conflicts (recon/detect-conflicts theirs-changes ours-changes)
        color-conflict (get-in conflicts [:elem-1 :color])]
    (and
     (assert-test "Conflict detection - both modified detected"
                  (= :conflict (:type color-conflict))
                  (str "Expected :conflict, got " (:type color-conflict)))
     (assert-test "Conflict detection - both modified type"
                  (= :both-modified (:conflict-type color-conflict))
                  (str "Expected :both-modified, got " (:conflict-type color-conflict)))
     (assert-test "Conflict detection - both modified values"
                  (and (= "red" (:base-value color-conflict))
                       (= "blue" (:theirs-value color-conflict))
                       (= "green" (:ours-value color-conflict)))
                  (str "Conflict values incorrect: " color-conflict)))))

;;
;; Test 4: Conflict Detection - No Conflict (Only THEIRS Changed)
;; ============================================================================

(defn test-no-conflict-only-theirs
  "Test that no conflict when only THEIRS changed."
  []
  (let [base {:elem-1 {:color "red"}}
        theirs {:elem-1 {:color "blue"}}   ; THEIRS changed
        ours {:elem-1 {:color "red"}}      ; OURS unchanged
        theirs-changes (recon/detect-changes base theirs)
        ours-changes (recon/detect-changes base ours)
        conflicts (recon/detect-conflicts theirs-changes ours-changes)]
    (assert-test "No conflict - only THEIRS changed"
                 (empty? conflicts)
                 (str "Expected no conflicts, got: " conflicts))))

;;
;; Test 5: Conflict Detection - Both Deleted (No Conflict)
;; ============================================================================

(defn test-no-conflict-both-deleted
  "Test that no conflict when both sides delete the same property."
  []
  (let [base {:elem-1 {:color "red" :padding "1rem"}}
        theirs {:elem-1 {:padding "1rem"}}   ; THEIRS deleted color
        ours {:elem-1 {:padding "1rem"}}     ; OURS deleted color
        theirs-changes (recon/detect-changes base theirs)
        ours-changes (recon/detect-changes base ours)
        conflicts (recon/detect-conflicts theirs-changes ours-changes)]
    (assert-test "No conflict - both deleted same property"
                 (empty? conflicts)
                 (str "Expected no conflicts when both delete same property, got: " conflicts))))

;;
;; Test 6: Conflict Detection - Delete vs Modify
;; ============================================================================

(defn test-conflict-delete-vs-modify
  "Test conflict when one side deletes and other modifies."
  []
  (let [base {:elem-1 {:color "red"}}
        theirs {:elem-1 {}}                  ; THEIRS deleted color
        ours {:elem-1 {:color "blue"}}       ; OURS modified color
        theirs-changes (recon/detect-changes base theirs)
        ours-changes (recon/detect-changes base ours)
        conflicts (recon/detect-conflicts theirs-changes ours-changes)
        color-conflict (get-in conflicts [:elem-1 :color])]
    (and
     (assert-test "Conflict detection - delete vs modify detected"
                  (= :conflict (:type color-conflict))
                  (str "Expected :conflict, got " (:type color-conflict)))
     (assert-test "Conflict detection - delete vs modify type"
                  (= :theirs-deleted-ours-modified (:conflict-type color-conflict))
                  (str "Expected :theirs-deleted-ours-modified, got " (:conflict-type color-conflict)))
     (assert-test "Conflict detection - delete vs modify values"
                  (and (= "red" (:base-value color-conflict))
                       (nil? (:theirs-value color-conflict))
                       (= "blue" (:ours-value color-conflict)))
                  (str "Conflict values incorrect: " color-conflict)))))

;;
;; Test 7: Merge Auto - No Conflicts
;; ============================================================================

(defn test-merge-auto-no-conflicts
  "Test auto-merge with no conflicts (clean merge)."
  []
  (let [base {:elem-1 {:color "red" :padding "1rem"}}
        theirs {:elem-1 {:color "blue" :padding "1rem"}}     ; THEIRS changed color
        ours {:elem-1 {:color "red" :padding "2rem"}}        ; OURS changed padding
        result (recon/reconcile base theirs ours {:strategy :auto})
        merged (:merged result)
        conflicts (:conflicts result)
        stats (:stats result)]
    (and
     (assert-test "Auto-merge - no conflicts detected"
                  (empty? conflicts)
                  (str "Expected no conflicts, got: " conflicts))
     (assert-test "Auto-merge - THEIRS change accepted"
                  (= "blue" (get-in merged [:elem-1 :color]))
                  (str "Expected color=blue, got " (get-in merged [:elem-1 :color])))
     (assert-test "Auto-merge - OURS change accepted"
                  (= "2rem" (get-in merged [:elem-1 :padding]))
                  (str "Expected padding=2rem, got " (get-in merged [:elem-1 :padding])))
     (assert-test "Auto-merge - stats correct"
                  (= 2 (:auto-merged-count stats))
                  (str "Expected 2 auto-merged, got " (:auto-merged-count stats))))))

;;
;; Test 8: Merge Auto - With Conflicts
;; ============================================================================

(defn test-merge-auto-with-conflicts
  "Test auto-merge with conflicts (returns unresolved conflicts)."
  []
  (let [base {:elem-1 {:color "red" :padding "1rem"}}
        theirs {:elem-1 {:color "blue" :padding "1rem"}}     ; THEIRS changed color
        ours {:elem-1 {:color "green" :padding "1rem"}}      ; OURS changed color (conflict!)
        result (recon/reconcile base theirs ours {:strategy :auto})
        conflicts (:conflicts result)
        stats (:stats result)]
    (and
     (assert-test "Auto-merge - conflict detected"
                  (= 1 (count (get conflicts :elem-1)))
                  (str "Expected 1 conflict, got " (count (get conflicts :elem-1))))
     (assert-test "Auto-merge - conflict type correct"
                  (= :both-modified (get-in conflicts [:elem-1 :color :conflict-type]))
                  (str "Expected :both-modified, got " (get-in conflicts [:elem-1 :color :conflict-type])))
     (assert-test "Auto-merge - conflict count in stats"
                  (pos? (:conflict-count stats))
                  (str "Expected conflict count > 0, got " (:conflict-count stats))))))

;;
;; Test 9: Merge Theirs-Wins
;; ============================================================================

(defn test-merge-theirs-wins
  "Test theirs-wins strategy (THEIRS always wins conflicts)."
  []
  (let [base {:elem-1 {:color "red"}}
        theirs {:elem-1 {:color "blue"}}      ; THEIRS changed to blue
        ours {:elem-1 {:color "green"}}       ; OURS changed to green (conflict)
        result (recon/reconcile base theirs ours {:strategy :theirs-wins})
        merged (:merged result)]
    (assert-test "Theirs-wins - THEIRS wins conflict"
                 (= "blue" (get-in merged [:elem-1 :color]))
                 (str "Expected color=blue (THEIRS), got " (get-in merged [:elem-1 :color])))))

;;
;; Test 10: Merge Ours-Wins
;; ============================================================================

(defn test-merge-ours-wins
  "Test ours-wins strategy (OURS always wins conflicts)."
  []
  (let [base {:elem-1 {:color "red"}}
        theirs {:elem-1 {:color "blue"}}      ; THEIRS changed to blue
        ours {:elem-1 {:color "green"}}       ; OURS changed to green (conflict)
        result (recon/reconcile base theirs ours {:strategy :ours-wins})
        merged (:merged result)]
    (assert-test "Ours-wins - OURS wins conflict"
                 (= "green" (get-in merged [:elem-1 :color]))
                 (str "Expected color=green (OURS), got " (get-in merged [:elem-1 :color])))))

;;
;; Test 11: Merge Manual
;; ============================================================================

(defn test-merge-manual
  "Test manual strategy with custom resolve function."
  []
  (let [base {:elem-1 {:color "red"}}
        theirs {:elem-1 {:color "blue"}}
        ours {:elem-1 {:color "green"}}
        resolve-fn (fn [elem-id prop-key conflict]
                     ;; Custom logic: always take THEIRS for color, OURS otherwise
                     (if (= prop-key :color)
                       (:theirs-value conflict)
                       (:ours-value conflict)))
        result (recon/reconcile base theirs ours {:strategy :manual
                                                   :resolve-fn resolve-fn})
        merged (:merged result)
        stats (:stats result)]
    (and
     (assert-test "Manual merge - custom resolution applied"
                  (= "blue" (get-in merged [:elem-1 :color]))
                  (str "Expected color=blue (custom logic), got " (get-in merged [:elem-1 :color])))
     (assert-test "Manual merge - manual-resolved-count stat"
                  (= 1 (:manual-resolved-count stats))
                  (str "Expected 1 manual resolution, got " (:manual-resolved-count stats))))))

;;
;; Test 12: Diff Report Generation
;; ============================================================================

(defn test-diff-report-generation
  "Test diff report generation."
  []
  (let [base {:elem-1 {:color "red"}}
        theirs {:elem-1 {:color "blue"}}
        ours {:elem-1 {:color "green"}}
        result (recon/reconcile base theirs ours {:strategy :auto})
        report (recon/generate-diff-report result)]
    (and
     (assert-test "Diff report - generated successfully"
                  (string? report)
                  (str "Expected string report, got " (type report)))
     (assert-test "Diff report - contains summary"
                  (clojure.string/includes? report "SUMMARY:")
                  "Report missing SUMMARY section")
     (assert-test "Diff report - contains conflicts"
                  (clojure.string/includes? report "CONFLICTS DETECTED:")
                  "Report missing CONFLICTS section")
     (assert-test "Diff report - mentions conflict count"
                  (clojure.string/includes? report "Conflicts: 1")
                  "Report doesn't mention conflict count"))))

;;
;; Test 13: Preview Reconciliation (Dry-Run)
;; ============================================================================

(defn test-preview-reconciliation
  "Test preview mode (dry-run without applying changes)."
  []
  (let [base {:elem-1 {:color "red"}}
        theirs {:elem-1 {:color "blue"}}
        ours {:elem-1 {:color "red"}}
        result (recon/preview-reconciliation base theirs ours {:strategy :auto})
        diff-report (:diff-report result)]
    (and
     (assert-test "Preview - returns result"
                  (some? result)
                  "Preview should return result")
     (assert-test "Preview - includes diff report"
                  (string? diff-report)
                  (str "Expected diff report string, got " (type diff-report)))
     (assert-test "Preview - diff report is readable"
                  (clojure.string/includes? diff-report "RECONCILIATION REPORT")
                  "Diff report missing title"))))

;;
;; Test 14: Edge Case - Empty States
;; ============================================================================

(defn test-edge-case-empty-states
  "Test handling of empty states (no elements)."
  []
  (let [base {}
        theirs {:elem-1 {:color "blue"}}   ; THEIRS added element
        ours {}                              ; OURS unchanged
        result (recon/reconcile base theirs ours {:strategy :auto})
        merged (:merged result)]
    (and
     (assert-test "Edge case - empty base handled"
                  (some? merged)
                  "Merge result should exist")
     (assert-test "Edge case - THEIRS addition accepted"
                  (= "blue" (get-in merged [:elem-1 :color]))
                  (str "Expected color=blue, got " (get-in merged [:elem-1 :color]))))))

;;
;; Test 15: Edge Case - Identical Versions (No Changes)
;; ============================================================================

(defn test-edge-case-identical-versions
  "Test when all versions are identical (no changes)."
  []
  (let [base {:elem-1 {:color "red"}}
        theirs {:elem-1 {:color "red"}}
        ours {:elem-1 {:color "red"}}
        result (recon/reconcile base theirs ours {:strategy :auto})
        stats (:stats result)
        conflicts (:conflicts result)]
    (and
     (assert-test "Edge case - no changes detected"
                  (zero? (:auto-merged-count stats))
                  (str "Expected 0 changes, got " (:auto-merged-count stats)))
     (assert-test "Edge case - no conflicts"
                  (empty? conflicts)
                  (str "Expected no conflicts, got " conflicts)))))

;;
;; Test Runner
;; ============================================================================

(defn run-all-phase4-4-tests
  "Run all Phase 4.4 reconciliation tests."
  []
  (println "\n=== Phase 4.4: Project Reconciliation Tests ===\n")

  (println "--- Change Detection ---")
  (let [t1 (test-change-detection-basic)
        t2 (test-change-detection-deletions)]

    (println "\n--- Conflict Detection ---")
    (let [t3 (test-conflict-detection-both-modified)
          t4 (test-no-conflict-only-theirs)
          t5 (test-no-conflict-both-deleted)
          t6 (test-conflict-delete-vs-modify)]

      (println "\n--- Merge Strategies ---")
      (let [t7 (test-merge-auto-no-conflicts)
            t8 (test-merge-auto-with-conflicts)
            t9 (test-merge-theirs-wins)
            t10 (test-merge-ours-wins)
            t11 (test-merge-manual)]

        (println "\n--- Diff Reporting ---")
        (let [t12 (test-diff-report-generation)
              t13 (test-preview-reconciliation)]

          (println "\n--- Edge Cases ---")
          (let [t14 (test-edge-case-empty-states)
                t15 (test-edge-case-identical-versions)]

            (println "\n=== Phase 4.4 Test Summary ===")
            (let [all-tests [t1 t2 t3 t4 t5 t6 t7 t8 t9 t10 t11 t12 t13 t14 t15]
                  passed (count (filter identity all-tests))
                  total (count all-tests)]
              (println (str "Passed: " passed "/" total))
              (if (= passed total)
                (println "✅ ALL PHASE 4.4 TESTS PASSING!")
                (println "❌ Some tests failed"))
              (= passed total))))))))

;; Auto-run if loaded directly
(comment
  (run-all-phase4-4-tests))
