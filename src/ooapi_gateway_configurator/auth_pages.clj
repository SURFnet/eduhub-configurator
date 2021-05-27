(ns ooapi-gateway-configurator.auth-pages
  (:require [ooapi-gateway-configurator.html :refer [layout]]
            [ooapi-gateway-configurator.http :as status]
            [ring.util.response :as response]))

(defn- unauthorized-page
  []
  [:div [:h3 "Unauthorized"]
   [:p "Your account is not authorized to access this page. You may
   want to log in using another account."]])


(defn- unauthenticated-page
  []
  [:div [:h3 "Unauthenticated"]
   [:p "Access to this page is restricted. Please log in"]])

(defn- render-page
  [response page request]
  (-> response
      (assoc :body (layout page request))
      (response/content-type "text/html")
      (response/charset "utf-8")))

(defn wrap-auth-pages
  [f]
  (fn [request]
    (let [{:keys [status] :as response} (f request)]
      (cond
        (= status status/forbidden)
        (render-page response (unauthorized-page) request)

        (= status status/unauthorized) ;; HTTP status names are messed up; unauthorized means not logged in here
        (render-page response (unauthenticated-page) request)

        :else
        response))))
