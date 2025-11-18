(ns forma.dev.phase5-2-tests
  "Phase 5.2 Tests - Advanced Minification

   Tests for EDN-driven advanced minification features:
   - Context-aware HTML minification (preserve <pre>, <script>, <style>)
   - Advanced HTML optimizations (attribute compression, boolean attrs)
   - Advanced CSS optimizations (color shortening, value compression)

   All tests verify that advanced minification is working via the
   EDN-declared operations in platform configs."
  (:require [forma.minification.core :as minify]
            [forma.compiler :as compiler]))

;; ============================================================================
;; Test Utilities
;; ============================================================================

(defn run-test [test-name test-fn]
  (try
    (test-fn)
    (println (str "✅ " test-name))
    true
    (catch Exception e
      (println (str "❌ " test-name))
      (println (str "   Error: " (.getMessage e)))
      (.printStackTrace e)
      false)))

(defn assert-equal [expected actual message]
  (when (not= expected actual)
    (throw (Exception. (str message "\n  Expected: " (pr-str expected) "\n  Actual:   " (pr-str actual))))))

;; ============================================================================
;; Context-Aware HTML Minification Tests
;; ============================================================================

(defn test-context-aware-whitespace-basic
  "Test that whitespace is collapsed outside preserve tags"
  []
  (let [html-config (compiler/load-platform-config :html)
        input "  <div>   Hello   World   </div>  "
        expected " <div> Hello World </div> "
        actual (minify/minify-with-platform-config
                input html-config :html-string
                {:context-aware-whitespace true})]
    (assert-equal expected actual "Basic whitespace collapse failed")))

(defn test-context-aware-whitespace-preserve-pre
  "Test that whitespace is preserved in <pre> tags"
  []
  (let [html-config (compiler/load-platform-config :html)
        input "<div>  <pre>  code   here  </pre>  </div>"
        ;; Should collapse whitespace outside <pre> but preserve inside
        ;; Note: inter-tag whitespace removal also applies
        expected "<div><pre>  code   here  </pre></div>"
        actual (minify/minify-with-platform-config
                input html-config :html-string
                {:context-aware-whitespace true
                 :remove-inter-tag-whitespace true})]
    (assert-equal expected actual "Whitespace preservation in <pre> failed")))

(defn test-context-aware-whitespace-preserve-code
  "Test that whitespace is preserved in <code> tags"
  []
  (let [html-config (compiler/load-platform-config :html)
        input "<p>  <code>  x   =   5  </code>  </p>"
        expected "<p><code>  x   =   5  </code></p>"
        actual (minify/minify-with-platform-config
                input html-config :html-string
                {:context-aware-whitespace true
                 :remove-inter-tag-whitespace true})]
    (assert-equal expected actual "Whitespace preservation in <code> failed")))

(defn test-context-aware-whitespace-preserve-script
  "Test that whitespace is preserved in <script> tags"
  []
  (let [html-config (compiler/load-platform-config :html)
        input "<div>  <script>  var x   =   5;  </script>  </div>"
        expected "<div><script>  var x   =   5;  </script></div>"
        actual (minify/minify-with-platform-config
                input html-config :html-string
                {:context-aware-whitespace true
                 :remove-inter-tag-whitespace true})]
    (assert-equal expected actual "Whitespace preservation in <script> failed")))

(defn test-context-aware-whitespace-preserve-style
  "Test that whitespace is preserved in <style> tags"
  []
  (let [html-config (compiler/load-platform-config :html)
        input "<div>  <style>  .card   {   color:   red;   }  </style>  </div>"
        expected "<div><style>  .card   {   color:   red;   }  </style></div>"
        actual (minify/minify-with-platform-config
                input html-config :html-string
                {:context-aware-whitespace true
                 :remove-inter-tag-whitespace true})]
    (assert-equal expected actual "Whitespace preservation in <style> failed")))

(defn test-context-aware-whitespace-preserve-textarea
  "Test that whitespace is preserved in <textarea> tags"
  []
  (let [html-config (compiler/load-platform-config :html)
        input "<form>  <textarea>  line1\n  line2  </textarea>  </form>"
        expected "<form><textarea>  line1\n  line2  </textarea></form>"
        actual (minify/minify-with-platform-config
                input html-config :html-string
                {:context-aware-whitespace true
                 :remove-inter-tag-whitespace true})]
    (assert-equal expected actual "Whitespace preservation in <textarea> failed")))

