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

(ns ooapi-gateway-configurator.user-info
  (:require [clj-http.client :as http]
            [ooapi-gateway-configurator.logging :as logging]))

(defn- fetch-user-info
  "Get user-info from session or from user-info-endpoint
  for every available OAuth2 access token"
  [{:keys                 [oauth2/access-tokens]
    {:keys [::user-info]} :session}
   profiles]
  (reduce (fn [result [key _]]
            (if-let [token (get-in access-tokens [key :token])] ;;
              (assoc result key
                     (or (get user-info key)
                         ;; http/get throws exception on http errors
                         (:body (http/get (get-in profiles [key :user-info-uri])
                                          {:oauth-token token
                                           :accept      :json
                                           :as          :json}))))
              result))
          {}
          profiles))

(defn wrap-user-info
  "Middleware providing additional user information.

       (-> handler
           (wrap-user-info profiles)
           (ring.middleware.oauth2/wrap-oauth2 profiles))

  When oauth2 authentication tokens are present in the request, this
  middleware will request the available user-info for the tokens and
  provide it as `:oauth2/user-info` in the request and the response.

  The user-info is persisted in the session and kept in sync with the
  access tokens; user-info that has no corresponding access token will
  be removed.

  This middleware also adds an SLF4J MDC key \"user-id\" with the
  subject (\"sub\") of value of the user-info when `handler` is
  called.

  This middleware relies on `:oauth2/access-tokens` being provided, so
  it should normally be wrapped with `ring.middleware.oauth2/wrap-oauth2`

    - `handler` is the ring handler to be wrapped

    - `profiles` is a map of key to profile, so you can provide multiple
      profiles when using multiple oauth2 providers. The profile  needs
      to contain at least `:user-info-uri`. Profile keys must match
      the keys used to configure `ring.middleware.oauth2/wrap-oauth2`."
  [handler profiles]
  {:pre [(map? profiles)
         (every? :user-info-uri (vals profiles))]}
  (fn [request]
    (let [user-info (fetch-user-info request profiles)]
      (logging/with-mdc {:user-id (get-in user-info [:conext :sub])}
        (let [response (-> request
                           (assoc :oauth2/user-info user-info)
                           (handler))]
          (if (empty? user-info)
            response
            (-> response
                (assoc :session (:session response (:session request)))
                (assoc-in [:session ::user-info] user-info)
                (assoc :oauth2/user-info user-info))))))))
