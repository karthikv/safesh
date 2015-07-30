(ns safesh.ls
  (:require [clojure.tools.cli :as cli]
            [clojure.string :as string]
            [safesh.utils :as utils])
  (:gen-class))

(def CLI-OPTIONS
  [["-h" "--help" "Print help information."]])

(defn usage [summary & {message :message}]
  (let [lines ["safesh ls lists secrets that can be decrypted."
               ""
               "Usage: safesh ls OPTIONS"
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
      (not= (count arguments) 0)
        (utils/print-exit! 1 (usage summary :message "Must have no arguments."))
      errors (utils/print-exit! 1 (usage summary :message
                                         (str (string/join "\n" errors)))))

    (let [{:keys [key-name secrets]} safe-options]
      (doseq [secret-name (->> secrets
                               (filter #(some #{key-name} (second %)))
                               (map first))]
              (-> secret-name str (subs 1) println)))))
