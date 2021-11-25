(ns hooks.user
  "Provides custom clj-kondo hooks.

  These are configured in the `.clj-kondo/config.edn` file.

  See also
  https://github.com/clj-kondo/clj-kondo/blob/master/doc/hooks.md"
  (:require [clj-kondo.hooks-api :as hooks]
            [clojure.string :as string]))

;; TODO: get docstring from metadata, defmulti, defprotocol... more
;; get positional arguments where relevant
(defn get-doc-nodes
  "Return a collection of docstring nodes from the given `def`-like form.

  Return nil if no docstring is available."
  [{:keys [node]}]
  (let [n (some-> node :children first hooks/sexpr)]
    (cond
      (and (= 'def n)
           (= 4 (some-> node :children count)))
      (some-> node :children (nth 2) vector)

      (and (= 'defn n)
           (<= 4 (some-> node :children count))
           (string? (hooks/sexpr (some-> node :children (nth 2)))))
      (some-> node :children (nth 2) vector)

      (and (= 'ns n)
           (<= 3 (some-> node :children count))
           (string? (hooks/sexpr (some-> node :children (nth 2)))))
      (some-> node :children (nth 2) vector))))

;; TODO:
;; - all arguments should be mentioned
;; - arguments and vars should be quoted
(defn messages
  "Return a sequence of linting messages for the given `docstring`."
  [docstring]
  (cond-> nil
    (string/blank? docstring)
    (conj {:message "Docstring should not be blank."
           :type :docstring-blank})

    (not (re-matches #"(?s)[A-Z][^\r\n]*\.([\r\n].*)?" docstring))
    (conj {:message "First line of the docstring should be a complete, capitalized sentence ending with `.`."
           :type :docstring-first-line-sentence})

    (or (re-matches #"(?s)\s.*" docstring)
        (re-matches #"(?s).*\s" docstring))
    (conj {:message "Docstring should not have leading or trailing whitespace."
           :type :docstring-leading-trailing-whitespace})))


(defn findings
  "Given an AST node, returns a collection of findings."
  [ast]
  (mapcat (fn [doc-node]
            (let [{:keys [:row :col]} (some-> doc-node meta)]
              (map #(assoc % :row row :col col) (messages (hooks/sexpr doc-node)))))
          (get-doc-nodes ast)))

(defn docstring
  "Hook for docstring linting rules. Expects def-like `ast`s."
  [ast]
  (doseq [finding (findings ast)]
    (hooks/reg-finding! finding)))
