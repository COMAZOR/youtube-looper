(ns youtube-looper.next.kv-stores
  (:require [wilkerdev.local-storage :as ls]))

; Key Value Stores

(defprotocol KVSyncStore
  (kv-get [this key])
  (kv-set! [this key value]))

(defn kv-update! [store key f]
  (kv-set! store key (f (kv-get store key))))

(defrecord MapKVStore [data]
  KVSyncStore
  (kv-get [_ key] (get @data key))
  (kv-set! [_ key value] (swap! data assoc key value)))

(defn map-kv-store
  ([] (map-kv-store {}))
  ([data] (MapKVStore. (atom data))))

(defrecord LocalStorageKVStore [prefix]
  KVSyncStore
  (kv-get [_ key] (ls/get (str prefix (name key))))
  (kv-set! [_ key value] (ls/set! (str prefix (name key)) value)))

(defn local-storage-kv-store
  ([] (LocalStorageKVStore. ""))
  ([prefix] (LocalStorageKVStore. prefix)))
