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
            [ring.util.response :refer [status]])
  (:import java.time.format.DateTimeFormatter))

(def title "OOAPI Gateway Configurator")

(defn layout
  [body {:keys [flash] :as req} & [subtitle]]
  (html5
   {:lang "en"}
   [:head
    [:title (if subtitle (str subtitle " — " title) title)]
    [:link {:href "/screen.css" :rel "stylesheet"}]
    [:meta {:name "viewport", :content "width=device-width"}]
    [:script {:src "/unload.js"}]]
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

(def ^:private iso8601-formatter
  ^DateTimeFormatter
  (.withZone (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
             (java.time.ZoneId/of "GMT")))

(def ^:private full-date-time-formatter
  (.withZone (DateTimeFormatter/ofLocalizedDateTime java.time.format.FormatStyle/LONG)
             (java.time.ZoneId/systemDefault)))

(defprotocol HtmlTime
  "Format a given thing for rendering in the user interface using an
  HTML Time Element.

  See also
  https://developer.mozilla.org/en-US/docs/Web/HTML/Element/time"
  (machine-time [item]
    "Render item as a datetime attribute string for machine processing.

  See https://developer.mozilla.org/en-US/docs/Web/HTML/Element/time
  for the valid formats.")
  (human-time [item]
    "Render time as human readable string using the available precision."))

(extend-protocol HtmlTime
  java.time.Instant
  ;; an instant is a global fixed moment (not a local datetime)
  (machine-time [this]
    (.format iso8601-formatter this))
  (human-time [this]
    (.format full-date-time-formatter this)))

(defn time
  "Format given time as human readable HTML.

  This will render the given temporal/time-like thing with the
  \"correct\" precision in the default timezone.

  Only implemented for java.time.Instant at the moment; extendable
  using the HtmlTime protocol."
  [t]
  [:time {:datetime (machine-time t)}
   (escape-html (human-time t))])
