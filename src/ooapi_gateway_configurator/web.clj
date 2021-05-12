(ns ooapi-gateway-configurator.web
  (:require [compojure.core :refer [defroutes GET]]
            [compojure.route :refer [not-found resources]]
            [hiccup.page :refer [html5]]
            [ooapi-gateway-configurator.institutions :as institutions]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.util.codec :refer [url-encode]]))

(def title "OOAPI Gateway Configurator")

(defn layout
  [body]
  (html5
   {:lang "en"}
   [:head
    [:title title]
    [:link {:href "/screen.css" :rel "stylesheet"}]
    [:meta {:name "viewport", :content "width=device-width"}]]
   [:body
    [:header [:h1 title]]
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
  (GET "/" []
       (-> (main-page)
           (layout)))
  (GET "/institutions/:id" {:keys [institutions]
                            {:keys [id]} :params}
       (-> institutions
           (get (keyword id))
           (institution-page id)
           (layout)))
  (GET "/institutions" {:keys [institutions]}
       (-> institutions
           (institutions-page)
           (layout)))
  (resources "/" {:root "public"})
  (not-found "nothing here.."))

(defn wrap-institutions [app institutions-yaml-fname]
  (fn [req]
    (app (assoc req :institutions
                (institutions/fetch institutions-yaml-fname)))))

(defn mk-app
  [{:keys [institutions-yaml-fname]}]
  (-> handler
      (wrap-institutions institutions-yaml-fname)
      (wrap-defaults site-defaults)))
