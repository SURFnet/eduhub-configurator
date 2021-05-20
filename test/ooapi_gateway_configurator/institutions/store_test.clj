(ns ooapi-gateway-configurator.institutions.store-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [ooapi-gateway-configurator.institutions.store :as sut])
  (:import java.io.File))

(def ^:dynamic *yaml-fname*)

(defn setup-yaml [f]
  (let [temp-file (File/createTempFile "institutions-test" "yml")]
    (spit temp-file (slurp (io/resource "test/gateway.config.yml")))
    (binding [*yaml-fname* (.getPath temp-file)]
      (f))
    (.delete temp-file)))

(use-fixtures :each setup-yaml)

(deftest fetch
  (let [institutions (sut/fetch *yaml-fname*)]
    (is (contains? institutions :BasicAuthBackend))))

(deftest put
  (testing "round trip"
    (let [before (sut/fetch *yaml-fname*)]
      (sut/put *yaml-fname* before)
      (is (= before (sut/fetch *yaml-fname*)))))

  (testing "setting institutions"
    (sut/put *yaml-fname* {:put-test {:url "http://example.com/put-test"}})
    (let [institutions (sut/fetch *yaml-fname*)]
      (is (= 1 (count institutions)))
      (is (= {:put-test {:url "http://example.com/put-test"}}
             institutions)))))
