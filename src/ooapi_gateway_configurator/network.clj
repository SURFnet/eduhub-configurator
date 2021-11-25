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

(ns ooapi-gateway-configurator.network
  (:require [clojure.data.json :as json]
            [clojure.string :as s]
            [compojure.core :refer [defroutes GET]]
            [datascript.core :as d]
            [ooapi-gateway-configurator.html :refer [layout]]
            [ooapi-gateway-configurator.model :as model]
            [ring.util.response :as response]))

(def app-node {:shape "box", :color "#ffaaaa"})
(def ins-node {:shape "ellipse", :color "#aaffaa"})

(defn ->nodes-edges [{:keys [model]}]
  [;; nodes
   (into (map (fn [id] (into app-node
                             {:id    (str "app-" id)
                              :label id
                              :url   (str "/applications/" id)}))
              (model/app-ids model))
         (map (fn [id] (into ins-node
                             {:id    (str "ins-" id)
                              :label id
                              :url   (str "/institutions/" id)}))
              (model/institution-ids model)))
   ;; edges
   (map (fn [[app institution]]
          {:from (str "app-" app)
           :to (str "ins-" institution)})
        (d/q '[:find ?a ?e :where
               [?eid :institution/id ?e]
               [?aid :app/id ?a]
               [?xs :access/paths _] ;; access entity must have paths to count
               [?xs :access/app ?aid]
               [?xs :access/institution ?eid]]
             model))])

(defn ->dot [[nodes edges]]
  (str "graph network {\n"
       (->> nodes
            (map #(str "  " (pr-str (:id %))
                       " [label=" (pr-str (:label %))
                       " shape=" (pr-str (:shape %))
                       " color=" (pr-str (:color %))
                       "];"))
            (s/join "\n"))
       "\n\n"
       (->> edges
            (map #(str "  " (pr-str (:from %)) " -- " (pr-str (:to %)) ";"))
            (s/join "\n"))
       "\n}\n"))

(defn network-page [[nodes edges]]
  [:div.index
   [:nav
    [:a {:href "/"} "⌂"]
    " / "
    [:a.current "Network"]]

   [:section
    [:noscript
     [:p.warning "Sorry, you'll need JavaScript to use this function."]
     [:p
      "Download a dot-file instead and use "
      [:a {:href "https://www.graphviz.org"} "graphviz"]
      " to visualize the network."]
     [:p [:a.button {:href "/network.dot"} "network.dot"]]]

    [:div#network {:style "display:none"}
     [:div#network-box [:div#network-content]]
     [:script {:src "/vis-network/vis-network.min.js"}]
     [:script {:type "text/javascript"}
      "(function() {
         const nodes = " (json/json-str nodes) ";
         const edges = " (json/json-str edges) ";
         const nw = new vis.Network(
           document.getElementById('network-content'), {
             nodes: new vis.DataSet(nodes),
             edges: new vis.DataSet(edges)
           }, {
             // options
           }
         );
         nw.on('doubleClick', (ev) => {
           const nodeId = ev.nodes[0];
           const node = nodes.find(({id}) => id === nodeId);
           if (node) {
             if (ev.event.srcEvent.ctrlKey) {
               const win = window.open(node.url, 'ooapi-gateway-node');
               win.focus();
             } else {
               document.location = node.url;
             }
           }
         });
         document.getElementById('network').style.display = null;
       })()"]
     [:p.help
      "Scroll to zoom in/out, drag nodes to reposition, reload to
      reposition all, double click to navigate to the detail page,
      ctrl double click to open detail page in a different browser
      tab."]]]])

(defroutes handler
  (GET "/network/" req
    (-> req
        (->nodes-edges)
        (network-page)
        (layout req "Network")))
  (GET "/network.dot" req
    (-> req
        (->nodes-edges)
        (->dot)
        (response/response)
        (response/content-type "text/x-graphviz")
        (assoc-in [:headers "Content-Disposition"]
                  "attachment; filename=\"network.dot\""))))
