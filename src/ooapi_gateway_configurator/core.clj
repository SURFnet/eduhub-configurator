(ns ooapi-gateway-configurator.core
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [environ.core :as environ]
            [ooapi-gateway-configurator.web :as web]
            [ring.adapter.jetty :refer [run-jetty]]))

(def default-env
  "settings that can be changed from the environment or java
  properties. Values are all strings or nil (unset)."
  {:http-host           "localhost"
   :http-port           "8080"

   ;; TODO remove this
   :gateway-config-yaml "resources/test/gateway.config.yml"})

(defn get-env
  [env k]
  (or (get env k)
      (get default-env k)))

(defn get-str
  [env k]
  (get-env env k))

(defn get-int
  [env k]
  (when-let [s (get-env env k)]
    (try
      (Integer/parseInt s)
      (catch NumberFormatException e
        (throw (ex-info (str "Configuration option " k " should be a valid integer")
                        {:option k :value s}))))))

(defn get-file
  [env k & {:keys [existing]}]
  (let [s (get-env env k)]
    (when (and existing (or (not s) (not (.exists (io/file s)))))
      (throw (ex-info (str "Configuration option " k " does not refer to an existing file")
                      {:option k :value s})))
    s))

(defn mk-config
  [env]
  {:jetty {:host  (get-str env :http-host)
           :port  (get-int env :http-port)
           :join? false}
   :web   {:institutions-yaml-fname (get-file env :gateway-config-yaml :existing true)}})

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
          (start-webserver config (web/mk-app (:web config)))))

(defn -main
  [& _]
  (let [config (mk-config environ/env)]
    (start! config)))
