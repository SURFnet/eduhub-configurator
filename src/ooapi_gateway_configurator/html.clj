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

(ns ooapi-gateway-configurator.html
  (:refer-clojure :exclude [time])
  (:require [compojure.response :refer [render]]
            [hiccup.page :refer [html5]]
            [hiccup.util :refer [escape-html]]
            [ooapi-gateway-configurator.auth :as auth]
            [ooapi-gateway-configurator.http :as http]
            [ooapi-gateway-configurator.store :as store]
            [ring.util.response :refer [status]]))

(def title "OOAPI Gateway Configurator")

(defn layout
  [body {:keys [::store/uncommitted? flash] :as req} & [subtitle]]
  (html5
   {:lang "en"}
   [:head
    [:title (if subtitle
              (str (escape-html subtitle) " &mdash; " (escape-html title))
              (escape-html title))]
    [:link {:href "/screen.css" :rel "stylesheet"}]
    [:meta {:name "viewport", :content "width=device-width"}]
    [:script {:src "/unload.js"}]]
   [:body
    [:header
     [:h1 [:a {:href "/"} (escape-html title)]]
     (auth/auth-component req)
     (when flash
       [:p.flash (escape-html flash)])
     (when uncommitted?
       [:a.uncommited {:href "/"} "pending changes"])]
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
