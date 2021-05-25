(ns ooapi-gateway-configurator.auth-test
  (:require [clj-http.client :as client]
            [clj-http.cookies :refer [cookie-store]]
            [clojure.test :refer [deftest is testing]]
            [compojure.core :refer [GET wrap-routes]]
            [ooapi-gateway-configurator.auth :as auth]
            [ooapi-gateway-configurator.auth-test.provider :as provider]
            [ooapi-gateway-configurator.http :as status]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]))

;; these tests run actual http servers so that redirection in the
;; authentication flow can be tested

(def client-id "my-test-client-id")
(def client-secret "top-secret-secret")

(defmacro with-cleanup
  [bindings-with-cleanup & body]
  (when-not (zero? (rem (count bindings-with-cleanup) 3))
    (throw (IllegalArgumentException. "bindings-with-cleanup should be divisible by 3")))
  (if (seq bindings-with-cleanup)
    (let [[sym open close & rst] bindings-with-cleanup]
      `(let [~sym ~open]
         (try (with-cleanup ~rst
                ~@body)
              (finally ~close))))
    `(do ~@body)))

(deftest auth
  (let [handler (-> (GET "/ok" _
                         {:status status/ok
                          :body   "OK"})
                    (wrap-routes auth/wrap-member-of #{"flintstones"})
                    (auth/wrap-authentication {:authorize-uri    "http://localhost:9991/oidc/authorize"
                                               :access-token-uri "http://localhost:9991/oidc/token"
                                               :user-info-uri    "http://localhost:9991/oidc/userinfo"
                                               :client-id        client-id
                                               :client-secret    client-secret})
                    (wrap-defaults site-defaults))

        cookies (cookie-store)
        do-get  (fn do-get
                  ([url opts]
                   (client/get url (assoc opts
                                          :throw-exceptions? false
                                          :cookie-store cookies)))
                  ([url]
                   (do-get url nil)))]
    (with-cleanup [server (run-jetty handler {:join? false :port 9990})
                   (.stop server)

                   provider (run-jetty (provider/mk-provider {:client-id client-id :client-secret client-secret
                                                              :user-info {:edumember_is_member_of ["flintstones"]}})
                                       {:port 9991 :join? false})
                   (.stop provider)]
      (testing "forbidden with not logged in"
        (let [response (do-get "http://localhost:9990/ok")]
          (is (= status/forbidden (:status response)))))
      (testing "logging in"
        (let [response (do-get "http://localhost:9990/oauth2/conext")]
          (is (= status/ok (:status response)))))
      (testing "allowed in now"
        (let [response (do-get "http://localhost:9990/ok")]
          (is (= status/ok (:status response))))))))
