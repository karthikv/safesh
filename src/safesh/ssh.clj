(ns safesh.ssh
  (:require [safesh.utils :as utils]
            [clojure.string :as string])
  (:import javax.crypto.Cipher
           java.security.Security
           java.security.KeyFactory
           java.security.PublicKey
           java.security.spec.DSAPublicKeySpec
           java.security.spec.RSAPublicKeySpec
           java.math.BigInteger
           java.util.Scanner
           org.bouncycastle.jce.provider.BouncyCastleProvider
           org.bouncycastle.openssl.PasswordFinder
           org.bouncycastle.openssl.EncryptionException)
  (:gen-class))

(Security/addProvider (BouncyCastleProvider.))

(defn encrypt [text public-key]
  (let [cipher (doto (Cipher/getInstance "RSA/ECB/PKCS1Padding" "BC")
                 (.init Cipher/ENCRYPT_MODE public-key))]
    (utils/base64 (.doFinal cipher (utils/get-bytes text)))))

(defn decrypt [text private-key]
  (let [cipher (doto (Cipher/getInstance "RSA/ECB/PKCS1Padding" "BC")
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

(deftype PasswordFn [password-fn]
  PasswordFinder
  (getPassword [_]
    (.toCharArray (password-fn))))

(defn ask-password! []
  (let [should-run (atom true)
        erase-thread (future
                       (print "SSH key password: ")
                       (flush)
                       (loop []
                         (Thread/sleep 10)
                         (when @should-run
                           (print "\010 ")
                           (flush)
                           (recur))))]
    (let [password (read-line)]
      (swap! should-run not)
      (if (nil? password)
        (utils/print-exit! 1 "No password provided.")
        password))))

(defn read-private-key! [path]
  (if-let [private-key (try (-> path
                                java.io.FileReader.
                                (org.bouncycastle.openssl.PEMReader.
                                  (PasswordFn. ask-password!))
                                .readObject)
                            (catch EncryptionException e nil))]
    (.getPrivate private-key)
    (recur path)))
