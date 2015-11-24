(ns youtube-looper.next.parser
  (:require [om.next :as om]
            [wilkerdev.local-storage :as ls]))

(defn mk-ref [{:keys [db/id]}] [:entities/by-id id])

(defn read-track [st {:keys [track/selected-loop] :as track}]
  (cond-> (update track :track/loops #(map (partial get-in st) %))
      selected-loop (assoc :track/selected-loop (get-in st selected-loop))))

; Client Parser

(defmulti read om/dispatch)

(defmethod read :default [{:keys [state]} key _]
  (let [st @state]
    (if-let [[_ v] (find st key)]
      {:value v}
      {:value :not-found})))

(defmethod read :app/current-track [{:keys [state ast]} k _]
  (let [st @state
        video (get st :youtube/current-video)]
    (if (contains? st k)
      {:value (->> (get st k)
                   (get-in st)
                   (read-track st))}
      {:remote (assoc ast :params {:youtube/id video})})))

(defmulti mutate om/dispatch)

(defn ref-value? [value]
  (and (vector? value)
       (= 2 (count value))
       (keyword? (first value))))

(defn remove-entity [entity]
  #(filterv (fn [e] (not= (:db/id e) (:db/id entity))) %))

(defn remove-ref [entity]
  #(filterv (fn [e] (not= (second e) (:db/id entity))) %))

(defmethod mutate 'track/new-loop [{:keys [state ast]} _ {:keys [db/id loop]}]
  {:action (fn [] (swap! state #(-> (update-in % [:entities/by-id id :track/loops] conj (mk-ref loop))
                                    (assoc-in [:entities/by-id (:db/id loop)] loop))))
   :remote (assoc-in ast [:params :youtube/id] (get-in @state [:entities/by-id id :youtube/id]))})

(defmethod mutate 'track/update-loop [{:keys [state ast]} _ {:keys [db/id loop]}]
  {:action (fn [] (swap! state #(-> (update-in % [:entities/by-id (:db/id loop)] merge loop))))
   :remote (assoc-in ast [:params :youtube/id] (get-in @state [:entities/by-id id :youtube/id]))})

(defmethod mutate 'track/remove-loop [{:keys [state ast]} _ {:keys [db/id loop]}]
  {:action (fn [] (swap! state #(-> (update-in % [:entities/by-id id :track/loops] (remove-ref loop))
                                    (update :entities/by-id dissoc (:db/id loop)))))
   :remote (assoc-in ast [:params :youtube/id] (get-in @state [:entities/by-id id :youtube/id]))})

(defmethod mutate 'track/select-loop [{:keys [state ast]} _ {:keys [db/id loop]}]
  {:action (fn [] (swap! state #(-> (assoc-in % [:entities/by-id id :track/selected-loop] (mk-ref loop)))))})

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

(defn blank-track [id] {:youtube/id id :track/loops [] :db/id (random-uuid)})

(defmulti remote-read om/dispatch)

(defmethod remote-read :app/current-track [{:keys [store]} _ {:keys [youtube/id]}]
  {:value (or (kv-get store id)
              (let [track (blank-track id)]
                (kv-set! store id track)
                track))})

(defmulti remote-mutate om/dispatch)

(defmethod remote-mutate 'track/new-loop [{:keys [store]} _ {:keys [youtube/id loop] :as base}]
  {:action #(kv-update! store id (fn [track] (-> (or track (dissoc base :loop))
                                                 (update :track/loops conj loop))))})

(defn find-loop [{:keys [track/loops]} {:keys [db/id]}]
  (->> (keep-indexed #(if (= (:db/id %2) id) %) loops)
       first))

(defmethod remote-mutate 'track/update-loop [{:keys [store]} _ {:keys [youtube/id loop]}]
  {:action
   #(kv-update! store id
     (fn [track]
       (update-in track [:track/loops (find-loop track loop)] merge loop)))})

(defmethod remote-mutate 'track/remove-loop [{:keys [store]} _ {:keys [youtube/id loop]}]
  {:action #(kv-update! store id (fn [track] (update-in track [:track/loops] (remove-entity loop))))})

(def remote-parser (om/parser {:read remote-read :mutate remote-mutate}))
