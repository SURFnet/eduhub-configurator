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

(ns ooapi-gateway-configurator.applications
  (:require [clojure.string :as s]
            [compojure.core :refer [defroutes DELETE GET POST]]
            [compojure.response :refer [render]]
            [datascript.core :as d]
            [hiccup.util :refer [escape-html]]
            [ooapi-gateway-configurator.digest :as digest]
            [ooapi-gateway-configurator.form :as form]
            [ooapi-gateway-configurator.html :refer [layout not-found]]
            [ooapi-gateway-configurator.http :as http]
            [ooapi-gateway-configurator.model :as model]
            [ring.util.codec :refer [url-encode]]
            [ring.util.response :refer [redirect status]]))

(defn- hex
  "Transform byte array into a hex string."
  [bs]
  (s/join (map #(format "%02x" %) bs)))

(defn- generate-random-string
  "Generate a 32 character long random (hex) string."
  []
  (hex (repeatedly 16 (partial rand-int 255))))

(defn- hash-password
  "Hash given password with salt using SHA-256.  Both pass and salt
  should be 32 character strings."
  [pass salt]
  {:pre [(= 32 (count pass))
         (= 32 (count salt))]}
  (-> (str pass "-" salt) digest/sha256 hex))

(defn- ->params
  "Application model to form params."
  [{:app/keys [password-salt password-hash id]}]
  {"id"            id
   "password-salt" password-salt
   "password-hash" password-hash})

(defn- params->
  "Form params to application model."
  [{:strs [id password]}]
  (into #:app {:id id}
        (when password
          (let [salt (generate-random-string)]
            #:app {:password-salt salt
                   :password-hash (hash-password password salt)}))))

(def id-pattern-re #"[a-zA-Z0-9_:-]*")
(def id-pattern-message "only a-z, A-Z, 0-9, _, : and - characters allowed")

(defn- params-errors
  [{:strs [id password]}]
  (cond-> []
    (s/blank? id)
    (conj "ID can not be blank")

    (= "new" id)
    (conj "ID can not be 'new' (reserved word)")

    (and id (not (re-matches id-pattern-re id)))
    (conj (str "ID pattern does not match: " id-pattern-message))

    (and password (not= 32 (count password)))
    (conj "Password must be 32 characters long")

    :finally seq))

(defn- form [{:strs [id reset-password]} orig-id]
  (let [show-password (or (not orig-id) reset-password)]
    [[:div.field
      [:label {:for "id"} "ID "
       [:span.info (escape-html id-pattern-message)]]
      [:input {:type  "text", :pattern id-pattern-re, :required true
               :id    "id",   :name    "id"
               :value id}]]
     [:div.field
      [:label {:for "password"} "Password "
       (when show-password
         [:span.info "only visible now, a hash will be stored"])]
      (if show-password
        [:input {:type  "text",     :readonly true
                 :id    "password", :name     "password"
                 :value (generate-random-string)}]
        [:input {:type "submit",         :class "secondary"
                 :name "reset-password", :value "Reset password"}])]]))

(defn- index-page
  "List of applications hiccup."
  [application-ids]
  [:div.index
   [:nav
    [:a {:href "/"} "⌂"]
    " / "
    [:a.current "Applications"]]
   [:ul
    (for [id (sort application-ids)]
      [:li [:a {:href (url-encode id)} (escape-html id)]])]
   [:div.actions
    [:a {:href :new, :class "button"} "New application"]]])

(defn- detail-page
  "Application detail hiccup."
  [application orig-id & {:strs [dirty]}]
  [:div.detail
   (into
    [:nav
     [:a {:href "/"} "⌂"]
     " / "
     [:a {:href "./"} "Applications"]
     " / "]
    (if orig-id
      [[:a.current [:q (escape-html orig-id)]]
       " / "
       [:a {:href (str orig-id "/access-control-list")}
        "Access Control List"]]
      [[:a.current "New application"]]))

   (if orig-id
     [:h2 "Edit Application"]
     [:h2 "Create Application"])

   (form/form
    (cond-> {:method "post"}
      dirty (assoc :data-dirty "true"))
    [:input.hidden {:type "submit"}] ;; ensure enter key submits
    (into [:div] (form application orig-id))

    [:div.actions
     [:button {:type "submit", :class "primary"} (if orig-id "Update" "Create")]
     " "
     [:a {:href ".", :class "button"} "Cancel"]])

   (when orig-id
     [:div.bottom-actions
      (form/form
       {:method "delete"
        :class  "delete"}
       [:button {:type                 "submit"
                 :data-confirm-event   "click"
                 :data-confirm-message (str "Really delete application '" orig-id "'?")}
        "Delete"])])])

(defn- subtitle [id]
  (if id
    (str "'" id "' application")
    "new application"))

(defn- create-or-update
  "Handle create or update request."
  [{:keys                       [model params]
    {:keys [orig-id]}           :params
    {:strs [id reset-password]} :params
    :as                         req}]
  (let [subtitle (subtitle orig-id)
        errors   (params-errors params)]
    (cond
      reset-password
      (-> params
          (assoc :reset-password true)
          (detail-page orig-id :dirty true)
          (layout req subtitle))

      errors
      (-> params
          (detail-page orig-id :dirty true)
          (layout (assoc req :flash (str "Invalid input;\n" (s/join ",\n" errors))) subtitle)
          (render req)
          (status http/not-acceptable))

      (and (not= id orig-id) (d/entid model [:app/id id]))
      (-> params
          (detail-page orig-id :dirty true)
          (layout (assoc req :flash (str "ID already taken; " id)) subtitle))

      :else
      (let [application (params-> params)]
        (-> "."
            (redirect :see-other)
            ;; Also return a transaction to update or insert the
            ;; application. Note that a transaction is a collection of
            ;; changes.
            ;;
            ;; See also
            ;; https://docs.datomic.com/on-prem/transactions/transactions.html
            (assoc ::model/tx (cond-> []
                                (and orig-id (not= orig-id (:app/id application)))
                                ;; The app/id changed, we need to
                                ;; transact that attribute first. This
                                ;; transaction finds the attribute by
                                ;; it's original id, and set its value
                                ;; to the new id.
                                (conj [:db/add [:app/id orig-id] :app/id (:app/id application)])

                                true
                                ;; Update the rest of the attributes;
                                ;; uses an entity map as a
                                ;; change. This adds all attributes in
                                ;; the map. The entity is found by the
                                ;; app/id attribute.
                                ;;
                                ;; See also
                                ;; https://docs.datomic.com/on-prem/transactions/transactions.html#adding-entity-references
                                (conj application)))
            (assoc :flash (str (if orig-id "Updated" "Created") " application '" id "'")))))))

(defroutes handler
  (GET "/applications/" {:keys [model] :as req}
    (-> model
        (model/app-ids)
        (index-page)
        (layout req "applications")))

  (GET "/applications/new" req
    (-> {}
        (detail-page nil)
        (layout req (subtitle nil))))

  (POST "/applications/new" req
    (create-or-update req))

  (GET "/applications/:orig-id" {:keys             [model]
                                 {:keys [orig-id]} :params
                                 :as               req}
    (if-let [application (d/pull model '[*] [:app/id orig-id])]
      (-> application
          (->params)
          (detail-page orig-id)
          (layout req (subtitle orig-id)))
      (not-found (str "Application '" orig-id "' not found..")
                 req)))

  (POST "/applications/:orig-id" {:keys             [model]
                                  {:keys [orig-id]} :params
                                  :as               req}
    (if (d/entid model [:app/id orig-id])
      (create-or-update req)
      (not-found (str "Application '" orig-id "' not found..")
                 req)))

  (DELETE "/applications/:id" {:keys        [model]
                               {:keys [id]} :params
                               :as          req}
    (if (d/entid model [:app/id id])
      (-> "."
          (redirect :see-other)
          (assoc ::model/tx (model/remove-app model id))
          (assoc :flash (str "Deleted application '" id "'")))
      (not-found (str "Application '" id "' not found..")
                 req))))
