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

(ns ooapi-gateway-configurator.digest
  (:require [clojure.string :as string])
  (:import java.security.MessageDigest))

(defn hex
  "Transform byte array into a hex string."
  [bs]
  (string/join (map #(format "%02x" %) bs)))

(defn sha256
  "Get SHA-256 digest of string as a byte array."
  [^String s]
  (.digest (MessageDigest/getInstance "SHA-256")
           (.getBytes s "UTF-8")))
