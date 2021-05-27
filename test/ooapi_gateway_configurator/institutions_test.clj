(ns ooapi-gateway-configurator.institutions-test
  (:require [clj-yaml.core :as yaml]
            [clojure.test :refer :all]
            [ooapi-gateway-configurator.http :as http]
            [ooapi-gateway-configurator.institutions :as institutions]
            [ooapi-gateway-configurator.state :as state]
            [ooapi-gateway-configurator.store-test :as store-test]
            [ring.mock.request :refer [request]]))

(def ^:dynamic *app*)

(use-fixtures :each
  (fn [f]
    (let [state (store-test/setup)]
      (binding [*app* (store-test/wrap institutions/handler state)]
        (f))
      (store-test/teardown state))))

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
    (is (= http/not-found (:status (do-get "/institutions/DoesNotExist")))
        "Not Found")
    (let [res (do-get "/institutions/BasicAuthBackend")]
      (is (= http/ok (:status res))
          "OK")
      (is (re-find #"BasicAuthBackend" (:body res))
          "includes institution ID")
      (is (re-find #"https://example.com/test-backend" (:body res))
          "include institution URL"))))

(deftest do-delete
  (testing "POST /institutions/BasicAuthBackend/delete"
    (is (= http/not-found (:status (do-post "/institutions/DoesNotExist/delete")))
        "Not Found")
    (let [res (do-post "/institutions/BasicAuthBackend/delete")]
      (is (= http/see-other (:status res))
          "see other")
      (is (= "http://localhost/institutions" (-> res :headers (get "Location")))
          "redirected back to institutions list")
      (is (:flash res)
          "has a message about deletion")
      (is (= [::state/delete-institution "BasicAuthBackend"] (-> res ::state/command))
          "has delete-application command for BasicAuthBackend"))))

(deftest do-update
  (testing "POST /institutions/BasicAuthBackend/update"
    (is (= http/not-found (:status (do-post "/institutions/DoesNotExist/update")))
        "Not Found")
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
          "has a message about update")
      (is (= [::state/update-institution "BasicAuthBackend"
              {:id  "test",
               :url "https://example.com/test",
               :proxyOptions {:auth "fred:betty"
                              :headers {"X-test" "1", "X-other" "2"}}}]
             (-> res ::state/command))
          "has update-institution command for BasicAuthBackend")))

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
          "has a message about creation")
      (is (= ::state/create-institution (-> res ::state/command first))
          "has create-institution command")
      (is (= {:id                  "test"
              :url                 "https://example.com/test"
              :proxyOptions {:oauth2
                             {:clientCredentials
                              {:tokenEndpoint
                               {:url "https://oauth/test",
                                :params
                                {:grant_type "client_credentials",
                                 :client_id "fred",
                                 :client_secret "wilma"}}}}}}
             (-> res ::state/command last))))))

(def test-institutions
  {:BasicAuthBackend {:id           "BasicAuthBackend"
                      :url          "https://example.com/test-backend"
                      :proxyOptions {:auth "fred:wilma"}}
   :Oauth2Backend    {:id           "Oauth2Backend"
                      :url          "https://example.com/other-test-backend"
                      :proxyOptions {:oauth2 {:clientCredentials {:tokenEndpoint {:url    "http://localhost:8084/mock/token"
                                                                                  :params {:grant_type    "client_credentials"
                                                                                           :client_id     "fred"
                                                                                           :client_secret "wilma"}}}}}}
   :ApiKeyBackend    {:id           "ApiKeyBackend"
                      :url          "https://example.com/api-key-backend"
                      :proxyOptions {:headers {:Authorization "Bearer test-api-key"}}}})

(deftest ->form->
  (testing "->form and form-> round trip")
  (doseq [[id institution] test-institutions]
    (is (= (yaml/generate-string institution) ;; yaml it to avoid problems with header names becoming keywords
           (yaml/generate-string (#'institutions/form-> (#'institutions/->form institution id)))))))

(deftest form-errors
  (testing "test institutions do not have errors"
    (doseq [institution (map (fn [[id m]] (#'institutions/->form m id)) test-institutions)]
      (is (not (#'institutions/form-errors institution))))))
