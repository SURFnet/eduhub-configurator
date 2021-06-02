(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh-all]]
            [environ.core :refer [env]]
            [ooapi-gateway-configurator.core :as core]))

(defn start!
  []
  (core/start! (-> env
                   (assoc :gateway-config-yaml "resources/test/gateway.config.yml"
                          :credentials-json    "resources/test/credentials.json")
                   (core/mk-config))))

(defn stop!
  []
  (core/stop!))

(defn restart!
  []
  (stop!)
  (refresh-all :after 'user/start!))
