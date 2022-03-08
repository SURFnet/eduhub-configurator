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

(ns ooapi-gateway-configurator.model-test
  (:require [clojure.test :refer [deftest is testing]]
            [datascript.core :as d]
            [ooapi-gateway-configurator.model :as model]))

(defn test-conn
  []
  (let [conn (d/create-conn model/schema)]
    (d/transact! conn [{:app/id "barney"
                        :access/_app [{:access/paths [{:path/spec "/"}
                                                      {:path/spec "/foo"}]
                                       :access/institution {:institution/id "endp"}}]}])
    conn))

(defn get-paths
  [model app-id institution-id]
  (set (d/q '[:find [?s ...]
              :in $ ?app-id ?institution-id
              :where
              [?a :app/id ?app-id]
              [?i :institution/id ?institution-id]
              [?xs :access/app ?a]
              [?xs :access/institution ?i]
              [?xs :access/paths ?p]
              [?p :path/spec ?s]]
            model
            app-id
            institution-id)))

(deftest model
  (testing "query functions"
    (let [conn (test-conn)
          model @conn]
      (is (= #{"barney"}
             (model/app-ids model)))
      (is (= #{"endp"}
             (model/institution-ids model)))

      (is (= #{"/" "/foo"}
             (model/api-paths model)))))

  (testing "removing apps and access"
    (let [conn (test-conn)]
      (is (= #{"/" "/foo"}
             (get-paths @conn "barney" "endp"))
          "app has some access entities")

      (d/transact! conn (model/remove-app @conn "barney"))

      (is (empty? (d/q '[:find ?xs :where [?xs :access/paths _]]
                       @conn))
          "removing app also removes related access entities")))

  (testing "setting paths"
    (let [conn (test-conn)]
      (testing "for existing apps"
        (is (= #{"/" "/foo"} (get-paths @conn "barney" "endp"))
            "app has some paths")
        (d/transact! conn (model/set-paths @conn {:app-id "barney" :institution-id "endp" :paths #{"/"}}))
        (is (= #{"/"} (get-paths @conn "barney" "endp"))
            "paths are updated in db"))

      (testing "for new apps"
        ;; insert new app
        (d/transact! conn [{:app/id "fred"}])

        (d/transact! conn (model/set-paths @conn {:app-id "fred" :institution-id "endp" :paths ["/foo" "/"]}))
        (is (= #{"/foo" "/"} (get-paths @conn "fred" "endp"))
            "paths are set in the db")))))
