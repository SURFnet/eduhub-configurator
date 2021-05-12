(ns ooapi-gateway-configurator.institutions-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [ooapi-gateway-configurator.institutions :as sut]))

(deftest fetch
  (let [institutions (sut/fetch "resources/test/gateway.config.yml")]
    (is (contains? institutions :TestBackend))))
