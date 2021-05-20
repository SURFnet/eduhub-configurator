(ns ooapi-gateway-configurator.institutions.store
  (:require [clj-yaml.core :as yaml]))

(defn fetch
  [yaml-fname]
  (-> yaml-fname
      slurp
      yaml/parse-string
      :serviceEndpoints))

(defn put
  [yaml-fname institutions]
  (spit yaml-fname
        (-> yaml-fname
            slurp
            yaml/parse-string
            (assoc :serviceEndpoints institutions)
            yaml/generate-string)))
