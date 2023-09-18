;; Copyright (C) 2021, 2022 SURFnet B.V.
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

(defproject nl.surf/ooapi-gateway-configurator "0.1.0-SNAPSHOT"
  :description "Configure the Surf OOAPI Gateway"
  :license {:name "GPLv3"
            :url  "https://www.gnu.org/licenses/gpl-3.0.en.html"}
  :url "https://github.com/SURFnet/ooapi-gateway-configurator"

  :dependencies [[ch.qos.logback.contrib/logback-jackson "0.1.5"]
                 [ch.qos.logback.contrib/logback-json-classic "0.1.5"]
                 [ch.qos.logback/logback-classic "1.4.11"]
                 [clj-commons/clj-yaml "1.0.27"]
                 [compojure "1.7.0"]
                 [datascript "1.5.3"]
                 [environ "1.2.0"]
                 [hiccup "1.0.5"]
                 [nl.jomco/envopts "0.0.4"]
                 [nl.jomco/clj-http-status-codes "0.1"]
                 [nl.jomco/ring-trace-context "0.0.8"]
                 [org.clojure/clojure "1.11.1"]
                 [org.clojure/data.json "2.4.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/tools.logging "1.2.4"]
                 [ring "1.10.0"]
                 [ring-oauth2 "0.2.2"]
                 [ring/ring-defaults "0.4.0"]

                 [ring/ring-jetty-adapter "1.10.0"
                  :exclusions [org.eclipse.jetty/jetty-server]]
                 ;; CVE-2023-40167
                 [org.eclipse.jetty/jetty-server "9.4.52.v20230823" :upgrade :security]

                 ;; CVE-2020-36518, CVE-2022-42003, CVE-2022-42004
                 [com.fasterxml.jackson.core/jackson-databind "2.15.2"]]

  :main ^:skip-aot ooapi-gateway-configurator.core

  :target-path "target/%s"

  :profiles {:uberjar {:aot          :all
                       :target-path  "target/uberjar"
                       :uberjar-name "ooapi-gateway-configurator.jar"}
             :dev     {:source-paths ["dev"]
                       :dependencies [[clj-kondo "2023.09.07"]
                                      [org.clojure/tools.namespace "1.4.4"]
                                      [ring/ring-json "0.5.1"]
                                      [ring/ring-mock "0.4.0"]]
                       :plugins      [[lein-ancient "0.7.0"]]
                       :aliases      {"lint"  ["run" "-m" "clj-kondo.main" "--lint" "src"]
                                      "check-deps" ["ancient" "check" ":no-profiles" ":exclude" "security"]}}}

  :repl-options {:init-ns user})
