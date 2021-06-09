(ns ooapi-gateway-configurator.access-control-lists
  (:require [clojure.set :as set]
            [compojure.core :refer [defroutes GET POST]]
            [hiccup.element :refer [javascript-tag]]
            [hiccup.util :refer [escape-html]]
            [ooapi-gateway-configurator.anti-forgery :refer [anti-forgery-field]]
            [ooapi-gateway-configurator.html :refer [layout not-found]]
            [ooapi-gateway-configurator.state :as state]
            [ring.util.codec :refer [url-encode]]
            [ring.util.response :refer [redirect]]))

(defn- ->form
  [access-control-list application-id]
  {:application-id      (name application-id)
   :access-control-list (zipmap (keys access-control-list)
                                (->> access-control-list vals (map set)))})

(defn- form->
  "From params into an access-control-list.  Ensure all institutions are
  represented."
  [{:keys [access-control-list]} institutions]
  (reduce (fn [m institution-id] (update m institution-id set))
          access-control-list
          (keys institutions)))

(defn- form [{:keys [access-control-list]} api-paths]
  [[:div.field]
   (for [[institution paths] (sort-by key access-control-list)]
     (let [inst-id (name institution)]
       [:div.institution {:id (str "institution-" inst-id)}
        [:h3 (escape-html inst-id)]
        [:div.actions
         [:input {:type  "submit", :class "secondary"
                  :name  (str "select-all-" inst-id)
                  :value "Select all"}]
         " "
         [:input {:type  "submit", :class "secondary"
                  :name  (str "select-none-" inst-id)
                  :value "Select none"}]]

        (for [path (sort paths)]
          [:label.path
           [:input {:type    "checkbox"
                    :name    (str "access-control-list[" inst-id "][]")
                    :value   path
                    :checked (contains? paths path)}]
           (escape-html path)])

        (when-let [unselected-paths (-> api-paths (set/difference paths) (sort) (seq))]
          [:div.unselected
           [:button {:id      (str "ut-" inst-id)
                     :type    "button", :class "secondary"
                     :style   "display:none"
                     ;; note: inst-id will not contain characters which need quoting
                     :onclick (str "document.getElementById('ut-" inst-id "').style.display = 'none';"
                                   "document.getElementById('up-" inst-id "').style.display = 'inherit';")}
            "More.."]
           [:div.paths {:id (str "up-" inst-id)}
            (for [path unselected-paths]
              [:label.path
               [:input {:type    "checkbox"
                        :name    (str "access-control-list[" inst-id "][]")
                        :value   path
                        :checked (contains? paths path)}]
               (escape-html path)])]
           (javascript-tag
            ;; note: inst-id will not contain characters which need quoting
            (str "document.getElementById('ut-" inst-id "').style.display = 'inherit';"
                 "document.getElementById('up-" inst-id "').style.display = 'none';"))])]))])

(defn- detail-page
  "Application detail hiccup."
  [{:keys [application-id] :as access-control-list} api-paths & {:keys [scroll-to]}]
  [:div.detail
   [:h2 "Access Control List: " application-id]

   [:form {:method :post}
    [:input {:type "submit", :style "display: none"}] ;; ensure enter key submits
    (anti-forgery-field)

    (into [:fieldset] (form access-control-list api-paths))

    [:div.actions
     [:button {:type "submit", :class "primary"} "Update"]
     " "
     [:a {:href (str "../" (url-encode application-id)), :class "button"} "Cancel"]]]

   (when scroll-to
     (javascript-tag (str "document.getElementById(" (pr-str scroll-to) ").scrollIntoView()")))])

(defn- do-update
  "Handle update request."
  [{:keys        [params
                              ::state/api-paths
                              ::state/institutions]
    {:keys [id]} :params
    :as          req}]
  (let [select-all          (->> params
                                 keys
                                 (keep #(last (re-find #"select-all-(.*)" (name %))))
                                 first)
        select-none         (->> params
                                 keys
                                 (keep #(last (re-find #"select-none-(.*)" (name %))))
                                 first)
        access-control-list (form-> params institutions)]
    (cond
      select-all
      (-> access-control-list
          (->form id)
          (assoc-in [:access-control-list (keyword select-all)] api-paths)
          (detail-page api-paths :scroll-to (str "institution-" select-all))
          (layout req))

      select-none
      (-> access-control-list
          (->form id)
          (assoc-in [:access-control-list (keyword select-none)] #{})
          (detail-page api-paths :scroll-to (str "institution-" select-none))
          (layout req))

      :else
      (-> (str "../" (url-encode id))
          (redirect :see-other)
          (assoc ::state/command [::state/update-access-control-list-for-application
                                  id access-control-list])
          (assoc :flash (str "Updated access-control-list for application '" id "'"))))))

(defroutes handler
  (GET "/applications/:id/access-control-list" {:keys        [::state/access-control-lists
                                                              ::state/api-paths]
                                                {:keys [id]} :params
                                                :as          req}
       (if-let [access-control-list (get access-control-lists (keyword id))]
         (-> access-control-list
             (->form id)
             (detail-page api-paths)
             (layout req))
         (not-found (str "Application '" id "' not found..")
                    req)))

  (POST "/applications/:id/access-control-list" {:keys        [::state/access-control-lists]
                                                 {:keys [id]} :params
                                                 :as          req}
        (if (get access-control-lists (keyword id))
          (do-update req)
          (not-found (str "Application '" id "' not found..")
                     req))))
