(ns ooapi-gateway-configurator.web
  (:require [compojure.core :refer [defroutes GET]]
            [compojure.route :refer [not-found resources]]
            [hiccup.page :refer [html5]]
            [ooapi-gateway-configurator.auth :as auth]
            [ooapi-gateway-configurator.institutions :as institutions]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.util.codec :refer [url-encode]]))

(def title "OOAPI Gateway Configurator")

(defn layout
  [body request]
  (html5
   {:lang "en"}
   [:head
    [:title title]
    [:link {:href "/screen.css" :rel "stylesheet"}]
    [:meta {:name "viewport", :content "width=device-width"}]]
   [:body
    [:header [:h1 title]
     (if-let [token (auth/token request)]
       [:pre (prn-str (auth/decode-token token))]
       [:a {:href "/oauth2/conext"} "Log in"])]
    [:main body]]))

(defn ->html
  [v]
  (cond
    (map? v)
    [:dl
     (mapcat (fn [[k v]]
               [[:dt k]
                [:dd (->html v)]])
             v)]

    (sequential? v)
    [:ul
     (for [v v]
       [:li (->html v)])]

    :else
    (str v)))

(defn institutions-path
  [& [id]]
  (if id
    (str "/institutions/" (url-encode (name id)))
    "/institutions"))

(defn main-page
  []
  [:ul
   [:li [:a {:href (institutions-path)}
         "Institutions"]]])

(defn institutions-page
  [institutions]
  [:div
   [:h2 "Institutions"]
   [:ul
    (for [[id _] institutions]
      [:li [:a {:href (institutions-path id)} id]])]])

(defn institution-page
  [institution id]
  [:div
   [:h2 "Institution: " (name id)]
   (->html institution)])

(defroutes handler
  (GET "/" r
       (-> (main-page)
           (layout r)))
  (GET "/institutions/:id" {:keys [institutions]
                            {:keys [id]} :params
                            :as r}
       (-> institutions
           (get (keyword id))
           (institution-page id)
           (layout r)))
  (GET "/institutions" {:keys [institutions] :as r}
       (-> institutions
           (institutions-page)
           (layout r)))
  (resources "/" {:root "public"})
  (not-found "nothing here.."))

(defn wrap-institutions [app institutions-yaml-fname]
  (fn [req]
    (app (assoc req :institutions
                (institutions/fetch institutions-yaml-fname)))))

(defn mk-app
  [config]
  (-> #'handler
      (auth/wrap-authentication (:auth config))
      (wrap-defaults (-> site-defaults
                         (assoc-in [:session :cookie-attrs :same-site] :lax)))
      (wrap-institutions (get-in config [:web :institutions-yaml-fname]))))
