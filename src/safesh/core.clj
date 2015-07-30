(ns safesh.core
  (:require [me.raynes.fs :as fs]
            [clojure.tools.cli :as cli]
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
               "ls - lists secrets that can be decrypted"
               "cat - decrypts and outputs a secret to stdout"
               "update - updates and releases a secret to those with permissions"
               "fetch - fetches secrets to local filesystem"
               "rm - deletes secrets"
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

    (fs/with-mutable-cwd
      (let [options (safe/set-up! options)
            command (first arguments)
            command-args (rest arguments)]
        (case command
          "ls" (safe/ls! command-args options)
          "cat" (safe/cat! command-args options)
          "update" (safe/update! command-args options)
          "fetch" (safe/fetch! command-args options)
          "rm" (safe/rm! command-args options)
          (utils/print-exit! 1 "Invalid command"))
        (shutdown-agents)))))
