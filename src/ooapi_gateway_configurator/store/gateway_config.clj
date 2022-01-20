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

(ns ooapi-gateway-configurator.store.gateway-config
  (:require [clojure.string :as string]
            [datascript.core :as d]
            [ooapi-gateway-configurator.store.klist :as klist]))

(defn yaml->model
  "Parse the gateway config `yaml` as a collection of entities.

  The returned entity collection can be directly transacted into the
  model database."
  [{:keys [serviceEndpoints pipelines] :as config} pipeline]
  (let [{:keys [policies apiEndpoints]} (get pipelines (keyword pipeline))
        api                             (-> apiEndpoints first keyword)]
    (-> []
        (into (map (fn [p]
                     {:path/spec p})
                   (-> config :apiEndpoints api :paths)))
        (into (map (fn [[n {:keys [passwordSalt passwordHash]}]]
                     {:app/id (name n) ;; name is a keyword when read from the yaml
                      :app/password-salt passwordSalt
                      :app/password-hash passwordHash})
                   (klist/get-in policies [:gatekeeper :action :apps])))
        (into (map (fn [[n {:keys [proxyOptions url notes]}]]
                     (cond-> {:institution/id (name n) ;; name is a keyword when read from the yaml
                              :institution/url url}
                       ;; datomic/datascript do not allow nil values
                       (seq notes) (assoc :institution/notes notes)
                       (some? proxyOptions) (assoc :institution/proxy-options proxyOptions)))
                   serviceEndpoints))
        (into (mapcat (fn [{:keys [app endpoints]}]
                        (map (fn [{:keys [endpoint paths]}]
                               {:access/app [:app/id app]
                                :access/institution [:institution/id endpoint]
                                :access/paths (map (fn [path] [:path/spec path])
                                                   paths)})
                             endpoints))
                      (klist/get-in policies [:gatekeeper :action :acls]))))))

(defn- ->acl
  "Create app + endpoints ACL map.

  Skips endpoints without paths. If endpoints is empty returns nil."
  [{app :app/id
    access :access/_app}]
  (when-let [endpoints (->> access
                            (keep (fn ->acl-endpoint [xs]
                                    (when (seq (:access/paths xs))
                                      {:endpoint (:institution/id (:access/institution xs))
                                       :paths    (map :path/spec (:access/paths xs))})))
                            seq)]
    {:app app
     :endpoints endpoints}))

(defn model->yaml
  "Update the gateway config `yaml-contents` with configuration from `model`."
  [model yaml-contents pipeline]
  (-> yaml-contents
      (assoc :serviceEndpoints
             (reduce (fn ->endpoint [res [{:institution/keys [id url notes proxy-options]}]]
                       (assert (not (string/blank? id)))
                       (assoc res id (cond-> {:url          url
                                              :proxyOptions proxy-options}
                                       (seq notes)
                                       (assoc :notes notes))))
                     {}
                     (d/q '[:find (pull ?e [*]) :where [?e :institution/id _]] model)))

      (klist/update-in [:pipelines (keyword pipeline) :policies :gatekeeper]
                       klist/assoc-in [:action :acls]
                       (->> (d/q '[:find (pull ?a [:app/id
                                                   {:access/_app [{:access/paths [:path/spec]}
                                                                  {:access/institution [:institution/id]}]}])
                                   :where [?a :app/id _]] model)
                            (map first) ;; results are tuples of one entity
                            (sort-by :app/id)
                            (keep ->acl)))

      (klist/update-in [:pipelines (keyword pipeline) :policies :gatekeeper]
                       klist/assoc-in [:action :apps]
                       (reduce (fn ->app [res [{:app/keys [id password-hash password-salt]}]]
                                 (assoc res id {:passwordHash password-hash
                                                :passwordSalt password-salt}))
                               {}
                               (d/q '[:find (pull ?a [*]) :where [?a :app/id _]] model)))))
