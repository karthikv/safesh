(defproject safesh "0.1.0"
  :description "Store and manage encrypted secrets using SSH keys."
  :url "https://github.com/karthikv/safesh"
  :license {:name "MIT"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.cli "0.3.1"]
                 [me.raynes/fs "1.4.6"]
                 [clj-yaml "0.4.0"]
                 [commons-codec/commons-codec "1.7"]
                 [org.bouncycastle/bcprov-jdk15 "1.46"]]
  :plugins [[lein-bin "0.3.4"]]
  :bin {:name "safesh"}
  :main safesh.core
  :aot [safesh.core])
