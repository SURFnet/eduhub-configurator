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

(ns ooapi-gateway-configurator.store.klist
  "Functions that treat sequential collections of maps with a single
  key-value pair as maps, similar to traditional plists.

  This is slow and mostly useless, so don't use it unless you have to
  deal with data that is already in this format."
  (:refer-clojure :exclude [get assoc assoc-in update-in update get-in]))

(defn- klist-item?
  [item]
  (and (map? item)
       (= 1 (count item))))

(defn klist?
  "Test if `coll` is a klist.

  klists are sequential collections where every item is a map of
  exactly one key/value pair. Empty sequential collections are
  klists."
  [coll]
  (and (sequential? coll)
       (every? klist-item? coll)))

(defn get
  "Like `clojure.core/get` but also works on klists."
  ([m k not-found]
   (if (klist? m)
     (if-let [kv (first (filter #(= (key (first %)) k) m))]
       (val (first kv))
       not-found)
     (clojure.core/get m k not-found)))
  ([m k]
   (get m k nil)))

(defn get-in
  "Like `clojure.core/get-in` but also works on klists."
  ([m [k & ks] not-found]
   (if ks
     (get-in (get m k) ks not-found)
     (get m k not-found)))
  ([m ks]
   (get-in m ks nil)))

(defn assoc
  "Like `clojure.core/assoc` but also works on klists.

  Returns a map when m is nil."
  [m k v]
  (if (klist? m)
    (into (empty m) ;; keep existing collection type - list, vector...
          (map (fn [item]
                 (let [item-key (key (first item))]
                   (if (= k item-key)
                     {item-key v}
                     item))))
          m)
    (clojure.core/assoc m k v)))

(defn update
  "Like `clojure.core/update` but also works on klists.

  If m is nil, returns a map, not a klist"
  [m k f & args]
  (if (klist? m)
    (assoc m k (apply f (get m k) args))
    (apply clojure.core/update m k f args)))

(defn update-in
  "Like `clojure.core/update-in` but also works on klists.

  If any levels do not exist, maps will be created."
  [m [k & ks] f & args]
  (if ks
    (apply update m k update-in ks f args)
    (apply update m k f args)))

(defn assoc-in
  "Like `clojure.core/assoc-in` but handles klists like maps.

  Like `clojure.core/assoc-in`, if any level doesn't exist, creates a
  map at that point."
  [m [k & ks] v]
  (if ks
    (update m k assoc-in ks v)
    (assoc m k v)))
