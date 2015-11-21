(ns youtube-looper.next.parser
  (:require [om.next :as om]
            [wilkerdev.local-storage :as ls]))

; Client Parser

(defmulti read om/dispatch)

(defmethod read :default [{:keys [state]} key _]
  (let [st @state]
    (if-let [[_ v] (find st key)]
      {:value v}
      {:value :not-found})))

(defmethod read :app/current-track [{:keys [state]} k _]
  (let [st @state]
    (if (contains? st k)
      {:value (->> (get st k)
                   (get-in st))}
      {:remote true})))

(defmulti mutate om/dispatch)

(defn ref-value? [value]
  (and (vector? value)
       (= 2 (count value))
       (keyword? (first value))))

(defn reduce-path [state path]
  (loop [path path
         out-path []]
    (let [[h & t] path]
      (if h
        (let [out-path (conj out-path h)
              value (get-in state out-path)]
          (if (ref-value? value)
            (recur t value)
            (recur t out-path)))
        out-path))))

(defmethod mutate 'track/new-loop [{:keys [state ast]} _ {:keys [youtube/id loop]}]
  {:action #(swap! state update-in [:tracks/by-youtube-id id :track/loops] conj loop)
   :remote ast})

(defmethod mutate 'track/remove-loop [{:keys [state ast]} _ {:keys [youtube/id loop]}]
  {:action #(swap! state update-in [:tracks/by-youtube-id id :track/loops] disj loop)
   :remote ast})

(def parser (om/parser {:read read :mutate mutate}))

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
  (kv-get [_ key](ls/get (str prefix (name key))))
  (kv-set! [_ key value]
    (do
      (ls/set! (str prefix (name key)) value))))

(defn local-storage-kv-store
  ([] (LocalStorageKVStore. ""))
  ([prefix] (LocalStorageKVStore. prefix)))

; Remote Key Value Parser

(defmulti remote-read om/dispatch)

(defmethod remote-read :app/current-track [{:keys [store current-track]} _ _]
  {:value (or (kv-get store (current-track))
              {:youtube/id (current-track)
               :track/loops #{}})})

(defmulti remote-mutate om/dispatch)

(defmethod remote-mutate 'track/new-loop [{:keys [store]} _ {:keys [youtube/id loop]}]
  {:action #(kv-update! store id (fn [track] (-> (or track {})
                                                 (assoc :youtube/id id)
                                                 (update :track/loops (fn [x] (conj (or x #{}) loop))))))})

(defmethod remote-mutate 'track/remove-loop [{:keys [store]} _ {:keys [youtube/id loop]}]
  {:action #(kv-update! store id (fn [track] (update-in track [:track/loops] disj loop)))})

(def remote-parser (om/parser {:read remote-read :mutate remote-mutate}))
