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

(ns ooapi-gateway-configurator.institutions-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [ooapi-gateway-configurator.http :as http]
            [ooapi-gateway-configurator.institutions :as institutions]
            [ooapi-gateway-configurator.model :as model]
            [ooapi-gateway-configurator.store-test :as store-test]
            [ring.mock.request :refer [request]]))

(def ^:dynamic *app*)

(use-fixtures :each
  (fn [f]
    (let [state (store-test/setup)]
      (binding [*app* (store-test/wrap institutions/handler (assoc state :read-only? true))]
        (f))
      (store-test/teardown state))))

(defn do-get [uri]
  (*app* (request :get uri)))

(defn do-post [uri & [params]]
  (*app* (request :post uri params)))

(defn do-delete [uri & [params]]
  (*app* (request :delete uri params)))

(deftest index-page
  (testing "GET /institutions/"
    (let [res (do-get "/institutions/")]
      (is (= http/ok (:status res))
          "OK")
      (is (re-find #"Basic.Auth.Backend" (:body res))
          "lists Basic.Auth.Backend"))))

(deftest detail-page
  (testing "GET /institutions/Basic.Auth.Backend"
    (is (= http/not-found (:status (do-get "/institutions/DoesNotExist")))
        "Not Found")
    (let [res (do-get "/institutions/Basic.Auth.Backend")]
      (is (= http/ok (:status res))
          "OK")
      (is (re-find #"Edit Institution" (:body res))
          "includes header")
      (is (re-find #"Basic.Auth.Backend" (:body res))
          "includes institution ID")
      (is (re-find #"https://example.com/test-backend" (:body res))
          "include institution URL"))))

(deftest delete-institution
  (testing "DELETE /institutions/Basic.Auth.Backend"
    (is (= http/not-found (:status (do-delete "/institutions/DoesNotExist")))
        "Not Found")
    (let [res (do-delete "/institutions/Basic.Auth.Backend")]
      (is (= http/see-other (:status res))
          "see other")
      (is (= "http://localhost/institutions/" (-> res :headers (get "Location")))
          "redirected back to institutions list")
      (is (:flash res)
          "has a message about deletion")
      (is (= :db/retractEntity (-> res ::model/tx ffirst))
          "has delete-application command for Basic.Auth.Backend"))))


(defn- testing-errors
  [path & fns]
  (testing "errors"
    (let [res (do-post path
                       {:id  "test"
                        :url "bad"})]
      (is (= http/not-acceptable (:status res))
          "not acceptable")
      (is (re-find #"flash" (:body res))
          "includes error message")
      (doseq [f fns] (f res)))))

(defn- testing-add-header
  [path & fns]
  (testing "add header"
    (let [res (do-post path
                       {"id"         "test"
                        "url"        "https://example.com/test"
                        "add-header" ".."})]
      (is (= http/ok (:status res))
          "OK")
      (is (re-find #"<li [^>]*?class=\"header\"" (:body res))
          "has header input")
      (doseq [f fns] (f res)))))

(defn- testing-delete-header
  [path & fns]
  (testing "delete header"
    (let [res (do-post path
                       {"id"              "test"
                        "url"             "https://example.com/test"
                        "header-names"    ["First" "Second"]
                        "header-values"   ["1" "2"]
                        "delete-header-0" ".."})]
      (is (= http/ok (:status res))
          "OK")
      (is (= 1 (count (re-seq #"<li [^>]*?class=\"header\"" (:body res))))
          "has header input")
      (let [input (re-find #"<input [^>]*?name=\"header-names\[\]\".*?>" (:body res))]
        (is input
            "has header-name input")
        (is (re-find #"value=\"Second\"" input)
            "has header-name input"))
      (doseq [f fns] (f res)))))

(defn- testing-select-auth
  [path & fns]
  (testing "select-auth"
    (let [res (do-post path
                       {"id"          "test"
                        "url"         "https://example.com/test"
                        "auth"        "basic"
                        "select-auth" ".."})]
      (is (= http/ok (:status res))
          "OK")
      (is (re-find #"<input.*?name=\"basic-auth-user\"" (:body res))
          "has basic auth input")
      (doseq [f fns] (f res)))
    (let [res (do-post path
                       {"id"          "test"
                        "url"         "https://example.com/test"
                        "auth"        "oauth"
                        "select-auth" ".."})]
      (is (= http/ok (:status res))
          "OK")
      (is (re-find #"<input.*?name=\"oauth-url\"" (:body res))
          "has oauth URL input")
      (doseq [f fns] (f res)))))

(deftest update-institution
  (testing "POST /institutions/Basic.Auth.Backend"
    (is (= http/not-found (:status (do-post "/institutions/DoesNotExist")))
        "Not Found")
    (let [res (do-post "/institutions/Basic.Auth.Backend"
                       {:id              "test"
                        :url             "https://example.com/test"
                        :auth            "basic"
                        :basic-auth-user "fred"
                        :basic-auth-pass "betty"
                        :header-names    ["X-test" "X-other" ""]
                        :header-values   ["1" "2" ""]})]
      (is (= http/see-other (:status res))
          "see other")
      (is (= "http://localhost/institutions/" (-> res :headers (get "Location")))
          "redirected back to institutions list")
      (is (:flash res)
          "has a message about update")
      (is (= :db/add (-> res ::model/tx first first))
          "rename entity")
      (is (= #:institution{:id          "test"
                        :url           "https://example.com/test"
                        :proxy-options {:auth    "fred:betty"
                                        :headers {"X-test" "1", "X-other" "2"}}}
             (-> res ::model/tx last))
          "updates Basic.Auth.Backend")))

  (testing-errors
   "/institutions/Basic.Auth.Backend"
   (fn [res]
     (is (re-find #"Edit Institution" (:body res))
         "has header")))

  (testing-add-header
   "/institutions/Basic.Auth.Backend"
   (fn [res]
     (is (re-find #"<h2>Edit Institution</h2>" (:body res))
         "has edit title")))

  (testing-delete-header
   "/institutions/Basic.Auth.Backend"
   (fn [res]
     (is (re-find #"<h2>Edit Institution</h2>" (:body res))
         "has edit title")))

  (testing-select-auth
   "/institutions/Basic.Auth.Backend"
   (fn [res]
     (is (re-find #"<h2>Edit Institution</h2>" (:body res))
         "has edit title"))))

(deftest new-institution
  (testing "GET /institutions/new"
    (let [res (do-get "/institutions/new")]
      (is (= http/ok (:status res))
          "OK")
      (is (re-find #"<h2>Create Institution</h2>" (:body res))
          "has create title")))

  (testing "POST /institutions/new"
    (let [res (do-post "/institutions/new"
                       {"id"                  "test"
                        "url"                 "https://example.com/test"
                        "auth"                "oauth"
                        "oauth-url"           "https://oauth/test"
                        "oauth-client-id"     "fred"
                        "oauth-client-secret" "wilma"})]
      (is (= http/see-other (:status res))
          "see other")
      (is (= "http://localhost/institutions/" (-> res :headers (get "Location")))
          "redirected back to institutions list")
      (is (:flash res)
          "has a message about creation")
            (is (= #:institution{:proxy-options
                        {:oauth2
                         {:clientCredentials
                          {:tokenEndpoint
                           {:url "https://oauth/test",
                            :params
                            {:grant_type "client_credentials",
                             :client_id "fred",
                             :client_secret "wilma"}}}}},
                        :url "https://example.com/test",
                        :id "test"}
             (-> res ::model/tx first))
          "will insert new institution")))

  (testing-errors
   "/institutions/new"
   (fn [res]
     (is (re-find #"Create Institution" (:body res))
         "has header")))

  (testing-add-header
   "/institutions/new"
   (fn [res]
     (is (re-find #"Create Institution" (:body res))
         "has header")))

  (testing-delete-header
   "/institutions/new"
   (fn [res]
     (is (re-find #"Create Institution" (:body res))
         "has header")))

  (testing-select-auth
   "/institutions/new"
   (fn [res]
     (is (re-find #"Create Institution" (:body res))
         "has header"))))
