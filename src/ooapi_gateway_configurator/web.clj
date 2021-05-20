(ns ooapi-gateway-configurator.web
  (:require [compojure.core :refer [defroutes GET]]
            [compojure.route :refer [not-found resources]]
            [ooapi-gateway-configurator.auth :as auth]
            [ooapi-gateway-configurator.html :refer [layout]]
            [ooapi-gateway-configurator.institutions.web :as institutions]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]))

(defn main-page
  []
  [:ul
   [:li [:a {:href (institutions/path)}
         "Institutions"]]])

(defroutes handler
  (GET "/" req
       (layout (main-page) req))

  institutions/handler

  (resources "/" {:root "public"})
  (not-found "nothing here.."))

(defn mk-app
  [config]
  (-> #'handler
      (auth/wrap-authentication (:auth config))
      (wrap-defaults (-> config
                         (get :site-defaults site-defaults)
                         (assoc-in [:session :cookie-attrs :same-site] :lax)))
      (institutions/wrap (get-in config [:web :institutions-yaml-fname]))))
