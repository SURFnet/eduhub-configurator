(ns ooapi-gateway-configurator.auth
  "Provide authentication and authorization information via SURF Conext.

  This middleware needs to be configured and the application needs to
  be registered with SURF Conext as an OAuth2/OpenID Connect client.

  SURF Conext then provides a standard OAuth2/OpenID Connect service
  the provides login flow and configured user information which is
  passed to the application in the request under :oauth2/user-info.

  https://wiki.surfnet.nl/display/surfconextdev/Connect+in+5+Steps


  The user info from Conext can provide claims (need to be configured
  for the app). We can use edu_person_entitlement flags or group
  membership via edumember_is_member_of to determine
  authorization. See

    https://wiki.surfnet.nl/display/surfconextdev/Standardized+values+for+eduPersonEntitlement

  When the Conext test service is set up, you can login using your own
  account (eduid or otherwise) or the \"SURFconext Test IdP\" and use
  one of the provided test accounts:
  https://idp.diy.surfconext.nl/showusers.php"
  (:require [clojure.tools.logging :as log]
            [compojure.core :refer [defroutes POST]]
            [ooapi-gateway-configurator.anti-forgery :refer [anti-forgery-field]]
            [ooapi-gateway-configurator.http :as status]
            [ooapi-gateway-configurator.user-info :as user-info]
            [ring.middleware.oauth2 :as oauth2]
            [ring.util.response :as response]))

(defn- wrap-auth-error
  "When the Conext identity provider signals an error with
  authentication, show it and log a summary."
  [handler]
  (fn [{:keys [uri params] :as request}]
    (if (and (= uri "/oauth2/conext/callback")
             (:error params))
      (do (log/error "OAuth2 error"
                     (select-keys request [:uri :params]))
          (-> "An error was received from the authentication service.\nThis is probably caused by a configuration error.\n\n"
              (str (pr-str params))
              (response/bad-request )
              (response/content-type "text/plain")))
      (handler request))))

(defn wrap-authentication
  [handler {:keys [authorize-uri
                   access-token-uri
                   user-info-uri
                   client-id
                   client-secret]}]
  (-> handler
      (user-info/wrap-user-info {:conext {:user-info-uri user-info-uri}})
      (oauth2/wrap-oauth2 {:conext {:authorize-uri    authorize-uri
                                    :access-token-uri access-token-uri
                                    :client-id        client-id
                                    :client-secret    client-secret
                                    :scopes           ["openid"]
                                    :launch-uri       "/oauth2/conext"
                                    :redirect-uri     "/oauth2/conext/callback"
                                    :landing-uri      "/"}})
      wrap-auth-error))

(defn- user-info
  [request]
  (get-in request [:oauth2/user-info :conext]))

(defn- member-of?
  "test if current user is a member of any of the given groups"
  [request groups]
  (boolean (some groups (-> request user-info :edumember_is_member_of))))

;; TODO: return unauthorized status when not logged in at all
;; - maybe needs to be separate middleware
(defn wrap-member-of
  "Middleware restricting access to members of `groups`.
  Membership is tested for by matching the `edumember_is_member_of`
  claim in the Conext user-info."
  [handler groups]
  {:pre [(set? groups)]}
  (fn [request]
    (if (member-of? request groups)
      (handler request)
      {:status  status/forbidden
       :headers {"Content-Type" "text/plain"}
       :body    "You're not authorized to access this resource"})))

(defn auth-component
  "Login/logout block for html interface"
  [request]
  [:div.login
   (if (user-info request)
     [:form {:action "/logout"
             :method "POST"}
      (anti-forgery-field)
      [:button.button {:type :submit}
       "Log out"]]
     [:a.button {:href "/oauth2/conext"} "Log in"])])

(defroutes logout-handler
  (POST "/logout" _
        (-> "/"
            (response/redirect :see-other)
            (assoc :session {}
                   :flash "You are logged out"))))
