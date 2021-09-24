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
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [ooapi-gateway-configurator.state :as state]
            [ooapi-gateway-configurator.store :as store]
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
  [handler {:keys [gateway-config-file]}]
  (-> handler
      (store/wrap {:gateway-config-yaml (.getPath gateway-config-file)
                   :pipeline            "test"})
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
  (let [{:keys [::state/applications
                ::state/institutions
                ::state/access-control-lists]} (#'store/fetch *config*)]
    (testing "applications"
      (is (contains? applications "fred"))
      (is (contains? applications "barney"))
      (is (contains? applications "bubbles")))

    (testing "institutions"
      (is (contains? institutions "Basic.Auth.Backend"))
      (is (contains? institutions "Oauth-2.Backend"))
      (is (contains? institutions "Api.Key.Backend"))

      (testing "shape"
        (is (= {:url          "https://example.com/test-backend"
                :proxyOptions {:auth "fred:wilma"}}
               (get institutions "Basic.Auth.Backend")))))

    (testing "access-control-lists"
      (is (contains? access-control-lists "fred"))
      (is (contains? access-control-lists "barney"))
      (is (contains? access-control-lists "bubbles"))

      (testing "shape"
        (is (= {"Basic.Auth.Backend" nil
                "Oauth-2.Backend"    nil
                "Api.Key.Backend"    #{"/"}}
               (get access-control-lists "bubbles")))))))

(deftest put
  (let [state {::state/applications         {"fred" {:passwordHash "..", :passwordSalt ".."}}
               ::state/institutions         {"backend" {:url "http://example.com/put-test"}}
               ::state/access-control-lists {"fred" {"backend" #{"/"}}}}]
    (testing "round trip"
      (#'store/put state *config*)
      (#'store/commit! *config*)
      (is (= state (-> (#'store/fetch *config*)
                       (select-keys [::state/applications
                                     ::state/institutions
                                     ::state/access-control-lists])))))))
