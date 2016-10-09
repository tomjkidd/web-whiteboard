(defproject web-whiteboard "0.1.0-SNAPSHOT"
  :description "A clojure/clojurescript whiteboard for interactive drawing in the browser"
  :url "https://github.com/tomjkidd/web-whiteboard.git"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.229"]
                 [org.clojars.tomjkidd/carafe "0.1.0-SNAPSHOT"]
                 [info.sunng/ring-jetty9-adapter "0.9.5"]
                 [ring "1.5.0"]
                 [compojure "1.5.1"]
                 [ring/ring-defaults "0.2.1"
                  :exclusions [javax.servlet/servlet-api]]
                 [com.cognitect/transit-clj "0.8.288"]
                 [com.cognitect/transit-cljs "0.8.239"]
                 [org.clojure/core.async "0.2.391"]
                 [log4j/log4j "1.2.16" :exclusions [javax.mail/mail
                                              javax.jms/jms
                                              com.sun.jdmk/jmxtools
                                              com.sun.jmx/jmxri]]
                 [org.clojure/tools.logging "0.3.1"]
                 ; TODO: timbre is nice, but is heavy for reloading
                 ;[com.taoensso/timbre "4.7.4"]
                 [levand/immuconf "0.1.0"]
                 [com.stuartsierra/component "0.3.1"]
                 ]
  :jvm-opts ^:replace ["-Xmx1g" "-server"]
  :plugins [[lein-npm "0.6.1"]
            [lein-cljsbuild "1.1.4"]
            [lein-ring "0.9.7"]]
  :npm {:dependencies [[source-map-support "0.4.0"]]}
  :source-paths ["src" "target/classes"]
  :clean-targets ["resources/public/out" "release"]
  :target-path "target"

  :cljsbuild {
    :builds [{:id "none"
              :source-paths ["src"]
              :compiler {
                 :output-to "resources/public/js/webwhiteboard.js"
                 :output-dir "resources/public/out"
                 :optimizations :none
                 :source-map true}}]}

  :main ^:skip-aot web-whiteboard.server.core
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.0"]
                        [com.cemerick/piggieback "0.2.1"]
                        [org.clojure/tools.nrepl "0.2.10"]]
         :source-paths ["dev"]
         :repl-options {:init-ns user
                        :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}})
