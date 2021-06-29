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
