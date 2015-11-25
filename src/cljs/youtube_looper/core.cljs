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
            [youtube-looper.views :as v]
            [youtube-looper.track :as track]
            [wilkerdev.util.dom :as wd]))

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

#_ (def store (p/map-kv-store {}))

(defn read-selected-loop [state]
  (let [{:keys [app/current-track]} (p/parser {:state state} [{:app/current-track [{:track/selected-loop [:loop/start :loop/finish]}]}])]
    (get current-track :track/selected-loop)))

(defn youtube-video-position []
  (let [pos (some-> (yt/get-video) (wd/video-current-time))]
    (if (js/isNaN pos) nil pos)))

(defn inject-font-awesome-css []
  (doto (wd/create-element! "link")
    (wd/set-properties! {:rel "stylesheet"
                         :href "//maxcdn.bootstrapcdn.com/font-awesome/4.3.0/css/font-awesome.min.css"})
    (wd/append-to! (wd/$ "head"))))

(defn ^:export init []
  (track/initialize "55eGVmS7Ty7Sa4cLxwpKL235My8elBtQBOk4wx1R" "ghUQSvjYReHqwNhdOROgzI3xm0aybyarCXW30usM")
  (let [bus (chan 1024 (map (partial debug-input "flux message")))
        pub (async/pub bus first)
        youtube-id (yt/current-video-id)
        reconciler (p/reconciler
                     {:state  {:youtube/current-video youtube-id
                               :app/current-track     (or #_ (p/kv-get store youtube-id)
                                                          (p/blank-track youtube-id))
                               :app/visible?          true}
                      :shared {:current-position youtube-video-position}
                      :parser p/parser
                      ;:send   
                      #_ (fn [{:keys [remote]} cb]
                        (cb (p/remote-parser {:store store}
                                             remote)))})]

    (set! (.-recon js/window) reconciler)
    
    ; watch for video page changes
    (async/pipe (yt/watch-video-load
                  (chan 1024 (comp (filter #(not= % :yt/no-video))
                                   (map #(vector :video-load %)))))
                bus)

    (go-sub* pub :video-load _ (chan 1 (take 1))
      (inject-font-awesome-css)
      ; wait video element to be available
      (<! (wait-for-presence yt/get-video))
      ; wait for video duration to be available
      (<! (wait-for-presence #(not (js/isNaN (wd/video-duration (yt/get-video))))))
      
      (track/track-extension-loaded)

      (swap! (get-in reconciler [:config :state]) #(assoc-in % [:app/current-track :track/duration]
                                                             (wd/video-duration (yt/get-video))))
      
      (setup-video-time-update bus)
      (om/add-root! reconciler ui/LoopPage (v/dialog-container)))

    (go-sub pub :video-load [_ video-id]
      (println "set current video" video-id))

    (go-sub pub :time-update _
      (when-let [loop (read-selected-loop (get-in reconciler [:config :state]))]
        (loop-back (yt/get-video) loop)))

    (go-sub pub :set-playback-rate [_ rate]
      (track/track-playback-rate-changed rate)
      (wd/video-playback-rate! (yt/get-video) rate))

    reconciler))
