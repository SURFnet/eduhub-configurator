(ns ooapi-gateway-configurator.web
  (:require [compojure.core :refer [GET routes wrap-routes]]
            [compojure.route :refer [not-found resources]]
            [ooapi-gateway-configurator.applications :as applications]
            [ooapi-gateway-configurator.auth :as auth]
            [ooapi-gateway-configurator.html :refer [layout]]
            [ooapi-gateway-configurator.institutions :as institutions]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]))

(defn main-page
  []
  [:ul
   [:li [:a {:href (applications/path)}
         "Applications"]]
   [:li [:a {:href (institutions/path)}
         "Institutions"]]])

(defn mk-handler
  [config]
  (routes (GET "/" req
               (layout (main-page) req))

          (wrap-routes applications/handler
                       auth/wrap-member-of (get-in config [:auth :group-ids]))
          (wrap-routes institutions/handler
                       auth/wrap-member-of (get-in config [:auth :group-ids]))

          auth/logout-handler
          (resources "/" {:root "public"})
          (not-found "nothing here..")))

(defn mk-app
  [config]
  (-> config
      (mk-handler)
      (institutions/wrap (get-in config [:web :gateway-config-yaml]))
      (applications/wrap (get-in config [:web :credentials-json]))
      (auth/wrap-authentication (:auth config))
      (wrap-defaults (-> config
                         (get :site-defaults site-defaults)
                         (assoc-in [:session :cookie-attrs :same-site] :lax)))))
