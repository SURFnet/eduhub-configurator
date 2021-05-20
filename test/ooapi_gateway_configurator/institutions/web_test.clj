(ns ooapi-gateway-configurator.institutions.web-test
  (:require [clj-yaml.core :as yaml]
            [clojure.java.io :as io]
            [clojure.test :refer :all]
            [ooapi-gateway-configurator.http :as http]
            [ooapi-gateway-configurator.institutions.store :as store]
            [ooapi-gateway-configurator.institutions.web :as sut]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.mock.request :refer [request]])
  (:import java.io.File))

(def ^:dynamic *app*)

(defn setup-app [f]
  (let [temp-file (File/createTempFile "institutions-test" "yml")]
    (spit temp-file (slurp (io/resource "test/gateway.config.yml")))
    (binding [*app* (-> sut/handler
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
  (testing "GET /institutions"
    (let [res (do-get "/institutions")]
      (is (= http/ok (:status res))
          "OK")
      (is (re-find #"BasicAuthBackend" (:body res))
          "lists BasicAuthBackend"))))

(deftest do-detail
  (testing "GET /institutions/BasicAuthBackend"
    (let [res (do-get "/institutions/BasicAuthBackend")]
      (is (= http/ok (:status res))
          "OK")
      (is (re-find #"BasicAuthBackend" (:body res))
          "includes institution ID")
      (is (re-find #"https://example.com/test-backend" (:body res))
          "include institution URL"))))

(deftest do-delete
  (testing "POST /institutions/BasicAuthBackend/delete"
    (let [res (do-post "/institutions/BasicAuthBackend/delete")]
      (is (= http/see-other (:status res))
          "see other")
      (is (= "http://localhost/institutions" (-> res :headers (get "Location")))
          "redirected back to institutions list")
      (is (:flash res)
          "has a message about deletion")
      (is (:institutions res)
          "has new set of institutions")
      (is (not (get (:institutions res) :BasicAuthBackend))
          "BasicAuthBackend is missing"))))

(deftest do-update
  (testing "POST /institutions/BasicAuthBackend/update"
    (let [res (do-post "/institutions/BasicAuthBackend/update"
                       {:id              "test"
                        :url             "https://example.com/test"
                        :auth            "basic"
                        :basic-auth-user "fred"
                        :basic-auth-pass "betty"
                        :header-names    ["X-test" "X-other" ""]
                        :header-values   ["1" "2" ""]})]
      (is (= http/see-other (:status res))
          "see other")
      (is (= "http://localhost/institutions" (-> res :headers (get "Location")))
          "redirected back to institutions list")
      (is (:flash res)
          "has a message about deletion")
      (is (:institutions res)
          "has new set of institutions")
      (is (not (get (:institutions res) :BasicAuthBackend))
          "BasicAuthBackend is missing because it's renamed")
      (is (= "https://example.com/test"
             (get-in res [:institutions :test :url]))
          "got URL")
      (is (= ["1" "2"]
             [(get-in res [:institutions :test :proxyOptions :headers "X-test"])
              (get-in res [:institutions :test :proxyOptions :headers "X-other"])])
          "got headers")
      (is (= "fred:betty"
             (get-in res [:institutions :test :proxyOptions :auth]))
          "got new basic auth credentials")))

  (testing "errors"
    (let [res (do-post "/institutions/BasicAuthBackend/update"
                       {:id  "test"
                        :url "bad"})]
      (is (= http/not-acceptable (:status res))
          "not acceptable")
      (is (re-find #"flash" (:body res))
          "includes error message")))

  (testing "add header"
    (let [res (do-post "/institutions/BasicAuthBackend/update"
                       {:id         "test"
                        :url        "https://example.com/test"
                        :add-header ".."})]
      (is (= http/ok (:status res))
          "OK")
      (is (re-find #"<li [^>]*?class=\"header\"" (:body res))
          "has header input")))

  (testing "delete header"
    (let [res (do-post "/institutions/BasicAuthBackend/update"
                       {:id              "test"
                        :url             "https://example.com/test"
                        :header-names    ["First" "Second"]
                        :header-values   ["1" "2"]
                        :delete-header-0 ".."})]
      (is (= http/ok (:status res))
          "OK")
      (is (= 1 (count (re-seq #"<li [^>]*?class=\"header\"" (:body res))))
          "has header input")
      (let [input (re-find #"<input [^>]*?name=\"header-names\[\]\".*?>" (:body res))]
        (is input
            "has header-name input")
        (is (re-find #"value=\"Second\"" input)
            "has header-name input"))))

  (testing "select-auth"
    (let [res (do-post "/institutions/BasicAuthBackend/update"
                       {:id          "test"
                        :url         "https://example.com/test"
                        :auth        "basic"
                        :select-auth ".."})]
      (is (= http/ok (:status res))
          "OK")
      (is (re-find #"<input.*?name=\"basic-auth-user\"" (:body res))
          "has basic auth input"))
    (let [res (do-post "/institutions/BasicAuthBackend/update"
                       {:id          "test"
                        :url         "https://example.com/test"
                        :auth        "oauth"
                        :select-auth ".."})]
      (is (= http/ok (:status res))
          "OK")
      (is (re-find #"<input.*?name=\"oauth-url\"" (:body res))
          "has oauth URL input"))))

(deftest do-create
  (testing "GET /institutions/new"
    (let [res (do-get "/institutions/new")]
      (is (= http/ok (:status res))
          "OK")
      (is (re-find #"New institution" (:body res))
          "includes header")))

  (testing "POST /institutions/create"
    (let [res (do-post "/institutions/create"
                       {:id                  "test"
                        :url                 "https://example.com/test"
                        :auth                "oauth"
                        :oauth-url           "https://oauth/test"
                        :oauth-client-id     "fred"
                        :oauth-client-secret "wilma"})]
      (is (= http/see-other (:status res))
          "see other")
      (is (= "http://localhost/institutions" (-> res :headers (get "Location")))
          "redirected back to institutions list")
      (is (:flash res)
          "has a message about deletion")
      (is (:institutions res)
          "has new set of institutions")
      (is (= "https://example.com/test"
             (get-in res [:institutions :test :url]))
          "got URL")
      (is (= "https://oauth/test"
             (get-in res [:institutions :test :proxyOptions
                          :oauth2 :clientCredentials :tokenEndpoint :url]))
          "got oauth token URL")
      (is (= "fred"
             (get-in res [:institutions :test :proxyOptions
                          :oauth2 :clientCredentials :tokenEndpoint :params :client_id]))
          "got oauth client id")
      (is (= "wilma"
             (get-in res [:institutions :test :proxyOptions
                          :oauth2 :clientCredentials :tokenEndpoint :params :client_secret]))
          "got oauth client secret"))))

(def test-institutions (store/fetch "resources/test/gateway.config.yml"))

(deftest ->form->
  (testing "->form and form-> round trip")
  (doseq [[id institution] test-institutions]
    (is (= (yaml/generate-string institution) ;; yaml it to avoid problems with header names becoming keywords
           (yaml/generate-string (sut/form-> (sut/->form institution id)))))))

(deftest form-errors
  (testing "test institutions do not have errors"
    (doseq [institution (map (fn [[id m]] (sut/->form m id)) test-institutions)]
      (is (not (sut/form-errors institution))))))
