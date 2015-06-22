(ns safesh.safe
  (:require [me.raynes.fs :as fs]
            [clj-yaml.core :as yaml]
            [safesh.ls :as ls]
            [safesh.cat :as cat]
            [safesh.update :as update]
            [safesh.utils :as utils])
  (:gen-class))

(def DEFAULT-PRIVATE-KEY-PATH "~/.ssh/id_rsa")

(defn list-keys [keys-dir]
  (->> keys-dir .list vec))

(defn ask-key-name! [keys-dir]
  (let [keys-list (list-keys keys-dir)
        num-keys (count keys-list)]
    (loop []
      (println "Which key belongs to you?")
      (doseq [s (map-indexed #(str "  [" %1 "] " %2) keys-list)] (println s))
      (print ">> ")
      (flush)
      (let [index-raw (read-line)]
        (if (nil? index-raw) nil
          (if-let [index (-> index-raw Integer/parseInt
                             (try (catch NumberFormatException e nil)))]
            (if (and (>= index 0) (< index (count keys-list))) (nth keys-list index) (recur))
            (recur)))))))

(defn ask-private-key-path! []
  (loop []
    (print (str "Where is your private key stored (default: "
                  DEFAULT-PRIVATE-KEY-PATH ")? "))
    (flush)
    (let [path-raw (read-line)]
      (if (nil? path-raw) nil
        (let [path (->
                     (if (= 0 (count path-raw)) DEFAULT-PRIVATE-KEY-PATH path-raw)
                     fs/expand-home
                     str)]
          (if (fs/file? path) (-> path fs/absolute str) (recur)))))))

(defn create-config! [keys-dir config-path]
  (println "Config file not found; creating one...")
  (if-let [key-name (ask-key-name! keys-dir)]
    (if-let [private-key-path (ask-private-key-path!)]
      (->>
        (yaml/generate-string {:key-name key-name :private-key-path private-key-path})
        (spit config-path))
      (utils/print-exit! 1 "\nMust provide a private key path."))
    (utils/print-exit! 1 "\nMust provide a key name.")))

(defn extract-paths [options]
  {:config-path (-> options :config-path fs/expand-home str)
   :permissions-path (-> options :permissions-path fs/expand-home str)
   :keys-path (-> options :keys-path fs/expand-home str)
   :secrets-path (-> options :secrets-path fs/expand-home str)})

(defn set-up! [options]
  (let [{:keys [config-path permissions-path keys-path secrets-path]} (extract-paths options)]
    (cond
      (not (fs/file? permissions-path)) (utils/print-exit! 1 "Must have a permissions file.")
      (not (fs/directory? keys-path)) (utils/print-exit! 1 "Must have a keys directory.")
      (not (fs/directory? secrets-path)) (utils/print-exit! 1 "Must have a secrets directory.")
      (fs/directory? config-path) (utils/print-exit! 1 "Config file can't be a directory."))

    (when (-> config-path fs/file? not) (create-config! (fs/file keys-path) config-path))))

(defn validate-group [keys-path group-name group-key-names]
  (let [invalid-key-name (->> group-key-names
                              (filter #(->> % (str keys-path "/") fs/file? not))
                              first)]
    (if (nil? invalid-key-name) nil
      (str "Group " group-name " has invalid key " invalid-key-name "."))))

(defn validate-secret [keys-path groups secret-name secret-members]
  (let [invalid-member (->> secret-members
                            (filter #(and (->> % (str keys-path "/") fs/file? not)
                                          (->> % keyword (contains? groups) not)))
                            first)]
    (if (nil? invalid-member) nil
      (str "Secret " secret-name " has invalid member " invalid-member "."))))

(defn members-to-keys [groups secrets]
  (into {}
    (for [[secret-name members] secrets]
      [secret-name, (->> members
                         (map #(or (groups (keyword %)) %))
                         flatten)])))

(defn validate-options [options]
  (let [{:keys [permissions-path keys-path config-path secrets-path]} (extract-paths options)
        permissions (yaml/parse-string (slurp permissions-path))
        groups (:groups permissions)
        secrets (:secrets permissions)
        config (yaml/parse-string (slurp config-path))]
    (cond
      (-> config :key-name nil?) [true "Config file missing key-name."]
      (-> config :private-key-path nil?) [true "Config file missing private-key-path."]
      (->> config :key-name (str keys-path "/") fs/file? not)
        [true (str "Config file key-name is not a valid key in the " keys-path " directory.")]
      (->> config :private-key-path fs/file? not)
        [true "Config file private-key-path is not a valid file."]
      (and (-> groups nil? not)
           (-> groups type (= clojure.lang.PersistentArrayMap) not))
        [true "Groups must be a map from name -> array of keys."]
      (-> secrets type (= clojure.lang.PersistentArrayMap) not)
        [true "Secrets must be a map from name -> array of keys/groups."]
      :else
        (let [group-error (->> groups
                               (map #(validate-group keys-path (first %) (second %)))
                               (filter #(-> % nil? not)) first)
              secret-error (->> secrets
                                (map #(validate-secret keys-path groups (first %) (second %)))
                                (filter #(-> % nil? not)) first)]
          (cond
            (-> group-error nil? not) [true group-error]
            (-> secret-error nil? not) [true secret-error]
            :else [false {:keys-path keys-path
                          :secrets-path secrets-path
                          :secrets (members-to-keys groups secrets)
                          :key-name (:key-name config)
                          :private-key-path (:private-key-path config)}])))))

(defn run-command! [args options command]
  (let [[error message-env] (validate-options options)]
    (if error (utils/print-exit! 1 message-env) (command message-env args))))

(defn ls! [args options] (run-command! args options ls/execute!))
(defn cat! [args options] (run-command! args options cat/execute!))
(defn update! [args options] (run-command! args options update/execute!))
