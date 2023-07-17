;; Copyright (C) 2021, 2023 SURFnet B.V.
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
            [nl.jomco.envopts :as envopts]
            [ooapi-gateway-configurator.web :as web]
            [ring.adapter.jetty :refer [run-jetty]]))

(def opt-specs
  {:http-host ["canonical hostname for webserver" :str
               :default "localhost" :in [:jetty :host]]
   :http-port ["http port for webserver" :int
               :default 8080 :in [:jetty :port]]

   :auth-authorize-uri    ["OAUTH authorization URI" :http
                           :in [:auth :authorize-uri]]
   :auth-access-token-uri ["OAUTH access token URI" :http
                           :in [:auth :access-token-uri]]
   :auth-client-id        ["OAUTH Client ID" :str
                           :in [:auth :client-id]]
   :auth-client-secret    ["OAUTH Client secret" :str
                           :in [:auth :client-secret]]
   :auth-user-info-uri    ["OAUTH User-Info URI" :http
                           :in [:auth :user-info-uri]]
   :auth-redirect-uri     ["OAUTH Redirect URI" :http
                           :default (java.net.URI/create "http://localhost:8080/oauth2/conext/callback")
                           :in [:auth :redirect-uri]]
   :auth-conext-group-ids ["Conext group ids that are authorized for this application" ::set
                           :in [:auth :group-ids]]

   :gateway-config-yaml ["Path to gateway configuration file" ::file
                         :existing? true :in [:store :gateway-config-yaml]]
   :work-dir            ["Path to directory for workfiles and backups" :dir
                         :existing? true :in [:store :work-dir]
                         :default nil]
   :pipeline            ["Name of the pipeline to configure in gateway configuration file" :str
                         :in [:store :pipeline]]

   :secrets-key-file ["File containing 192 bit hexadecimal key to encode/decode secrets"
                      ::file-content, :existing? true
                      :in [:store :secrets-key]]})


(defmethod envopts/parse ::set
  [s _]
  [(set (string/split s #"\w*,\w*"))])

(defmethod envopts/parse ::file
  [s {:keys [existing?]}]
  (let [f (io/file s)]
    (if (and existing? (not (.exists f)))
      [nil (format "file '%s' does not exist" s)]
      [f])))

(defmethod envopts/parse ::dir
  [s {:keys [existing?]}]
  (let [d (io/file s)]
    (when (and existing? (not (.isDirectory d)))
      [nil (format "'%s' is not a directory" s)]
      [d])))

(defmethod envopts/parse ::file-content
  [s {:keys [existing?]}]
  (let [f (io/file s)]
    (if (and existing? (not (.exists f)))
      [nil (format "file '%s' does not exist" s)]
      [(-> f (slurp) (string/trim))])))

(defn mk-config
  [env]
  (envopts/opts env opt-specs))

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
  (let [[config errs] (mk-config environ/env)]
    (when errs
      (println (envopts/errs-description errs))
      (System/exit 1))
    (start! config)))
