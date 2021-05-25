(ns ooapi-gateway-configurator.web-test
  (:require [clojure.test :refer :all]
            [ooapi-gateway-configurator.http :as http]
            [ooapi-gateway-configurator.web :as sut]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.mock.request :refer [request]]))

(def ^:dynamic *app*)

(def app (-> (sut/mk-handler {:auth {:group-ids #{}}}) (wrap-defaults site-defaults)))

(defn do-get [uri]
  (app (request :get uri)))

(deftest handler
  (testing "GET /"
    (let [res (do-get "/")]
      (is (= http/ok (:status res))
          "OK"))))
