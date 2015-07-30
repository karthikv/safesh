(ns safesh.delete
  (:require [clojure.tools.cli :as cli]
            [clojure.string :as string]
            [me.raynes.fs :as fs]
            [clojure.java.io :as io]
            [safesh.utils :as utils])
  (:gen-class))

(def CLI-OPTIONS [])

(defn usage [summary & {message :message}]
  (let [lines ["safesh delete deletes secrets that are no longer needed."
               "Usage: safesh delete NAME [NAME [NAME [...]]]"
               ""
               "NAME: the name of the secret to delete (can specify many)"
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
      (= (count arguments) 0)
        (utils/print-exit! 1 (usage summary :message
                                    "Must specify at least one secret."))
      errors (utils/print-exit! 1 (usage summary :message
                                         (str (string/join "\n" errors)))))

    (let [{:keys [secrets secrets-path key-name private-key-path]} safe-options]
      (doseq [secret-name arguments]
        (let [members (-> secret-name keyword secrets)]
          (if (nil? members)
            (println (str "Secret " secret-name " doesn't exist."))
            (if (some #{key-name} members)
              (doseq [member members]
                (let [path (str secrets-path "/" member "/" secret-name)]
                  (when (fs/file? path)
                    (println (str "Deleting " path))
                    (io/delete-file path))))
              (println (str "You don't have access to secret " secret-name ".")))))))))
