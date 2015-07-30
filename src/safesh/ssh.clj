(ns safesh.ssh
  (:require [safesh.utils :as utils]
            [clojure.string :as string]
            [clojure.java.shell :as shell])
  (:import javax.crypto.Cipher
           java.security.Security
           java.security.KeyFactory
           java.security.PublicKey
           java.security.spec.DSAPublicKeySpec
           java.security.spec.RSAPublicKeySpec
           java.security.spec.PKCS8EncodedKeySpec
           java.math.BigInteger
           java.util.Scanner)
  (:gen-class))

(defn encrypt [text public-key]
  (let [cipher (doto (Cipher/getInstance "RSA/ECB/PKCS1Padding")
                 (.init Cipher/ENCRYPT_MODE public-key))]
    (utils/base64 (.doFinal cipher (utils/get-bytes text)))))

(defn decrypt [text private-key]
  (let [cipher (doto (Cipher/getInstance "RSA/ECB/PKCS1Padding")
                 (.init Cipher/DECRYPT_MODE private-key))]
    (String. (.doFinal cipher (utils/debase64 text)))))

(defn decode-string [bb]
  (let [len (.getInt bb)
        buf (byte-array len)]
    (.get bb buf)
    (String. buf)))

(defn decode-bigint [bb]
  (let [len (.getInt bb)
        buf (byte-array len)]
    (.get bb buf)
    (BigInteger. buf)))

(defn read-public-key! [path]
  (let [contents (slurp path)
        parts (clojure.string/split contents #" ")
        key-bytes (->> parts
                       (filter #(.startsWith % "AAAA"))
                       first
                       javax.xml.bind.DatatypeConverter/parseBase64Binary)
        bb (-> key-bytes
               alength
               java.nio.ByteBuffer/allocate
               (.put key-bytes)
               .flip)]
    (case (decode-string bb)
      "ssh-rsa" (.generatePublic (KeyFactory/getInstance "RSA")
                                 (let [[e m] (repeatedly 2 #(decode-bigint bb))]
                                   (RSAPublicKeySpec. m e)))
      "ssh-dss" (.generatePublic (KeyFactory/getInstance "DSA")
                                 (let [[p q g y] (repeatedly 4 #(decode-bigint bb))]
                                   (DSAPublicKeySpec. y p q g)))
      (throw (ex-info "Unknown key type"
                      {:reason ::unknown-key-type
                       :type type})))))

(defn ask-password! [path]
  (let [should-run (atom true)
        erase-thread (future
                       (.print *err* (str "Enter pass phrase for " path ": "))
                       (.flush *err*)
                       (loop []
                         (Thread/sleep 10)
                         (when @should-run
                           (.print *err* "\010 ")
                           (.flush *err*)
                           (recur))))]
    (let [password (read-line)]
      (swap! should-run not)
      (if (nil? password)
        (utils/print-exit! 1 "No password provided.")
        password))))

(defn read-private-key! [path]
  (let [password (ask-password! path)
        {:keys [exit out]} (shell/sh "openssl" "pkcs8" "-topk8" "-in" path "-nocrypt"
                                     "-passin" (str "pass:" password))]
    (if (= exit 0)
      (do
        (let [key-bytes (utils/debase64 (string/join "\n" (-> out
                                                              (string/split #"\n")
                                                              rest
                                                              drop-last)))]
          (-> (KeyFactory/getInstance "RSA")
              (.generatePrivate (PKCS8EncodedKeySpec. key-bytes)))))
      (recur path))))
