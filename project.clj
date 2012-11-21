(defproject itsy "0.1.2-SNAPSHOT"
  :description "A threaded web-spider written in Clojure "
  :url "https://github.com/dakrone/itsy"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [clj-http "0.5.7"]
                 [com.cemerick/url "0.0.6"]
                 [org.clojure/tools.logging "0.2.3"]
                 [log4j "1.2.16"]
                 [org.slf4j/slf4j-api "1.6.4"]
                 [org.slf4j/slf4j-log4j12 "1.6.4"]
                 [org.slf4j/jcl-over-slf4j "1.6.4"]
                 [org.slf4j/jul-to-slf4j "1.6.4"]
                 [org.apache.tika/tika-core "1.1"]
                 [org.apache.tika/tika-parsers "1.1"]]
  :resource-paths ["etc"])
