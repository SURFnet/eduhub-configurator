(ns ooapi-gateway-configurator.html
  (:require [compojure.response :refer [render]]
            [hiccup.page :refer [html5]]
            [hiccup.util :refer [escape-html]]
            [ooapi-gateway-configurator.auth :as auth]
            [ooapi-gateway-configurator.http :as http]
            [ring.util.response :refer [status]]))

(def title "OOAPI Gateway Configurator")

(defn layout
  [body {:keys [flash] :as req}]
  (html5
   {:lang "en"}
   [:head
    [:title title]
    [:link {:href "/screen.css" :rel "stylesheet"}]
    [:meta {:name "viewport", :content "width=device-width"}]]
   [:body
    [:header
     [:h1 [:a {:href "/"} title]]
     (auth/auth-component req)
     (when flash [:p.flash (escape-html flash)])]
    [:main body]]))

(defn not-found
  [msg req]
  (-> [:div [:h3 "Not Found"]
       [:p (escape-html msg)]]
      (layout req)
      (render req)
      (status http/not-found)))

(defn confirm-js [action resource id]
  (str "return confirm("
       (pr-str (str "Really " (name action) " " resource " '" id "'?"))
       ")"))

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
