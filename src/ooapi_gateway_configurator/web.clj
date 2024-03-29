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

(ns ooapi-gateway-configurator.web
  (:require [compojure.core :refer [GET routes wrap-routes]]
            [compojure.route :refer [resources]]
            [nl.jomco.http-status-codes :as http-status]
            [nl.jomco.ring-trace-context :as ring-trace-context]
            [ooapi-gateway-configurator.access-control-lists :as access-control-lists]
            [ooapi-gateway-configurator.applications :as applications]
            [ooapi-gateway-configurator.auth :as auth]
            [ooapi-gateway-configurator.auth-pages :as auth-pages]
            [ooapi-gateway-configurator.html :refer [layout not-found]]
            [ooapi-gateway-configurator.institutions :as institutions]
            [ooapi-gateway-configurator.logging :as logging]
            [ooapi-gateway-configurator.network :as network]
            [ooapi-gateway-configurator.session :as session]
            [ooapi-gateway-configurator.store :as store]
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.util.response :as response]))

(defn main-page
  [req]
  [:div.main-page
   [:nav
    [:a.current "⌂"]]
   [:ul
    [:li [:a {:href "applications/"}
          "Applications"]]
    [:li [:a {:href "institutions/"}
          "Institutions"]]
    [:li [:a {:href "network/"}
          "Network"]]]
   (store/commit-component req)])

(defn not-found-handler
  [req]
  (not-found "Oeps, nothing here.." req))

(defn mk-private-handler
  "Handler only served when user logged in and member of given groups."
  [config]
  (wrap-routes
   (routes (GET "/" req (layout (main-page req) req))
            #'applications/handler
            #'institutions/handler
            #'access-control-lists/handler
            #'network/handler
            (store/mk-handler (:store config)))
   auth/wrap-member-of (get-in config [:auth :group-ids])))

(defn mk-handler
  [config]
  (routes
   (mk-private-handler config)

   (GET "/userinfo" req
        (-> (response/response (pr-str (:oauth2/user-info req)))
            (response/content-type "text/plain")))

   auth/logout-handler

   (resources "/" {:root "public"})

   not-found-handler))

(defn wrap-csp
  "Middleware to set CSP header unless already set."
  [handler default-value]
  (fn [req]
    (when-let [res (handler req)]
      (update-in res [:headers "Content-Security-Policy"]
                 (fn [value]
                   (if value
                     value
                     default-value))))))

(defn- anti-forgery-error-handler
  [{:keys [session] :as req}]
  (if (empty? session)
    {:status  http-status/unauthorized
     :headers {"Content-Type" "text/html"}
     :body    (layout
               [:div [:h3 "Session expired"]
                [:p "Please use the \"Log in\" button to login again."]]
               req)}
    {:status  http-status/forbidden
     :headers {"Content-Type" "text/html"}
     :body    (layout
               [:div [:h3 "Invalid anti-forgery token"]]
               req)}))

(defn mk-app
  [config]
  (-> config
      (mk-handler)

      (store/wrap (:store config))

      ;; note: the below middleware does not enforce security but only supports it
      (auth-pages/wrap-auth-pages)
      (auth/wrap-authentication (:auth config))

      (wrap-anti-forgery {:strategy      (session/mk-strategy)
                          :error-handler anti-forgery-error-handler})

      (wrap-defaults (-> config
                         (get :site-defaults site-defaults)
                         (assoc-in [:params :keywordize] false)
                         (assoc-in [:session :store] (session/mk-store))

                         ;; manually added with other strategy
                         (assoc-in [:security :anti-forgery] false)))

      ;; Do not allow inline style/script but anything loaded from
      ;; this origin is fine.
      (wrap-csp "default-src 'self'")
      (logging/wrap-logging)
      (ring-trace-context/wrap-trace-context)))
