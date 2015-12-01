(ns youtube-looper.core
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [wilkerdev.util.macros :refer [dochan go-sub go-sub* all-or-nothing]])
  (:require [cljs.core.async :refer [chan put! <! >! close!] :as async]
            [om.next :as om :refer-macros [defui]]
            [wilkerdev.util.dom :as wd]
            [wilkerdev.util.reactive :as r]
            [youtube-looper.next.parser2 :as p]
            [youtube-looper.next.ui :as ui]
            [youtube-looper.youtube :as yt]
            [youtube-looper.track :as track]
            [youtube-looper.next.kv-stores :as kv]
            [wilkerdev.util.dom :as wd :refer [$]]))

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

(defn dialog-container []
  (or ($ ".ytp-looper-container")
      (doto (wd/create-element! "div")
        (wd/add-class! "ytp-looper-container")
        (wd/set-properties! {"data-layer" 9})
        (wd/set-style! {:z-index 20
                         :position "absolute"
                         :bottom "130px" :right "12px"})
        (wd/append-to! ($ "#movie_player")))))

(def store (kv/local-storage-kv-store "youtube-looper-"))

(defn read-selected-loop [state]
  (let [{:keys [app/current-track]} (p/parser {:state state} [{:app/current-track [{:track/selected-loop [:loop/start :loop/finish]}]}])]
    (get current-track :track/selected-loop)))

(defn norm-nan [v] (if (js/isNaN v) nil v))

(defn youtube-video-position []
  (some-> (yt/get-video) (wd/video-current-time) (norm-nan)))

(defn youtube-video-duration []
  (some-> (yt/get-video) (wd/video-duration) (norm-nan)))

(defn inject-font-awesome-css []
  (doto (wd/create-element! "link")
    (wd/set-properties! {:rel "stylesheet"
                         :href "//maxcdn.bootstrapcdn.com/font-awesome/4.5.0/css/font-awesome.min.css"})
    (wd/append-to! (wd/$ "head"))))

(defn ^:export init []
  (track/initialize "55eGVmS7Ty7Sa4cLxwpKL235My8elBtQBOk4wx1R" "ghUQSvjYReHqwNhdOROgzI3xm0aybyarCXW30usM")
  (println "initialized")
  (let [bus (chan (async/sliding-buffer 512) (map (partial debug-input "flux message")))
        pub (async/pub bus first)
        youtube-id (yt/current-video-id)
        state (atom {:youtube/current-video youtube-id
                     :app/visible?          true})
        reconciler (p/reconciler
                     {:state     state
                      :normalize false
                      :shared    {:current-position youtube-video-position
                                  :current-duration youtube-video-duration
                                  :bus              bus}
                      :send      (partial p/send store)
                      :logger    nil})]

    (set! (.-recon js/window) reconciler)
    
    ; watch for video page changes
    (async/pipe (yt/watch-video-load
                  (chan 1024 (comp (filter #(not= % :yt/no-video))
                                   (map #(vector :video-load %)))))
                bus)

    (go-sub* pub :video-load _ (chan 1 (take 1))
      (inject-font-awesome-css)
      ; wait for video duration to be available
      (<! (wait-for-presence youtube-video-duration))
      
      (track/track-extension-loaded)

      (setup-video-time-update bus)
      (om/add-root! reconciler ui/LoopPage (dialog-container)))

    (go-sub pub :video-load [_ video-id]
      (<! (wait-for-presence youtube-video-duration))
      (om/transact! reconciler `[(app/change-video {:youtube/id ~video-id}) :app/current-track]))

    (go-sub pub :seek-to [_ time]
      (wd/video-seek! (yt/get-video) time))

    (go-sub pub :time-update _
      (let [video (yt/get-video)]
        (om/transact! reconciler `[(app/update-current-time {:value ~(wd/video-current-time video)}) :video/current-time])
        (when-let [loop (read-selected-loop (get-in reconciler [:config :state]))]
          (if-not (empty? loop) (loop-back video loop)))))

    (go-sub pub :set-playback-rate [_ rate]
      (track/track-playback-rate-changed rate)
      (wd/video-playback-rate! (yt/get-video) rate))

    reconciler))
