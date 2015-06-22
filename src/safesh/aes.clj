(ns safesh.aes
  (:require [safesh.utils :as utils])
  (:gen-class))

(import (javax.crypto Cipher KeyGenerator SecretKey)
        (javax.crypto.spec SecretKeySpec)
        (java.security SecureRandom))

(defn generate-key []
  (let [keygen (KeyGenerator/getInstance "AES")]
    (.init keygen 128)  ; 128 max key size for AES
    (utils/base64 (.. keygen generateKey getEncoded))))

(defn get-cipher [mode aes-key]
  (let [key-spec (SecretKeySpec. (utils/debase64 aes-key) "AES")
        cipher (Cipher/getInstance "AES")]
    (.init cipher mode key-spec)
    cipher))

(defn encrypt [text aes-key]
  (let [text-bytes (utils/get-bytes text)
        cipher (get-cipher Cipher/ENCRYPT_MODE aes-key)]
    (utils/base64 (.doFinal cipher text-bytes))))

(defn decrypt [text aes-key]
  (let [cipher (get-cipher Cipher/DECRYPT_MODE aes-key)]
    (String. (.doFinal cipher (utils/debase64 text)))))
