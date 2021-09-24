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

(ns ooapi-gateway-configurator.state-test
  (:require [clojure.test :refer :all]
            [ooapi-gateway-configurator.state :as state]))

(def applications
  {"fred"    {:passwordSalt "fred-salt"
              :passwordHash "fred-hash"}
   "barney"  {:passwordSalt "barney-salt"
              :passwordHash "barney-hash"}
   "bubbles" {:passwordSalt "barney-bubbles"
              :passwordHash "barney-bubble"}})

(def institutions
  {"Basic.Auth.Backend" {:url          "https://example.com/test-backend"
                         :proxyOptions {:auth "fred:wilma"}}
   "Oauth-2.Backend"    {:url          "https://example.com/other-test-backend"
                         :proxyOptions {:oauth2
                                        {:clientCredentials
                                         {:tokenEndpoint
                                          {:url "https://oauth/test",
                                           :params
                                           {:grant_type    "client_credentials",
                                            :client_id     "fred",
                                            :client_secret "wilma"}}}}}}
   "Api.Key.Backend"    {:url          "https://example.com/api-key-backend"
                         :proxyOptions {:headers {:Authorization "Bearer test-api-key"}}}})

(def access-control-lists
  {"fred"    {"Basic.Auth.Backend" #{"/", "/courses", "/courses/:id"}
              "Oauth-2.Backend"    #{"/", "/courses", "/courses/:id"}
              "Api.Key.Backend"    nil}
   "barney"  {"Basic.Auth.Backend" #{"/", "/courses", "/courses/:id"}
              "Oauth-2.Backend"    #{"/"}
              "Api.Key.Backend"    #{"/"}}
   "bubbles" {"Basic.Auth.Backend" nil
              "Oauth-2.Backend"    nil
              "Api.Key.Backend"    #{"/"}}})

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
                   (get-in [::state/applications "test"])))
            "create a new application"))

      (testing "update-application"
        (is (= {:passwordSalt "new-salt"
                :passwordHash "new-hash"}
               (-> request
                   (assoc ::state/command [::state/update-application "fred" {:id           "fred"
                                                                              :passwordSalt "new-salt"
                                                                              :passwordHash "new-hash"}])
                   (app)
                   (get-in [::state/applications "fred"])))
            "update changes password salt and hash")
        (let [state    (-> request
                           (assoc ::state/command [::state/update-application "fred" {:id "wilma"}])
                           (app))
              new-apps (::state/applications state)
              new-acls (::state/access-control-lists state)]
          (is (and (get new-apps "wilma")
                   (= (get applications "fred")
                      (get new-apps "wilma")))
              "update name password data not overwritten")
          (is (and (get new-acls "wilma")
                   (= (get access-control-lists "fred")
                      (get new-acls "wilma")))
              "access control list application renamed too")
          (is (= #{"wilma" "barney" "bubbles"}
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
                   (get-in [::state/applications "wilma"])))
            "update changes both id, password salt and hash"))

      (testing "delete-application"
        (let [state (-> request
                        (assoc ::state/command [::state/delete-application "fred"])
                        (app))]
          (is (= #{"barney" "bubbles"}
                 (-> state
                     (get ::state/applications)
                     keys
                     set))
              "deleted from applications")
          (is (= #{"barney" "bubbles"}
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
                   (get-in [::state/institutions "test"])))
            "create a new institution"))

      (testing "update-institution"
        (is (= {:url "https://other.example.com"}
               (-> request
                   (assoc ::state/command [::state/update-institution
                                           "Api.Key.Backend"
                                           {:id  "Api.Key.Backend"
                                            :url "https://other.example.com"}])
                   (app)
                   (get-in [::state/institutions "Api.Key.Backend"])))
            "update changes url")
        (let [state     (-> request
                            (assoc ::state/command [::state/update-institution
                                                    "Api.Key.Backend"
                                                    {:id  "test"
                                                     :url "https://other.example.com"}])
                            (app))
              new-insts (::state/institutions state)
              new-acls  (::state/access-control-lists state)]
          (is (= #{"test" "Basic.Auth.Backend" "Oauth-2.Backend"}
                 (set (keys new-insts)))
              "Api.Key.Backend is replaced by test rest is still there")
          (is (= #{"test" "Basic.Auth.Backend" "Oauth-2.Backend"}
                 (-> new-acls (get "fred") keys set))
              "access control list institution renamed too")
          (is (= {:url "https://other.example.com"}
                 (get new-insts "test"))
              "new updated version present")))

      (testing "delete-institution"
        (let [state     (-> request
                            (assoc ::state/command [::state/delete-institution
                                                    "Api.Key.Backend"])
                            (app))
              new-insts (::state/institutions state)
              new-acls  (::state/access-control-lists state)]
          (is (= #{"Basic.Auth.Backend" "Oauth-2.Backend"}
                 (-> new-insts keys set)))
          (is (= #{"Basic.Auth.Backend" "Oauth-2.Backend"}
                 (-> new-acls (get "fred") keys set)))))

      (testing "update-access-control-list-for-application"
        (is (= #{"/"}
               (-> request
                   (assoc ::state/command [::state/update-access-control-list-for-application
                                           "fred"
                                           {"Basic.Auth.Backend" #{"/"}}])
                   (app)
                   (get-in [::state/access-control-lists "fred" "Basic.Auth.Backend"])))))

      (testing "update-access-control-list-for-application"
        (is (= #{"/"}
               (-> request
                   (assoc ::state/command [::state/update-access-control-list-for-institution
                                           "Basic.Auth.Backend"
                                           {"fred" #{"/"}}])
                   (app)
                   (get-in [::state/access-control-lists "fred" "Basic.Auth.Backend"]))))))))
