(ns ooapi-gateway-configurator.auth
  (:require [clojure.string :as string]
            [ring.middleware.oauth2 :as oauth2]
            [clojure.data.json :as json]
            [clojure.java.io :as io])
  (:import java.util.Base64))

(defn wrap-authentication
  [handler {:keys [authorize-uri
                   access-token-uri
                   client-id
                   client-secret]}]
  (oauth2/wrap-oauth2 handler
                      {:conext {:authorize-uri    authorize-uri
                                :access-token-uri access-token-uri
                                :client-id        client-id
                                :client-secret    client-secret
                                :scopes           []
                                :launch-uri       "/oauth2/conext"
                                :redirect-uri     "/oauth2/conext/callback"
                                :landing-uri      "/"}}))

(defn token
  [r]
  (get-in r [:oauth2/access-tokens :conext :token]))

(defn- decode-base64
  [s]
  (.decode (Base64/getDecoder) s))

(defn- parse-json
  [bytes]
  (-> bytes
      (io/input-stream)
      (io/reader)
      (json/read)))

(defn decode-token
  [s]
  (let [[head payload signature] (string/split s #"\.")]
    {:head      (parse-json (decode-base64 head))
     :payload   (parse-json (decode-base64 payload))
     :signature signature}))
