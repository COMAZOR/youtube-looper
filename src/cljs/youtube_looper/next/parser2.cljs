(ns youtube-looper.next.parser2
  (:require [cljs.core.async :as async]
            [om-tutorial.parsing :as p]
            [om.next :as om]
            [wilkerdev.local-storage :as ls]))

(defn blank-track [id]
  {:db/id (random-uuid)
   :youtube/id id
   :track/new-loop {:db/id (random-uuid)}
   :track/loops []})

; Local Read

#_ (defn read-local
     [{:keys [ast query state] :as env} key _]
     (let [st @state]
       (case key
         :track/new-loop2 {:value (om/db->tree query (get st :track/new-loop) st)}

         {:value (if query
                   (om/db->tree query (get st key) st)
                   (get st key))})))

(defn read-local
  [{:keys [ast query state] :as env} key _]
  (let [st @state]
    (case key
      :db/id (if (om/ident? (:key ast))
               {:value (p/parse-join-with-reader read-local (assoc env :db-path []) (:key ast))}
               (p/db-value env key))
      :app/current-track {:value (p/parse-join-with-reader read-local env key)}
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
             (async/put! bus [:seek-to start])
             (swap! state assoc-in (conj ref :track/selected-loop) (if id [:db/id id])))})

(defmethod mutate 'track/new-loop
  [{:keys [state ref]} _ {:keys [db/id] :as loop}]
  {:action (fn [] (swap! state #(-> (update-in % (conj ref :track/loops) conj [:db/id id])
                                    (assoc-in [:db/id id] loop))))})

(defmethod mutate 'track/remove-loop
  [{:keys [state ref]} _ {:keys [db/id] :as loop}]
  {:action (fn [] (swap! state #(-> (update-in % (conj ref :track/loops) (remove-ref loop))
                                    (update :db/id dissoc id))))})

(defmethod mutate 'app/update-current-time
  [{:keys [state]} _ {:keys [value]}]
  {:action (fn [] (swap! state assoc :video/current-time value))})

; Remote Read

(defn read-remote
  [env key params]
  (case key
    :widget (p/recurse-remote env key true)
    :people (p/fetch-if-missing env key :make-root)
    :not-remote))

; Building blocks

(def parser
  (om/parser {:read   (p/new-read-entry-point read-local {:remote read-remote})
              :mutate mutate}))

(defn send [{:keys [remote]} cb]
  #_(let [{:keys [query rewrite]} (om/process-roots remote) ;; FIXME: BUG: process-roots should NOT return empty!
          server-response (simulated-server query)]
      (js/setTimeout (fn []
                       (println "SERVER response is: " server-response)
                       (cb (rewrite server-response))
                       ) 100)))

(defn reconciler [config]
  (om/reconciler (merge config
                        {:parser  parser
                         :id-key  :db/id
                         :pathopt false})))
