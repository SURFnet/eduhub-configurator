;; Copyright (C) 2021 Remco van 't Veer, Joost Diepenmaat
;;
;; This program is free software: you can redistribute it and/or modify
;; it under the terms of the GNU Affero General Public License as published by
;; the Free Software Foundation, either version 3 of the License, or
;; (at your option) any later version.
;;
;; This program is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;; GNU Affero General Public License for more details.
;;
;; You should have received a copy of the GNU Affero General Public License
;; along with this program.  If not, see <https://www.gnu.org/licenses/>.

(defproject ooapi-gateway-configurator "0.1.0-SNAPSHOT"
  :description "Configure the Surf OOAPI Gateway"

  :license {:name "GNU Affero General Public License"
            :url  "https://www.gnu.org/licenses/agpl-3.0.html"}

  :dependencies [[ch.qos.logback/logback-classic "1.2.3"]
                 [clj-commons/clj-yaml "0.7.106"]
                 [compojure "1.6.2"]
                 [environ "1.2.0"]
                 [hiccup "1.0.5"]
                 [org.clojure/clojure "1.10.3"]
                 [org.clojure/data.json "2.2.3"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/tools.logging "1.1.0"]
                 [ring "1.9.3"]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-jetty-adapter "1.9.3"]
                 [ring/ring-mock "0.4.0"]]

  :main ^:skip-aot ooapi-gateway-configurator.core

  :target-path "target/%s"

  :profiles {:uberjar {:aot :all}
             :dev     {:source-paths ["dev"]}}

  :repl-options {:init-ns user})
