(ns safesh.fetch
  (:require [clojure.tools.cli :as cli]
            [clojure.string :as string]
            [safesh.utils :as utils]
            [safesh.cat :as cat])
  (:gen-class))

(def CLI-OPTIONS [])

(defn usage [summary & {message :message}]
  (let [lines ["safesh fetch fetches secrets to the local filesystem. fetch reads "
               "in a YAML file with key-value pairs. The keys correspond to document "
               "names in safesh, and the values correspond to paths on the local "
               "filesystem. safesh fetches the documents by name and puts them at "
               "their corresponding paths."
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
                                    "Need exactly one argument: YAML file path"))
      errors (utils/print-exit! 1 (usage summary :message
                                         (str (string/join "\n" errors)))))

    (let [path (or (first arguments) "safesh.yml")]
              )))
