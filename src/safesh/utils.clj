(ns safesh.utils
  (:gen-class))

(import (org.apache.commons.codec.binary Base64))

(defn get-bytes [s]
  (.getBytes s "UTF-8"))

(defn base64 [b]
  (Base64/encodeBase64String b))

(defn debase64 [s]
  (Base64/decodeBase64 (get-bytes s)))

(defn print-exit! [status message]
  (println message)
  (System/exit status))
