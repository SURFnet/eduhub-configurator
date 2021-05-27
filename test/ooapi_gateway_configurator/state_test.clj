(ns ooapi-gateway-configurator.state-test
  (:require [clojure.test :refer :all]
            [ooapi-gateway-configurator.state :as state]))

(def applications
  {:fred    {:passwordSalt "fred-salt"
             :passwordHash "fred-hash"}
   :barney  {:passwordSalt "barney-salt"
             :passwordHash "barney-hash"}
   :bubbles {:passwordSalt "barney-bubbles"
             :passwordHash "barney-bubble"}})

(def institutions
  {:BasicAuthBackend {:url          "https://example.com/test-backend"
                      :proxyOptions {:auth "fred:wilma"}}
   :Oauth2Backend    {:url          "https://example.com/other-test-backend"
                      :proxyOptions {:oauth2
                                     {:clientCredentials
                                      {:tokenEndpoint
                                       {:url "https://oauth/test",
                                        :params
                                        {:grant_type    "client_credentials",
                                         :client_id     "fred",
                                         :client_secret "wilma"}}}}}}
   :ApiKeyBackend {:url "https://example.com/api-key-backend"
                   :proxyOptions {:headers {:Authorization "Bearer test-api-key"}}}})

(def request {::state/applications applications
            ::state/institutions institutions})

(deftest wrap
  (let [app (state/wrap #(select-keys % [::state/command]))]
    (is (= {} (app {})))

    (testing "application commands"
      (testing "create-application"
        (is (= {:passwordSalt "salt"
                :passwordHash "hash"}
               (-> request
                   (into {::state/command [::state/create-application {:id           "test"
                                                                       :passwordSalt "salt"
                                                                       :passwordHash "hash"}]})
                   (app)
                   (get-in [::state/applications :test])))
            "create a new application"))

      (testing "update-application"
        (is (= {:passwordSalt "new-salt"
                :passwordHash "new-hash"}
               (-> request
                   (into {::state/command [::state/update-application "fred" {:id           "fred"
                                                                              :passwordSalt "new-salt"
                                                                              :passwordHash "new-hash"}]})
                   (app)
                   (get-in [::state/applications :fred])))
            "update changes password salt and hash")
        (let [new-applications (-> request
                                   (into {::state/command [::state/update-application "fred" {:id "wilma"}]})
                                   (app)
                                   (get ::state/applications))]
          (is (= (:fred applications)
                 (:wilma new-applications))
              "update name password data not overwritten")
          (is (= #{:wilma :barney :bubbles}
                 (set (keys new-applications)))
              "fred is replaced by wilma rest is still there"))
        (is (= {:passwordSalt "new-salt"
                :passwordHash "new-hash"}
               (-> request
                   (into {::state/command [::state/update-application "fred" {:id           "wilma"
                                                                              :passwordSalt "new-salt"
                                                                              :passwordHash "new-hash"}]})
                   (app)
                   (get-in [::state/applications :wilma])))
            "update changes both id, password salt and hash"))

      (testing "delete-application"
        (is (= #{:barney :bubbles}
               (-> request
                   (into {::state/command [::state/delete-application "fred"]})
                   (app)
                   (get ::state/applications)
                   keys
                   set)))))

    (testing "institution commands"
      (testing "create-institution"
        (is (= {:url "https://example.com"}
               (-> request
                   (into {::state/command [::state/create-institution {:id  "test"
                                                                       :url "https://example.com"}]})
                   (app)
                   (get-in [::state/institutions :test])))
            "create a new institution"))

      (testing "update-institution"
        (is (= {:url "https://other.example.com"}
               (-> request
                   (into {::state/command [::state/update-institution "ApiKeyBackend" {:id  "ApiKeyBackend"
                                                                                       :url "https://other.example.com"}]})
                   (app)
                   (get-in [::state/institutions :ApiKeyBackend])))
            "update changes url")
        (let [new-institutions (-> request
                                   (into {::state/command [::state/update-institution "ApiKeyBackend" {:id  "test"
                                                                                                       :url "https://other.example.com"}]})
                                   (app)
                                   (get ::state/institutions))]
          (is (= #{:test :BasicAuthBackend :Oauth2Backend}
                 (set (keys new-institutions)))
              "ApiKeyBackend is replaced by test rest is still there")
          (is (= {:url "https://other.example.com"}
                 (get new-institutions :test))
              "new updated version present")))

      (testing "delete-institution"
        (is (= #{:BasicAuthBackend :Oauth2Backend}
               (-> request
                   (into {::state/command [::state/delete-institution "ApiKeyBackend"]})
                   (app)
                   (get ::state/institutions)
                   keys
                   set)))))))
