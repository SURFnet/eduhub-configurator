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

(ns ooapi-gateway-configurator.auth-test.provider
  "A minimal implementation of an OpenID Connect provider.

  Only provides the code flow for authenticating users.  This is just
  the bare minimum needed to test OIDC clients with the authorization
  code flow."
  (:require [clojure.tools.logging :as log]
            [compojure.core :refer [GET POST routes]]
            [ooapi-gateway-configurator.http :as status]
            [ring.middleware.defaults :refer [api-defaults wrap-defaults]]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.util.codec :as codec]
            [ring.util.response :as response])
  (:import java.util.UUID))

(defn wrap-trace
  [f]
  (fn [r]
    (let [response (f r)]
      (log/trace (:status response) (:request-method r) (:uri r))
      response)))

(defn mk-provider
  [{:keys [client-id user-info]}]
  (let [codestore (atom {})]
    (-> (routes
         (GET "/oidc/authorize" {{:keys [client_id redirect_uri state]} :params}
              (if  (= client-id client_id)
                (let [code (str (UUID/randomUUID))]
                  (swap! codestore assoc code {:state state})
                  (let [uri (str redirect_uri "?" (codec/form-encode {:code code :state state}))]
                    (log/trace :redirect uri)
                    (response/redirect uri)))
                {:status status/forbidden
                 :body   {:error "Invalid client id"}}))
         (POST "/oidc/token" _
               {:status status/ok
                :body {:access_token "fake"}})
         (GET "/oidc/userinfo" _
              {:status status/ok
               :body user-info}))
        wrap-trace
        wrap-json-response
        (wrap-defaults api-defaults))))