;; ============================================================================
;; HTML Attribute Optimization Tests
;; ============================================================================

(defn test-boolean-attribute-shortening
  "Test that boolean attributes are shortened"
  []
  (let [html-config (compiler/load-platform-config :html)
        input "<input checked=\"checked\" disabled=\"disabled\">"
        expected "<input checked disabled>"
        actual (minify/minify-with-platform-config
                input html-config :html-string
                {:shorten-boolean-attributes true})]
    (assert-equal expected actual "Boolean attribute shortening failed")))

(defn test-attribute-quote-removal
  "Test that quotes are removed from simple attribute values"
  []
  (let [html-config (compiler/load-platform-config :html)
        input "<div class=\"card\" id=\"main-content\">"
        expected "<div class=card id=main-content>"
        actual (minify/minify-with-platform-config
                input html-config :html-string
                {:remove-attribute-quotes true})]
    (assert-equal expected actual "Attribute quote removal failed")))

(defn test-attribute-quote-preservation
  "Test that quotes are preserved when necessary (spaces, special chars)"
  []
  (let [html-config (compiler/load-platform-config :html)
        input "<div class=\"btn btn-primary\" data-value=\"hello world\">"
        ;; Quotes should be preserved because values contain spaces
        expected "<div class=\"btn btn-primary\" data-value=\"hello world\">"
        actual (minify/minify-with-platform-config
                input html-config :html-string
                {:remove-attribute-quotes true})]
    (assert-equal expected actual "Attribute quote preservation failed")))

(defn test-redundant-type-attribute-removal
  "Test that redundant type attributes are removed"
  []
  (let [html-config (compiler/load-platform-config :html)
        input "<script type=\"text/javascript\">alert('Hi');</script><style type=\"text/css\">.card{}</style>"
        expected "<script>alert('Hi');</script><style>.card{}</style>"
        actual (minify/minify-with-platform-config
                input html-config :html-string
                {:remove-redundant-attributes true})]
    (assert-equal expected actual "Redundant type attribute removal failed")))

;; ============================================================================
;; HTML Comment Removal Tests
;; ============================================================================

(defn test-html-comment-removal
  "Test that HTML comments are removed"
  []
  (let [html-config (compiler/load-platform-config :html)
        input "<div><!-- This is a comment --><p>Hello</p></div>"
        expected "<div><p>Hello</p></div>"
        actual (minify/minify-with-platform-config
                input html-config :html-string
                {:remove-comments true})]
    (assert-equal expected actual "HTML comment removal failed")))

;; ============================================================================
;; Inter-Tag Whitespace Removal Tests
;; ============================================================================

(defn test-inter-tag-whitespace-removal
  "Test that whitespace between tags is removed"
  []
  (let [html-config (compiler/load-platform-config :html)
        input "<div>   </div>   <div>   </div>"
        expected "<div></div><div></div>"
        actual (minify/minify-with-platform-config
                input html-config :html-string
                {:context-aware-whitespace true
                 :remove-inter-tag-whitespace true})]
    (assert-equal expected actual "Inter-tag whitespace removal failed")))

;; ============================================================================
;; CSS Color Shortening Tests
;; ============================================================================

(defn test-css-hex-color-shortening
  "Test that 6-digit hex colors are shortened to 3-digit when possible"
  []
  (let [css-config (compiler/load-platform-config :css)
        input ".card { color: #ffffff; background: #ff0000; }"
        expected ".card{color:#fff;background:#f00;}"
        actual (minify/minify-with-platform-config
                input css-config :css-string
                {:shorten-colors true
                 :remove-delimiter-spaces true})]
    (assert-equal expected actual "CSS hex color shortening failed")))

