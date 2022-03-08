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

(ns ooapi-gateway-configurator.store-test
  (:require [clj-yaml.core :as yaml]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest testing is use-fixtures]]
            [datascript.core :as d]
            [ooapi-gateway-configurator.model :as model]
            [ooapi-gateway-configurator.store :as store]
            [ooapi-gateway-configurator.store.klist :as klist]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]])
  (:import java.io.File))

(defn setup
  []
  (let [gateway-config-file (File/createTempFile "ooapi-gw-configurator" "gateway.config.yml")]
    (spit gateway-config-file (slurp (io/resource "test/gateway.config.yml")))
    {:gateway-config-file gateway-config-file
     :pipeline            "test"}))

(defn teardown
  [{:keys [gateway-config-file]}]
  (.delete gateway-config-file))

(defn wrap
  [handler {:keys [gateway-config-file read-only?] :or {read-only? false}}]
  (-> handler
      (store/wrap {:gateway-config-yaml (.getPath gateway-config-file)
                   :pipeline            "test"
                   :read-only?          read-only?})
      (wrap-defaults (-> site-defaults
                         (assoc-in [:params :keywordize] false)
                         (dissoc :security)))))

(def ^:dynamic *config*)

(use-fixtures :each
  (fn [f]
    (let [{:keys [gateway-config-file]
           :as   state} (setup)]
      (binding [*config* {:gateway-config-yaml (.getPath gateway-config-file)
                          :pipeline            "test"}]
        (f))
      (teardown state))))

(deftest fetch
  (let [{:keys [model]} (#'store/fetch *config*)]
    (testing "applications"
      (is (= #{"fred" "barney" "bubbles"}
             (model/app-ids model))))

    (testing "institutions"
      (is (= #{"Basic.Auth.Backend" "Oauth-2.Backend" "Api.Key.Backend"}
             (model/institution-ids model)))

      (testing "shape"
        (is (= #:institution {:url           "https://example.com/test-backend"
                              :notes         "Fred <3 Wilma"
                              :proxy-options {:auth         "fred:wilma"
                                              :proxyTimeout 31415}}
               (d/pull model
                       '[:institution/url :institution/notes :institution/proxy-options]
                       [:institution/id "Basic.Auth.Backend"])))))

    (testing "access-control-lists"
      (doseq [n ["fred" "barney" "bubbles"]]
        (is (seq (d/q '[:find ?xs
                        :in $ ?n
                        :where
                        [?a :app/id ?n]
                        [?xs :access/app ?a]]
                      model
                      n)))))))

(deftest put
  (let [yaml (yaml/parse-string (slurp (:gateway-config-yaml *config*)))
        {:keys [model]} (#'store/fetch *config*)]
    (testing "round trip does not change the data in the gateway configuration"
      (#'store/put model *config*)
      (#'store/commit! *config*)
      (is (= yaml (yaml/parse-string (slurp (:gateway-config-yaml *config*)))))))

  (let [{:keys [conn]} (#'store/fetch *config*)
        get-acl (fn []
                  (-> *config*
                      :gateway-config-yaml
                      slurp
                      yaml/parse-string
                      (klist/get-in [:pipelines :test :policies :gatekeeper :action :acls])
                      first))]
    (testing "empty ACL entries are skipped"
      (is (= {:app "barney"
              :endpoints [{:endpoint "Basic.Auth.Backend",
                           :paths ["/" "/courses" "/courses/:courseId"]}
                          {:endpoint "Oauth-2.Backend", :paths ["/"]}
                          {:endpoint "Api.Key.Backend", :paths ["/"]}]}
             (get-acl))
          "ACL entry with paths")
      ;; clear paths

      (#'store/put (:db-after (d/transact! conn
                                           (model/set-paths @conn
                                                            {:app-id "barney"
                                                             :institution-id "Basic.Auth.Backend"
                                                             :paths []})))
                   *config*)
      (#'store/commit! *config*)

      (is (= {:app "barney"
              :endpoints [{:endpoint "Oauth-2.Backend", :paths ["/"]}
                          {:endpoint "Api.Key.Backend", :paths ["/"]}]}
             (get-acl))
          "ACL entry without paths removed"))))
