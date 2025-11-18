(ns forma.provenance.core
  "Provenance tracking for Forma compilations.")

(defn start-session
  "Initialise a provenance session for a compilation run.
  Returns a context map that accumulates provenance records."
  ([] (start-session {}))
  ([opts]
   (merge {:records []
           :config opts}
          opts)))

(defn record
  "Record provenance information for a single element.
  `session` is the provenance context; `entry` is a map containing
  hierarchy and styling metadata. Returns the updated session."
  [session entry]
  (update session :records conj entry))

(defn finalize
  "Finalize the session and produce a provenance report.
  The report should be a map that can be serialised to EDN or JSON."
  [session]
  {:provenance (:records session)
   :config (:config session)})

(defn provenance->json
  "Serialise a provenance report to a JSON string.
  Placeholder implementation to be filled once a JSON library is integrated."
  [report]
  (throw (ex-info "JSON serialisation not implemented" {:report report})) )

