(require '[forma.parsers.html :as html])
(require '[clojure.pprint :as pp])

;; Test tokenization first
(println "Testing tokenization...")
(def tokens1 (#'html/tokenize "<div class=\"card\">Hello</div>"))
(println "Tokens for div with class:")
(pp/pprint tokens1)

(println "\nTest normalize-attributes...")
(let [token (first tokens1)
      raw-attrs (:attrs token)
      ;; Test the actual function body
      _ (println "Raw attrs:" raw-attrs)
      _ (println "attrs truthy?:" (if raw-attrs "yes" "no"))
      parse-style? true
      result (cond-> raw-attrs
               true
               (clojure.walk/postwalk (fn [x] (cond (= x "true") true (= x "false") false :else x))))
      _ (println "cond-> result:" result)
      normalized (#'html/normalize-attributes raw-attrs {})]
  (println "Normalized:" normalized))

(println "\nTest build-tree...")
(let [[tree remaining] (#'html/build-tree tokens1 {})]
  (println "Tree result:")
  (pp/pprint tree))

(println "\n" (apply str (repeat 60 "=")) "\n")

(println "Test 1: Simple HTML with class")
(def test1 (html/parse "<div class=\"card\">Hello</div>"))
(pp/pprint test1)

(println "\nTest 2: Self-closing with src")
(def test2 (html/parse "<img src=\"test.jpg\" />"))
(pp/pprint test2)

(println "\nTest 3: With inline style")
(def test3 (html/parse "<div style=\"color: red; background: blue\">Test</div>"))
(pp/pprint test3)

(println "\nTest 4: Nested")
(def test4 (html/parse "<div class=\"card\"><p>Hello</p><p>World</p></div>"))
(pp/pprint test4)
