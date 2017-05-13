(defproject jenkins-dl "0.1.0-SNAPSHOT"
  :description "Small Utility for Downloading Artifacts from Jenkins-CI"
  :url "https://github.com/paper-tiger/jenkins-dl"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.cli "0.3.5"]
                 [clj-http "2.3.0"]
                 [org.clojure/data.json "0.2.6"]
                 [slingshot "0.12.2"]
                 [org.clojure/tools.logging "0.3.1"]]
  :main ^:skip-aot jenkins-dl.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
