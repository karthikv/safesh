(ns safesh.store
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
  (let [lines ["safesh store stores a new secret and releases it to those with permissions."
               "Usage: safesh store OPTIONS NAME"
               ""
               "NAME: the name of the secret to store"
               ""
               "OPTIONS:"
               summary]]
    (string/join "\n"
      (if (nil? message) lines (concat [message ""] lines)))))

(defn store-secret! [options]
  (let [{:keys [keys-path members secrets-path secret-name text]} options]
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
                                            :ciphertext ciphertext})))))))

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

    (let [{:keys [keys-path key-name secrets secrets-path]} safe-options
          secret-name (first arguments)
          members (-> secret-name keyword secrets)]
      (cond
        (nil? members)
          (utils/print-exit! 1 (str "Must have " secret-name " in permissions file."))
        (not (some #{key-name} members))
          (utils/print-exit! 1 (str "Must give " key-name " permissions to " secret-name "."))
        :else
          (do
            (println "Enter your secret text:")
            (let [text (read-input! "")]
              (when (= 0 (count text)) (utils/print-exit! 1 "Must provide secret text."))
              (store-secret! {:keys-path keys-path
                              :members members
                              :secrets-path secrets-path
                              :secret-name secret-name
                              :text text})))))))
