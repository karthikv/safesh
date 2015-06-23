(ns safesh.update
  (:require [clojure.tools.cli :as cli]
            [clojure.string :as string]
            [clojure.set :refer [difference]]
            [me.raynes.fs :as fs]
            [clj-yaml.core :as yaml]
            [safesh.ssh :as ssh]
            [safesh.aes :as aes]
            [safesh.utils :as utils]
            [safesh.cat :as cat])
  (:gen-class))

(def CLI-OPTIONS
  [["-r" "--release-only" "Only release secret; don't rewrite it."
    :default false]
   ["-h" "--help" "Print help information."]])

(defn usage [summary & {message :message}]
  (let [lines ["safesh update updates and releases a secret to those with permissions."
               "Usage: safesh update OPTIONS NAME"
               ""
               "NAME: the name of the secret to update/release"
               ""
               "OPTIONS:"
               summary]]
    (string/join "\n"
      (if (nil? message) lines (concat [message ""] lines)))))

(defn update-secret! [options]
  (let [{:keys [keys-path members secrets-path secret-name text]} options
        keys-set (->> keys-path fs/file .list set)
        non-members (difference keys-set (set members))]
    (doseq [member members]
      (let [key-path (str keys-path "/" member)
            dir-path (str secrets-path "/" member)
            path (str dir-path "/" secret-name)]
        (cond
          (fs/file? dir-path) (utils/print-exit! (str dir-path " can't be a file"))
          (-> dir-path fs/directory? not) (.mkdir (java.io.File. dir-path)))
        (if (fs/directory? path) (utils/print-exit! (str path " can't be a directory")))
        (let [public-key (ssh/read-public-key! key-path)
              aes-key (aes/generate-key)
              aes-key-encrypted (ssh/encrypt aes-key public-key)
              ciphertext (aes/encrypt text aes-key)]
          (println (str "Storing ciphertext into " path))
          (spit path (yaml/generate-string {:key aes-key-encrypted
                                            :ciphertext ciphertext})))))

    (doseq [non-member non-members]
      (let [path (str secrets-path "/" non-member "/" secret-name)]
        (cond
          (fs/directory? path) (utils/print-exit! (str path " can't be a directory"))
          (fs/file? path) (do
                            (println (str "Deleting ciphertext from " path))
                            (fs/delete path)))))))

(defn read-input! [input-so-far]
  (if-let [input (read-line)]
    (recur (str input-so-far input "\n"))
    input-so-far))

(defn execute! [safe-options args]
  (let [{:keys [options arguments errors summary]}
       (cli/parse-opts args CLI-OPTIONS)]
    (cond
      (:help options) (utils/print-exit! 0 (usage summary))
      (not= (count arguments) 1)
        (utils/print-exit! 1 (usage summary :message
                                    "Need exactly one argument: secret name."))
      errors (utils/print-exit! 1 (usage summary :message
                                         (str (string/join "\n" errors)))))

    (let [{:keys [keys-path key-name private-key-path secrets secrets-path]} safe-options
          secret-name (first arguments)
          members (-> secret-name keyword secrets)]
      (cond
        (nil? members)
          (utils/print-exit! 1 (str "Must have " secret-name " in permissions file."))
        (not (some #{key-name} members))
          (utils/print-exit! 1 (str "Must give " key-name " permissions to " secret-name "."))
        :else
          (let [secret-path (str secrets-path "/" key-name "/" secret-name)]
            (cond
              (fs/directory? secret-path)
                (utils/print-exit! 1 (str secret-path " can't be a directory."))
              (and (-> secret-path fs/file? not) (options :release-only))
                (utils/print-exit! 1 (str secret-path " must exist.")))

            (let [text (if (options :release-only)
                         (cat/read-secret secret-path private-key-path)
                         (do
                           (println "Enter your secret text:")
                           (read-input! "")))]
              (when (= 0 (count text)) (utils/print-exit! 1 "Must provide secret text."))
              (update-secret! {:keys-path keys-path
                               :members members
                               :secrets-path secrets-path
                               :secret-name secret-name
                               :text text})))))))
