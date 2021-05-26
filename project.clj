(defproject ooapi-gateway-configurator "0.1.0-SNAPSHOT"
  :description "Configure the Surf OOAPI Gateway"

  :dependencies [[cheshire "5.10.0"]
                 [ch.qos.logback/logback-classic "1.2.3"]
                 [clj-commons/clj-yaml "0.7.106"]
                 [compojure "1.6.2"]
                 [environ "1.2.0"]
                 [hiccup "1.0.5"]
                 [org.clojure/clojure "1.10.3"]
                 [org.clojure/data.json "2.2.3"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/tools.logging "1.1.0"]
                 [ring "1.9.3"]
                 [ring-oauth2 "0.1.5"]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-jetty-adapter "1.9.3"]
                 [ring/ring-mock "0.4.0"]]

  :main ^:skip-aot ooapi-gateway-configurator.core

  :target-path "target/%s"

  :profiles {:uberjar {:aot :all}
             :dev     {:source-paths ["dev"]
                       :dependencies [[org.clojure/tools.namespace "1.1.0"]
                                      [clj-kondo "2021.04.23"]]
                       :plugins      [[lein-kibit "0.1.8"]]
                       :aliases      {"lint" ["do"
                                              ["run" "-m" "clj-kondo.main" "--lint" "src"]
                                              "kibit"]}}}

  :repl-options {:init-ns user})
