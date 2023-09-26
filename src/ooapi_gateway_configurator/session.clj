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

(ns ooapi-gateway-configurator.session
  (:require [clojure.core.cache :as cache]
            [crypto.equality :as crypto]
            [crypto.random :as random]
            [ring.middleware.anti-forgery.strategy :as strategy]
            [ring.middleware.session.store :as store])
  (:import java.util.UUID))

(deftype Store [cache-atom]
  store/SessionStore
  (read-session [_ k]
    (let [v (cache/lookup @cache-atom k ::not-found)]
      (if (= v ::not-found)
        nil
        (do
          (swap! cache-atom cache/miss k v) ;; reset TTL
          v))))
  (write-session [_ k v]
    (let [k (or k (str (UUID/randomUUID)))]
      (swap! cache-atom cache/miss k v)
      k))
  (delete-session [_ k]
    (swap! cache-atom cache/evict k)
    nil))

(defn mk-store
  "Return an in memory ring session store with a per session TTL.

  Uses clojure.core.cache/ttl-cache-factory to keep values and allows
  an optional `:ttl` argument to define the time to live of
  sessions (defaults to half an hour).  Accessing a session resets the TTL
  for that session."
  [& {:keys [ttl] :or {ttl (* 1000 60 30)}}]
  (->Store (atom (cache/ttl-cache-factory {} :ttl ttl))))

(defn- session-token [request]
  (get-in request [:session ::anti-forgery-token]))

;; Slightly adapted version of default
;; ring.middleware.anti-forgery.session.  This version does not put a
;; token on the session when there's not already a session.
(deftype Strategy []
  strategy/Strategy
  (get-token [_ request]
    (or (session-token request)
        (random/base64 60)))

  (valid-token? [_ request token]
    (when-let [stored-token (session-token request)]
      (crypto/eq? token stored-token)))

  (write-token [_ request response token]
    (if (empty? (:session response (:session request)))
      ;; only write to session when we already have one
      response
      (let [old-token (session-token request)]
        (if (= old-token token)
          response
          (-> response
              (assoc :session (:session response (:session request)))
              (assoc-in [:session ::anti-forgery-token] token)))))))

(defn mk-strategy
  "Return a strategy for anti-forgery/wrap-anti-forgery middleware.

  This version is based on the default session stategy but which does
  nothing when the user does not already have a session yet."
  []
  (->Strategy))
