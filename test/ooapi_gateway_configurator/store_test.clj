(ns ooapi-gateway-configurator.store-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [ooapi-gateway-configurator.state :as state]
            [ooapi-gateway-configurator.store :as sut]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]])
  (:import java.io.File))

(defn setup
  []
  (let [credentials-file    (File/createTempFile "ooapi-gw-configurator" "credentials.json")
        gateway-config-file (File/createTempFile "ooapi-gw-configurator" "gateway.config.yml")]
    (spit credentials-file (slurp (io/resource "test/credentials.json")))
    (spit gateway-config-file (slurp (io/resource "test/gateway.config.yml")))
    {:credentials-file    credentials-file
     :gateway-config-file gateway-config-file}))

(defn teardown
  [{:keys [credentials-file gateway-config-file]}]
  (.delete credentials-file)
  (.delete gateway-config-file))

(defn wrap
  [handler {:keys [credentials-file gateway-config-file]}]
  (-> handler
      (sut/wrap {:credentials-json    (.getPath credentials-file)
                 :gateway-config-yaml (.getPath gateway-config-file)})
      (wrap-defaults (dissoc site-defaults :security))))

(def ^:dynamic *config*)

(use-fixtures :each
  (fn [f]
    (let [{:keys [credentials-file gateway-config-file]
           :as   state} (setup)]
      (binding [*config* {:credentials-json    (.getPath credentials-file)
                          :gateway-config-yaml (.getPath gateway-config-file)}]
        (f))
      (teardown state))))

(deftest fetch
  (let [{:keys [::state/applications
                ::state/institutions]} (#'sut/fetch *config*)]
    (is (contains? applications :fred))
    (is (contains? applications :barney))
    (is (contains? applications :bubbles))
    (is (contains? institutions :BasicAuthBackend))
    (is (contains? institutions :Oauth2Backend))
    (is (contains? institutions :ApiKeyBackend))))

(deftest put
  (testing "setting applications"
    (#'sut/put {::state/applications {:test {:passwordHash "..", :passwordSalt ".."}}
                ::state/institutions {:put-test {:url "http://example.com/put-test"}}}
               *config*)
    (let [{:keys [::state/applications
                  ::state/institutions]} (#'sut/fetch *config*)]
      (is (= 1 (count applications)))
      (is (= {:test {:passwordHash "..", :passwordSalt ".."}}
             applications))

      (is (= 1 (count institutions)))
      (is (= {:put-test {:url "http://example.com/put-test"}}
             institutions))))

  (testing "round trip"
    (let [before (#'sut/fetch *config*)]
      (#'sut/put before *config*)
      (is (= before (#'sut/fetch *config*))))))
