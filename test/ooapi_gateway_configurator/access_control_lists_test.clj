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

(ns ooapi-gateway-configurator.access-control-lists-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [ooapi-gateway-configurator.access-control-lists :as access-control-lists]
            [ooapi-gateway-configurator.http :as http]
            [ooapi-gateway-configurator.model :as model]
            [ooapi-gateway-configurator.store-test :as store-test]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.mock.request :refer [request]]))

(def ^:dynamic *app*)

(use-fixtures :each
  (fn [f]
    (let [state (store-test/setup)]
      (binding [*app* (-> access-control-lists/handler
                          wrap-nested-params
                          (store-test/wrap state))]
        (f))
      (store-test/teardown state))))

(defn do-get [uri]
  (*app* (request :get uri)))

(defn do-post [uri & [params]]
  (*app* (request :post uri params)))

(deftest do-detail
  (testing "GET /applications/fred/access-control-list"
    (is (= http/not-found (:status (do-get "/applications/DoesNotExist/access-control-list")))
        "Not Found")
    (let [res (do-get "/applications/fred/access-control-list")]
      (is (= http/ok (:status res))
          "OK")
      (is (re-find #"Edit Access Control List" (:body res))
          "includes header")
      (is (re-find #"fred" (:body res))
          "includes application ID")
      (is (re-find #"Basic.Auth.Backend" (:body res))
          "includes institution ID")))
  (testing "GET /institutions/Basic.Auth.Backend/access-control-list"
    (is (= http/not-found (:status (do-get "/institutions/DoesNotExist/access-control-list")))
        "Not Found")
    (let [res (do-get "/institutions/Basic.Auth.Backend/access-control-list")]
      (is (= http/ok (:status res))
          "OK")
      (is (re-find #"Basic.Auth.Backend" (:body res))
          "includes institution ID")
      (is (re-find #"Basic.Auth.Backend" (:body res))
          "includes institution ID"))))

(deftest do-update
  (testing "applications"
    (testing "POST /applications/fred/access-control-list"
      (is (= http/not-found (:status (do-post "/applications/DoesNotExist/access-control-list")))
          "Not Found")
      (let [res (do-post "/applications/fred/access-control-list" {"access-control-list[Basic.Auth.Backend][]" "/"})]
        (is (= http/see-other (:status res))
            "see other")
        (is (= "../fred" (-> res :headers (get "Location")))
            "redirected back to access-control-lists list")
        (is (:flash res)
            "has a message about update")
        (is (seq (::model/tx res))
            "updates acl")))

    (testing "select-all"
      (let [res (do-post "/applications/fred/access-control-list"
                         {"select-all-Basic.Auth.Backend"          ".."
                          "access-control-list[Api.Key.Backend][]" "/"})]
        (is (= http/ok (:status res))
            "OK")
        (let [basic-auth-backend-checkboxes (re-seq #"<input [^>]*?\bname=\"access-control-list\[Basic.Auth.Backend\]\[\]\".*?>"
                                                    (:body res))]
          (is (seq basic-auth-backend-checkboxes)
              "got checkboxes for Basic.Auth.Backend")
          (is (seq (filter #(re-find #"\bchecked\b" %) basic-auth-backend-checkboxes))
              "got checked checkboxes")
          (is (empty? (filter (complement #(re-find #"\bchecked\b" %)) basic-auth-backend-checkboxes))
              "got no unchecked checkboxes"))
        (is (= 1 (->> (:body res)
                      (re-seq #"<input [^>]*?\bname=\"access-control-list\[Api.Key.Backend\]\[\]\".*?>")
                      (filter #(re-find #"\bchecked\b" %))
                      count))
            "only 1 checked for Api.Key.Backend")))

    (testing "select-none"
      (let [res (do-post "/applications/fred/access-control-list"
                         {"select-none-Basic.Auth.Backend"            ".."
                          "access-control-list[Basic.Auth.Backend][]" "/"
                          "access-control-list[Api.Key.Backend][]"    "/"})]
        (is (= http/ok (:status res))
            "OK")
        (let [basic-auth-backend-checkboxes (re-seq #"<input [^>]*?\bname=\"access-control-list\[Basic.Auth.Backend\]\[\]\".*?>"
                                                    (:body res))]
          (is (seq basic-auth-backend-checkboxes)
              "got checkboxes for Basic.Auth.Backend")
          (is (seq (filter (complement #(re-find #"\bchecked\b" %)) basic-auth-backend-checkboxes))
              "none checked"))
        (is (= 1 (->> (:body res)
                      (re-seq #"<input [^>]*?\bname=\"access-control-list\[Api.Key.Backend\]\[\]\".*?>")
                      (filter #(re-find #"\bchecked\b" %))
                      count))
            "only 1 checked for Api.Key.Backend"))))

  (testing "institutions"
    (testing "POST /institutions/Basic.Auth.Backend/access-control-list"
      (is (= http/not-found (:status (do-post "/institutions/DoesNotExist/access-control-list")))
          "Not Found")
      (let [res (do-post "/institutions/Basic.Auth.Backend/access-control-list" {"access-control-list[fred][]" "/"})]
        (is (= http/see-other (:status res))
            "see other")
        (is (= "../Basic.Auth.Backend" (-> res :headers (get "Location")))
            "redirected back to access-control-lists list")
        (is (:flash res)
            "has a message about update")
        (is (seq (::model/tx res))
            "updates acl")))

    (testing "select-all"
      (let [res (do-post "/institutions/Basic.Auth.Backend/access-control-list"
                         {"select-all-fred"               ".."
                          "access-control-list[barney][]" "/"})]
        (is (= http/ok (:status res))
            "OK")
        (let [basic-auth-backend-checkboxes (re-seq #"<input [^>]*?\bname=\"access-control-list\[fred\]\[\]\".*?>"
                                                    (:body res))]
          (is (seq basic-auth-backend-checkboxes)
              "got checkboxes for fred")
          (is (seq (filter #(re-find #"\bchecked\b" %) basic-auth-backend-checkboxes))
              "got checked checkboxes")
          (is (empty? (filter (complement #(re-find #"\bchecked\b" %)) basic-auth-backend-checkboxes))
              "got no unchecked checkboxes"))
        (is (= 1 (->> (:body res)
                      (re-seq #"<input [^>]*?\bname=\"access-control-list\[barney\]\[\]\".*?>")
                      (filter #(re-find #"\bchecked\b" %))
                      count))
            "only 1 checked for barney")))

    (testing "select-none"
      (let [res (do-post "/institutions/Basic.Auth.Backend/access-control-list"
                         {"select-none-fred"              ".."
                          "access-control-list[fred][]"   "/"
                          "access-control-list[barney][]" "/"})]
        (is (= http/ok (:status res))
            "OK")
        (let [basic-auth-backend-checkboxes (re-seq #"<input [^>]*?\bname=\"access-control-list\[fred\]\[\]\".*?>"
                                                    (:body res))]
          (is (seq basic-auth-backend-checkboxes)
              "got checkboxes for fred")
          (is (seq (filter (complement #(re-find #"\bchecked\b" %)) basic-auth-backend-checkboxes))
              "none checked"))
        (is (= 1 (->> (:body res)
                      (re-seq #"<input [^>]*?\bname=\"access-control-list\[barney\]\[\]\".*?>")
                      (filter #(re-find #"\bchecked\b" %))
                      count))
            "only 1 checked for barney")))))
