(ns ooapi-gateway-configurator.store
  (:require [clj-yaml.core :as yaml]
            [clojure.data.json :as json]
            [ooapi-gateway-configurator.state :as state]))

(defn- fetch
  [{:keys [credentials-json gateway-config-yaml]}]
  {::state/applications (-> credentials-json
                            (slurp)
                            (json/read-str :key-fn keyword))
   ::state/institutions (-> gateway-config-yaml
                            (slurp)
                            (yaml/parse-string)
                            :serviceEndpoints)})

(defn- put
  [{:keys [::state/applications ::state/institutions]}
   {:keys [credentials-json gateway-config-yaml]}]
  (when applications
    (spit credentials-json
          (json/write-str applications :key-fn name)))
  (when institutions
    (spit gateway-config-yaml
          (-> gateway-config-yaml
              slurp
              yaml/parse-string
              (assoc :serviceEndpoints institutions)
              yaml/generate-string))))

(defn wrap
  "Middleware to allow reading and writing configuration."
  [app config]
  (fn [req]
    (let [cur (fetch config)
          res (app (into req cur))
          new (select-keys res [::state/applications ::state/institutions])]
      (when (seq new)
        (put new config))
      res)))
