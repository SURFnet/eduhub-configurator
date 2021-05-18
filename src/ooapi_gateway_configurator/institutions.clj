(ns ooapi-gateway-configurator.institutions
  (:require [clj-yaml.core :as yaml]
            [clojure.string :as s]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.response :refer [render]]
            [ooapi-gateway-configurator.html :refer [confirm-js layout]]
            [ooapi-gateway-configurator.anti-forgery :refer [anti-forgery-field]]
            [ooapi-gateway-configurator.http :as http]
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
            oauth2]} :proxyOptions} id]
  (cond-> {:id            (name id)
           :orig-id       (name id)
           :url           url
           :auth          (cond auth   "basic"
                                oauth2 "oauth")
           :header-names  (map as-str (keys headers)) ;; yaml parse may keyword keys
           :header-values (vals headers)}
    auth   (assoc :basic-auth-user (first (s/split auth #":" 2))
                  :basic-auth-pass (last (s/split auth #":" 2)))
    oauth2 (assoc :oauth-url (-> oauth2 :clientCredentials :tokenEndpoint :url)
                  :oauth-client-id (-> oauth2 :clientCredentials :tokenEndpoint :params :client_id)
                  :oauth-client-secret (-> oauth2 :clientCredentials :tokenEndpoint :params :client_secret)
                  :oauth-scope  (-> oauth2 :clientCredentials :tokenEndpoint :params :scope))))

(defn- form->
  "Transform form parameters into an institution."
  [{:keys [url
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
    (cond-> {:url url}
      (seq opts)
      (assoc :proxyOptions opts))))

(defn- valid-http-url? [s]
  (try
    (let [url (java.net.URL. s)]
      (contains? #{"http" "https"} (.getProtocol url)))
    (catch Exception _ false)))

(defn- form-errors
  [{:keys [id url auth
           header-names header-values
           basic-auth-user basic-auth-pass
           oauth-url oauth-client-id oauth-client-secret]}]
  (cond-> []
    (s/blank? id)
    (conj "ID can not be blank")

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

(defn path
  "Path to an institution resource."
  ([] "/institutions")
  ([id]
   (if (keyword? id)
     (str "/institutions/" (url-encode (name id)))
     (str "/institutions/" (url-encode id))))
  ([id action] (str "/institutions/" (url-encode id)
                    "/" (url-encode (name action)))))

(defn- form
  "Form hiccup for institution params."
  [{:keys [id url auth
           header-names header-values
           basic-auth-user basic-auth-pass
           oauth-url oauth-client-id oauth-client-secret oauth-scope]}]
  [[:div.field
    [:label {:for "id"} "ID"]
    [:input {:type "text", :required true
             :id   "id",   :name     "id", :value id}]]

   [:div.field
    [:label {:for "url"} "URL"]
    [:input {:type "url", :pattern "https?://.*", :required true
             :id   "url", :name    "url",         :value    url}]]

   [:div.field
    [:label "Headers"]
    [:ul.headers
     (for [[name value i] (map #(vector %1 %2 %3) header-names header-values (iterate inc 0))]
       [:li {:class "header"}
        [:input {:name "header-names[]", :value name, :placeholder "Name"}]
        ": "
        [:input {:name "header-values[]", :value value, :placeholder "Value"}]
        [:input {:type  "submit", :name  (str "delete-header-" i),
                 :value "🗑",      :title "Delete header", :class "delete-header"}]])
     [:li [:input {:type "submit", :name "add-header", :value "Add header"}]]]]

   [:div.field
    [:label {:for "auth"} "Authentication"]
    [:select {:id "auth", :name "auth"}
     (for [v ["" "basic" "oauth"]]
       [:option (cond-> {:value v}
                  (= v auth) (assoc :selected true))
        ({""      "Other"
          "basic" "Basic Authentication"
          "oauth" "OAuth2 Client Credentials"} v)])]
    [:input {:type "submit", :name "select-auth", :value "Select method"}]]

   (cond
     (= "basic" auth)
     [:fieldset [:legend "Basic Authentication"]
      [:div.field
       [:label {:for "basis-user"} "Basic Authentication User"]
       [:input {:type "text",            :pattern "[^:]*"
                :name "basic-auth-user", :value   basic-auth-user}]]

      [:div.field
       [:label {:for "basis-pass"} "Basic Authentication Password"]
       [:input {:type "password",        :autocomplete "new-password"
                :name "basic-auth-pass", :value        basic-auth-pass}]]]

     (= "oauth" auth)
     [:fieldset [:legend "OAuth2 Client Credentials"]
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
  [:div
   [:h2 "Institutions"]
   [:ul
    (for [id (->> institutions (map :id) (sort))]
      [:li [:a {:href (path id)} id]])]
   [:a {:href (path :new), :class "button"} "New institution"]])

(defn- detail-page
  "Institution detail hiccup."
  [{:keys [orig-id] :as institution}]
  [:div.detail
   (if orig-id
     [:h2 "Institution: " orig-id]
     [:h2 "New institution"])

   [:form {:action   (if orig-id (path orig-id :update) (path :create))
           :method   :post}
    [:input {:type "submit", :style "display: none"}] ;; ensure enter key submits
    (anti-forgery-field)

    (into [:fieldset] (form institution))

    [:div.actions
     [:button {:type "submit"} (if orig-id "Update" "Create")]
     " "
     [:a {:href (path), :class "button"} "Cancel"]]]

   (when orig-id
     [:form {:action (path orig-id :delete), :method :post, :class "delete"}
      (anti-forgery-field)
      [:button {:type "submit", :onclick (confirm-js :delete "institution" orig-id)} "Delete"]])])

(defn- delete-header-fn-from-params
  "Find parameter named \"delete-header-X\" were X is a number and
  return a function to delete element at X."
  [params]
  (when-let [k (->> params keys (map name) (filter #(re-matches #"delete-header-\d+" %)) first)]
    (let [n (Integer/parseInt (re-find #"\d+" k))]
      (fn [coll]
        (concat (take n coll) (drop (inc n) coll))))))

(defn- create-or-update
  "Handle create or update request."
  [{:keys                            [institutions params]
    {:keys [id orig-id
            add-header select-auth]} :params
    :as                              req}]
  (let [errors           (form-errors params)
        delete-header-fn (delete-header-fn-from-params params)]
    (cond
      add-header
      (-> params
          (update :header-names conj "")
          (update :header-values conj "")
          (detail-page)
          (layout req))

      delete-header-fn
      (-> params
          (update :header-names delete-header-fn)
          (update :header-values delete-header-fn)
          (detail-page)
          (layout req))

      select-auth
      (-> params
          (detail-page)
          (layout req))

      errors
      (-> params
          (detail-page)
          (layout (assoc req :flash (str "Invalid input;\n" (s/join ",\n" errors))))
          (render req)
          (status http/not-acceptable))

      (and (not= id orig-id) (contains? institutions (keyword id)))
      (-> params
          (detail-page)
          (layout (assoc req :flash (str "ID already taken; " id))))

      :else
      (-> "/institutions"
          (redirect :see-other)
          (assoc :institutions (-> institutions
                                   (dissoc (keyword orig-id))
                                   (assoc (keyword id) (form-> params))))
          (assoc :flash (str "Updated institution '" id "'"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defroutes handler
  (GET "/institutions" {:keys [institutions] :as req}
       (-> (map (fn [[id m]] (->form m id)) institutions)
           (index-page)
           (layout req)))

  (GET "/institutions/new" req
       (-> {}
           (detail-page)
           (layout req)))

  (POST "/institutions/create" req
        (create-or-update req))

  (GET "/institutions/:id" {:keys        [institutions]
                            {:keys [id]} :params
                            :as          req}
       (-> institutions
           (get (keyword id))
           (->form id)
           (detail-page)
           (layout req)))

  (POST "/institutions/:id/delete" {:keys        [institutions]
                                    {:keys [id]} :params}
        (-> "/institutions"
            (redirect :see-other)
            (assoc :institutions (dissoc institutions (keyword id)))
            (assoc :flash (str "Deleted institution '" id "'"))))

  (POST "/institutions/:orig-id/update" req
        (create-or-update req)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- fetch
  [yaml-fname]
  (-> yaml-fname
      slurp
      yaml/parse-string
      :serviceEndpoints))

(defn- put
  [yaml-fname institutions]
  (spit yaml-fname
        (-> yaml-fname
            slurp
            yaml/parse-string
            (assoc :serviceEndpoints institutions)
            yaml/generate-string)))

(defn wrap
  "Middleware to allow reading and writing institutions."
  [app institutions-yaml-fname]
  (fn [req]
    (let [old (fetch institutions-yaml-fname)
          res (app (assoc req :institutions old))
          new (get res :institutions)]
      (when new
        (put institutions-yaml-fname new))
      res)))
