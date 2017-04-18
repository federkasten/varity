(defproject varity "0.2.0-SNAPSHOT"
  :description "Variant translation library for Clojure"
  :url "https://github.com/chrovis/varity"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :min-lein-version "2.7.0"
  :dependencies [[org.clojure/clojure "1.8.0" :scope "provided"]
                 [clj-hgvs "0.1.0"]
                 [cljam "0.2.1"]
                 [org.apache.commons/commons-compress "1.13"]
                 [proton "0.1.1"]]
  :test-selectors {:default (complement :slow)
                   :slow :slow
                   :all (constantly true)}
  :profiles {:dev {:dependencies [[cavia "0.4.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0-alpha15"]]}})