(defn test-css-hex-color-no-shortening
  "Test that hex colors that can't be shortened are preserved"
  []
  (let [css-config (compiler/load-platform-config :css)
        ;; Use a color where pairs are NOT identical: #ff0012 (ff, 00, 12)
        ;; #ff0012 cannot be shortened because the third pair (12) is not identical
        input ".card { color: #ff0012; }"
        expected ".card{color:#ff0012;}"
        actual (minify/minify-with-platform-config
                input css-config :css-string
                {:shorten-colors true
                 :remove-delimiter-spaces true})]
    (assert-equal expected actual "CSS hex color preservation failed")))

;; ============================================================================
;; CSS Value Compression Tests
;; ============================================================================

(defn test-css-leading-zero-removal
  "Test that leading zeros are removed from decimals"
  []
  (let [css-config (compiler/load-platform-config :css)
        input ".card { opacity: 0.5; margin: 0.25em; }"
        expected ".card{opacity:.5;margin:.25em;}"
        actual (minify/minify-with-platform-config
                input css-config :css-string
                {:compress-values true
                 :remove-delimiter-spaces true})]
    (assert-equal expected actual "CSS leading zero removal failed")))

(defn test-css-zero-unit-removal
  "Test that units are removed from zero values"
  []
  (let [css-config (compiler/load-platform-config :css)
        input ".card { margin: 0px; padding: 0em; border-width: 0rem; }"
        expected ".card{margin:0;padding:0;border-width:0;}"
        actual (minify/minify-with-platform-config
                input css-config :css-string
                {:compress-values true
                 :remove-delimiter-spaces true})]
    (assert-equal expected actual "CSS zero unit removal failed")))

(defn test-css-trailing-zero-removal
  "Test that trailing zeros are removed"
  []
  (let [css-config (compiler/load-platform-config :css)
        input ".card { font-size: 1.00em; margin: 2.50rem; }"
        expected ".card{font-size:1em;margin:2.5rem;}"
        actual (minify/minify-with-platform-config
                input css-config :css-string
                {:compress-values true
                 :remove-delimiter-spaces true})]
    (assert-equal expected actual "CSS trailing zero removal failed")))

;; ============================================================================
;; CSS Comment Removal Tests
;; ============================================================================

(defn test-css-comment-removal
  "Test that CSS comments are removed"
  []
  (let [css-config (compiler/load-platform-config :css)
        input ".card { /* This is a comment */ color: red; }"
        expected ".card{color:red;}"
        actual (minify/minify-with-platform-config
                input css-config :css-string
                {:remove-comments true
                 :remove-delimiter-spaces true})]
    (assert-equal expected actual "CSS comment removal failed")))

;; ============================================================================
;; CSS Whitespace Tests
;; ============================================================================

(defn test-css-whitespace-collapse
  "Test that CSS whitespace is collapsed"
  []
  (let [css-config (compiler/load-platform-config :css)
        input ".card   {   color:   red;   }"
        expected ".card{color:red;}"
        actual (minify/minify-with-platform-config
                input css-config :css-string
                {:remove-whitespace true
                 :remove-delimiter-spaces true})]
    (assert-equal expected actual "CSS whitespace collapse failed")))

(defn test-css-delimiter-space-removal
  "Test that spaces around CSS delimiters are removed"
  []
  (let [css-config (compiler/load-platform-config :css)
        input ".card { color : red ; background : blue ; }"
        expected ".card{color:red;background:blue;}"
        actual (minify/minify-with-platform-config
                input css-config :css-string
                {:remove-delimiter-spaces true})]
    (assert-equal expected actual "CSS delimiter space removal failed")))

;; ============================================================================
;; CSS Value Normalization Tests
;; ============================================================================

(defn test-css-font-weight-normalization
  "Test that font-weight values are normalized"
  []
  (let [css-config (compiler/load-platform-config :css)
        input ".card { font-weight: normal; } .title { font-weight: bold; }"
        expected ".card{font-weight:400;}.title{font-weight:700;}"
        actual (minify/minify-with-platform-config
                input css-config :css-string
                {:normalize-values true
                 :remove-delimiter-spaces true})]
    (assert-equal expected actual "CSS font-weight normalization failed")))

;; ============================================================================
;; Integration Tests
;; ============================================================================

