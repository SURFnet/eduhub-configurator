;; Copyright (C) 2021, 2022 SURFnet B.V.
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

(ns ooapi-gateway-configurator.logging
  "Ring middleware for catching and logging exceptions."
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log]
            [ooapi-gateway-configurator.html :as html])
  (:import java.util.UUID
           org.slf4j.MDC))

(defmacro with-mdc
  "Adds the map `m` to the slf4j log context and executes `body`.

  Keys are converted to strings using `name`
  Values are converted to strings using `str`

  Removes the given keys before returning."
  [m & body]
  `(let [m# ~m]
     (try
       (doseq [[k# v#] m#]
         (MDC/put (name k#) (str v#)))
       ~@body
       (finally
         (doseq [k# (keys m#)]
           (MDC/remove (name k#)))))))

(defn wrap-request-logging
  [f]
  (fn [{:keys [request-method uri] :as request}]
    (let [method (string/upper-case (name request-method))]
      (with-mdc {:request_method method
                 :url uri}
        (when-let [{:keys [status] :as response} (f request)]
          (with-mdc {:http_status status}
            (log/info status method uri)
            response))))))

(defn log-exception
  [e id]
  ;; Request info is provided in MDC, see wrap-request-logging
  (with-mdc {:error_id id}
    (log/error e (str "Error " id))))

(defn wrap-exception-logging
  [f]
  (fn [request]
    (try
      (f request)
      (catch Throwable e
        (let [id (str (UUID/randomUUID))]
          (log-exception e id)
          (html/exception id request))))))

(defn wrap-logging
  [f]
  (-> f
      wrap-exception-logging
      wrap-request-logging))
