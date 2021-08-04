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
            [ooapi-gateway-configurator.form :as form]
            [ooapi-gateway-configurator.html :as html]
            [ooapi-gateway-configurator.state :as state]
            [ooapi-gateway-configurator.store.klist :as klist]
            [ooapi-gateway-configurator.versioning :as versioning]
            [ring.util.response :as response])
  (:import java.time.Instant))

(defn- checkout-yaml [yaml-file]
  (versioning/checkout yaml-file yaml/parse-string))

(defn- in-yaml [yaml-file f & args]
  (let [{:keys [version contents]} (checkout-yaml yaml-file)]
    (versioning/stage! yaml-file version
                       (yaml/generate-string (apply f contents args)))))

(defn- acl->access-control-list
  "Transform gatekeeper policy action to a access control list for the
  given application.  Only matching endpoints from the institutions
  set will be used and institutions without a matching endpoint will
  be registered with nil paths.

  The shape of a ACL (in YAML) is as follows:

    - app: fred
      endpoints:
        - endpoint: BasicAuthBackend
          paths: ['/', '/courses', '/courses/:id']

  The shape of the output (in EDN):

    {:BasicAuthBackend #{'/', '/courses', '/courses/:id'}
     :ApiKeyBackend    nil
     ..}
  "
  [application-id acls institution-ids]
  (let [endps (->> acls
                   (filter #(= application-id (keyword (:app %))))
                   first
                   :endpoints
                   (reduce (fn [m {:keys [endpoint paths]}]
                             (assoc m (keyword endpoint) (set paths)))
                           {}))]
    (reduce (fn [m id] (assoc m id (get endps id)))
            {}
            institution-ids)))

(defn- gatekeeper-acls->acls
  "Transform a list of gatekeeper action ACLs to a map from application
  IDs to access control lists."
  [acls app-ids institution-ids]
  (reduce (fn [m app]
            (assoc m app (acl->access-control-list app acls institution-ids)))
          {}
          app-ids))

(defn- fetch
  [{:keys [gateway-config-yaml pipeline]}]
  (let [{{:keys [serviceEndpoints pipelines] :as gw} :contents
         current-version                             :version
         :as                                         checkout} (checkout-yaml gateway-config-yaml)
        {:keys [policies apiEndpoints]}                        (get pipelines (keyword pipeline))
        api                                                    (-> apiEndpoints first keyword)
        apps                                                   (klist/get-in policies [:gatekeeper :action :apps])]
    {::state/applications         apps
     ::state/institutions         serviceEndpoints
     ::uncommitted?               (versioning/uncommitted? checkout)
     ::versions                   (versioning/versions gateway-config-yaml)
     ::current-version            current-version
     ::state/access-control-lists (-> policies
                                      (klist/get-in [:gatekeeper :action :acls])
                                      (gatekeeper-acls->acls (keys apps) (keys serviceEndpoints)))
     ::state/api-paths            (-> gw :apiEndpoints api :paths set)}))

(defn- acls->gatekeeper-acls
  [acls]
  (keep (fn [[app acl]]
          (when-let [endpoints (->> acl
                                    (filter (comp seq last))
                                    (map (fn [[endp paths]]
                                           {:endpoint endp, :paths paths}))
                                    seq)]
            {:app       app
             :endpoints endpoints}))
        acls))

(defn- put
  [{:keys [::state/applications ::state/institutions ::state/access-control-lists]}
   {:keys [gateway-config-yaml pipeline]}]
  (in-yaml gateway-config-yaml
           #(cond-> %
              institutions
              (assoc-in [:serviceEndpoints] institutions)

              access-control-lists
              (klist/update-in [:pipelines (keyword pipeline) :policies :gatekeeper]
                               klist/assoc-in [:action :acls]
                               (acls->gatekeeper-acls access-control-lists))

              applications
              (klist/update-in [:pipelines (keyword pipeline) :policies :gatekeeper]
                               klist/assoc-in [:action :apps] applications))))

(defn- commit!
  [{:keys [gateway-config-yaml]}]
  (versioning/commit! gateway-config-yaml))

(defn- last-commit
  [{:keys [gateway-config-yaml]}]
  (Instant/ofEpochMilli (.lastModified (io/as-file gateway-config-yaml))))

(defn commit-component
  [{::keys [uncommitted? versions last-commit current-version]}]
  (if last-commit
    [:div.commit-status
     (when uncommitted?
       [:fieldset [:legend "Pending changes"]
        (form/form
         {:action "/versioning"
          :method "post"}
         "Some changes since deploy at " (html/time last-commit) "."
         [:div.secundary-actions
          [:button {:type    "submit"
                    :name    "commit"
                    :value   "true"
                    :onclick "return confirm('Deploy edits?')"}
           "Deploy changes"]
          [:button {:type    "submit"
                    :name    "reset"
                    :value   "true"
                    :onclick "return confirm('Discard changes?')"}
           "Discard changes"]])])
     (form/form
      {:action     "/versioning"
       :method     "post"
       :data-dirty "never"
       :onsubmit   "return confirm('Reset edits?')"}
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
               (html/time timestamp)
               (when deployed?
                 [:em.deployed "Currently deployed"])])
            versions)
       [:button {:type  "submit"
                 :name  "reset"
                 :value "true"}
        "Reset pending changes"]])]
    [:div.commit-status
     "Configuration error? No value for " ::last-commit "."]))

(defn wrap
  "Middleware to allow reading and writing configuration."
  [app {:keys [gateway-config-yaml] :as config}]
  (fn [{:keys [request-method uri params] :as req}]
    (if (and (= :post request-method)
             (= "/versioning" uri))
      (merge
       (response/redirect "/" :see-other)
       (cond
         (:commit params)
         (do (versioning/commit! gateway-config-yaml)
             {:flash "Deployed changes"})

         (and (:reset params) (or (= "current" (:timestamp params))
                                  (nil? (:timestamp params))))
         (do (versioning/unstage! gateway-config-yaml)
             {:flash "Discarded changes - reset to currently deployed version."})

         (and (:reset params) (:timestamp params))
         (let [timestamp (-> params
                             :timestamp
                             Long/parseLong
                             java.time.Instant/ofEpochMilli)]
           (if (versioning/reset! gateway-config-yaml (:current-version params) timestamp)
             {:flash (str "Discarded changes - reset to version of " (html/human-time timestamp))}
             {:flash "Reset failed!"}))))
      (let [cur (fetch config)
            res (-> req
                    (into cur)
                    (assoc ::last-commit (last-commit config))
                    app)
            new (select-keys res [::state/applications ::state/institutions ::state/access-control-lists])]
        (when (seq new)
          (put new config))
        res))))
