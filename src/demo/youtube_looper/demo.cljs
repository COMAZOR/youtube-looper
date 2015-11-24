(ns youtube-looper.demo
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [wilkerdev.util.macros :refer [dochan go-sub go-sub* all-or-nothing]])
  (:require [cljs.core.async :refer [chan put! <! >! close!] :as async]
            [om.next :as om :refer-macros [defui]]
            [wilkerdev.util.dom :as wd]
            [wilkerdev.util.reactive :as r]
            [youtube-looper.next.parser :as p]
            [youtube-looper.next.ui :as ui]
            [youtube-looper.youtube :as yt]
            [youtube-looper.views :as v]
            [om.dom :as dom]
            [wilkerdev.util.dom :as wd]))

(def app-state (atom {:count 0}))

(defn read [{:keys [state] :as env} key params]
  (let [st @state]
    (if-let [[_ value] (find st key)]
      {:value value}
      {:value :not-found})))

(defn mutate [{:keys [state] :as env} key params]
  (if (= 'increment key)
    {:value {:keys [:count]}
     :action #(swap! state update-in [:count] inc)}
    {:value :not-found}))

(defui Counter
  static om/IQuery
  (query [this]
         [:count])
  Object
  (render [this]
          (let [{:keys [count]} (om/props this)]
            (dom/div nil
              (dom/span nil (str "Count: " count))
              (dom/button
                #js {:onClick
                     (fn [e] (om/transact! this '[(increment)]))}
                "Click me!")))))

(def b-reconciler
  (om/reconciler
    {:state app-state
     :parser (om/parser {:read read :mutate mutate})}))

(enable-console-print!)

(def ^:dynamic *log-debug* false)

(defn log [& args]
  (if *log-debug*
    (apply (.. js/console -log) args)))

(defn constantly-chan [value] (chan 1 (map (constantly value))))

(defn loop-back [video {:keys [loop/start loop/finish]}]
  (let [position (wd/video-current-time video)]
    (when (> position finish)
      (wd/video-seek! video start))))

(defn debug-input [label [msg :as i]]
  (let [ignored-messages #{:time-update}]
    (if-not (contains? ignored-messages msg)
      (log label (pr-str i))))
  i)

(defn setup-video-time-update [bus]
  (async/pipe (r/listen (yt/get-video) "timeupdate" (constantly-chan [:time-update])) bus))

(defn wait-for-presence
  ([f] (wait-for-presence f 10))
  ([f delay]
   (go
     (while (not (f)) (<! (async/timeout delay)))
     (f))))

(defonce store (p/map-kv-store {"demo-id" {:youtube/id  "demo-id"
                                           :db/id       (random-uuid)
                                           :track/loops [{:db/id (random-uuid) :loop/label "full" :loop/start 5 :loop/finish 50}
                                                         {:db/id (random-uuid) :loop/label "intro" :loop/start 60 :loop/finish 70}]}}))

(def demo-video-id "demo-id") 

(defn init []
  (let [bus (chan 1024 (map (partial debug-input "flux message")))
        pub (async/pub bus first)
        reconciler (om/reconciler
                     {:state  {:youtube/current-video demo-video-id}
                      :parser p/parser
                      :send   (fn [{:keys [remote]} cb]
                                (cb (p/remote-parser {:store store}
                                                     remote)))})]

    
    
    ; watch for video page changes
    (async/put! bus [:video-load])

    (go-sub* pub :video-load _ (chan 1 (take 1))
      (om/add-root! reconciler ui/LoopPage (wd/$ ".app-container"))
      #_ (setup-video-time-update bus))

    (go-sub pub :video-load [_ video-id]
      (println "set current video" video-id))

    (go-sub pub :select-loop [_ loop]
      (if loop (wd/video-seek! (yt/get-video) (:loop/start loop))))

    (go-sub pub :set-playback-rate [_ rate]
      (wd/video-playback-rate! (yt/get-video) rate))))

(init)
