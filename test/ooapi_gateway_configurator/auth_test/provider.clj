(ns ooapi-gateway-configurator.auth-test.provider
  "This provides a minimal implementation of an OpenID Connect provider
  with code flow for authenticating users.

  This is just the bare minimum needed to test OIDC clients with the
  authorization code flow."
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
         (GET "/oidc/authorize" {{:keys [client_id response_type redirect_uri response_mode scope state]} :params :as request}
              (if  (= client-id client_id)
                (let [code (str (UUID/randomUUID))]
                  (swap! codestore assoc code {:state state})
                  (let [uri (str redirect_uri "?" (codec/form-encode {:code code :state state}))]
                    (log/trace :redirect uri)
                    (response/redirect uri)))
                {:status status/forbidden
                 :body   {:error "Invalid client id"}}))
         (POST "/oidc/token" {{:keys [code redirect-uri client_id]} :params}
               {:status status/ok
                :body {:access_token "fake"}})
         (GET "/oidc/userinfo" request
              {:status status/ok
               :body user-info}))
        wrap-trace
        wrap-json-response
        (wrap-defaults api-defaults))))
