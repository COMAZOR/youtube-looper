(ns youtube-looper.core
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [wilkerdev.util.macros :refer [dochan go-sub go-sub* all-or-nothing]])
  (:require [cljs.core.async :refer [chan put! <! >! close!] :as async]
            [om.next :as om]
            [wilkerdev.util.dom :as dom]
            [wilkerdev.util.reactive :as r]
            [youtube-looper.next.parser :as p]
            [youtube-looper.next.ui :as ui]
            [youtube-looper.youtube :as yt]
            [youtube-looper.views :as v]
            [youtube-looper.track :as track]))

(enable-console-print!)

(def ^:dynamic *log-debug* false)

(defn log [& args]
  (if *log-debug*
    (apply (.. js/console -log) args)))

(defn constantly-chan [value] (chan 1 (map (constantly value))))

(defn loop-back [video {:keys [loop/start loop/finish]}]
  (let [position (dom/video-current-time video)]
    (when (> position finish)
      (dom/video-seek! video start))))

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

(def store (p/map-kv-store {}))

(defn ^:export init []
  (track/initialize "55eGVmS7Ty7Sa4cLxwpKL235My8elBtQBOk4wx1R" "ghUQSvjYReHqwNhdOROgzI3xm0aybyarCXW30usM")
  (let [bus (chan 1024 (map (partial debug-input "flux message")))
        pub (async/pub bus first)
        reconciler (om/reconciler
                     {:state  {:youtube/current-video (yt/current-video-id)}
                      :parser p/parser
                      :send   (fn [{:keys [remote]} cb]
                                (cb (p/remote-parser {:store store}
                                                     remote)))})]

    ; watch for video page changes
    (async/pipe (yt/watch-video-load
                  (chan 1024 (comp (filter #(not= % :yt/no-video))
                                   (map #(vector :video-load %)))))
                bus)

    (go-sub* pub :video-load _ (chan 1 (take 1))
      ; on Firefox even after the video load is detected the video sometimes takes
      ; a little longer to be available
      (<! (wait-for-presence yt/get-video))
      (track/track-extension-loaded)

      (setup-video-time-update bus)
      (om/add-root! reconciler ui/LoopPage (v/dialog-container))
      #_ (async/pipe (r/listen (v/looper-action-button) "click" (constantly-chan [:invoke-looper])) bus)
      #_(d/update-settings! conn {:ready? true}))

    (go-sub pub :video-load [_ video-id]
      (println "set current video" video-id)
      #_(d/set-current-video! conn video-id))

    (go-sub pub :time-update _
      #_(if-let [loop (d/current-loop @conn)]
          (loop-back (yt/get-video) loop)))

    (go-sub pub :show-dialog _
      #_(d/set-dialog-visibility! conn true))

    (go-sub pub :hide-dialog _
      #_(d/set-dialog-visibility! conn false))

    (go-sub pub :invoke-looper _
      (println "invoke looper")
      #_(if-not (v/dialog-el)
          (put! bus [:show-dialog])))

    (go-sub pub :select-loop [_ loop]
      #_(d/select-loop! conn loop)
      (if loop (track/track-loop-selected) (track/track-loop-disabled))
      (if loop (dom/video-seek! (yt/get-video) (:loop/start loop))))

    #_ (go-sub pub :create-loop _
      (track/track-loop-created)
      (d/create-from-new-loop! conn)
      (sync-loops! @conn))

    #_ (go-sub pub :rename-loop [_ loop]
      (when-let [new-label (js/prompt (t "new_loop_name") (:loop/label loop))]
        (track/track-loop-renamed)
        (d/rename-loop! conn loop new-label))
      (sync-loops! @conn))

    #_ (go-sub pub :remove-loop [_ loop]
      (track/track-loop-removed)
      (d/remove-loop! conn loop)
      (sync-loops! @conn))

    #_ (go-sub pub :update-new-start [_ val]
      (d/update-new-loop! conn {:loop/start val}))

    #_ (go-sub pub :update-new-finish [_ val]
      (d/update-new-loop! conn {:loop/finish val}))

    (go-sub pub :set-playback-rate [_ rate]
      (track/track-playback-rate-changed rate)
      (dom/video-playback-rate! (yt/get-video) rate))

    (go-sub pub :export-data _
      #_(.log js/console (d/export-data)))

    #_looper))
