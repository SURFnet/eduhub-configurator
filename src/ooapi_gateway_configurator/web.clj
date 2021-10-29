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

(ns ooapi-gateway-configurator.web
  (:require [compojure.core :refer [GET routes wrap-routes]]
            [compojure.route :refer [resources]]
            [ooapi-gateway-configurator.access-control-lists :as access-control-lists]
            [ooapi-gateway-configurator.applications :as applications]
            [ooapi-gateway-configurator.auth :as auth]
            [ooapi-gateway-configurator.auth-pages :as auth-pages]
            [ooapi-gateway-configurator.html :refer [layout not-found]]
            [ooapi-gateway-configurator.institutions :as institutions]
            [ooapi-gateway-configurator.state :as state]
            [ooapi-gateway-configurator.store :as store]
            [ooapi-gateway-configurator.network :as network]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]))

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

(defn mk-handler
  [config]
  (routes
   (wrap-routes
    (routes (GET "/" req (layout (main-page req) req))
            applications/handler
            institutions/handler
            access-control-lists/handler
            network/handler)
    auth/wrap-member-of (get-in config [:auth :group-ids]))
   auth/logout-handler
   (resources "/" {:root "public"})
   (fn [req] (not-found "Oeps, nothing here.." req))))

(defn mk-app
  [config]
  (-> config
      (mk-handler)

      (state/wrap)
      (store/wrap (:store config))

      (auth-pages/wrap-auth-pages)
      (auth/wrap-authentication (:auth config))

      (wrap-defaults (-> config
                         (get :site-defaults site-defaults)
                         (assoc-in [:params :keywordize] false)
                         (assoc-in [:session :cookie-attrs :same-site] :lax)))))
