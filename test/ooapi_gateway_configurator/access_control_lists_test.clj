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
  (:require [clj-yaml.core :as yaml]
            [clojure.test :refer :all]
            [ooapi-gateway-configurator.http :as http]
            [ooapi-gateway-configurator.access-control-lists :as access-control-lists]
            [ooapi-gateway-configurator.state :as state]
            [ooapi-gateway-configurator.store-test :as store-test]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.mock.request :refer [request]]))

(def ^:dynamic *app*)

(use-fixtures :each
  (fn [f]
    (let [state (store-test/setup)]
      (binding [*app* (-> access-control-lists/handler
                          wrap-keyword-params
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
      (is (re-find #"BasicAuthBackend" (:body res))
          "includes institution ID")))
  (testing "GET /institutions/BasicAuthBackend/access-control-list"
    (is (= http/not-found (:status (do-get "/institutions/DoesNotExist/access-control-list")))
        "Not Found")
    (let [res (do-get "/institutions/BasicAuthBackend/access-control-list")]
      (is (= http/ok (:status res))
          "OK")
      (is (re-find #"BasicAuthBackend" (:body res))
          "includes institution ID")
      (is (re-find #"BasicAuthBackend" (:body res))
          "includes institution ID"))))

(deftest do-update
  (testing "applications"
    (testing "POST /applications/fred/access-control-list"
      (is (= http/not-found (:status (do-post "/applications/DoesNotExist/access-control-list")))
          "Not Found")
      (let [res (do-post "/applications/fred/access-control-list" {"access-control-list[BasicAuthBackend][]" "/"})]
        (is (= http/see-other (:status res))
            "see other")
        (is (= "http://localhost/applications/fred" (-> res :headers (get "Location")))
            "redirected back to access-control-lists list")
        (is (:flash res)
            "has a message about update")
        (is (= [:ooapi-gateway-configurator.state/update-access-control-list-for-application
                "fred"
                {:BasicAuthBackend #{"/"}, :Oauth2Backend #{}, :ApiKeyBackend #{}}]
               (-> res ::state/command))
            "has update-access-control-list-for-application command for fred")))

    (testing "select-all"
      (let [res (do-post "/applications/fred/access-control-list"
                         {"select-all-BasicAuthBackend"          ".."
                          "access-control-list[ApiKeyBackend][]" "/"})]
        (is (= http/ok (:status res))
            "OK")
        (let [basic-auth-backend-checkboxes (re-seq #"<input [^>]*?\bname=\"access-control-list\[BasicAuthBackend\]\[\]\".*?>"
                                                    (:body res))]
          (is (seq basic-auth-backend-checkboxes)
              "got checkboxes for BasicAuthBackend")
          (is (seq (filter #(re-find #"\bchecked\b" %) basic-auth-backend-checkboxes))
              "got checked checkboxes")
          (is (empty? (filter (complement #(re-find #"\bchecked\b" %)) basic-auth-backend-checkboxes))
              "got no unchecked checkboxes"))
        (is (= 1 (->> (:body res)
                      (re-seq #"<input [^>]*?\bname=\"access-control-list\[ApiKeyBackend\]\[\]\".*?>")
                      (filter #(re-find #"\bchecked\b" %))
                      count))
            "only 1 checked for ApiKeyBackend")))

    (testing "select-none"
      (let [res (do-post "/applications/fred/access-control-list"
                         {"select-none-BasicAuthBackend"          ".."
                          "access-control-list[BasicAuthBackend][]" "/"
                          "access-control-list[ApiKeyBackend][]" "/"})]
        (is (= http/ok (:status res))
            "OK")
        (let [basic-auth-backend-checkboxes (re-seq #"<input [^>]*?\bname=\"access-control-list\[BasicAuthBackend\]\[\]\".*?>"
                                                    (:body res))]
          (is (seq basic-auth-backend-checkboxes)
              "got checkboxes for BasicAuthBackend")
          (is (seq (filter (complement #(re-find #"\bchecked\b" %)) basic-auth-backend-checkboxes))
              "none checked"))
        (is (= 1 (->> (:body res)
                      (re-seq #"<input [^>]*?\bname=\"access-control-list\[ApiKeyBackend\]\[\]\".*?>")
                      (filter #(re-find #"\bchecked\b" %))
                      count))
            "only 1 checked for ApiKeyBackend"))))

  (testing "institutions"
    (testing "POST /institutions/BasicAuthBackend/access-control-list"
      (is (= http/not-found (:status (do-post "/institutions/DoesNotExist/access-control-list")))
          "Not Found")
      (let [res (do-post "/institutions/BasicAuthBackend/access-control-list" {"access-control-list[fred][]" "/"})]
        (is (= http/see-other (:status res))
            "see other")
        (is (= "http://localhost/institutions/BasicAuthBackend" (-> res :headers (get "Location")))
            "redirected back to access-control-lists list")
        (is (:flash res)
            "has a message about update")
        (is (= [:ooapi-gateway-configurator.state/update-access-control-list-for-institution
                "BasicAuthBackend"
                {:fred #{"/"}, :barney #{}, :bubbles #{}}]
               (-> res ::state/command))
            "has update-access-control-list-for-institution command for BasicAuthBackend")))

    (testing "select-all"
      (let [res (do-post "/institutions/BasicAuthBackend/access-control-list"
                         {"select-all-fred"          ".."
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
      (let [res (do-post "/institutions/BasicAuthBackend/access-control-list"
                         {"select-none-fred"          ".."
                          "access-control-list[fred][]" "/"
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

(def test-access-control-lists
  {:fred {:BasicAuthBackend #{"/"}
          :Oauth2Backend    #{"/courses"}
          :ApiKeyBackend    #{"/news-feeds/:newsFeedId"}}})

(def test-institutions
  {:BasicAuthBackend {:url "https://basic.example.com"}
   :Oauth2Backend    {:url "https://oauth.example.com"}
   :ApiKeyBackend    {:url "https://api.example.com"}})

(deftest ->form->
  (testing "->form and form-> round trip")
  (doseq [[id access-control-list] test-access-control-lists]
    (is (= access-control-list
           (#'access-control-lists/form-> (#'access-control-lists/->form access-control-list id)
                                          test-access-control-lists)))))
