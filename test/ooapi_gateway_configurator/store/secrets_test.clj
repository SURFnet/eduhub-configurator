;; Copyright (C) 2023 SURFnet B.V.
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

(ns ooapi-gateway-configurator.store.secrets-test
  (:require [clojure.test :refer [deftest is]]
            [ooapi-gateway-configurator.store.secrets :as secrets]))

(deftest encrypt-decrypt
  (let [key       "0123456789abcdef0123456789abcdef0123456789abcdef"
        data      "fred <3 wilma"
        encrypted (secrets/encrypt key data)
        decrypted (secrets/decrypt key encrypted)]
    (is (not= data encrypted))
    (is (= data decrypted))))

(deftest encode-decode
  (let [key     "0123456789abcdef0123456789abcdef0123456789abcdef"
        data    {:fred "wilma"}
        encoded (secrets/encode key data)
        decoded (secrets/decode key encoded)]
    (is (not= data encoded))
    (is (= data decoded))))
