(ns user
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [clojure.tools.namespace.repl :refer :all]
            [environ.core :refer [env]]
            [ooapi-gateway-configurator.core :as core])
  (:import java.util.logging.LogManager))

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
