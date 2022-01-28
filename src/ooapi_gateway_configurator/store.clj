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

(ns ooapi-gateway-configurator.store
  (:require [clj-yaml.core :as yaml]
            [clojure.java.io :as io]
            [datascript.core :as d]
            [ooapi-gateway-configurator.form :as form]
            [ooapi-gateway-configurator.html-time :as html-time]
            [ooapi-gateway-configurator.model :as model]
            [ooapi-gateway-configurator.store.gateway-config :as gateway-config]
            [ooapi-gateway-configurator.versioning :as versioning]
            [ring.util.response :as response])
  (:import java.time.Instant))

(defn- checkout-yaml [yaml-file opts]
  (versioning/checkout yaml-file yaml/parse-string opts))

(defn- yaml->conn
  [config pipeline]
  (doto (d/create-conn model/schema)
    (d/transact! (gateway-config/yaml->model config pipeline))))

(defn- fetch
  [{:keys [gateway-config-yaml work-dir pipeline]}]
  (let [{gw     :contents
         current-version :version
         :as             checkout}      (checkout-yaml gateway-config-yaml {:work-dir work-dir})
        conn (yaml->conn gw pipeline)]
    {:conn conn
     :model @conn
     ::uncommitted?               (versioning/uncommitted? checkout)
     ::versions                   (versioning/versions gateway-config-yaml {:work-dir work-dir})
     ::current-version            current-version}))

(defn- put
  [model
   {:keys [gateway-config-yaml work-dir pipeline]}]
  (let [opts               {:work-dir work-dir}
        {:keys [version
                contents]} (checkout-yaml gateway-config-yaml opts)
        new-contents       (gateway-config/model->yaml model contents pipeline)]
    (versioning/stage! gateway-config-yaml version
                       (yaml/generate-string new-contents)
                       opts)))

(defn- commit!
  [{:keys [gateway-config-yaml work-dir]}]
  (versioning/commit! gateway-config-yaml {:work-dir work-dir}))

(defn- last-commit
  [{:keys [gateway-config-yaml]}]
  (Instant/ofEpochMilli (.lastModified (io/as-file gateway-config-yaml))))

(defn commit-component
  [{::keys [uncommitted? versions last-commit current-version]}]
  (if last-commit
    [:div.commit-status
     (when uncommitted?
       [:fieldset.uncommitted [:legend "Pending changes"]
        (form/form
         {:action "/versioning"
          :method "post"}
         "Some changes since deploy at " (html-time/time last-commit) "."
         [:div.secundary-actions
          [:button.primary {:type                 "submit"
                            :name                 "commit"
                            :value                "true"
                            :data-confirm-event   "click"
                            :data-confirm-message "Deploy edits?"}
           "Deploy changes"]
          [:button {:type                 "submit"
                    :name                 "reset"
                    :value                "true"
                    :data-confirm-event   "click"
                    :data-confirm-message "Discard changes?"}
           "Discard changes"]])])
     (form/form
      {:action               "/versioning"
       :method               "post"
       :data-dirty           "never"
       :data-confirm-event   "submit"
       :data-confirm-message "Reset edits?"}
      [:input {:type  "hidden"
               :name  "current-version"
               :value current-version}]
      [:fieldset.older-versions
       [:legend "Deployed versions"]
       (map (fn [{:keys [timestamp deployed?]}]
              [:label.version
               [:input {:type    "radio"
                        :name    "timestamp"
                        :checked (when deployed?
                                   "checked")
                        :value   (if deployed?
                                   "current"
                                   (inst-ms timestamp))}]
               (html-time/time timestamp)
               (when deployed?
                 [:em.deployed " &mdash; Currently deployed"])])
            versions)
       [::div.secundary-actions
        [:button {:type  "submit"
                  :name  "reset"
                  :value "true"}
         "Reset pending changes"]]])]
    [:div.commit-status
     "Configuration error? No value for " ::last-commit "."]))

;; TODO: this function is doing too much. Extract the handling of `model` and `conn`?
(defn wrap
  "Middleware to allow reading and writing configuration."
  [app {:keys [gateway-config-yaml work-dir read-only?] :as config}]
  (fn [{:keys                                            [request-method uri]
        {:strs [commit reset timestamp current-version]} :params
        :as                                              req}]
    (if (and (= :post request-method)
             (= "/versioning" uri))
      (merge
       (response/redirect "/" :see-other)
       (cond
         commit
         (do (versioning/commit! gateway-config-yaml {:work-dir work-dir})
             {:flash "Deployed changes"})

         (and reset (or (= "current" timestamp) (nil? timestamp)))
         (do (versioning/unstage! gateway-config-yaml {:work-dir work-dir})
             {:flash "Discarded changes — reset to currently deployed version."})

         (and reset timestamp)
         (let [timestamp (-> timestamp
                             Long/parseLong
                             java.time.Instant/ofEpochMilli)]
           (if (versioning/reset! gateway-config-yaml
                                  current-version
                                  timestamp
                                  {:work-dir work-dir})
             {:flash (str "Discarded changes — reset to version of " (html-time/human-time timestamp))}
             {:flash "Reset failed!"}))))
      (let [{:keys [model conn] :as cur} (fetch config)
            res (-> req
                    ;; only provide read-only model - transactions
                    ;; should be put on the response
                    (into (dissoc cur :conn))
                    (assoc ::last-commit (last-commit config))
                    app)]
        (when-not read-only?
          (when-let [tx (::model/tx res)]
            (d/transact! conn tx))
          (let [new-model @conn]
            (when (and (seq new-model)
                       (not= new-model model))
              (put new-model config))))
        res))))
