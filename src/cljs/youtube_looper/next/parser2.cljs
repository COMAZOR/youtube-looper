(ns youtube-looper.next.parser2
  (:require [cljs.core.async :as async]
            [youtube-looper.next.kv-stores :as kv]
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

(defn read-local
  [{:keys [ast query state] :as env} key _]
  (let [st @state]
    (case key
      :db/id (if (om/ident? (:key ast))
               {:value (p/parse-join-with-reader read-local (assoc env :db-path []) (:key ast))}
               (p/db-value env key))
      :app/current-track {:value (p/parse-join-with-reader read-local env key)}
      :track/duration {:value ((get-in env [:shared :current-duration]))}
      :track/loops {:value (p/parse-join-with-reader read-local env key)}
      :track/new-loop {:value (p/parse-join-with-reader read-local env key)}
      :track/new-loop2 {:value (p/parse-join-with-reader read-local env :track/new-loop)}
      :video/current-time {:value (p/dbget (assoc env :db-path []) key 0)}
      
      (p/db-value env key))))

; Local Mutations

(defn remove-ref [entity]
  #(filterv (fn [e] (not= (second e) (:db/id entity))) %))

(defmulti mutate om/dispatch)

(defmethod mutate 'entity/set
  [{:keys [state ref]} _ props]
  {:action (fn [] (if ref (swap! state update-in ref merge props)
                          (swap! state merge props)))})

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
                {:track/loops [:db/id :loop/label :loop/start :loop/finish]}]
               (:app/current-track st) st))

(defn remote-save-track [state]
  (-> (om/query->ast `[(track/save ~(read-track @state))])
      (get-in [:children 0])))

(defmethod mutate 'track/new-loop
  [{:keys [ast state ref]} _ loop]
  {:action (fn [] (swap! state #(add-loop % ref loop)))
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
  {:value (or (kv/kv-get store id)
              (let [track (blank-track id)]
                (kv/kv-set! store id track)
                track))})

(defmulti remote-mutate om/dispatch)

(defmethod remote-mutate 'track/save [{:keys [store]} _ {:keys [youtube/id] :as track}]
  {:action #(kv/kv-set! store id track)})

(def remote-parser (om/parser {:read remote-read :mutate remote-mutate}))

(defn send [store {:keys [remote]} cb]
  (let [{:keys [query rewrite]} (om/process-roots remote)
        server-response (remote-parser {:store store} query)
        tree-db #(om/tree->db ui/LoopPage % true)]
    (js/setTimeout #(cb (->> server-response
                             (rewrite)
                             (tree-db)))
                   100)))
