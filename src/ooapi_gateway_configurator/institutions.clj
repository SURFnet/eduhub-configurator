(ns ooapi-gateway-configurator.institutions
  (:require [clj-yaml.core :as yaml]))

(defn fetch
  [yaml-fname]
  (-> yaml-fname
      slurp
      yaml/parse-string
      :serviceEndpoints))
