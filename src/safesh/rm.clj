(ns safesh.rm
  (:require [clojure.tools.cli :as cli]
            [clojure.string :as string]
            [me.raynes.fs :as fs]
            [clojure.java.io :as io]
            [safesh.utils :as utils])
  (:gen-class))

(def CLI-OPTIONS
  [["-h" "--help" "Print help information."]])

(defn usage [summary & {message :message}]
  (let [lines ["safesh rm deletes secrets that are no longer needed. After "
               "deleting a secret, you need to manually remove it from your "
               "permissions file."
               ""
               "Usage: safesh rm NAME [NAME [NAME [...]]]"
               ""
               "NAME: the name of the secret to delete (can specify many)"
               ""
               "OPTIONS:"
               summary]]
    (string/join "\n"
      (if (nil? message) lines (concat [message ""] lines)))))

(defn delete-secret! [options]
  (let [{:keys [secret-name secrets key-name secrets-path]} options
        members (-> secret-name keyword secrets)]
    (if (nil? members)
      (do
          (.println *err* (str "Secret " secret-name " doesn't exist."))
          false)

      (if (some #{key-name} members)
        (do
          (doseq [member members]
            (let [path (str secrets-path "/" member "/" secret-name)]
              (when (fs/file? path)
                (println (str "Deleting " path))
                (io/delete-file path))))
          true)

        (do
          (.println *err* (str "You don't have access to secret " secret-name "."))
          false)))))

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

    (let [{:keys [secrets secrets-path key-name private-key-path]} safe-options
          deleted-secrets (filter #(delete-secret! (merge {:secret-name %} safe-options))
                                  arguments)]
      (when (> (count deleted-secrets) 0)
        (->>
          deleted-secrets
          (string/join ", ")
          (printf "Please remove %s from your permissions file.\n"))))))
