(ns ooapi-gateway-configurator.store
  (:require [clj-yaml.core :as yaml]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [ooapi-gateway-configurator.anti-forgery :refer [anti-forgery-field]]
            [ooapi-gateway-configurator.html :as html]
            [ooapi-gateway-configurator.state :as state]
            [ooapi-gateway-configurator.versioning :as versioning]
            [ring.util.response :as response])
  (:import java.time.Instant))

(defn- checkout-json [json-file]
  (versioning/checkout json-file #(json/read-str % :key-fn keyword)))

(defn- checkout-yaml [yaml-file]
  (versioning/checkout yaml-file yaml/parse-string))

(defn- in-yaml [yaml-file f & args]
  (let [{:keys [version contents]} (checkout-yaml yaml-file)]
    (versioning/stage! yaml-file version
                       (yaml/generate-string (apply f contents args)))))

(defn- in-json [json-file f & args]
  (let [{:keys [version contents]} (checkout-json json-file)]
    (versioning/stage! json-file version
                       (json/write-str (apply f contents args) :key-fn name))))

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
  (let [{apps :contents :as apps-checkout} (checkout-json credentials-json)
        {gw :contents :as gw-checkout}     (checkout-yaml gateway-config-yaml)
        insts                              (:serviceEndpoints gw)
        pl                                 (-> gw :pipelines (get (keyword pipeline)))
        policies                           (:policies pl)
        api                                (-> pl :apiEndpoints first keyword)]
    {::state/applications         apps
     ::state/institutions         insts
     ::uncommitted?               (or (versioning/uncommitted? apps-checkout)
                                      (versioning/uncommitted? gw-checkout))
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
    (in-json credentials-json (constantly applications)))
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

(defn- commit!
  [{:keys [credentials-json gateway-config-yaml]}]
  (versioning/commit! credentials-json)
  (versioning/commit! gateway-config-yaml))

(defn- last-commit
  [{:keys [credentials-json gateway-config-yaml]}]
  (Instant/ofEpochMilli (min (.lastModified (io/as-file credentials-json))
                             (.lastModified (io/as-file gateway-config-yaml)))))

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
        "Some changes since commit at " (html/time last-commit)
        [:div.actions
         [:button {:type :submit :class :secondary} "Commit changes"]]]
       [:span "No changes since last commit at " (html/time last-commit)])]
    [:div.commit-status
     "Configuration error? no value for " ::last-commit]))

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
