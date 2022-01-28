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

(defn bare-layout
  [body-tag subtitle]
  (html5
   {:lang "en"}
   [:head
    [:title (if subtitle
              (str (escape-html subtitle) " &mdash; " (escape-html title))
              (escape-html title))]
    [:link {:href "/screen.css" :rel "stylesheet"}]
    [:meta {:name "viewport", :content "width=device-width"}]
    [:script {:src "/unload.js"}]
    [:script {:src "/confirm.js"}]]
   body-tag))

(defn header
  []
  [:header
   [:h1 [:a {:href "/"} (escape-html title)]]])

(defn layout
  [main-body {:keys [::store/uncommitted? flash] :as req} & [subtitle]]
  (bare-layout
   [:body
    (cond-> (header)
      true
      (conj (auth/auth-component req))

      flash
      (conj [:p.flash (escape-html flash)])

      uncommitted?
      (conj [:a.uncommited {:href "/"} "pending changes"]))
    [:main main-body]]
   subtitle))

(defn not-found
  [msg req]
  (-> [:div [:h3 "Not Found"]
       [:p (escape-html msg)]]
      (layout req)
      (render req)
      (status http/not-found)))

(defn exception
  [id req]
  (-> [:body.error
       (header)
       [:main
        [:h3 "Error ID " [:code (escape-html id)]]
        [:p "An internal error occurred. The details have been logged with error id " [:code (escape-html id)] "."]]]
      (bare-layout (str "Error ID " id))
      (render req)
      (status http/internal-server-error)))
