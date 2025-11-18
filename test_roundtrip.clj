(require '[forma.output.transformer :as transformer])
(require '[forma.parsers.html :as html-parser])

(println "Test hiccup->jsx-string directly\n")

(def test-elem [:div {:class "card"} "Hello"])
(def jsx (transformer/hiccup->jsx-string test-elem))
(println "Direct call result:")
(println "Type:" (type jsx))
(prn jsx)

(println "\nTest transform-element with empty config:")
(def transformed (transformer/transform-element test-elem {}))
(println "Transformed:")
(prn transformed)
(println "Type:" (type transformed))

(println "\n" (apply str (repeat 60 "=")) "\n")

(println "Test HTML Round-Trip\n")

(def original [:div {:class "card"} "Hello"])
(println "Original:")
(prn original)

(def html (transformer/transform original {} :jsx))
(println "\nTransformed to JSX:")
(println "Type:" (type html))
(println "Value:")
(prn html)

(def parsed (html-parser/parse html))
(println "\nParsed back:")
(prn parsed)

(println "\nEquals?:" (= original parsed))
(println "Original type:" (type original))
(println "Parsed type:" (type parsed))
