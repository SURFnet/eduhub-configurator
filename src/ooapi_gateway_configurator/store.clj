(ns ooapi-gateway-configurator.store
  (:require [clj-yaml.core :as yaml]
            [clojure.java.io :as io]
            [ooapi-gateway-configurator.anti-forgery :refer [anti-forgery-field]]
            [ooapi-gateway-configurator.html :as html]
            [ooapi-gateway-configurator.state :as state]
            [ooapi-gateway-configurator.store.klist :as klist]
            [ooapi-gateway-configurator.versioning :as versioning]
            [ring.util.response :as response])
  (:import java.time.Instant))

(defn- checkout-yaml [yaml-file]
  (versioning/checkout yaml-file yaml/parse-string))

(defn- in-yaml [yaml-file f & args]
  (let [{:keys [version contents]} (checkout-yaml yaml-file)]
    (versioning/stage! yaml-file version
                       (yaml/generate-string (apply f contents args)))))

(defn- acl->access-control-list
  "Transform gatekeeper policy action to a access control list for the
  given application.  Only matching endpoints from the institutions
  set will be used and institutions without a matching endpoint will
  be registered with nil paths.

  The shape of a ACL (in YAML) is as follows:

    - app: fred
      endpoints:
        - endpoint: BasicAuthBackend
          paths: ['/', '/courses', '/courses/:id']

  The shape of the output (in EDN):

    {:BasicAuthBackend #{'/', '/courses', '/courses/:id'}
     :ApiKeyBackend    nil
     ..}
  "
  [application-id acls institution-ids]
  (let [endps (->> acls
                   (filter #(= application-id (keyword (:app %))))
                   first
                   :endpoints
                   (reduce (fn [m {:keys [endpoint paths]}]
                             (assoc m (keyword endpoint) (set paths)))
                           {}))]
    (reduce (fn [m id] (assoc m id (get endps id)))
            {}
            institution-ids)))

(defn- gatekeeper-acls->acls
  "Transform a list of gatekeeper action ACLs to a map from application
  IDs to access control lists."
  [acls app-ids institution-ids]
  (reduce (fn [m app]
            (assoc m app (acl->access-control-list app acls institution-ids)))
          {}
          app-ids))

(defn- fetch
  [{:keys [gateway-config-yaml pipeline]}]
  (let [{{:keys [serviceEndpoints pipelines] :as gw} :contents
         :as                                         checkout} (checkout-yaml gateway-config-yaml)
        {:keys [policies apiEndpoints]}                        (get pipelines (keyword pipeline))
        api                                                    (-> apiEndpoints first keyword)
        apps                                                   (klist/get-in policies [:gatekeeper :action :apps])]
    {::state/applications         apps
     ::state/institutions         serviceEndpoints
     ::uncommitted?               (versioning/uncommitted? checkout)
     ::state/access-control-lists (-> policies
                                      (klist/get-in [:gatekeeper :action :acls])
                                      (gatekeeper-acls->acls (keys apps) (keys serviceEndpoints)))
     ::state/api-paths            (-> gw :apiEndpoints api :paths set)}))

(defn- acls->gatekeeper-acls
  [acls]
  (keep (fn [[app acl]]
          (when-let [endpoints (->> acl
                                    (filter (comp seq last))
                                    (map (fn [[endp paths]]
                                           {:endpoint endp, :paths paths}))
                                    seq)]
            {:app       app
             :endpoints endpoints}))
        acls))

(defn- put
  [{:keys [::state/applications ::state/institutions ::state/access-control-lists]}
   {:keys [gateway-config-yaml pipeline]}]
  (in-yaml gateway-config-yaml
           #(cond-> %
              institutions
              (assoc-in [:serviceEndpoints] institutions)

              access-control-lists
              (klist/update-in [:pipelines (keyword pipeline) :policies :gatekeeper]
                               klist/assoc-in [:action :acls]
                               (acls->gatekeeper-acls access-control-lists))

              applications
              (klist/update-in [:pipelines (keyword pipeline) :policies :gatekeeper]
                               klist/assoc-in [:action :apps] applications))))

(defn- commit!
  [{:keys [gateway-config-yaml]}]
  (versioning/commit! gateway-config-yaml))

(defn- last-commit
  [{:keys [gateway-config-yaml]}]
  (Instant/ofEpochMilli (.lastModified (io/as-file gateway-config-yaml))))

(defn commit-component
  [{:keys [::uncommitted? ::last-commit] :as r}]
  (if last-commit
    [:div.commit-status
     (if uncommitted?
       [:form {:action "/commit"
               :method :post}
        [:input {:type  :hidden
                 :name  :redirect
                 :value (:uri r)}]
        (anti-forgery-field)
        [:button {:type :submit :class :secondary} "Commit changes"]
        "Some changes since commit at " (html/time last-commit) "."]
       [:span "No changes since last commit at " (html/time last-commit) "."])]
    [:div.commit-status
     "Configuration error? No value for " ::last-commit "."]))

(defn wrap
  "Middleware to allow reading and writing configuration."
  [app config]
  (fn [{:keys [request-method uri params] :as req}]
    (if (and (= :post request-method)
             (= "/commit" uri))
      (do (commit! config)
          (assoc (response/redirect (:redirect params "/") :see-other)
                 :flash "Committed"))
      (let [cur (fetch config)
            res (-> req
                    (into cur)
                    (assoc ::last-commit (last-commit config))
                    app)
            new (select-keys res [::state/applications ::state/institutions ::state/access-control-lists])]
        (when (seq new)
          (put new config))
        res))))
