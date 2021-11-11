;; Copyright (C) 2021 SURFnet B.V.
;;
;; This program is free software: you can redistribute it and/or modify it
;; under the terms of the GNU General Public License as published by the Free
;; Software Foundation, either version 3 of the License, or (at your option)
;; any later version.
;;
;; This program is distributed in the hope that it will be useful, but WITHOUT
;; ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
;; FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
;; more details.
;;
;; You should have received a copy of the GNU General Public License along
;; with this program. If not, see http://www.gnu.org/licenses/.

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
  (let [[config errs] (mk-dev-config)]
    (or errs
        (core/start! (assoc-in config [:jetty :join?] false)))))

(defn stop!
  []
  (core/stop!))

(defn restart!
  []
  (stop!)
  (refresh-all :after 'user/start!))
