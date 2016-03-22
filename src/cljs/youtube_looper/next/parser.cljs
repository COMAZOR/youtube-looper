(ns youtube-looper.next.parser
  (:require [cljs.core.async :as async]
            [youtube-looper.next.stores :as kv]
            [clojure.set :refer [rename-keys]]
            [om-tutorial.parsing :as p]
            [om.next :as om]
            [youtube-looper.next.ui :as ui]
            [wilkerdev.local-storage :as ls]))

(defn blank-track [id]
  {:db/id          (random-uuid)
   :youtube/id     id
   :track/new-loop {:db/id (random-uuid)}
   :track/loops    []})

; Local Read

(defn get-root [env key default]
  (p/dbget (assoc env :db-path []) key default))

(defn read-local
  [{:keys [ast query state] :as env} key _]
  (let [st @state
        join #(-> {:value (p/parse-join-with-reader read-local env key)})]
    (case key
      :db/id (if (om/ident? (:key ast))
               {:value (p/parse-join-with-reader read-local (assoc env :db-path []) (:key ast))}
               (p/db-value env key))
      :app/current-track (join)
      :track/duration {:value ((get-in env [:shared :current-duration]))}
      :track/loops (join)
      :track/new-loop (join)
      :video/current-time {:value (get-root env key 0)}
      :app/precision-mode? {:value (get-root env key false)}
      :app/playback-rate {:value (get-root env key 1)}

      (p/db-value env key))))

; Local Mutations

(defn remove-ref [entity]
  #(filterv (fn [e] (not= (second e) (:db/id entity))) %))

(defmulti mutate om/dispatch)

(defmethod mutate 'entity/set
  [{:keys [state ref]} _ props]
  {:action (fn [] (if ref (swap! state update-in ref merge props)
                          (swap! state merge props)))})

(defmethod mutate 'app/set
  [{:keys [state]} _ props]
  {:action (fn [] (swap! state merge props))
   :value  {:keys (keys props)}})

(defn call-map-fn [m k & args]
  (when-let [f (get m k)]
    (assert (fn? f))
    (apply f args)))

(defmethod mutate 'app/set-playback-rate
  [{:keys [state shared]} _ {:keys [value]}]
  {:action (fn [] (do
                    (swap! state assoc :app/playback-rate value)
                    (call-map-fn shared :set-playback-rate value)))
   :value  {:keys [:app/playback-rate]}})

(defmethod mutate 'loop/set-current-video-time
  [{:keys [state ref] {:keys [current-position]} :shared} _ {:keys [at]}]
  {:action (fn [] (swap! state assoc-in (conj ref at) (current-position)))})

(defmethod mutate 'track/select-loop
  [{:keys [state ref] {:keys [bus]} :shared} _ {:keys [loop/start db/id]}]
  {:action (fn []
             (if id (async/put! bus [:seek-to start]))
             (swap! state assoc-in (conj ref :track/selected-loop) (if id [:db/id id])))})

(defn add-loop [st ref {:keys [db/id] :as loop}]
  (-> (update-in st (conj ref :track/loops) conj [:db/id id])
      (assoc-in [:db/id id] loop)))

(defn read-track [st]
  (om/db->tree [:db/id
                :youtube/id
                {:track/new-loop [:db/id]}
                {:track/loops [:db/id :loop/label :loop/start :loop/finish]}]
               (:app/current-track st) st))

(defn remote-save-track [state]
  (-> (om/query->ast `[(track/save ~(read-track @state))])
      (get-in [:children 0])))

(defmethod mutate 'track/new-loop
  [{:keys [ast state ref]} _ loop]
  {:action (fn [] (swap! state #(add-loop % ref loop)))
   :remote (remote-save-track state)})

(defmethod mutate 'track/duplicate-loop
  [{:keys [ast state ref]} _ loop]
  {:action (fn [] (swap! state #(add-loop % ref (assoc loop :db/id (random-uuid)))))
   :remote (remote-save-track state)})

(defmethod mutate 'track/remove-loop
  [{:keys [state ref ast]} _ {:keys [db/id] :as loop}]
  {:action (fn [] (swap! state #(-> (update-in % (conj ref :track/loops) (remove-ref loop))
                                    (update :db/id dissoc id))))
   :remote (remote-save-track state)})

(defmethod mutate 'track/update-loop
  [{:keys [state ref ast]} _ props]
  {:action (fn [] (if ref (swap! state update-in ref merge props)))
   :remote (remote-save-track state)})

(defmethod mutate 'app/update-current-time
  [{:keys [state ast]} _ {:keys [value]}]
  {:action (fn [] (swap! state assoc :video/current-time value))})

(defmethod mutate 'track/save
  [_ _ _]
  {:remote true})

(defmethod mutate 'app/change-video [{:keys [state]} _ {:keys [youtube/id]}]
  {:action (fn [] (swap! state #(cond-> %
                                 (not= id (:youtube/current-video %)) (-> (dissoc % :app/current-track)
                                                                          (assoc :youtube/current-video id)))))
   :value  {:keys [:app/current-track :youtube/current-video]}})

; Remote Read

(defn read-remote
  [{:keys [state] :as env} key params]
  (case key
    :app/current-track (some-> (p/fetch-if-missing env key true)
                               (assoc-in [:remote :params] {:youtube/id (get @state :youtube/current-video)}))
    :not-remote))

; Building blocks

(def parser
  (om/parser {:read   (p/new-read-entry-point read-local {:remote read-remote})
              :mutate mutate}))

(defn reconciler [config]
  (om/reconciler (merge config
                        {:parser  parser
                         :id-key  :db/id
                         :pathopt false})))

; Server

(defmulti remote-read om/dispatch)

(defmethod remote-read :app/current-track [{:keys [store]} _ {:keys [youtube/id]}]
  {:value (if id (or (kv/kv-get store id)
                     (blank-track id)))})

(defmulti remote-mutate om/dispatch)

(defmethod remote-mutate 'track/save [{:keys [store]} _ {:keys [youtube/id] :as track}]
  {:action #(kv/kv-set! store id track)})

(def remote-parser (om/parser {:read remote-read :mutate remote-mutate}))

(defn send [store {:keys [remote]} cb]
  (let [{:keys [query rewrite]} (om/process-roots remote)
        server-response (remote-parser {:store store} query)
        tree-db #(om/tree->db ui/LoopPage % true)]
    (cb (->> server-response
             (rewrite)
             (tree-db)))))

; Migration Store

(defrecord MigrationStore [main-store secondary-store]
  kv/KVSyncStore
  (kv-get [_ key]
    (or (kv/kv-get main-store key)
        (if-let [loops (kv/kv-get secondary-store key)]
          (-> (blank-track key)
              (assoc :track/loops (mapv #(-> (rename-keys % {:name   :loop/label
                                                             :start  :loop/start
                                                             :finish :loop/finish})
                                             (assoc :db/id (random-uuid))) loops))))))

  (kv-set! [_ key value] (kv/kv-set! main-store key value)))

(defn migration-store [main-store secondary-store] (MigrationStore. main-store secondary-store))
