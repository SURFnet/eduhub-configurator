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
      (wrap-defaults (dissoc site-defaults :security))))

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
      (is (contains? applications :fred))
      (is (contains? applications :barney))
      (is (contains? applications :bubbles)))

    (testing "institutions"
      (is (contains? institutions :BasicAuthBackend))
      (is (contains? institutions :Oauth2Backend))
      (is (contains? institutions :ApiKeyBackend))

      (testing "shape"
        (is (= {:url          "https://example.com/test-backend"
                :proxyOptions {:auth "fred:wilma"}}
               (:BasicAuthBackend institutions)))))

    (testing "access-control-lists"
      (is (contains? access-control-lists :fred))
      (is (contains? access-control-lists :barney))
      (is (contains? access-control-lists :bubbles))

      (testing "shape"
        (is (= {:BasicAuthBackend nil
                :Oauth2Backend    nil
                :ApiKeyBackend    #{"/"}}
               (:bubbles access-control-lists)))))))

(deftest put
  (let [state {::state/applications         {:fred {:passwordHash "..", :passwordSalt ".."}}
               ::state/institutions         {:backend {:url "http://example.com/put-test"}}
               ::state/access-control-lists {:fred {:backend #{"/"}}}}]
    (testing "round trip"
      (#'store/put state
                   *config*)
      (#'store/commit! *config*)
      (is (= state (-> (#'store/fetch *config*)
                       (select-keys [::state/applications
                                     ::state/institutions
                                     ::state/access-control-lists])))))))
