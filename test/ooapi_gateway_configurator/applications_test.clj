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

(ns ooapi-gateway-configurator.applications-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [nl.jomco.http-status-codes :as http-status]
            [ooapi-gateway-configurator.applications :as applications]
            [ooapi-gateway-configurator.model :as model]
            [ooapi-gateway-configurator.store-test :as store-test]
            [ring.mock.request :refer [request]]))

(def ^:dynamic *app* nil)

(use-fixtures :each
  (fn [f]
    (let [state (store-test/setup)]
      (binding [*app* (store-test/wrap applications/handler (assoc state :read-only? true))]
        (f))
      (store-test/teardown state))))

(defn do-get [uri]
  (*app* (request :get uri)))

(defn do-post [uri & [params]]
  (*app* (request :post uri params)))

(defn do-delete [uri & [params]]
  (*app* (request :delete uri params)))

(deftest index-page
  (testing "GET /applications/"
    (let [res (do-get "/applications/")]
      (is (= http-status/ok (:status res))
          "OK")
      (is (re-find #"fred" (:body res))
          "lists fred")
      (is (re-find #"<div[^>]+class=\"notes\"[^>]*>Pebbles &lt;3 Bamm-Bamm" (:body res))
          "includes notes"))))

(deftest detail-page
  (testing "GET /applications/pebbles"
    (is (= http-status/not-found (:status (do-get "/applications/DoesNotExist")))
        "Not Found")
    (let [res (do-get "/applications/pebbles")]
      (is (= http-status/ok (:status res))
          "OK")
      (is (re-find #"Edit Application" (:body res))
          "includes header")
      (is (re-find #"pebbles" (:body res))
          "includes application ID")
      (is (re-find #"<textarea[^>]+name=\"notes\"[^>]*>Pebbles &lt;3 Bamm-Bamm"
                   (:body res))
          "includes form file with notes for institution")
      (is (re-find #"<input[^>]*value=\"Reset password\"" (:body res))
          "has reset password button"))))

(deftest delete-application
  (testing "POST /applications/fred"
    (is (= http-status/not-found (:status (do-delete "/applications/DoesNotExist")))
        "Not Found")
    (let [res (do-delete "/applications/fred")]
      (is (= http-status/see-other (:status res))
          "see other")
      (is (= "." (-> res :headers (get "Location")))
          "redirected back to applications list")
      (is (:flash res)
          "has a message about deletion")
      (is (= :db/retractEntity (-> res ::model/tx ffirst))))))

(deftest update-application
  (testing "POST /applications/fred"
    (is (= http-status/not-found (:status (do-post "/applications/DoesNotExist")))
        "Not Found")
    (let [res (do-post "/applications/fred"
                       {"id" "betty"})]
      (is (= http-status/see-other (:status res))
          "see other")
      (is (= "." (-> res :headers (get "Location")))
          "redirected back to applications list")
      (is (:flash res)
          "has a message about update")
      (is (= :db/add (-> res ::model/tx first first))
          "rename")
      (is (= #:app {:id "betty", :notes nil} (-> res ::model/tx last))
          "update entity")))

  (testing "errors"
    (let [res (do-post "/applications/fred"
                       {"id" ""})]
      (is (= http-status/not-acceptable (:status res))
          "not acceptable")
      (is (re-find #"flash" (:body res))
          "includes error message")))

  (testing "reset password"
    (let [res (do-post "/applications/fred"
                       {"id"             "fred"
                        "reset-password" ".."})]
      (is (= http-status/ok (:status res))
          "OK")
      (is (re-find #"<input [^>]*?name=\"password\"" (:body res))
          "has password input"))))

(deftest new-application
  (testing "GET /applications/new"
    (let [res (do-get "/applications/new")]
      (is (= http-status/ok (:status res))
          "OK")
      (is (re-find #"Create Application" (:body res))
          "includes header")
      (is (re-find #"<input[^>]*name=\"password\"" (:body res))
          "include password")))

  (testing "POST /applications/new"
    (let [res (do-post "/applications/new"
                       {"id"       "test"
                        "password" "0123456789abcdef0123456789abcdef"})]
      (is (= http-status/see-other (:status res))
          "see other")
      (is (= "." (-> res :headers (get "Location")))
          "redirected back to applications list")
      (is (:flash res)
          "has a message about creation")

      (is (= "test"
             (-> res ::model/tx first :app/id))
          "created entity"))))

(deftest hash-password
  (is (= "083aa7e8c594c639ca378dce248174d5e74bb6d64ad695ccb69ebda1d7278cf6"
         (#'applications/hash-password "fredfredfredfredfredfredfredfred"
                                       "wilmawilmawilmawilmawilmawilmawi"))
      "reference values match")
  (let [pass0 (#'applications/generate-random-string)
        salt0 (#'applications/generate-random-string)
        pass1 (#'applications/generate-random-string)
        salt1 (#'applications/generate-random-string)]
    (is (= (#'applications/hash-password pass0 salt0)
           (#'applications/hash-password pass0 salt0)))
    (is (not= (#'applications/hash-password pass0 salt0)
              (#'applications/hash-password pass0 salt1)))
    (is (not= (#'applications/hash-password pass0 salt0)
              (#'applications/hash-password pass1 salt0)))))
