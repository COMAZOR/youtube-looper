(ns youtube-looper.data
  (:require-macros [wilkerdev.util.macros :refer [all-or-nothing]])
  (:require [datascript :as d]
            [clojure.set :refer [rename-keys]]
            [youtube-looper.browser :refer [t]]
            [youtube-looper.util :refer [parse-time]]
            [wilkerdev.util.datascript :as dh]
            [wilkerdev.local-storage :as store]))

; local database

(def SETTINGS_ID 1)
(def NEW_LOOP_ID 2)

(def listen! d/listen!)

(def schema
  {:loop/start    {}
   :loop/finish   {}
   :loop/label    {}
   :loop/user     {:db/valueType :db.type/ref}
   :loop/video    {}

   ; settings
   :current-video {}
   :show-dialog?  {}
   :ready?        {}
   :current-loop  {:db/valueType :db.type/ref}})

(def initial-settings {:show-dialog? false :ready? false})

(defn create-conn
  ([] (create-conn initial-settings))
  ([settings]
   (doto (d/create-conn schema)
     (d/transact! [(assoc settings :db/id SETTINGS_ID)
                   {:db/id NEW_LOOP_ID :loop/start "" :loop/finish ""}]))))

(defn entity [db eid] (d/entity db eid))

(defn settings
  ([db] (entity db SETTINGS_ID))
  ([db & ks] (get-in (settings db) ks)))

(defn new-loop [db] (into {} (entity db NEW_LOOP_ID)))

(defn current-loop [db] (get (settings db) :current-loop))

(defn loops-for-current-video [db]
  (dh/qes '[:find ?l
            :where
              [1 :current-video ?v]
              [?l :loop/video ?v]]
          db))

(defn update-settings! [conn settings]
  (d/transact! conn [(assoc settings :db/id 1)]))

(defn select-loop! [conn {:keys [db/id]}]
  (if (nil? id)
    (d/transact! conn [[:db.fn/retractAttribute SETTINGS_ID :current-loop]])
    (update-settings! conn {:current-loop id})))

(defn set-dialog-visibility! [conn state]
  (update-settings! conn {:show-dialog? state}))

(defn set-current-video! [conn video-id]
  (update-settings! conn {:current-video video-id}))

(defn load-loops! [conn loops] (d/transact! conn loops))

(defn normalize-new-loop [{:keys [loop/start loop/finish]}]
  (when-let [[start finish] (all-or-nothing (parse-time start) (parse-time finish))]
    (if (< start finish)
      {:loop/start start :loop/finish finish :loop/label (t "unnamed_section")})))

(defn create-from-new-loop! [conn]
  (when-let [loop (-> (new-loop @conn) normalize-new-loop)]
    (d/transact! conn [{:db/id NEW_LOOP_ID :loop/start "" :loop/finish ""}
                       (assoc loop :loop/video (:current-video (settings @conn))
                                   :db/id -1)
                       {:db/id SETTINGS_ID :current-loop -1}])))

(defn rename-loop! [conn {:keys [db/id]} new-label]
  (d/transact! conn [{:loop/label new-label
                      :db/id id}]))

(defn remove-loop! [conn {:keys [db/id]}]
  (d/transact! conn [[:db.fn/retractEntity id]]))

(defn update-new-loop! [conn data]
  (d/transact! conn [(assoc data :db/id NEW_LOOP_ID)]))

; data storage

(defn store-key [video-id] (str "video-loops-" video-id))

(defn loops-for-video-on-storage [video-id]
  (->> (store/get (store-key video-id) [])
       (map #(assoc % :loop/video video-id))
       (map #(rename-keys % {:start  :loop/start
                             :finish :loop/finish
                             :name   :loop/label}))))

(defn loop-ent-to-storage [loop]
  (select-keys (into {} loop) [:loop/start :loop/finish :loop/label]))

(defn sync-loops-for-video! [video-id loops]
  (let [key (store-key video-id)]
    (if (empty? loops)
      (store/remove! key)
      (store/set! key loops))))

(defn export-data []
  (into {}
        (for [key (->> (js-keys store/local-storage)
                       (filter (partial re-find #"^video-loops-")))]
          [key (store/get key)])))
