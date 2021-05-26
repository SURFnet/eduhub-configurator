(ns ooapi-gateway-configurator.user-info
  (:require [clj-http.client :as http]))

(defn- fetch-user-info
  "Get user-info from session or from user-info-endpoint
  for every available OAuth2 access token"
  [{:keys [oauth2/access-tokens]
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
  provide it as `:oauth2/user-info`.

  The user-info is persisted in the session and kept in sync with the
  access tokens; user-info that has no corresponding access token will
  be removed.

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
    (let [user-info (fetch-user-info request profiles)
          response  (-> request
                        (assoc :oauth2/user-info user-info)
                        (handler))]
      (assoc response :session
             (assoc (or (:session response)
                        (:session request))
                    ::user-info user-info)))))
