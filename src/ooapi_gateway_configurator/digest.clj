(ns ooapi-gateway-configurator.digest
  (:import java.security.MessageDigest))

(defn sha256
  "Get SHA-256 digest of string as a byte array."
  [^String s]
  (.digest (MessageDigest/getInstance "SHA-256")
           (.getBytes s "UTF-8")))
