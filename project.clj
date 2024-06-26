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

  :dependencies [[org.clojure/clojure "1.11.3"]

                 ;; setup
                 [environ "1.2.0"]
                 [nl.jomco/envopts "0.0.4"]

                 ;; web
                 [compojure "1.7.1"]
                 [hiccup "1.0.5"]
                 [ring-oauth2 "0.2.2"]
                 [ring/ring-core "1.12.2"]
                 [ring/ring-defaults "0.5.0"]
                 [ring/ring-jetty-adapter "1.12.2"]
                 [nl.jomco/clj-http-status-codes "0.1"]
                 [nl.jomco/ring-trace-context "0.0.8"]

                 ;; data
                 [org.clojure/data.json "2.5.0"]
                 [org.clojure/core.cache "1.1.234"]
                 [clj-commons/clj-yaml "1.0.27"]
                 [datascript "1.7.1"]

                 ;; logging
                 [org.clojure/tools.logging "1.3.0"]
                 [ch.qos.logback.contrib/logback-jackson "0.1.5"]
                 [ch.qos.logback.contrib/logback-json-classic "0.1.5"]
                 [ch.qos.logback/logback-classic "1.5.6"]
                 [com.fasterxml.jackson.core/jackson-core "2.17.1"]
                 [com.fasterxml.jackson.core/jackson-databind "2.17.1"]]

  :main ^:skip-aot ooapi-gateway-configurator.core

  :target-path "target/%s"

  :profiles {:uberjar {:aot          :all
                       :target-path  "target/uberjar"
                       :uberjar-name "ooapi-gateway-configurator.jar"}
             :dev     {:source-paths ["dev"]
                       :dependencies [[clj-kondo "RELEASE"]
                                      [org.clojure/tools.namespace "RELEASE"]
                                      [ring/ring-json "RELEASE"]
                                      [ring/ring-mock "RELEASE"]]
                       :plugins      [[lein-ancient "RELEASE"]]
                       :aliases      {"lint"  ["run" "-m" "clj-kondo.main" "--lint" "src"]
                                      "check-deps" ["ancient" "check" ":no-profiles" ":exclude" "keep-this-version"]}}}

  :repl-options {:init-ns user})
