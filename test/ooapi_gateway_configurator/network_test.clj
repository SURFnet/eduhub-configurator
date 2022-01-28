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

(ns ooapi-gateway-configurator.network-test
  (:require [clojure.data.json :as json]
            [clojure.test :refer [deftest testing is use-fixtures]]
            [ooapi-gateway-configurator.http :as http]
            [ooapi-gateway-configurator.network :as network]
            [ooapi-gateway-configurator.store-test :as store-test]
            [ring.mock.request :refer [request]]))

(def ^:dynamic *app*)

(use-fixtures :each
  (fn [f]
    (let [state (store-test/setup)]
      (binding [*app* (store-test/wrap network/handler state)]
        (f))
      (store-test/teardown state))))

(defn do-get [uri]
  (*app* (request :get uri)))

(deftest network-page
  (testing "GET /network/"
    (let [res (do-get "/network/")]
      (is (= http/ok (:status res))
          "OK")
      (is (get-in res [:headers "Content-Security-Policy"])
          "has a custom CSP header"))))

(deftest network-json
  (testing "GET /network.json"
    (let [res (do-get "/network.json")]
      (is (= http/ok (:status res))
          "OK")
      (is (= "application/json; charset=utf-8"
             (get-in res [:headers "Content-Type"]))
          "proper content type")
      (let [json (-> res :body (json/read-str :key-fn keyword))]
        (is (-> json :nodes)
            "has nodes")
        (is (-> json :edges)
            "has edges")))))

(deftest network-dot
  (testing "GET /network.dot"
    (let [res (do-get "/network.dot")]
      (is (= http/ok (:status res))
          "OK")
      (is (= "text/x-graphviz; charset=utf-8"
             (get-in res [:headers "Content-Type"]))
          "proper content type")
      (is (= "attachment; filename=\"network.dot\""
             (get-in res [:headers "Content-Disposition"]))
          "force a download to a file named network.dot")
      (is (re-find #"label=\"fred\"" (:body res))
          "includes some node labeled fred")
      (is (re-find #"label=\"Basic\.Auth\.Backend" (:body res))
          "includes some node labeled Basic.Auth.Backend"))))
