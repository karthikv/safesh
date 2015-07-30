(ns safesh.fetch
  (:require [clojure.tools.cli :as cli]
            [clojure.string :as string]
            [me.raynes.fs :as fs]
            [clj-yaml.core :as yaml]
            [safesh.utils :as utils]
            [safesh.cat :as cat])
  (:gen-class))

(def CLI-OPTIONS
  [["-h" "--help" "Print help information."]])

(defn usage [summary & {message :message}]
  (let [lines ["safesh fetch fetches secrets to the local filesystem. fetch reads "
               "in a YAML file with key-value pairs. The keys correspond to secret "
               "names in safesh, and the values correspond to paths on the local "
               "filesystem. safesh fetches the secrets by name and puts them at "
               "their corresponding paths."
               ""
               "Usage: safesh fetch [PATH]"
               ""
               "PATH: (optional) path to the YAML file"
               ""
               "OPTIONS:"
               summary]]
    (string/join "\n"
      (if (nil? message) lines (concat [message ""] lines)))))

(defn execute! [safe-options args]
  (let [{:keys [options arguments errors summary]}
       (cli/parse-opts args CLI-OPTIONS)]
    (cond
      (:help options) (utils/print-exit! 0 (usage summary))
      (> (count arguments) 1)
        (utils/print-exit! 1 (usage summary :message
                                    "Need exactly one argument: YAML file path."))
      errors (utils/print-exit! 1 (usage summary :message
                                         (str (string/join "\n" errors)))))

    (let [path (utils/expand-path (or (first arguments) "safesh.yml"))]
      (if (-> path fs/file? not)
        (utils/print-exit! 1 (str "YAML path " path " must exist.")))

      (let [{:keys [secrets secrets-path key-name private-key-path]} safe-options
            dir-path (str secrets-path "/" key-name "/")
            secret-keyword-paths (-> path slurp yaml/parse-string)]
        (doseq [[secret-keyword path] secret-keyword-paths]
          (let [secret-name (name secret-keyword)]
            (if (-> secrets secret-keyword not)
              (.println *err* (str "Secret " secret-name " doesn't exist."))
              (do
                (->>
                  (cat/read-secret (str dir-path secret-name) private-key-path)
                  (spit (utils/expand-path path)))
                (println (str "Reading " secret-name " into " path))))))))))
