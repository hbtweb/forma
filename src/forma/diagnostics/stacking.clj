(ns forma.diagnostics.stacking
  "Diagnostics for styling system stacking and class conflicts.")

(defn detect-duplicate-classes
  "Return a vector of conflicts when duplicate class families are detected.
  `class-seqs` should be a collection of class name sequences."
  [class-seqs]
  (let [collisions (->> class-seqs
                        (mapcat identity)
                        (frequencies)
                        (filter (fn [[_ n]] (> n 1))))]
    (map (fn [[class-name count]]
           {:type :duplicate-class
            :class class-name
            :occurrences count})
         collisions)))

(defn stacked-extension-warning
  "If a styling stack contains systems that extend each other, return a warning map."
  [stack extension-map]
  (let [pairs (partition 2 1 stack)
        issues (keep (fn [[a b]]
                        (when (= (get extension-map b) a)
                          {:type :redundant-stack
                           :base a
                           :extender b}))
                      pairs)]
    (vec issues)))

