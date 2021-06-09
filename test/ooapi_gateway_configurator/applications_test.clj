(ns ooapi-gateway-configurator.applications-test
  (:require [clojure.test :refer :all]
            [ooapi-gateway-configurator.applications :as applications]
            [ooapi-gateway-configurator.http :as http]
            [ooapi-gateway-configurator.state :as state]
            [ooapi-gateway-configurator.store-test :as store-test]
            [ring.mock.request :refer [request]]))

(def ^:dynamic *json-fname*)
(def ^:dynamic *app*)

(use-fixtures :each
  (fn [f]
    (let [state (store-test/setup)]
      (binding [*app* (store-test/wrap applications/handler state)]
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
      (is (= http/ok (:status res))
          "OK")
      (is (re-find #"fred" (:body res))
          "lists fred"))))

(deftest detail-page
  (testing "GET /applications/fred"
    (is (= http/not-found (:status (do-get "/applications/DoesNotExist")))
        "Not Found")
    (let [res (do-get "/applications/fred")]
      (is (= http/ok (:status res))
          "OK")
      (is (re-find #"fred" (:body res))
          "includes application ID"))))

(deftest delete-application
  (testing "POST /applications/fred"
    (is (= http/not-found (:status (do-delete "/applications/DoesNotExist")))
        "Not Found")
    (let [res (do-delete "/applications/fred")]
      (is (= http/see-other (:status res))
          "see other")
      (is (= "http://localhost/applications/"
             (-> res :headers (get "Location")))
          "redirected back to applications list")
      (is (:flash res)
          "has a message about deletion")
      (is (= [::state/delete-application "fred"]
             (-> res ::state/command))
          "has delete-application command for fred"))))

(deftest update-application
  (testing "POST /applications/fred"
    (is (= http/not-found (:status (do-post "/applications/DoesNotExist")))
        "Not Found")
    (let [res (do-post "/applications/fred"
                       {:id "betty"})]
      (is (= http/see-other (:status res))
          "see other")
      (is (= "http://localhost/applications/"
             (-> res :headers (get "Location")))
          "redirected back to applications list")
      (is (:flash res)
          "has a message about update")
      (is (= [::state/update-application "fred" {:id "betty"}]
             (-> res ::state/command))
          "has update-application command for fred")))

  (testing "errors"
    (let [res (do-post "/applications/fred"
                       {:id ""})]
      (is (= http/not-acceptable (:status res))
          "not acceptable")
      (is (re-find #"flash" (:body res))
          "includes error message")))

  (testing "reset password"
    (let [res (do-post "/applications/fred"
                       {:id             "fred"
                        :reset-password ".."})]
      (is (= http/ok (:status res))
          "OK")
      (is (re-find #"<input [^>]*?name=\"password\"" (:body res))
          "has password input"))))

(deftest new-application
  (testing "GET /applications/new"
    (let [res (do-get "/applications/new")]
      (is (= http/ok (:status res))
          "OK")
      (is (re-find #"New application" (:body res))
          "includes header")))

  (testing "POST /applications/new"
    (let [res (do-post "/applications/new"
                       {:id       "test"
                        :password ".."})]
      (is (= http/see-other (:status res))
          "see other")
      (is (= "http://localhost/applications/" (-> res :headers (get "Location")))
          "redirected back to applications list")
      (is (:flash res)
          "has a message about creation")
      (is (= ::state/create-application (-> res ::state/command first))
          "has create-application command")
      (is (and (-> res ::state/command last :passwordSalt)
               (-> res ::state/command last :passwordHash))
          "got password stuff"))))

(deftest hash-password
  (is (= "8a8c6f4ad3d53a19d8847abfc693fd0331d22fa2cbab0e1eea34e39bad8be3b9"
         (#'applications/hash-password "pass" "salt"))
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
