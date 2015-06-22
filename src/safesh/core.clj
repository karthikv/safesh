(ns safesh.core
  (:require [clojure.tools.cli :as cli]
            [clojure.string :as string]
            [safesh.safe :as safe]
            [safesh.utils :as utils])
  (:gen-class))

(def CLI-OPTIONS
  [["-c" "--config-path PATH" "Local configuration file path."
    :default "config.yml"]
   ["-p" "--permissions-path PATH" "Secret permissions file path."
    :default "permissions.yml"]
   ["-k" "--keys-path PATH" "Directory where public SSH keys are stored."
    :default "keys"]
   ["-s", "--secrets-path PATH" "Directory where secrets are stored."
    :default "."]
   ["-h" "--help" "Print help information."]])

(defn usage [summary & {message :message}]
  (let [lines ["safesh stores secrets encrypted using SSH keys."
               "Usage: safesh OPTIONS COMMAND"
               ""
               "COMMANDS:"
               (str "update -- Releases/Revokes secrets based on the"
                 " given configuration file.")
               (str "store NAME -- Stores a new value for the secret"
                 " named NAME.")
               ""
               "OPTIONS:"
               summary]]
    (string/join "\n"
      (if (nil? message) lines (concat [message ""] lines)))))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]}
        (cli/parse-opts args CLI-OPTIONS :in-order true)]
    (cond
      (:help options) (utils/print-exit! 0 (usage summary))
      (< (count arguments) 1) (utils/print-exit! 1 (usage summary :message
                                                         "Missing command"))
      errors (utils/print-exit! 1 (usage summary :message
                                         (str (string/join "\n" errors)))))
    (safe/set-up! options)
    (let [command (first arguments)
          command-args (rest arguments)]
      (case command
        "ls" (safe/ls! command-args options)
        "cat" (safe/cat! command-args options)
        "update" (safe/update! command-args options)
        "store" (safe/store! command-args options)
        (utils/print-exit! 1 "Invalid command"))
      (shutdown-agents))))
