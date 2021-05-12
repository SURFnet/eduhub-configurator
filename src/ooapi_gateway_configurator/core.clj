(ns ooapi-gateway-configurator.core
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [environ.core :as environ]
            [ring.adapter.jetty :refer [run-jetty]]
            [ooapi-gateway-configurator.web :as web]))

(def default-env
  "settings that can be changed from the environment or java
  properties. Values are all strings or nil (unset)."
  {:http-host  "localhost"
   :http-port  "8080"})

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

(defn mk-config
  [env]
  {:jetty    {:host  (get-str env :http-host)
              :port  (get-int env :http-port)
              :join? false}})

(defonce server-atom (atom nil))

(defn stop! []
  (when-let [server @server-atom]
    (log/info "stopping server")
    (.stop server)
    (reset! server-atom nil)))

(defn start-webserver
  [{{:keys [host port] :as jetty-config} :jetty :as config} app]
  (log/info (str "Starting webserver at http://" host ":" port))
  (run-jetty app jetty-config))

(defn start!
  [config]
  (stop!)
  (reset! server-atom
          (start-webserver config (web/mk-app))))

(defn -main
  [& _]
  (let [config (mk-config environ/env)]
    (start! config)))
