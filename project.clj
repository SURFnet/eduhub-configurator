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

(defproject ooapi-gateway-configurator "0.1.0-SNAPSHOT"
  :description "Configure the Surf OOAPI Gateway"
  :license {:name "GPLv3"
            :url  "https://www.gnu.org/licenses/gpl-3.0.en.html"}
  :url "https://github.com/SURFnet/ooapi-gateway-configurator"

  :dependencies [[cheshire "5.10.1"]
                 [ch.qos.logback/logback-classic "1.2.5"]
                 [clj-commons/clj-yaml "0.7.107"]
                 [compojure "1.6.2"]
                 [environ "1.2.0"]
                 [hiccup "1.0.5"]
                 [org.clojure/clojure "1.10.3"]
                 [org.clojure/data.json "2.4.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/tools.logging "1.1.0"]
                 [ring "1.9.4"]
                 [ring-oauth2 "0.1.5"]
                 [ring/ring-defaults "0.3.3"]
                 [ring/ring-jetty-adapter "1.9.4"]]

  :main ^:skip-aot ooapi-gateway-configurator.core

  :target-path "target/%s"

  :profiles {:uberjar {:aot          :all
                       :uberjar-name "ooapi-gateway-configurator.jar"}
             :dev     {:source-paths ["dev"]
                       :dependencies [[org.clojure/tools.namespace "1.1.0"]
                                      [clj-kondo "2021.08.03"]
                                      [ring/ring-mock "0.4.0"]
                                      [ring/ring-json "0.5.1"]]
                       :plugins      [[lein-kibit "0.1.8"]]
                       :aliases      {"lint" ["do"
                                              ["run" "-m" "clj-kondo.main" "--lint" "src"]
                                              "kibit"]}}}

  :repl-options {:init-ns user})
