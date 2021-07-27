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
  "Hash given password with salt using SHA-256."
  [pass salt]
  (-> (str pass "-" salt) digest/sha256 hex))

(defn- ->form
  [{:keys [passwordSalt passwordHash]} id]
  {:id            (name id)
   :orig-id       (name id)
   :password-salt passwordSalt
   :password-hash passwordHash})

(defn- form->
  [{:keys [id password]}]
  (into {:id id}
        (when password
          (let [salt (generate-random-string)]
            {:passwordSalt salt
             :passwordHash (hash-password password salt)}))))

(defn- form-errors
  [{:keys [id]}]
  (cond-> []
    (s/blank? id)
    (conj "ID can not be blank")

    (= "new" id)
    (conj "ID can not be 'new' (reserved word)")

    (and id (not (re-matches #"[a-zA-Z0-8_:-]*" id)))
    (conj "ID can only contain letters, digits, _, : or -.")

    :finally seq))

(defn- form [{:keys [id orig-id reset-password]}]
  (let [show-password (or (not orig-id) reset-password)]
    [[:div.field
      [:label {:for "id"} "ID "
       [:span.info "only letters, digits, _, : or -"]]
      [:input {:type  "text", :pattern "[a-zA-Z0-8_:-]*", :required true
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
    [:a "Applications"]]
   [:ul
    (for [id (->> applications (map :id) (sort))]
      [:li [:a {:href (url-encode id)} (escape-html id)]])]
   [:a {:href :new, :class "button"} "New application"]])

(defn- detail-page
  "Application detail hiccup."
  [{:keys [orig-id] :as application} & {:keys [dirty]}]
  [:div.detail
   (into
    [:nav
     [:a {:href "/"} "⌂"]
     " / "
     [:a {:href "./"} "Applications"]
     " / "]
    (if orig-id
      [[:a.application (escape-html orig-id)]
       " / "
       [:a.access-control-list {:href (str orig-id "/access-control-list")}
        "Access Control List"]]
      [[:a.application "New application"]]))

   (if orig-id
     [:h2 "Edit Application"]
     [:h2 "Create Application"])

   (form/form
    (cond-> {:method "post"}
      dirty (assoc :data-dirty "true"))
    [:input {:type "submit", :style "display: none"}] ;; ensure enter key submits
    (into [:div] (form application))

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
  [{:keys                    [params ::state/applications]
    {:keys [id orig-id
            reset-password]} :params
    :as                      req}]
  (let [subtitle (subtitle orig-id)
        errors   (form-errors params)]
    (cond
      reset-password
      (-> params
          (assoc :reset-password true)
          (detail-page :dirty true)
          (layout req subtitle))

      errors
      (-> params
          (detail-page :dirty true)
          (layout (assoc req :flash (str "Invalid input;\n" (s/join ",\n" errors))) subtitle)
          (render req)
          (status http/not-acceptable))

      (and (not= id orig-id) (contains? applications (keyword id)))
      (-> params
          (detail-page :dirty true)
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
           (detail-page)
           (layout req (subtitle nil))))

  (POST "/applications/new" req
        (create-or-update req))

  (GET "/applications/:id" {:keys        [::state/applications]
                            {:keys [id]} :params
                            :as          req}
       (if-let [application (get applications (keyword id))]
         (-> application
             (->form id)
             (detail-page)
             (layout req (subtitle id)))
         (not-found (str "Application '" id "' not found..")
                    req)))

  (POST "/applications/:orig-id" {:keys             [::state/applications]
                                  {:keys [orig-id]} :params
                                  :as               req}
        (if (get applications (keyword orig-id))
          (create-or-update req)
          (not-found (str "Application '" orig-id "' not found..")
                     req)))

  (DELETE "/applications/:id" {:keys        [::state/applications]
                               {:keys [id]} :params
                               :as          req}
          (if (get applications (keyword id))
            (-> "."
                (redirect :see-other)
                (assoc ::state/command [::state/delete-application id])
                (assoc :flash (str "Deleted application '" id "'")))
            (not-found (str "Application '" id "' not found..")
                       req))))
