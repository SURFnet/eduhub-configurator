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

(ns ooapi-gateway-configurator.form
  (:require [ooapi-gateway-configurator.anti-forgery :refer [anti-forgery-field]]))

(defn request-method-field
  [method]
  [:input {:type "hidden",
           :name "_method",
           :value method}])

(defn form
  [{:keys [method] :as opts} & body]
  {:pre [(string? method)]}
  (let [[method _method] (if (#{"get" "post"} method) [method] ["post" method])]
    (cond-> [:form (assoc opts :method method)]

      _method
      (conj (request-method-field _method))

      :finally
      (into (cons (anti-forgery-field) body)))))
