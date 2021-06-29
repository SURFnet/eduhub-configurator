(ns ooapi-gateway-configurator.access-control-lists
  (:require [clojure.set :as set]
            [compojure.core :refer [defroutes GET POST routes]]
            [hiccup.element :refer [javascript-tag]]
            [hiccup.util :refer [escape-html]]
            [ooapi-gateway-configurator.form :as form]
            [ooapi-gateway-configurator.html :refer [layout not-found]]
            [ooapi-gateway-configurator.state :as state]
            [ring.util.codec :refer [url-encode]]
            [ring.util.response :refer [redirect]]))

(defn- subtitle [context id] (str "'" id "' " (name context) " ACL"))

(defn- ->form
  [access-control-list id]
  {:id                  (name id)
   :access-control-list (zipmap (keys access-control-list)
                                (->> access-control-list vals (map set)))})

(defn- form->
  "From params into an access-control-list.  Ensure all members are
  represented."
  [{:keys [access-control-list]} access-control-lists]
  (reduce (fn [m member-id] (update m member-id set))
          access-control-list
          (-> access-control-lists first val keys)))

(defn- form [context {:keys [access-control-list]} api-paths]
  (for [[member paths] (sort-by key access-control-list)]
    (let [id (name member)]
      [:div.member {:id (str "member-" id)}
       [:h3 [:a {:href (str ({:application "/institutions/"
                              :institution "/applications/"} context) id)}
             (escape-html id)]]
       [:div.actions
        [:input {:type  "submit", :class "secondary"
                 :name  (str "select-all-" id)
                 :value "Select all"}]
        " "
        [:input {:type  "submit", :class "secondary"
                 :name  (str "select-none-" id)
                 :value "Select none"}]]

       (for [path (sort paths)]
         [:label.path
          [:input {:type    "checkbox"
                   :name    (str "access-control-list[" id "][]")
                   :value   path
                   :checked (contains? paths path)}]
          (escape-html path)])

       (when-let [unselected-paths (-> api-paths (set/difference paths) (sort) (seq))]
         [:div.unselected
          [:button {:id      (str "ut-" id)
                    :type    "button", :class "secondary"
                    :style   "display:none"
                    ;; note: id will not contain characters which need quoting
                    :onclick (str "document.getElementById('ut-" id "').style.display = 'none';"
                                  "document.getElementById('up-" id "').style.display = 'inherit';")}
           "More.."]
          [:div.paths {:id (str "up-" id)}
           (for [path unselected-paths]
             [:label.path
              [:input {:type    "checkbox"
                       :name    (str "access-control-list[" id "][]")
                       :value   path
                       :checked (contains? paths path)}]
              (escape-html path)])]
          (javascript-tag
           ;; note: id will not contain characters which need quoting
           (str "document.getElementById('ut-" id "').style.display = 'inherit';"
                "document.getElementById('up-" id "').style.display = 'none';"))])])))

(defn- detail-page
  "Access control list detail hiccup."
  [{:keys [id] :as access-control-list} context api-paths & {:keys [scroll-to dirty]}]
  [:div.detail
   [:h2 "Access Control List: " id]

   (form/form
    (cond-> {:method "post"}
      dirty (assoc :data-dirty "true"))
    [:input {:type "submit", :style "display: none"}] ;; ensure enter key submits

    (into [:div] (form context access-control-list api-paths))

    [:div.actions
     [:button {:type "submit", :class "primary"} "Update"]
     " "
     [:a {:href (str "../" (url-encode id)), :class "button"} "Cancel"]])

   (when scroll-to
     (javascript-tag (str "document.getElementById(" (pr-str scroll-to) ").scrollIntoView()")))])

(defn- do-update
  "Handle update request."
  [{:keys        [params ::state/api-paths ::context]
    {:keys [id]} :params
    :as          req}
   access-control-lists]
  (let [select-all          (->> params
                                 keys
                                 (keep #(last (re-find #"select-all-(.*)" (name %))))
                                 first)
        select-none         (->> params
                                 keys
                                 (keep #(last (re-find #"select-none-(.*)" (name %))))
                                 first)
        access-control-list (form-> params access-control-lists)]
    (cond
      select-all
      (-> access-control-list
          (->form id)
          (assoc-in [:access-control-list (keyword select-all)] api-paths)
          (detail-page context api-paths :scroll-to (str "member-" select-all) :dirty true)
          (layout req (subtitle context id)))

      select-none
      (-> access-control-list
          (->form id)
          (assoc-in [:access-control-list (keyword select-none)] #{})
          (detail-page context api-paths :scroll-to (str "member-" select-none) :dirty true)
          (layout req (subtitle context id)))

      :else
      (-> (str "../" (url-encode id))
          (redirect :see-other)
          (assoc ::state/command
                 [({:application ::state/update-access-control-list-for-application
                    :institution ::state/update-access-control-list-for-institution} context)
                  id access-control-list])
          (assoc :flash (str "Updated access-control-list for " (name context) " '" id "'"))))))

(defroutes applications-handler
  (GET "/applications/:id/access-control-list" {:keys        [::context
                                                              ::state/access-control-lists
                                                              ::state/api-paths]
                                                {:keys [id]} :params
                                                :as          req}
       (if-let [access-control-list (get access-control-lists (keyword id))]
         (-> access-control-list
             (->form id)
             (detail-page context api-paths)
             (layout req (subtitle context id)))
         (not-found (str "Application '" id "' not found..")
                    req)))

  (POST "/applications/:id/access-control-list" {:keys        [::state/access-control-lists]
                                                 {:keys [id]} :params
                                                 :as          req}
        (if (get access-control-lists (keyword id))
          (do-update req access-control-lists)
          (not-found (str "Application '" id "' not found..")
                     req))))

(defroutes institutions-handler
  (GET "/institutions/:id/access-control-list" {:keys        [::context
                                                              ::state/access-control-lists
                                                              ::state/api-paths]
                                                {:keys [id]} :params
                                                :as          req}
       (let [access-control-lists (state/invert-access-control-lists access-control-lists)]
         (if-let [access-control-list (get access-control-lists (keyword id))]
           (-> access-control-list
               (->form id)
               (detail-page context api-paths)
               (layout req (subtitle context id)))
           (not-found (str "Institution '" id "' not found..")
                      req))))

  (POST "/institutions/:id/access-control-list" {:keys        [::state/access-control-lists]
                                                 {:keys [id]} :params
                                                 :as          req}
        (let [access-control-lists (state/invert-access-control-lists access-control-lists)]
          (if (get access-control-lists (keyword id))
            (do-update req access-control-lists)
            (not-found (str "Institution '" id "' not found..")
                       req)))))

(def handler
  (routes
   (fn [req]
     (applications-handler (assoc req ::context :application)))
   (fn [req]
     (institutions-handler (assoc req ::context :institution)))))
