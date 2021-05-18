(ns ooapi-gateway-configurator.html
  (:require [hiccup.page :refer [html5]]
            [ooapi-gateway-configurator.auth :as auth]))

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
     (when flash [:p.flash flash])]
    [:main body]]))

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
