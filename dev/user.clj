(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh-all]]
            [environ.core :refer [env]]
            [ooapi-gateway-configurator.core :as core]))

(defn mk-dev-config
  []
  (-> env
      (assoc :gateway-config-yaml "resources/test/gateway.config.yml"
             :pipeline            "test")
      (core/mk-config)))

(defn start!
  []
  (core/start! (mk-dev-config)))

(defn stop!
  []
  (core/stop!))

(defn restart!
  []
  (stop!)
  (refresh-all :after 'user/start!))
