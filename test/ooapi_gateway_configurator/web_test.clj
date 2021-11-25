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
            [ooapi-gateway-configurator.http :as http]
            [ooapi-gateway-configurator.web :as web]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.mock.request :refer [request]]))

(def ^:dynamic *app*)

(def app (-> {:auth {:group-ids #{"my-group"}}}
             (web/mk-handler)
             (wrap-defaults (assoc-in site-defaults [:params :keywordize] false))))

(defn do-get [uri]
  (app (-> (request :get uri)
           (assoc :oauth2/user-info {:conext {:edumember_is_member_of ["my-group"]}}))))

(deftest handler
  (testing "GET /"
    (let [res (do-get "/")]
      (is (= http/ok (:status res))
          "OK"))))
