(defproject btc-robot "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/data.json "0.2.6"]
                 [clj-http "2.0.0"]
                 [digest "1.4.4"]
                 [org.apache.logging.log4j/log4j-core "2.5"]
                 [org.slf4j/slf4j-log4j12 "1.7.13"]
                 [org.clojure/tools.logging "0.3.1"]]
  :main ^:skip-aot rocks.pho.btc-robot.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
