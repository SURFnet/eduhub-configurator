(ns ooapi-gateway-configurator.applications-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [ooapi-gateway-configurator.applications :as sut]
            [ooapi-gateway-configurator.http :as http]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.mock.request :refer [request]])
  (:import java.io.File))

(def ^:dynamic *json-fname*)
(def ^:dynamic *app*)

(defn setup-app [f]
  (let [temp-file (File/createTempFile "applications-test" "json")]
    (spit temp-file (slurp (io/resource "test/credentials.json")))
    (binding [*json-fname* (.getPath temp-file)
              *app*        (-> sut/handler
                               (sut/wrap (.getPath temp-file))
                               (wrap-defaults (dissoc site-defaults :security)))]
      (f))
    (.delete temp-file)))

(use-fixtures :each setup-app)

(defn do-get [uri]
  (*app* (request :get uri)))

(defn do-post [uri & [params]]
  (*app* (request :post uri params)))

(deftest do-index
  (testing "GET /applications"
    (let [res (do-get "/applications")]
      (is (= http/ok (:status res))
          "OK")
      (is (re-find #"fred" (:body res))
          "lists fred"))))

(deftest do-detail
  (testing "GET /applications/fred"
    (let [res (do-get "/applications/fred")]
      (is (= http/ok (:status res))
          "OK")
      (is (re-find #"fred" (:body res))
          "includes application ID"))))

(deftest do-delete
  (testing "POST /applications/fred/delete"
    (let [res (do-post "/applications/fred/delete")]
      (is (= http/see-other (:status res))
          "see other")
      (is (= "http://localhost/applications" (-> res :headers (get "Location")))
          "redirected back to applications list")
      (is (:flash res)
          "has a message about deletion")
      (is (:applications res)
          "has new set of applications")
      (is (not (get (:applications res) :fred))
          "fred is missing"))))

(deftest do-update
  (testing "POST /applications/fred/update"
    (let [res (do-post "/applications/fred/update"
                       {:id "betty"})]
      (is (= http/see-other (:status res))
          "see other")
      (is (= "http://localhost/applications" (-> res :headers (get "Location")))
          "redirected back to applications list")
      (is (:flash res)
          "has a message about update")
      (is (:applications res)
          "has new set of applications")
      (is (not (get (:applications res) :fred))
          "fred is missing because it's renamed")
      (is (and (get-in res [:applications :betty :passwordHash])
               (get-in res [:applications :betty :passwordSalt]))
          "got password hash")))

  (testing "errors"
    (let [res (do-post "/applications/fred/update"
                       {:id ""})]
      (is (= http/not-acceptable (:status res))
          "not acceptable")
      (is (re-find #"flash" (:body res))
          "includes error message")))

  (testing "reset password"
    (let [res (do-post "/applications/fred/update"
                       {:id             "fred"
                        :reset-password ".."})]
      (is (= http/ok (:status res))
          "OK")
      (is (re-find #"<input [^>]*?name=\"password\"" (:body res))
          "has password input"))))

(deftest do-create
  (testing "GET /applications/new"
    (let [res (do-get "/applications/new")]
      (is (= http/ok (:status res))
          "OK")
      (is (re-find #"New application" (:body res))
          "includes header")))

  (testing "POST /applications/create"
    (let [res (do-post "/applications/create"
                       {:id       "test"
                        :password ".."})]
      (is (= http/see-other (:status res))
          "see other")
      (is (= "http://localhost/applications" (-> res :headers (get "Location")))
          "redirected back to applications list")
      (is (:flash res)
          "has a message about creation")
      (is (:applications res)
          "has new set of applications")
      (is (and (get-in res [:applications :test :passwordHash])
               (get-in res [:applications :test :passwordSalt]))
          "got password stuff"))))

(def test-applications (#'sut/fetch "resources/test/credentials.json"))

(deftest form-errors
  (testing "test applications do not have errors"
    (doseq [app (map (fn [[id m]] (#'sut/->form m id)) test-applications)]
      (is (not (#'sut/form-errors app))))))

(deftest fetch
  (let [applications (#'sut/fetch *json-fname*)]
    (is (contains? applications :fred))
    (is (contains? applications :barney))
    (is (contains? applications :bubbles))))

(deftest put
  (testing "round trip"
    (let [before (#'sut/fetch *json-fname*)]
      (#'sut/put *json-fname* before)
      (is (= before (#'sut/fetch *json-fname*)))))

  (testing "setting applications"
    (#'sut/put *json-fname* {:test {:passwordHash "..", :passwordSalt ".."}})
    (let [applications (#'sut/fetch *json-fname*)]
      (is (= 1 (count applications)))
      (is (= {:test {:passwordHash "..", :passwordSalt ".."}}
             applications)))))

(deftest hash-password
  (is (= "8a8c6f4ad3d53a19d8847abfc693fd0331d22fa2cbab0e1eea34e39bad8be3b9"
         (#'sut/hash-password "pass" "salt"))
      "reference values match")
  (let [pass0 (#'sut/generate-random-string)
        salt0 (#'sut/generate-random-string)
        pass1 (#'sut/generate-random-string)
        salt1 (#'sut/generate-random-string)]
    (is (= (#'sut/hash-password pass0 salt0)
           (#'sut/hash-password pass0 salt0)))
    (is (not= (#'sut/hash-password pass0 salt0)
              (#'sut/hash-password pass0 salt1)))
    (is (not= (#'sut/hash-password pass0 salt0)
              (#'sut/hash-password pass1 salt0)))))
