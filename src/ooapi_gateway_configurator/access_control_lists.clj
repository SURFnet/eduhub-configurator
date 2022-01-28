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

(ns ooapi-gateway-configurator.access-control-lists
  (:require [clojure.set :as set]
            [compojure.core :refer [defroutes GET POST routes]]
            [datascript.core :as d]
            [hiccup.util :refer [escape-html]]
            [ooapi-gateway-configurator.form :as form]
            [ooapi-gateway-configurator.html :refer [layout not-found]]
            [ooapi-gateway-configurator.model :as model]
            [ring.util.codec :refer [url-encode]]
            [ring.util.response :refer [redirect]]))

(defn- subtitle [context id] (str "'" id "' " (name context) " ACL"))

(defn- ->params
  "From access-control-list to form params."
  [access-control-list id]
  (if (:access/_app access-control-list)
    {"id"                  (name id)
     ;; map of institution or app id to paths
     "access-control-list" (->> access-control-list
                                ;; Access entity refers to app, so
                                ;; since we start at app we need a
                                ;; reverse lookup, and get _ keys.
                                ;;
                                ;; See also
                                ;; https://docs.datomic.com/on-prem/query/pull.html#reverse-lookup
                                :access/_app
                                (reduce (fn [m {:access/keys [institution paths]}]
                                          (assoc m (:institution/id institution) (set (map :path/spec paths))))
                                        {}))}
    {"id"                  (name id)
     ;; map of institution or app id to paths
     "access-control-list" (->> access-control-list
                                ;; Access entity refers to
                                ;; institution, so since we start at
                                ;; institution we need a reverse
                                ;; lookup and get _ keys.
                                :access/_institution
                                (reduce (fn [m {:access/keys [app paths]}]
                                          (assoc m (:app/id app) (set (map :path/spec paths))))
                                        {}))}))

