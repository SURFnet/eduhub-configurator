(ns ooapi-gateway-configurator.web-test
  (:require [clojure.test :refer :all]
            [ooapi-gateway-configurator.web :as sut]
            [ring.mock.request :as mock]))

(defn request [& args]
  (assoc (apply mock/request args)
         :institutions {:test-institution {:url "http://example.com"}}))

(deftest handler

  (testing "GET /"
    (let [res (sut/handler (request :get "/"))]
      (is (= 200 (:status res)))))

  (testing "GET /institutions"
    (let [res (sut/handler (request :get "/institutions"))]
      (is (= 200 (:status res)))
      (is (re-find #"test-institution" (:body res)))))

  (testing "GET /institutions/test"
    (let [res (sut/handler (request :get "/institutions/test-institution"))]
      (is (= 200 (:status res)))
      (is (re-find #"test-institution" (:body res)))
      (is (re-find #"http://example.com" (:body res))))))
