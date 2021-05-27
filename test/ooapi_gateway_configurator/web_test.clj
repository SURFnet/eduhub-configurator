(ns ooapi-gateway-configurator.web-test
  (:require [clojure.test :refer :all]
            [ooapi-gateway-configurator.http :as http]
            [ooapi-gateway-configurator.web :as web]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.mock.request :refer [request]]))

(def ^:dynamic *app*)

(def app (-> (web/mk-handler {:auth {:group-ids #{"my-group"}}}) (wrap-defaults site-defaults)))

(defn do-get [uri]
  (app (-> (request :get uri)
           (assoc :oauth2/user-info {:conext {:edumember_is_member_of ["my-group"]}}))))

(deftest handler
  (testing "GET /"
    (let [res (do-get "/")]
      (is (= http/ok (:status res))
          "OK"))))
