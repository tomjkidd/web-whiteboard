(defproject web-whiteboard "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.229"]
                 [org.clojars.tomjkidd/carafe "0.1.0-SNAPSHOT"]]
  :jvm-opts ^:replace ["-Xmx1g" "-server"]
  :plugins [[lein-npm "0.6.1"]
            [lein-cljsbuild "1.1.4"]]
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
                 :source-map true}}]})