(defn test-html-full-minification
  "Test full HTML minification with all optimizations enabled"
  []
  (let [html-config (compiler/load-platform-config :html)
        input "  <div class=\"card\">  <!-- comment -->  <pre>  code  </pre>  <input checked=\"checked\">  </div>  "
        ;; Should: collapse whitespace, remove comment, preserve <pre>, shorten boolean
        expected " <div class=card><pre>  code  </pre><input checked></div> "
        actual (minify/minify-with-platform-config
                input html-config :html-string
                {:context-aware-whitespace true
                 :remove-comments true
                 :shorten-boolean-attributes true
                 :remove-attribute-quotes true
                 :remove-inter-tag-whitespace true})]
    (assert-equal expected actual "Full HTML minification failed")))

(defn test-css-full-minification
  "Test full CSS minification with all optimizations enabled"
  []
  (let [css-config (compiler/load-platform-config :css)
        input "  .card   {   color:   #ffffff;   margin:   0.50em   0px;   font-weight:   bold;   }  "
        ;; Should: collapse whitespace, shorten color, compress values, normalize font-weight, remove delimiter spaces
        ;; Leading space preserved by whitespace collapse, trailing space removed by delimiter-space removal
        expected " .card{color:#fff;margin:.50em 0;font-weight:700;}"
        actual (minify/minify-with-platform-config
                input css-config :css-string
                {:remove-whitespace true
                 :shorten-colors true
                 :compress-values true
                 :normalize-values true
                 :remove-delimiter-spaces true})]
    (assert-equal expected actual "Full CSS minification failed")))

;; ============================================================================
;; Test Runner
;; ============================================================================

(defn run-all-phase5-2-tests
  "Run all Phase 5.2 minification tests"
  []
  (println "\n========================================")
  (println "Phase 5.2 Tests - Advanced Minification")
  (println "========================================\n")

  (let [tests [
               ;; Context-aware HTML
               ["Context-aware whitespace - basic" test-context-aware-whitespace-basic]
               ["Context-aware whitespace - <pre>" test-context-aware-whitespace-preserve-pre]
               ["Context-aware whitespace - <code>" test-context-aware-whitespace-preserve-code]
               ["Context-aware whitespace - <script>" test-context-aware-whitespace-preserve-script]
               ["Context-aware whitespace - <style>" test-context-aware-whitespace-preserve-style]
               ["Context-aware whitespace - <textarea>" test-context-aware-whitespace-preserve-textarea]

               ;; HTML attributes
               ["Boolean attribute shortening" test-boolean-attribute-shortening]
               ["Attribute quote removal" test-attribute-quote-removal]
               ["Attribute quote preservation" test-attribute-quote-preservation]
               ["Redundant type attribute removal" test-redundant-type-attribute-removal]

               ;; HTML comments
               ["HTML comment removal" test-html-comment-removal]

               ;; Inter-tag whitespace
               ["Inter-tag whitespace removal" test-inter-tag-whitespace-removal]

               ;; CSS colors
               ["CSS hex color shortening" test-css-hex-color-shortening]
               ["CSS hex color no shortening" test-css-hex-color-no-shortening]

               ;; CSS values
               ["CSS leading zero removal" test-css-leading-zero-removal]
               ["CSS zero unit removal" test-css-zero-unit-removal]
               ["CSS trailing zero removal" test-css-trailing-zero-removal]

               ;; CSS comments
               ["CSS comment removal" test-css-comment-removal]

               ;; CSS whitespace
               ["CSS whitespace collapse" test-css-whitespace-collapse]
               ["CSS delimiter space removal" test-css-delimiter-space-removal]

               ;; CSS normalization
               ["CSS font-weight normalization" test-css-font-weight-normalization]

               ;; Integration
               ["HTML full minification" test-html-full-minification]
               ["CSS full minification" test-css-full-minification]]

        results (mapv (fn [[name test-fn]] (run-test name test-fn)) tests)
        passed (count (filter true? results))
        total (count results)]

    (println "\n========================================")
    (println (str "Results: " passed "/" total " tests passed"))
    (if (= passed total)
      (println "✅ All Phase 5.2 tests PASSED!")
      (println (str "❌ " (- total passed) " test(s) FAILED")))
    (println "========================================\n")

    (= passed total)))
