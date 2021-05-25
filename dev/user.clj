(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh-all]]
            [environ.core :refer [env]]
            [ooapi-gateway-configurator.core :as core]))

(defn start!
  []
  (core/start! (core/mk-config env)))

(defn stop!
  []
  (core/stop!))

(defn restart!
  []
  (stop!)
  (refresh-all :after 'user/start!))
