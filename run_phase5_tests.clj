(require '[forma.dev.phase5-tests :as t5])
(require '[forma.dev.sync-tests :as ts])

(println "\n🚀 Running Phase 5 Tests...\n")

(let [r1 (t5/run-all-phase5-tests)
      r2 (ts/run-all-sync-tests)]

  (println "\n" (apply str (repeat 80 "=")))
  (println "\n📊 COMBINED PHASE 5 RESULTS")
  (println (apply str (repeat 80 "=")))
  (println (format "Phase 5 Tests: %d/%d passing" (:passed r1) (:total r1)))
  (println (format "Sync Tests:    %d/%d passing" (:passed r2) (:total r2)))
  (println (format "Total:         %d/%d passing (%.0f%%)"
                   (+ (:passed r1) (:passed r2))
                   (+ (:total r1) (:total r2))
                   (* 100.0 (/ (+ (:passed r1) (:passed r2))
                               (+ (:total r1) (:total r2))))))

  (when (= 100 (int (* 100 (/ (+ (:passed r1) (:passed r2))
                               (+ (:total r1) (:total r2))))))
    (println "\n🎉🎉🎉 PHASE 5 COMPLETE - ALL TESTS PASSING"))

  (println))

(System/exit 0)
