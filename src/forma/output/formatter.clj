(ns forma.output.formatter
  "Output format abstraction protocol for multi-platform compilation.

  Enables compiling the same EDN to different output formats:
  - Hiccup (Clojure data structures)
  - HTML strings (server-side rendering)
  - JSX strings (React)
  - React.createElement calls
  - TypeScript JSX (TSX)
  - Oxygen JSON (WordPress)
  - UXML (Unity UI Toolkit)
  - Vue SFC (Single File Components)
  - Angular templates

  Each platform implements the OutputFormatter protocol.")

;; ============================================================================
;; Output Format Protocol
;; ============================================================================

(defprotocol OutputFormatter
  "Protocol for formatting compiled elements to different output formats"

  (to-hiccup [this element opts]
    "Convert element to Hiccup vector format (Clojure data).
    Example: [:div {:class 'card'} 'Hello']")

  (to-string [this element opts]
    "Convert element to string format (HTML, JSX, etc.).
    Example: '<div class=\"card\">Hello</div>' or '<div className=\"card\">Hello</div>'")

  (to-data [this element opts]
    "Convert element to platform-specific data format (JSON, maps, etc.).
    Example: {:type 'div' :props {:class 'card'} :children ['Hello']}"))

(defprotocol ReverseFormatter
  "Protocol for parsing platform-specific output back to EDN"

  (from-string [this output-str opts]
    "Parse string format (HTML, JSX) back to EDN.
    Example: '<div class=\"card\">Hello</div>' → [:div {:class 'card'} 'Hello']")

  (from-data [this output-data opts]
    "Parse platform-specific data format (JSON, maps) back to EDN.
    Example: {:type 'div' :props {:class 'card'}} → [:div {:class 'card'} 'Hello']"))

;; ============================================================================
;; Format Registry
;; ============================================================================

(def format-registry
  "Registry of available output formatters by platform"
  (atom {}))

(defn register-formatter!
  "Register an output formatter for a platform.

  Arguments:
  - platform: keyword platform identifier (:html, :react, :vue, etc.)
  - formatter: instance implementing OutputFormatter protocol"
  [platform formatter]
  (swap! format-registry assoc platform formatter)
  formatter)

(defn get-formatter
  "Get formatter for platform.

  Arguments:
  - platform: keyword platform identifier

  Returns: formatter instance or nil"
  [platform]
  (get @format-registry platform))

(defn list-formatters
  "List all registered formatters.

  Returns: vector of platform keywords"
  []
  (keys @format-registry))

;; ============================================================================
;; Hiccup Formatter (Default)
;; ============================================================================

(deftype HiccupFormatter []
  OutputFormatter
  (to-hiccup [_ element opts]
    element)  ; Already in Hiccup format

  (to-string [_ element opts]
    ;; Convert Hiccup to HTML string
    (require '[hiccup2.core :as h])
    (str ((resolve 'h/html) element)))

  (to-data [_ element opts]
    ;; Convert Hiccup to generic data structure
    (if (vector? element)
      (let [[tag attrs & children] (if (map? (second element))
                                     element
                                     (into [(first element) {}] (rest element)))]
        {:type (name tag)
         :props attrs
         :children (vec children)})
      {:type :text
       :value (str element)}))

  ReverseFormatter
  (from-string [_ output-str opts]
    ;; Parse HTML string to Hiccup (requires HTML parser)
    (throw (ex-info "HTML parsing not yet implemented" {:format :html})))

  (from-data [_ output-data opts]
    ;; Convert generic data structure to Hiccup
    (if (= (:type output-data) :text)
      (:value output-data)
      (into [(keyword (:type output-data)) (:props output-data)]
            (:children output-data)))))

;; Register default Hiccup formatter
(register-formatter! :hiccup (->HiccupFormatter))

;; ============================================================================
;; Format Conversion API
;; ============================================================================

(defn convert-format
  "Convert element from one format to another.

  Arguments:
  - element: element in source format
  - from-platform: source platform keyword
  - to-platform: target platform keyword
  - opts: conversion options

  Returns: element in target format"
  [element from-platform to-platform opts]
  (let [from-formatter (get-formatter from-platform)
        to-formatter (get-formatter to-platform)]
    (when-not from-formatter
      (throw (ex-info "Source formatter not found" {:platform from-platform})))
    (when-not to-formatter
      (throw (ex-info "Target formatter not found" {:platform to-platform})))

    ;; Convert to Hiccup as intermediate format
    (let [hiccup (if (= from-platform :hiccup)
                  element
                  (from-data from-formatter element opts))]
      ;; Convert from Hiccup to target format
      (case (get opts :output-type :hiccup)
        :hiccup (to-hiccup to-formatter hiccup opts)
        :string (to-string to-formatter hiccup opts)
        :data (to-data to-formatter hiccup opts)
        (to-hiccup to-formatter hiccup opts)))))

(defn format-element
  "Format element using specified platform formatter.

  Arguments:
  - element: Hiccup element
  - platform: platform keyword (:html, :react, :vue, etc.)
  - output-type: :hiccup, :string, or :data
  - opts: formatting options

  Returns: formatted element"
  [element platform output-type opts]
  (let [formatter (get-formatter platform)]
    (when-not formatter
      (throw (ex-info "Formatter not found" {:platform platform})))

    (case output-type
      :hiccup (to-hiccup formatter element opts)
      :string (to-string formatter element opts)
      :data (to-data formatter element opts)
      (to-hiccup formatter element opts))))

;; ============================================================================
;; Batch Conversion
;; ============================================================================

(defn convert-batch
  "Convert multiple elements from one format to another.

  Arguments:
  - elements: sequence of elements in source format
  - from-platform: source platform keyword
  - to-platform: target platform keyword
  - opts: conversion options

  Returns: sequence of elements in target format"
  [elements from-platform to-platform opts]
  (map #(convert-format % from-platform to-platform opts) elements))

;; ============================================================================
;; Compilation Integration
;; ============================================================================

(defn compile-with-formatter
  "Compile EDN element with specified output formatter.

  This integrates with forma.compiler to add output format selection.

  Arguments:
  - element: EDN element
  - compiler-fn: compilation function (forma.compiler/compile-element)
  - platform: platform keyword
  - output-type: :hiccup, :string, or :data
  - opts: compilation + formatting options

  Returns: formatted output"
  [element compiler-fn platform output-type opts]
  (let [;; Compile to Hiccup first
        hiccup (compiler-fn element opts)
        ;; Format to target output
        formatter (get-formatter platform)]
    (when-not formatter
      (throw (ex-info "Formatter not found" {:platform platform})))

    (case output-type
      :hiccup hiccup
      :string (to-string formatter hiccup opts)
      :data (to-data formatter hiccup opts)
      hiccup)))

;; ============================================================================
;; Format Metadata
;; ============================================================================

(defn attach-format-metadata
  "Attach format metadata to element.

  Useful for tracking which format an element is in during conversions.

  Arguments:
  - element: element in any format
  - platform: platform keyword
  - format-type: :hiccup, :string, or :data

  Returns: element with metadata"
  [element platform format-type]
  (if (vector? element)
    (vary-meta element assoc
               :forma/format platform
               :forma/format-type format-type)
    element))

(defn get-format-metadata
  "Get format metadata from element.

  Arguments:
  - element: element with metadata

  Returns: {:platform :html :format-type :hiccup} or nil"
  [element]
  (when (vector? element)
    (let [m (meta element)]
      (when (:forma/format m)
        {:platform (:forma/format m)
         :format-type (:forma/format-type m)}))))

;; ============================================================================
;; Formatter Utilities
;; ============================================================================

(defn formatter-info
  "Get information about a registered formatter.

  Arguments:
  - platform: platform keyword

  Returns: map with formatter details"
  [platform]
  (when-let [formatter (get-formatter platform)]
    {:platform platform
     :formatter formatter
     :supports-output? (satisfies? OutputFormatter formatter)
     :supports-reverse? (satisfies? ReverseFormatter formatter)}))

(defn all-formatter-info
  "Get information about all registered formatters.

  Returns: vector of formatter info maps"
  []
  (mapv formatter-info (list-formatters)))
