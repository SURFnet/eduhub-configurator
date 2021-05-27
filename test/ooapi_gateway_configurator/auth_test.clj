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

(defn- mk-test-handler
  []
  (-> (GET "/ok" _
           {:status status/ok
            :body   "OK"})
      (wrap-routes auth/wrap-member-of #{"flintstones"})
      (auth/wrap-authentication {:authorize-uri    "http://localhost:9991/oidc/authorize"
                                 :access-token-uri "http://localhost:9991/oidc/token"
                                 :user-info-uri    "http://localhost:9991/oidc/userinfo"
                                 :client-id        client-id
                                 :client-secret    client-secret})
      (wrap-defaults site-defaults)))

(defn- mk-client
  "Return a stateful http client (with a cookie store)"
  []
  (let [cookies (cookie-store)]
    (fn do-get
      ([url opts]
       (client/get url (assoc opts
                              :throw-exceptions? false
                              :cookie-store cookies)))
      ([url]
       (do-get url nil)))))

(defn- mk-provider
  [group]
  (provider/mk-provider {:client-id client-id :client-secret client-secret
                         :user-info {:edumember_is_member_of [group]}}))

(defn- run
  [handler port]
  (run-jetty handler {:join? false :port port}))

(deftest auth
  (let [do-get (mk-client)]
    (with-cleanup [server (run (mk-test-handler) 9990) (.stop server)
                   provider (run (mk-provider "flintstones") 9991) (.stop provider)]
      (testing "unauthorized with not logged in"
        (let [response (do-get "http://localhost:9990/ok")]
          (is (= status/unauthorized (:status response)))))
      (testing "logging in"
        (let [response (do-get "http://localhost:9990/oauth2/conext")]
          (is (= status/ok (:status response)))))
      (testing "allowed in now"
        (let [response (do-get "http://localhost:9990/ok")]
          (is (= status/ok (:status response))))))))

(deftest auth-unauthorized
  (let [handler (-> (mk-test-handler))
        do-get  (mk-client)]
    (with-cleanup [server (run handler 9990) (.stop server)
                   provider (run (mk-provider "simpsons") 9991) (.stop provider)]
      (testing "unauthorized with not logged in"
        (let [response (do-get "http://localhost:9990/ok")]
          (is (= status/unauthorized (:status response)))))
      (testing "logging in"
        (let [response (do-get "http://localhost:9990/oauth2/conext")]
          (is (= status/ok (:status response)))))
      (testing "forbidden"
        (let [response (do-get "http://localhost:9990/ok")]
          (is (= status/forbidden (:status response))))))))
