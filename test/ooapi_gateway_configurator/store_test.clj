(ns ooapi-gateway-configurator.store-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [ooapi-gateway-configurator.state :as state]
            [ooapi-gateway-configurator.store :as store]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]])
  (:import java.io.File))

(defn setup
  []
  (let [credentials-file    (File/createTempFile "ooapi-gw-configurator" "credentials.json")
        gateway-config-file (File/createTempFile "ooapi-gw-configurator" "gateway.config.yml")]
    (spit credentials-file (slurp (io/resource "test/credentials.json")))
    (spit gateway-config-file (slurp (io/resource "test/gateway.config.yml")))
    {:credentials-file    credentials-file
     :gateway-config-file gateway-config-file
     :pipeline            "test"}))

(defn teardown
  [{:keys [credentials-file gateway-config-file]}]
  (.delete credentials-file)
  (.delete gateway-config-file))

(defn wrap
  [handler {:keys [credentials-file gateway-config-file]}]
  (-> handler
      (store/wrap {:credentials-json    (.getPath credentials-file)
                   :gateway-config-yaml (.getPath gateway-config-file)
                   :pipeline            "test"})
      (wrap-defaults (dissoc site-defaults :security))))

(def ^:dynamic *config*)

(use-fixtures :each
  (fn [f]
    (let [{:keys [credentials-file gateway-config-file]
           :as   state} (setup)]
      (binding [*config* {:credentials-json    (.getPath credentials-file)
                          :gateway-config-yaml (.getPath gateway-config-file)
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
  (testing "setting applications"
    (#'store/put {::state/applications         {:fred {:passwordHash "..", :passwordSalt ".."}}
                  ::state/institutions         {:backend {:url "http://example.com/put-test"}}
                  ::state/access-control-lists {:fred {:backend #{"/"}}}}
                 *config*)
    (#'store/commit! *config*)
    (let [{:keys [::state/applications
                  ::state/institutions
                  ::state/access-control-lists]} (#'store/fetch *config*)]
      (is (= 1 (count applications)))
      (is (= {:fred {:passwordHash "..", :passwordSalt ".."}}
             applications))

      (is (= 1 (count institutions)))
      (is (= {:backend {:url "http://example.com/put-test"}}
             institutions))

      (is (= 1 (count access-control-lists)))
      (is (= {:fred {:backend #{"/"}}}
             access-control-lists))))

  (testing "round trip"
    (let [before (#'store/fetch *config*)]
      (#'store/put before *config*)
      (#'store/commit! *config*)

      (is (= before (#'store/fetch *config*))))))
