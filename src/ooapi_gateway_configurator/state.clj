(ns ooapi-gateway-configurator.state)

(defmulti process (fn [_ [command]] command))

(defmethod process ::create-application
  [state [_ {:keys [id] :as app}]]
  {:pre [(string? id)
         (string? (:passwordSalt app))
         (string? (:passwordHash app))
         (not (get-in state [::applications (keyword id)]))]}
  (let [app (dissoc app :id)]
    (assoc-in state [::applications (keyword id)]
              app)))

(defmethod process ::update-application
  [state [_ orig-id {:keys [id] :as app}]]
  {:pre [(string? orig-id)
         (string? id)
         (get-in state [::applications (keyword orig-id)])]}
  (let [app    (dissoc app :id)
        before (get-in state [::applications (keyword orig-id)])
        acl    (get-in state [::access-control-lists (keyword orig-id)])]
    (-> state
        (update ::applications dissoc (keyword orig-id))
        (assoc-in [::applications (keyword id)] (merge before app))
        (update ::access-control-lists dissoc (keyword orig-id))
        (assoc-in [::access-control-lists (keyword id)] acl))))

(defmethod process ::delete-application
  [state [_ id]]
  {:pre [(string? id)]}
  (let [id (keyword id)]
    (-> state
        (update ::applications dissoc id)
        (update ::access-control-lists dissoc id))))

(defmethod process ::create-institution
  [state [_ {:keys [id] :as institution}]]
  {:pre [(string? id)
         (:url institution)
         (not (get-in state [::institutions (keyword id)]))]}
  (assoc-in state [::institutions (keyword id)]
            (dissoc institution :id)))

(defn- rename-institution-in-acls
  "Rename institution-id in access-control-lists.  The ACLs are a map of
  application-ids to maps of institution-ids to sets of paths.
  Returns access-control-lists with the keys of the maps of
  institution-ids to paths renamed."
  [acls from to]
  (->> (vals acls)
       (map #(zipmap (replace {from to} (keys %)) (vals %)))
       (zipmap (keys acls))))

(defmethod process ::update-institution
  [state [_ orig-id {:keys [id] :as institution}]]
  {:pre [(string? orig-id) (string? id) (get-in state [::institutions (keyword orig-id)])]}
  (let [id (keyword id)
        orig-id (keyword orig-id)]
    (-> state
        (update ::institutions dissoc orig-id)
        (assoc-in [::institutions id] (dissoc institution :id))
        (update ::access-control-lists rename-institution-in-acls orig-id id))))

(defn- delete-institution-in-acls
  "Delete institution-id from access-control-lists.  The ACLs are a map
  of application-ids to maps of institution-ids to sets of paths.
  Returns access-control-lists without the entries where
  institution-ids is id."
  [acls id]
  (->> (vals acls)
       (map #(dissoc % id))
       (zipmap (keys acls))))

(defmethod process ::delete-institution
  [state [_ id]]
  {:pre [(string? id)
         (get-in state [::institutions (keyword id)])]}
  (let [id (keyword id)]
    (-> state
        (update ::institutions dissoc id)
        (update ::access-control-lists delete-institution-in-acls id))))

(defn invert-access-control-lists
  "Invert orientation of access-control-list from application based to
  institution based and visa versa."
  [access-control-lists]
  (reduce (fn [r [x m]]
            (reduce (fn [r [y paths]]
                      (assoc-in r [y x] paths))
                    r
                    m))
          {}
          access-control-lists))

(defmethod process ::update-access-control-list-for-application
  [state [_ application-id access-control-list]]
  {:pre [(string? application-id)]}
  (assoc-in state [::access-control-lists (keyword application-id)] access-control-list))

(defmethod process ::update-access-control-list-for-institution
  [state [_ institution-id access-control-list]]
  {:pre [(string? institution-id)]}
  (-> state
      (update ::access-control-lists invert-access-control-lists)
      (assoc-in [::access-control-lists (keyword institution-id)] access-control-list)
      (update ::access-control-lists invert-access-control-lists)))

(defn wrap
  "Middleware to catch manipulations."
  [app]
  (fn [req]
    (let [res (app req)]
      (assert (not (or (::applications res)
                       (::institutions res)
                       (::access-control-lists res)))
              "only state/wrap should manipulate store")
      (if-let [command (::command res)]
        (into (dissoc res ::command)
              (-> req
                  (select-keys [::applications
                                ::institutions
                                ::access-control-lists])
                  (process command)))
        res))))
