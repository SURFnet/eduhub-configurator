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
