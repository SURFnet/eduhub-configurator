(ns ooapi-gateway-configurator.html
  (:require [hiccup.page :refer [html5]]
            [ooapi-gateway-configurator.auth :as auth]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]))

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
     [:div.login
      (if-let [token (auth/token req)]
        [:pre (prn-str (auth/decode-token token))]
        [:a {:href "/oauth2/conext", :class "button"} "Log in"])]
     (when flash [:p.flash flash])]
    [:main body]]))

(defn anti-forgery-field
  []
  [:input {:type "hidden"
           :name "__anti-forgery-token"
           :value *anti-forgery-token*}])

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
