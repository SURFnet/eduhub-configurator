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

(def access-control-lists
  {:fred    {:BasicAuthBackend #{"/", "/courses", "/courses/:id"}
             :Oauth2Backend    #{"/", "/courses", "/courses/:id"}
             :ApiKeyBackend    nil}
   :barney  {:BasicAuthBackend #{"/", "/courses", "/courses/:id"}
             :Oauth2Backend    #{"/"}
             :ApiKeyBackend    #{"/"}}
   :bubbles {:BasicAuthBackend nil
             :Oauth2Backend    nil
             :ApiKeyBackend    #{"/"}}})

(def request {::state/applications         applications
              ::state/institutions         institutions
              ::state/access-control-lists access-control-lists})

(deftest wrap
  (let [app (state/wrap #(select-keys % [::state/command]))]
    (is (= {} (app {})))

    (testing "application commands"
      (testing "create-application"
        (is (= {:passwordSalt "salt"
                :passwordHash "hash"}
               (-> request
                   (assoc ::state/command [::state/create-application {:id           "test"
                                                                       :passwordSalt "salt"
                                                                       :passwordHash "hash"}])
                   (app)
                   (get-in [::state/applications :test])))
            "create a new application"))

      (testing "update-application"
        (is (= {:passwordSalt "new-salt"
                :passwordHash "new-hash"}
               (-> request
                   (assoc ::state/command [::state/update-application "fred" {:id           "fred"
                                                                              :passwordSalt "new-salt"
                                                                              :passwordHash "new-hash"}])
                   (app)
                   (get-in [::state/applications :fred])))
            "update changes password salt and hash")
        (let [state    (-> request
                           (assoc ::state/command [::state/update-application "fred" {:id "wilma"}])
                           (app))
              new-apps (::state/applications state)
              new-acls (::state/access-control-lists state)]
          (is (and (:wilma new-apps)
                   (= (:fred applications)
                      (:wilma new-apps)))
              "update name password data not overwritten")
          (is (and (:wilma new-acls)
                   (= (:fred access-control-lists)
                      (:wilma new-acls)))
              "access control list application renamed too")
          (is (= #{:wilma :barney :bubbles}
                 (set (keys new-apps)))
              "fred is replaced by wilma rest is still there"))
        (is (= {:passwordSalt "new-salt"
                :passwordHash "new-hash"}
               (-> request
                   (assoc ::state/command [::state/update-application
                                           "fred"
                                           {:id           "wilma"
                                            :passwordSalt "new-salt"
                                            :passwordHash "new-hash"}])
                   (app)
                   (get-in [::state/applications :wilma])))
            "update changes both id, password salt and hash"))

      (testing "delete-application"
        (let [state (-> request
                        (assoc ::state/command [::state/delete-application "fred"])
                        (app))]
          (is (= #{:barney :bubbles}
                 (-> state
                     (get ::state/applications)
                     keys
                     set))
              "deleted from applications")
          (is (= #{:barney :bubbles}
                 (-> state
                     (get ::state/access-control-lists)
                     keys
                     set))
              "deleted from access-control-lists"))))

    (testing "institution commands"
      (testing "create-institution"
        (is (= {:url "https://example.com"}
               (-> request
                   (assoc ::state/command [::state/create-institution
                                           {:id  "test"
                                            :url "https://example.com"}])
                   (app)
                   (get-in [::state/institutions :test])))
            "create a new institution"))

      (testing "update-institution"
        (is (= {:url "https://other.example.com"}
               (-> request
                   (assoc ::state/command [::state/update-institution
                                           "ApiKeyBackend"
                                           {:id  "ApiKeyBackend"
                                            :url "https://other.example.com"}])
                   (app)
                   (get-in [::state/institutions :ApiKeyBackend])))
            "update changes url")
        (let [state     (-> request
                            (assoc ::state/command [::state/update-institution
                                                    "ApiKeyBackend"
                                                    {:id  "test"
                                                     :url "https://other.example.com"}])
                            (app))
              new-insts (::state/institutions state)
              new-acls  (::state/access-control-lists state)]
          (is (= #{:test :BasicAuthBackend :Oauth2Backend}
                 (set (keys new-insts)))
              "ApiKeyBackend is replaced by test rest is still there")
          (is (= #{:test :BasicAuthBackend :Oauth2Backend}
                 (-> new-acls :fred keys set))
              "access control list institution renamed too")
          (is (= {:url "https://other.example.com"}
                 (get new-insts :test))
              "new updated version present")))

      (testing "delete-institution"
        (let [state     (-> request
                            (assoc ::state/command [::state/delete-institution
                                                    "ApiKeyBackend"])
                            (app))
              new-insts (::state/institutions state)
              new-acls  (::state/access-control-lists state)]
          (is (= #{:BasicAuthBackend :Oauth2Backend}
                 (-> new-insts keys set)))
          (is (= #{:BasicAuthBackend :Oauth2Backend}
                 (-> new-acls :fred keys set)))))

      (testing "update-access-control-list-for-application"
        (is (= #{"/"}
               (-> request
                   (assoc ::state/command [::state/update-access-control-list-for-application
                                           "fred"
                                           {:BasicAuthBackend #{"/"}}])
                   (app)
                   (get-in [::state/access-control-lists :fred :BasicAuthBackend]))))))))
