(ns safesh.utils
  (:require [me.raynes.fs :as fs])
  (:gen-class))

(import (org.apache.commons.codec.binary Base64))

(defn get-bytes [s]
  (.getBytes s "UTF-8"))

(defn base64 [b]
  (Base64/encodeBase64String b))

(defn debase64 [s]
  (Base64/decodeBase64 (get-bytes s)))

(defn expand-path [path]
  (-> path fs/expand-home fs/file str))

(defn print-exit! [status message]
  (if (= status 0)
    (println message)
    (.println *err* message))
  (System/exit status))