(defn- form [context {:strs [access-control-list]} api-paths ids]
  (for [id (sort ids)]
    (let [paths (set (get access-control-list id #{}))]
      [:div.member {:id (str "member-" id)}
       [:h3 [:a {:href (str ({:application "/institutions/"
                              :institution "/applications/"} context) id)}
             (escape-html id)]]
       [:div.secondary-actions
        [:input {:type  "submit", :class "secondary"
                 :name  (str "select-all-" id)
                 :value "Select all"}]
        " "
        [:input {:type  "submit", :class "secondary"
                 :name  (str "select-none-" id)
                 :value "Select none"}]]

       (for [path (sort paths)]
         [:label.path
          [:input {:type    "checkbox"
                   :name    (str "access-control-list[" id "][]")
                   :value   path
                   :checked true}]
          (escape-html path)])

       (when-let [unselected-paths (-> api-paths (set/difference paths) (sort) (seq))]
         [:div.unselected
          [:button.secondary.hidden {:type  "button"} "More.."]
          [:div.paths
           (for [path unselected-paths]
             [:label.path
              [:input {:type    "checkbox"
                       :name    (str "access-control-list[" id "][]")
                       :value   path
                       :checked false}]
              (escape-html path)])]])])))

(defn- detail-page
  "Access control list detail hiccup."
  [{:strs [id] :as access-control-list} context api-paths ids & {:keys [scroll-to dirty]}]
  [:div.detail
   [:nav
    [:a {:href "/"} "⌂"]
    " / "
    [:a {:href "../", :class context}
     ({:application "Applications"
       :institution "Institutions"} context)]
    " / "
    [:a {:href (str "../" (url-encode id)), :class context}
     [:q (escape-html id)]]
    " / "
    [:a.current "Access Control List"]]

   [:h2 "Edit Access Control List"]
   (form/form
    (cond-> {:method "post"}
      dirty (assoc :data-dirty "true"))
    [:input.hidden {:type "submit"}] ;; ensure enter key submits

    (into [:div] (form context access-control-list api-paths ids))

    [:div.actions
     [:button {:type "submit", :class "primary"} "Update"]
     " "
     [:a {:href (str "../" (url-encode id)), :class "button"} "Cancel"]])

   [:script {:src "/access_control_lists.js", :data-scroll-to scroll-to}]])

(defn- do-update
  "Handle update request."
  [{:keys        [params model ::context]
    {:keys [id]} :params
    :as          req}]
  (let [api-paths           (model/api-paths model)
        select-all          (->> (dissoc params :id)
                                 keys
                                 (keep #(last (re-find #"select-all-(.*)" %)))
                                 first)
        select-none         (->> (dissoc params :id)
                                 keys
                                 (keep #(last (re-find #"select-none-(.*)" %)))
                                 first)
        ids (case context
              :application (model/institution-ids model)
              :institution (model/app-ids model))]
    (cond
      select-all
      (-> params
          (assoc "id" id)
          (assoc-in ["access-control-list" select-all] api-paths)
          (detail-page context api-paths ids :scroll-to (str "member-" select-all) :dirty true)
          (layout req (subtitle context id)))

      select-none
      (-> params
          (assoc "id" id)
          (assoc-in ["access-control-list" select-none] #{})
          (detail-page context api-paths ids :scroll-to (str "member-" select-none) :dirty true)
          (layout req (subtitle context id)))

      :else
      (-> (str "../" (url-encode id))
          (redirect :see-other)
          (assoc ::model/tx (case context
                              :application
                              (mapcat (fn [institution]
                                        (model/set-paths model
                                                         :app-id id :institution-id institution
                                                         :paths (get-in params ["access-control-list" institution])))
                                      (model/institution-ids model))

                              :institution
                              (mapcat (fn [app]
                                        (model/set-paths model
                                                         :app-id app :institution-id id
                                                         :paths (get-in params ["access-control-list" app])))
                                      (model/app-ids model))))
          (assoc :flash (str "Updated access-control-list for " (name context) " '" id "'"))))))

(defroutes applications-handler
  (GET "/applications/:id/access-control-list" {:keys        [::context model]
                                                {:keys [id]} :params
                                                :as          req}
    (if-let [app (d/pull model
                         ;; We explicitly pull in :app/id, otherwise
                         ;; if no access entities are available we get
                         ;; no attributes at all, and pull returns
                         ;; nil.
                         '[:app/id
                           ;; Member :access/_app is a reverse
                           ;; lookup. See also
                           ;; https://docs.datomic.com/on-prem/query/pull.html#reverse-lookup
                           {:access/_app [{:access/institution [:institution/id]
                                           :access/paths [:path/spec]}]}]
                         [:app/id id])]
      (-> app
          (->params id)
          (detail-page context (model/api-paths model) (model/institution-ids model))
          (layout req (subtitle context id)))
      (not-found (str "Application '" id "' not found..")
                 req)))

  (POST "/applications/:id/access-control-list" {:keys        [model]
                                                 {:keys [id]} :params
                                                 :as          req}
    (if (d/entid model [:app/id id])
      (do-update req)
      (not-found (str "Application '" id "' not found..")
                 req))))

(defroutes institutions-handler
  (GET "/institutions/:id/access-control-list" {:keys        [::context model]
                                                {:keys [id]} :params
                                                :as          req}
    (if-let [institution
             (d/pull model
                     ;; We explicitly pull in :institution/id,
                     ;; otherwise if no access entities are available
                     ;; we get no attributes at all, and pull returns
                     ;; nil.
                     '[:institution/id
                       ;; Member :access/_institution is a reverse
                       ;; lookup. See also
                       ;; https://docs.datomic.com/on-prem/query/pull.html#reverse-lookup
                       {:access/_institution [{:access/app [:app/id]}
                                              {:access/paths [:path/spec]}]}]
                     [:institution/id id])]
      (-> institution
          (->params id)
          (detail-page context (model/api-paths model) (model/app-ids model))
          (layout req (subtitle context id)))
      (not-found (str "Institution '" id "' not found..")
                 req)))

  (POST "/institutions/:id/access-control-list" {:keys        [model]
                                                 {:keys [id]} :params
                                                 :as          req}
    (if (d/entid model [:institution/id id])
      (do-update req)
      (not-found (str "Institution '" id "' not found..")
                 req))))

(def handler
  (routes
   (fn [req]
     (applications-handler (assoc req ::context :application)))
   (fn [req]
     (institutions-handler (assoc req ::context :institution)))))
