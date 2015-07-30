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
  (:import java.io.File)
  (:gen-class))

(def CLI-OPTIONS
  [["-r" "--release-only" "Only release secret; don't update its value"
    :default false]
   ["-f" "--force-rewrite" "When -r is specified, rewrite secrets even if they exist."
    :default false]
   ["-h" "--help" "Print help information."]])

(defn usage [summary & {message :message}]
  (let [lines ["safesh update updates and releases a secret to those with permissions."
               ""
               "Usage: safesh update OPTIONS NAME [NAME [NAME [...]]]"
               ""
               "NAME: the name of the secret to update/release (can specify many);"
               "      the special keyword 'all' means all accessible secrets"
               ""
               "OPTIONS:"
               summary]]
    (string/join "\n"
      (if (nil? message) lines (concat [message ""] lines)))))

(defn write-secret! [options]
  (let [{:keys [keys-path members secrets-path secret-name text release-only
                force-rewrite]} options
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

        (if (or (not release-only) (-> path fs/file? not) force-rewrite)
          (let [public-key (ssh/read-public-key! key-path)
                aes-key (aes/generate-key)
                aes-key-encrypted (ssh/encrypt aes-key public-key)
                ciphertext (aes/encrypt text aes-key)]
            (println (str "Storing ciphertext into " path))
            (spit path (yaml/generate-string {:key aes-key-encrypted
                                              :ciphertext ciphertext}))))))

    (doseq [non-member non-members]
      (let [path (str secrets-path "/" non-member "/" secret-name)]
        (cond
          (fs/directory? path) (utils/print-exit! (str path " can't be a directory"))
          (fs/file? path) (do
                            (println (str "Deleting ciphertext from " path))
                            (fs/delete path)))))))

(defn read-input! [input-so-far]
  (if-let [input (read-line)]
    (if (= input "$$$") input-so-far
      (recur (str input-so-far input "\n")))
    input-so-far))

(defn update-secret! [secret-name safe-options options]
  (let [{:keys [keys-path key-name private-key-path secrets secrets-path]} safe-options
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
                         (println (str "Enter text for secret " secret-name ":"))
                         (println "Enter $$$ on a separate line to indicate EOF.")
                         (read-input! "")))]
            (when (= 0 (count text)) (utils/print-exit! 1 "Must provide secret text."))
            (write-secret! {:keys-path keys-path
                            :members members
                            :secrets-path secrets-path
                            :secret-name secret-name
                            :text text
                            :release-only (options :release-only)
                            :force-rewrite (options :force-rewrite)}))))))

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

    (let [{:keys [key-name secrets]} safe-options]
      (if (some #{"all"} arguments)
        (doseq [secret-keyword (keys secrets)]
          (if (some #{key-name} (secrets secret-keyword))
            (update-secret! (name secret-keyword) safe-options options)))
        (doseq [secret-name arguments]
          (update-secret! secret-name safe-options options))))))
