(ns ooapi-gateway-configurator.state)

(defmulti process (fn [_ [command]] command))

(defmethod process ::create-application
  [state [_ {:keys [id] :as app}]]
  {:pre [id (:passwordSalt app) (:passwordHash app)
         (not (get-in state [::applications (keyword id)]))]}
  (let [app (dissoc app :id)]
    (assoc-in state [::applications (keyword id)]
              app)))

(defmethod process ::update-application
  [state [_ orig-id {:keys [id] :as app}]]
  {:pre [orig-id id
         (get-in state [::applications (keyword orig-id)])]}
  (let [app    (dissoc app :id)
        before (get-in state [::applications (keyword orig-id)])]
    (-> state
        (update ::applications dissoc (keyword orig-id))
        (assoc-in [::applications (keyword id)]
                  (merge before app)))))

(defmethod process ::delete-application
  [state [_ id]]
  {:pre [id]}
  (update state ::applications dissoc (keyword id)))

(defmethod process ::create-institution
  [state [_ {:keys [id] :as institution}]]
  {:pre [id (:url institution) (not (get-in state [::institutions (keyword id)]))]}
  (assoc-in state [::institutions (keyword id)]
            (dissoc institution :id)))

(defmethod process ::update-institution
  [state [_ orig-id {:keys [id] :as institution}]]
  {:pre [orig-id id (get-in state [::institutions (keyword orig-id)])]}
  (-> state
      (update ::institutions dissoc (keyword orig-id))
      (assoc-in [::institutions (keyword id)]
                (dissoc institution :id))))

(defmethod process ::delete-institution
  [state [_ id]]
  {:pre [id (get-in state [::institutions (keyword id)])]}
  (update state ::institutions dissoc (keyword id)))

(defn wrap
  "Middleware to catch manipulations."
  [app]
  (fn [req]
    (let [res (app req)]
      (assert (and (not (::applications res)) (not (::institutions res)))
              "only state/wrap should manipulate store")
      (if-let [command (::command res)]
        (into (dissoc res ::command)
              (-> req
                  (select-keys [::applications
                                ::institutions])
                  (process command)))
        res))))
