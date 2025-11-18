(ns forma.sync.core
  "Provenance-aware sync utilities for Forma projects.")

(defn export-project
  "Export a Forma project with provenance metadata.
  Returns a map containing payload and provenance summary."
  [project-context provenance-report]
  {:payload project-context
   :provenance provenance-report})

(defn generate-diff
  "Generate a diff between two provenance-aware exports.
  Stub implementation."
  [old new]
  (when (not= old new)
    {:changes [:todo]}))

(defn import-diff
  "Apply a provenance-aware diff to the local project.
  For now returns a summary of what would be applied."
  [project-context diff]
  {:project project-context
   :applied diff})

