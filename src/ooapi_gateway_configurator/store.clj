(ns ooapi-gateway-configurator.store
  (:require [clj-yaml.core :as yaml]
            [clojure.data.json :as json]
            [ooapi-gateway-configurator.state :as state]))

(defn- slurp-json [json-file]
  (-> json-file (slurp) (json/read-str :key-fn keyword)))

(defn- spit-json [data json-file]
  (spit json-file (json/write-str data :key-fn name)))

(defn- slurp-yaml [yaml-file]
  (-> yaml-file (slurp) (yaml/parse-string)))

(defn- split-yaml [data yaml-file]
  (spit yaml-file (yaml/generate-string data)))

(defn- in-yaml [yaml-file f & args]
  (-> f
      (apply (slurp-yaml yaml-file) args)
      (split-yaml yaml-file)))

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

    {:BasicAuthBackend ['/', '/courses', '/courses/:id']
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

(defn acls->access-control-lists
  "Transform a list of gatekeeper action ACLs to a map from application
  IDs to access control lists."
  [acls app-ids institution-ids]
  (reduce (fn [m app]
            (assoc m app (acl->access-control-list app acls institution-ids)))
          {}
          app-ids))

(defn- keyed-list-member
  "Get a \"keyed\" member from a list; coll is a list of maps of one
  entry.  Returns the value of the entry which has key k."
  [coll k]
  (-> (filter (fn [x]
                (assert (and (map? x) (= 1 (count x))))
                (= k (key (first x))))
              coll)
      first
      (get k)))

(defn- policies->access-control-lists
  "Extract access control lists from policies.

  The shape of a policies (in YAML) is as follows:

    - ..
    - gatekeeper:
        - action:
            acls:
              - app: fred
                endpoints:
                  - endpoint: BasicAuthBackend
                    paths: ['/', '/courses', '/courses/:id']
    - ..

  The shape of the output (in EDN) with
  application-ids [:fred :barney] and
  institution-ids [:BasicAuthBackend :ApiKeyBackend].

    {:fred   {:BasicAuthBackend ['/', '/courses', '/courses/:id']
              :ApiKeyBackend    nil}
     :barney {:BasicAuthBackend nil
              :ApiKeyBackend    nil}}
  "
  [policies application-ids institution-ids]
  (-> policies
      (keyed-list-member :gatekeeper)
      (keyed-list-member :action)
      :acls
      (acls->access-control-lists application-ids institution-ids)))

(defn- fetch
  [{:keys [credentials-json gateway-config-yaml pipeline]}]
  (let [apps     (slurp-json credentials-json)
        gw       (slurp-yaml gateway-config-yaml)
        insts    (:serviceEndpoints gw)
        pl       (-> gw :pipelines (get (keyword pipeline)))
        policies (:policies pl)
        api      (-> pl :apiEndpoints first keyword)]
    {::state/applications         apps
     ::state/institutions         insts
     ::state/access-control-lists (policies->access-control-lists policies
                                                                  (keys apps)
                                                                  (keys insts))
     ::state/api-paths            (-> gw :apiEndpoints api :paths set)}))

(defn- access-control-lists->policies
  "Replace gatekeeper policy in list of policies with version reflecting
  the given access control lists."
  [policies access-control-lists]
  (map (fn [policy]
         (if (-> policy first key (= :gatekeeper))
           {:gatekeeper
            [{:action
              {:acls
               (keep (fn [[app acl]]
                       (when-let [endpoints (->> acl
                                                 (filter (comp seq last))
                                                 (map (fn [[endp paths]]
                                                        {:endpoint endp, :paths paths}))
                                                 seq)]
                         {:app       app
                          :endpoints endpoints}))
                     access-control-lists)}}]}
           policy))
       policies))

(defn- put
  [{:keys [::state/applications ::state/institutions ::state/access-control-lists]}
   {:keys [credentials-json gateway-config-yaml pipeline]}]
  (when applications
    (spit-json applications credentials-json))
  (when institutions
    (in-yaml gateway-config-yaml
             assoc-in
             [:serviceEndpoints]
             institutions))
  (when access-control-lists
    (in-yaml gateway-config-yaml
             update-in
             [:pipelines (keyword pipeline) :policies]
             access-control-lists->policies access-control-lists)))

(defn wrap
  "Middleware to allow reading and writing configuration."
  [app config]
  (fn [req]
    (let [cur (fetch config)
          res (app (into req cur))
          new (select-keys res [::state/applications ::state/institutions ::state/access-control-lists])]
      (when (seq new)
        (put new config))
      res)))
