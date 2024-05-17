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

(ns ooapi-gateway-configurator.web-test
  (:require [clojure.test :refer [deftest testing is]]
            [nl.jomco.http-status-codes :as http-status]
            [ooapi-gateway-configurator.web :as web]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.mock.request :refer [request]]))

(def app (-> {:auth {:group-ids #{"my-group"}}}
             (#'web/mk-handler)
             (wrap-defaults (-> site-defaults
                                (assoc-in [:params :keywordize] false)
                                (assoc-in [:security :anti-forgery] false)))))

(defn assoc-user-info [req]
  (assoc req
         :oauth2/user-info {:conext {:edumember_is_member_of ["my-group"]}}))

(defn do-get [uri]
  (app (-> (request :get uri) (assoc-user-info))))

(deftest handler
  (testing "GET /"
    (let [res (do-get "/")]
      (is (= http-status/ok (:status res))
          "OK")))

  (testing "Not found"
    (is (= http-status/not-found
           (:status (do-get "/does-not-exist")))))

  (testing "Authorization"
    (let [f (fn [& args]
              (-> (apply request args)
                  (app)
                  :status))]
      (is (= http-status/ok (f :get "/userinfo")))
      (is (= http-status/unauthorized (f :get "/")))
      (is (= http-status/unauthorized (f :get "/applications/")))
      (is (= http-status/unauthorized (f :get "/institutions/")))
      (is (= http-status/unauthorized (f :get "/network/")))
      (is (= http-status/unauthorized (f :post "/versioning"))))
    (let [f (fn [& args]
              (-> (apply request args)
                  (assoc-user-info)
                  (app)
                  :status))]
      (is (= http-status/ok (f :get "/userinfo")))
      (is (= http-status/ok (f :get "/")))
      (is (= http-status/ok (f :get "/applications/")))
      (is (= http-status/ok (f :get "/institutions/")))
      (is (= http-status/ok (f :get "/network/")))
      (is (= http-status/see-other (f :post "/versioning"))))))
