(ns forma.dev.phase4-3-tests
  "Phase 4.3: Multi-File Generation tests (simplified)."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [forma.hierarchy.generator :as generator]
            [forma.tokens.registry :as registry]
            [forma.hierarchy.classifier :as classifier]))

(deftest test-preview-simple
  (testing "Simple preview test"
    (let [token-registry {:colors {:primary "#4f46e5"}}
          classified-elements
          [{:type :button
            :properties {:background "#4f46e5" :text "Submit"}
            :classified {:global {:background "#4f46e5"}
                        :pages {:text "Submit"}}}]
          preview (generator/preview-file-structure
                   classified-elements
                   token-registry
                   "test-project")]
      (is (contains? preview :files))
      (is (contains? preview :summary))
      (is (pos? (count (:files preview)))))))

(deftest test-extract-components-simple
  (testing "Extract components"
    (let [classified-elements
          [{:type :button
            :properties {:padding "1rem"}
            :classified {:components {:padding "1rem"}}}]
          components (generator/extract-components classified-elements)]
      (is (contains? components :button))
      (is (= "1rem" (get-in components [:button :base :padding]))))))

(deftest test-extract-global-simple
  (testing "Extract global defaults"
    (let [token-registry {:colors {:primary "#4f46e5"}}
          classified-elements
          [{:type :button
            :properties {:background "#4f46e5"}
            :classified {:global {:background "#4f46e5"}}}]
          global-data (generator/extract-global-defaults token-registry classified-elements)]
      (is (= token-registry (:tokens global-data)))
      (is (= "#4f46e5" (get-in global-data [:defaults :button :background]))))))

(defn run-phase4-3-tests
  "Run Phase 4.3 tests"
  []
  (let [results (clojure.test/run-tests 'forma.dev.phase4-3-tests)]
    (println "\n=== Phase 4.3: Multi-File Generation Test Results ===")
    (println (format "Tests run: %d" (+ (:pass results) (:fail results) (:error results))))
    (println (format "Passed: %d" (:pass results)))
    (println (format "Failed: %d" (:fail results)))
    (println (format "Errors: %d" (:error results)))
    results))
