(ns ooapi-gateway-configurator.web
  (:require [compojure.core :refer [defroutes GET]]
            [compojure.route :refer [not-found resources]]
            [hiccup.page :refer [html5]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.util.response :as response]))

(def title "OOAPI Gateway Configurator")

(defn layout
  [body]
  (html5
   {:lang "nl-NL"}
   [:head
    [:title title]
    [:link {:href "screen.css" :rel "stylesheet"}]
    [:meta {:name "viewport", :content "width=device-width, initial-scale=1.0, minimal-ui"}]]
   [:body
    [:header [:h1 title]]
    body]))

(defroutes handler
  (GET "/" []
       (-> [:h1 "Hello, World"]
           layout))
  (resources "/" {:root "public"})
  (not-found "nothing here.."))


(defn mk-app
  []
  (-> handler
      (wrap-defaults site-defaults)))
