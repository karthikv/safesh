(ns safesh.cat
  (:require [clojure.tools.cli :as cli]
            [clojure.string :as string]
            [me.raynes.fs :as fs]
            [clj-yaml.core :as yaml]
            [safesh.ssh :as ssh]
            [safesh.aes :as aes]
            [safesh.utils :as utils])
  (:gen-class))

(def CLI-OPTIONS
  [["-h" "--help" "Print help information."]])

(defn usage [summary & {message :message}]
  (let [lines ["safesh cat decrypts and outputs a secret to stdout."
               ""
               "Usage: safesh cat OPTIONS NAME [NAME [NAME [...]]]"
               ""
               "NAME: the name of the secret to output (can specify many)"
               ""
               "OPTIONS:"
               summary]]
    (string/join "\n"
      (if (nil? message) lines (concat [message ""] lines)))))

(def private-key nil)
(defn read-secret [secret-path private-key-path]
  (if (not private-key)
    (def private-key (ssh/read-private-key! private-key-path)))

  (let [secret (-> secret-path slurp yaml/parse-string)
        aes-key (ssh/decrypt (secret :key) private-key)]
    (aes/decrypt (secret :ciphertext) aes-key)))

(defn execute! [safe-options args]
  (let [{:keys [options arguments errors summary]}
       (cli/parse-opts args CLI-OPTIONS)]
    (cond
      (:help options) (utils/print-exit! 0 (usage summary))
      (= (count arguments) 0)
        (utils/print-exit! 1 (usage summary :message
                                    "Must specify at least one secret."))
      errors (utils/print-exit! 1 (usage summary :message
                                         (str (string/join "\n" errors)))))

    (let [{:keys [private-key-path key-name secrets secrets-path]} safe-options]
      (doseq [secret-name arguments]
        (let [members (-> secret-name keyword secrets)]
          (cond
            (nil? members)
              (.println *err* (str secret-name " doesn't exist."))
            (not (some #{key-name} members))
              (.println *err* (str key-name " can't access " secret-name "."))
            :else
              (let [secret-path (str secrets-path "/" key-name "/" secret-name)]
                (when (-> secret-path fs/file? not)
                  (utils/print-exit! 1 "Secret doesn't exist on filesystem."))
                (let [secret (read-secret secret-path private-key-path)]
                  (print secret)
                  (flush)))))))))
