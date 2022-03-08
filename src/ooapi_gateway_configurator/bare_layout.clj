;; Copyright (C) 2021, 2022 SURFnet B.V.
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

(ns ooapi-gateway-configurator.bare-layout
  (:refer-clojure :exclude [time])
  (:require [compojure.response :refer [render]]
            [hiccup.page :refer [html5]]
            [hiccup.util :refer [escape-html]]
            [ooapi-gateway-configurator.http :as http]
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
