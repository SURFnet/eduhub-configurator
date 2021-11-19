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

(ns ooapi-gateway-configurator.institutions
  (:require [clojure.string :as s]
            [compojure.core :refer [defroutes DELETE GET POST]]
            [compojure.response :refer [render]]
            [hiccup.util :refer [escape-html]]
            [ooapi-gateway-configurator.form :as form]
            [ooapi-gateway-configurator.html :refer [confirm-js layout not-found]]
            [ooapi-gateway-configurator.http :as http]
            [ooapi-gateway-configurator.state :as state]
            [ring.util.codec :refer [url-encode]]
            [ring.util.response :refer [redirect status]]))

(defn- as-str [v]
  (if (keyword? v)
    (name v)
    (str v)))

(defn- ->form
  "Transform an institution into form parameters."
  [{:keys            [url]
    {:keys [auth
            headers
            oauth2]} :proxyOptions} orig-id]
  (cond-> {"id"            orig-id
           "url"           url
           "auth"          (cond auth   "basic"
                                 oauth2 "oauth")
           "header-names"  (map as-str (keys headers)) ;; yaml parse may keyword keys
           "header-values" (vals headers)}
    auth   (assoc "basic-auth-user" (first (s/split auth #":" 2))
                  "basic-auth-pass" (last (s/split auth #":" 2)))
    oauth2 (assoc "oauth-url" (-> oauth2 :clientCredentials :tokenEndpoint :url)
                  "oauth-client-id" (-> oauth2 :clientCredentials :tokenEndpoint :params :client_id)
                  "oauth-client-secret" (-> oauth2 :clientCredentials :tokenEndpoint :params :client_secret)
                  "oauth-scope"  (-> oauth2 :clientCredentials :tokenEndpoint :params :scope))))

(defn- form->
  "Transform form parameters into an institution."
  [{:strs [id
           url
           auth
           basic-auth-user basic-auth-pass
           oauth-url oauth-client-id oauth-client-secret oauth-scope
           header-names header-values]}]
  (let [opts (cond-> {}
               (= auth "basic")
               (assoc :auth
                      (str basic-auth-user ":" basic-auth-pass))

               (= auth "oauth")
               (assoc :oauth2
                      {:clientCredentials
                       {:tokenEndpoint
                        {:url    oauth-url
                         :params (cond->
                                     {:grant_type    "client_credentials"
                                      :client_id     oauth-client-id
                                      :client_secret oauth-client-secret}
                                   (not (s/blank? oauth-scope))
                                   (assoc :scope oauth-scope))}}})

               (seq header-names)
               (assoc :headers
                      (-> header-names
                          (zipmap header-values)
                          (dissoc ""))))]
    (cond-> {:id id, :url url}
      (seq opts)
      (assoc :proxyOptions opts))))

(defn- valid-http-url? [s]
  (try
    (let [url (java.net.URL. s)]
      (contains? #{"http" "https"} (.getProtocol url)))
    (catch Exception _ false)))

(def id-pattern-re #"[a-zA-Z0-9.-]*")
(def id-pattern-message "only a-z, A-Z, 0-9, . and - characters allowed")

(defn- form-errors
  [{:strs [id url auth
           header-names header-values
           basic-auth-user basic-auth-pass
           oauth-url oauth-client-id oauth-client-secret]}]
  (cond-> []
    (s/blank? id)
    (conj "ID can not be blank")

    (= "new" id)
    (conj "ID can not be 'new' (reserved word)")

    (and id (not (re-matches id-pattern-re id)))
    (conj (str "ID pattern does not match: " id-pattern-message))

    (s/blank? url)
    (conj "URL can not be blank")

    (and (not (s/blank? url)) (not (valid-http-url? url)))
    (conj "URL not a HTTP URL")

    (and (= "basic" auth) (s/blank? basic-auth-user))
    (conj "Basic Authentication User can not be blank")

    (and (= "basic" auth) (s/blank? basic-auth-pass))
    (conj "Basic Authentication Password can not be blank")

    (and (= "basic" auth) basic-auth-user (re-find #":" basic-auth-user))
    (conj "Basic Authentication User can not contain ':'")

    (and (= "oauth" auth) (s/blank? oauth-url))
    (conj "Oauth2 Token Endpoint URL can not be empty")

    (and (= "oauth" auth) (not (s/blank? oauth-url)) (not (valid-http-url? oauth-url)))
    (conj "Oauth2 Token Endpoint URL not a HTTP URL")

    (and (= "oauth" auth) (s/blank? oauth-client-id))
    (conj "Oauth2 Client ID can not be empty")

    (and (= "oauth" auth) (s/blank? oauth-client-secret))
    (conj "Oauth2 Client Secret can not be empty")

    (some identity (map #(and (s/blank? %1) (not (s/blank? %2)))
                        header-names header-values))
    (conj "Header missing name")

    (some identity (map #(and (not (s/blank? %1)) (s/blank? %2))
                        header-names header-values))
    (conj "Header missing value")

    (not= (count header-names) (count (set header-names)))
    (conj "Header name already taken")

    :finally seq))

(defn- form
  "Form hiccup for institution params."
  [{:strs [id url auth
           header-names header-values
           basic-auth-user basic-auth-pass
           oauth-url oauth-client-id oauth-client-secret oauth-scope]}]
  [[:div.field
    [:label {:for "id"} "ID "
     [:span.info (escape-html id-pattern-message)]]
    [:input {:type "text", :pattern id-pattern-re, :required true
             :id   "id",   :name    "id",          :value    id}]]

   [:div.field
    [:label {:for "url"} "URL"]
    [:input {:type "url", :pattern "https?://.*", :required true
             :id   "url", :name    "url",         :value    url}]]

   [:div.field
    [:label "Headers"]
    [:ul.headers
     (for [[name value i] (map vector header-names header-values (iterate inc 0))]
       [:li {:class "header"}
        [:input {:name "header-names[]", :value name, :placeholder "Name"}]
        ": "
        [:input {:name "header-values[]", :value value, :placeholder "Value"}]
        [:input {:type  "submit", :name  (str "delete-header-" i),
                 :value "🗑",      :title "Delete header", :class "secondary delete-header"}]])
     [:li [:input {:type "submit", :class "secondary", :name "add-header", :value "Add header"}]]]]

   [:div.field
    [:label {:for "auth"} "Authentication"]
    [:select {:id "auth", :name "auth"}
     (for [v ["" "basic" "oauth"]]
       [:option (cond-> {:value v}
                  (= v auth) (assoc :selected true))
        ({""      "Other"
          "basic" "Basic Authentication"
          "oauth" "OAuth2 Client Credentials"} v)])]
    [:input {:type "submit", :class "secondary", :name "select-auth", :value "Select method"}]]

   (cond
     (= "basic" auth)
     [:div
      [:div.field
       [:label {:for "basis-user"} "Basic Authentication User"]
       [:input {:type "text",            :pattern "[^:]*"
                :name "basic-auth-user", :value   basic-auth-user}]]

      [:div.field
       [:label {:for "basis-pass"} "Basic Authentication Password"]
       [:input {:type "password",        :autocomplete "new-password"
                :name "basic-auth-pass", :value        basic-auth-pass}]]]

     (= "oauth" auth)
     [:div
      [:div.field
       [:label {:for "oauth-url"} "Token Endpoint URL"]
       [:input {:type "url",       :pattern "https?://.*"
                :name "oauth-url", :value   oauth-url}]]

      [:div.field
       [:label {:for "oauth-grant-type"} "Grant Type"]
       [:input {:type "text",             :disabled true
                :name "oauth-grant-type", :value    "client_credentials"}]]

      [:div.field
       [:label {:for "oauth-client-id"} "Client ID"]
       [:input {:type "text", :name "oauth-client-id", :value oauth-client-id}]]

      [:div.field
       [:label {:for "oauth-client-secret"} "Client Secret"]
       [:input {:type "password",            :autocomplete "new-password"
                :name "oauth-client-secret", :value        oauth-client-secret}]]

      [:div.field
       [:label {:for "oauth-scope"} "Scope"]
       [:input {:type "text"
                :name "oauth-scope", :value oauth-scope}]]])])

(defn- index-page
  "List of institution hiccup."
  [institutions]
  [:div.index
   [:nav
    [:a {:href "/"} "⌂"]
    " / "
    [:a.current "Institutions"]]
   [:ul
    (for [id (->> institutions (map #(get % "id")) (sort))]
      [:li [:a {:href (url-encode id)} (escape-html id)]])]
   [:div.actions
    [:a {:href :new, :class "button"} "New institution"]]])

(defn- detail-page
  "Institution detail hiccup."
  [institution orig-id & {:keys [dirty]}]
  [:div.detail
   (into
    [:nav
     [:a {:href "/"} "⌂"]
     " / "
     [:a {:href "./"} "Institutions"]
     " / "]
    (if orig-id
      [[:a.current [:q (escape-html orig-id)]]
       " / "
       [:a {:href (str orig-id "/access-control-list")}
        "Access Control List"]]
      [[:a.current "New institution"]]))

   (if orig-id
     [:h2 "Edit Institution"]
     [:h2 "Create Institution"])

   (form/form
    (cond-> {:method "post"}
      dirty (assoc :data-dirty "true"))
    [:input {:type "submit", :style "display: none"}] ;; ensure enter key submits
    (into [:div] (form institution))

    [:div.actions
     [:button {:type "submit", :class "primary"} (if orig-id "Update" "Create")]
     " "
     [:a {:href ".", :class "button"} "Cancel"]])

   [:div.bottom-actions
    (form/form
     {:method "delete" :class "delete"}
     [:button {:type    "submit"
               :onclick (confirm-js :delete "institution" orig-id)}
      "Delete"])]])

(defn- delete-header-fn-from-params
  "Find parameter named \"delete-header-X\" were X is a number and
  return a function to delete element at X."
  [params]
  (when-let [k (->> params keys (map name) (filter #(re-matches #"delete-header-\d+" %)) first)]
    (let [n (Integer/parseInt (re-find #"\d+" k))]
      (fn [coll]
        (concat (take n coll) (drop (inc n) coll))))))

(defn- subtitle [id]
  (if id
    (str "'" id "' institution")
    "new institution"))

(defn- create-or-update
  "Handle create or update request."
  [{:keys                               [params ::state/institutions]
    {:keys [orig-id]}                   :params
    {:strs [id add-header select-auth]} :params
    :as                                 req}]
  (let [subtitle         (subtitle orig-id)
        errors           (form-errors params)
        delete-header-fn (delete-header-fn-from-params params)]
    (cond
      add-header
      (-> params
          (update "header-names" conj "")
          (update "header-values" conj "")
          (detail-page orig-id :dirty true)
          (layout req subtitle))

      delete-header-fn
      (-> params
          (update "header-names" delete-header-fn)
          (update "header-values" delete-header-fn)
          (detail-page orig-id :dirty true)
          (layout req subtitle))

      select-auth
      (-> params
          (detail-page orig-id :dirty true)
          (layout req subtitle))

      errors
      (-> params
          (detail-page orig-id :dirty true)
          (layout (assoc req :flash (str "Invalid input;\n" (s/join ",\n" errors))) subtitle)
          (render req)
          (status http/not-acceptable))

      (and (not= id orig-id) (contains? institutions id))
      (-> params
          (detail-page orig-id :dirty true)
          (layout (assoc req :flash (str "ID already taken; " id)) subtitle))

      :else
      (let [institution (form-> params)]
        (-> "."
            (redirect :see-other)
            (assoc ::state/command (if orig-id
                                     [::state/update-institution orig-id institution]
                                     [::state/create-institution institution]))
            (assoc :flash (str (if orig-id "Updated" "Created") " institution '" id "'")))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defroutes handler
  (GET "/institutions/" {:keys [::state/institutions] :as req}
       (-> (map (fn [[id m]] (->form m id)) institutions)
           (index-page)
           (layout req "institutions")))

  (GET "/institutions/new" req
       (-> {}
           (detail-page nil)
           (layout req (subtitle nil))))

  (POST "/institutions/new" req
        (create-or-update req))

  (GET "/institutions/:orig-id" {:keys             [::state/institutions]
                                 {:keys [orig-id]} :params
                                 :as               req}
       (if-let [institution (get institutions orig-id)]
         (-> institution
             (->form orig-id)
             (detail-page orig-id)
             (layout req (subtitle orig-id)))
         (not-found (str "Institution '" orig-id "' not found..")
                    req)))

  (POST "/institutions/:orig-id" {:keys             [::state/institutions]
                                  {:keys [orig-id]} :params
                                  :as               req}
        (if (get institutions orig-id)
          (create-or-update req)
          (not-found (str "Institution '" orig-id "' not found..")
                     req)))

  (DELETE "/institutions/:id" {:keys        [::state/institutions]
                               {:keys [id]} :params
                               :as          req}
          (if (get institutions id)
            (-> "."
                (redirect :see-other)
                (assoc ::state/command [::state/delete-institution id])
                (assoc :flash (str "Deleted institution '" id "'")))
            (not-found (str "Institution '" id "' not found..")
                       req))))
