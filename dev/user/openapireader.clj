(ns user.openapireader
  "This code converts openapi specs in the form of multiple yaml files
  with $ref entries the same spec as a single json file, since most of
  our tooling cannot handle $refs in specifications."
  (:require  [clj-yaml.core :as yaml]
             [clojure.java.io :as io]
             [clojure.data.json :as json]
             [clojure.walk :as walk]))

(defn- read-yaml
  [file]
  (yaml/parse-stream (io/reader (io/as-file file))))

(declare yamls->openapi-json)

(defn- $ref
  [x]
  (when (map? x)
    (:$ref x)))

(defn- inline-refs
  [basedir x]
  (if-let [path ($ref x)]
    (-> x
        (dissoc :$ref)
        (merge (yamls->openapi-json (str basedir "/" path))))
    x))

(defn yamls->openapi-json
  [rootfile]
  (let [basedir (-> rootfile io/as-file .getParent)]
    (->> rootfile
         read-yaml
         (walk/prewalk #(inline-refs basedir %))
         (into {})))) ;; into {} to ensure plain clojure map (not
                      ;; ordered map) - otherwise this won't merge
                      ;; with plain maps

(comment
  (->> (yamls->openapi-json "../ooapi-specification/v5-beta/spec.yaml")
       (json/json-str)
       (spit "./ooapi-v5-beta-spec.json")))
