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

(ns ooapi-gateway-configurator.core
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [environ.core :as environ]
            [ooapi-gateway-configurator.web :as web]
            [ring.adapter.jetty :refer [run-jetty]]))

(def default-env
  "Settings that can be changed from the environment or java
  properties. Values are all strings or nil (unset)."
  {:http-host "localhost"
   :http-port "8080"

   :auth-authorize-uri    nil
   :auth-access-token-uri nil
   :auth-client-id        nil
   :auth-client-secret    nil
   :auth-user-info-uri    nil
   :auth-redirect-uri     "/oauth2/conext/callback"

   :gateway-config-yaml nil
   :work-dir            nil
   :pipeline            nil})

(defn key-to-env
  [k]
  (-> k
      name
      string/upper-case
      (string/replace #"-" "_")))

(defn get-env
  [env k & {:keys [required?] :as opts}]
  (if-some [s (get env k (get default-env k))]
    s
    (when required?
      (throw (ex-info (str "Required configuration option " (key-to-env k) " was not provided")
                      {:key  k
                       :opts opts})))))

(defn get-str
  [env k & opts]
  (apply get-env env k opts))

(defn get-set
  [env k & opts]
  (when-let [s (apply get-env env k opts)]
    (set (string/split s #"\w*,\w*"))))

(defn get-int
  [env k & opts]
  (when-let [s (apply get-env env k opts)]
    (try
      (Integer/parseInt s)
      (catch NumberFormatException _
        (throw (ex-info (str "Configuration option " (key-to-env k) " should be a valid integer")
                        {:key k :value s}))))))

(defn get-file
  [env k & opts]
  (let [{:keys [existing?]} (apply hash-map opts)
        s                   (apply get-env env k opts)]
    (when (and s
               existing?
               (not (.exists (io/file s))))
      (throw (ex-info (str "Configuration option " (key-to-env k) " does not refer to an existing file")
                      {:key k :value s})))
    s))

(defn get-dir
  [env k & opts]
  (let [{:keys [existing?]} (apply hash-map opts)
        s                   (apply get-env env k opts)]
    (when (and s
               existing?
               (not (.isDirectory (io/file s))))
      (throw (ex-info (str "Configuration option " (key-to-env k) " does not refer to an existing directory")
                      {:key k :value s})))
    s))

(defn mk-config
  [env]
  {:jetty {:host  (get-str env :http-host)
           :port  (get-int env :http-port)
           :join? false}
   :store {:gateway-config-yaml (get-file env :gateway-config-yaml :required? true :existing? true)
           :work-dir            (get-dir env :work-dir :existing? true)
           :pipeline            (get-str env :pipeline :required? true)}
   :auth  {:authorize-uri    (get-str env :auth-authorize-uri :required? true)
           :access-token-uri (get-str env :auth-access-token-uri :required? true)
           :user-info-uri    (get-str env :auth-user-info-uri :required? true)
           :client-id        (get-str env :auth-client-id :required? true)
           :client-secret    (get-str env :auth-client-secret :required? true)
           :group-ids        (get-set env :auth-conext-group-ids :required? true)
           :redirect-uri     (get-str env :auth-redirect-uri)}})

(defonce server-atom (atom nil))

(defn stop! []
  (when-let [server @server-atom]
    (log/info "stopping server")
    (.stop server)
    (reset! server-atom nil)))

(defn start-webserver
  [{{:keys [host port] :as config} :jetty} app]
  (log/info (str "Starting webserver at http://" host ":" port))
  (run-jetty app config))

(defn start!
  [config]
  (stop!)
  (reset! server-atom
          (start-webserver config (web/mk-app config))))

(defn -main
  [& _]
  (let [config (mk-config environ/env)]
    (start! config)))
