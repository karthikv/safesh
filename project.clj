(defproject safesh "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.cli "0.3.1"]
                 [me.raynes/fs "1.4.6"]
                 [clj-yaml "0.4.0"]
                 [commons-codec/commons-codec "1.7"]
                 [org.bouncycastle/bcprov-jdk15 "1.46"]]
  :plugins [[lein-bin "0.3.4"]]
  :bin {:name "safesh"}
  :main safesh.core)
