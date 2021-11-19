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

(ns ooapi-gateway-configurator.applications
  (:require [clojure.string :as s]
            [compojure.core :refer [defroutes DELETE GET POST]]
            [compojure.response :refer [render]]
            [hiccup.util :refer [escape-html]]
            [ooapi-gateway-configurator.digest :as digest]
            [ooapi-gateway-configurator.form :as form]
            [ooapi-gateway-configurator.html :refer [confirm-js layout not-found]]
            [ooapi-gateway-configurator.http :as http]
            [ooapi-gateway-configurator.state :as state]
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

(defn- ->form
  "Application to form."
  [{:strs [passwordSalt passwordHash]} id]
  {"id"            (name id)
   "password-salt" passwordSalt
   "password-hash" passwordHash})

(defn- form->
  "Form to application."
  [{:strs [id password]}]
  (into {:id id}
        (when password
          (let [salt (generate-random-string)]
            {:passwordSalt salt
             :passwordHash (hash-password password salt)}))))

(def id-pattern-re #"[a-zA-Z0-9_:-]*")
(def id-pattern-message "only a-z, A-Z, 0-9, _, : and - characters allowed")

(defn- form-errors
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
  [applications]
  [:div.index
   [:nav
    [:a {:href "/"} "⌂"]
    " / "
    [:a.current "Applications"]]
   [:ul
    (for [id (->> applications (map #(get % "id")) (sort))]
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
    [:input {:type "submit", :style "display: none"}] ;; ensure enter key submits
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
       [:button {:type    "submit"
                 :onclick (confirm-js :delete "application" orig-id)}
        "Delete"])])])

(defn- subtitle [id]
  (if id
    (str "'" id "' application")
    "new application"))

(defn- create-or-update
  "Handle create or update request."
  [{:keys                       [params ::state/applications]
    {:keys [orig-id]}           :params
    {:strs [id reset-password]} :params
    :as                         req}]
  (let [subtitle (subtitle orig-id)
        errors   (form-errors params)]
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

      (and (not= id orig-id) (contains? applications id))
      (-> params
          (detail-page orig-id :dirty true)
          (layout (assoc req :flash (str "ID already taken; " id)) subtitle))

      :else
      (let [application (form-> params)]
        (-> "."
            (redirect :see-other)
            (assoc ::state/command (if orig-id
                                     [::state/update-application orig-id application]
                                     [::state/create-application application]))
            (assoc :flash (str (if orig-id "Updated" "Created") " application '" id "'")))))))

(defroutes handler
  (GET "/applications/" {:keys [::state/applications] :as req}
       (-> (map (fn [[id m]] (->form m id)) applications)
           (index-page)
           (layout req "applications")))

  (GET "/applications/new" req
       (-> {}
           (detail-page nil)
           (layout req (subtitle nil))))

  (POST "/applications/new" req
        (create-or-update req))

  (GET "/applications/:orig-id" {:keys             [::state/applications]
                                 {:keys [orig-id]} :params
                                 :as               req}
       (if-let [application (get applications orig-id)]
         (-> application
             (->form orig-id)
             (detail-page orig-id)
             (layout req (subtitle orig-id)))
         (not-found (str "Application '" orig-id "' not found..")
                    req)))

  (POST "/applications/:orig-id" {:keys             [::state/applications]
                                  {:keys [orig-id]} :params
                                  :as               req}
        (if (get applications orig-id)
          (create-or-update req)
          (not-found (str "Application '" orig-id "' not found..")
                     req)))

  (DELETE "/applications/:id" {:keys        [::state/applications]
                               {:keys [id]} :params
                               :as          req}
          (if (get applications id)
            (-> "."
                (redirect :see-other)
                (assoc ::state/command [::state/delete-application id])
                (assoc :flash (str "Deleted application '" id "'")))
            (not-found (str "Application '" id "' not found..")
                       req))))
