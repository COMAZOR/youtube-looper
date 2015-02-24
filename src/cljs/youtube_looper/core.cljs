(ns youtube-looper.core
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [wilkerdev.util.macros :refer [dochan go-sub go-sub* all-or-nothing]])
  (:require [cljs.core.async :refer [chan put! <! >! close!] :as async]
            [wilkerdev.util.dom :as dom]
            [wilkerdev.util.reactive :as r]
            [youtube-looper.browser :refer [t]]
            [youtube-looper.data :as d]
            [youtube-looper.youtube :as yt]
            [youtube-looper.views :as v]))

(defn constantly-chan [value] (chan 1 (map (constantly value))))

(defn loop-back [video {:keys [loop/start loop/finish]}]
  (let [position (dom/video-current-time video)]
    (when (> position finish)
      (dom/video-seek! video start))))

(defn debug-input [label [msg :as i]]
  (let [ignored-messages #{:time-update}]
    (if-not (contains? ignored-messages msg)
      (.log js/console label (pr-str i))))
  i)

(defn setup-video-time-update [bus]
  (async/pipe (r/listen (yt/get-video) "timeupdate" (constantly-chan [:time-update])) bus))

; this is hackish, but prevents UI to re-render while user is typing
(defn should-render? [{:keys [db-before db-after]}]
  (or (= (d/new-loop db-before)
         (d/new-loop db-after))
      (not= (count (d/loops-for-current-video db-before))
            (count (d/loops-for-current-video db-after)))))

(defn setup-render-engine [{:keys [conn bus]}]
  (let [render-engine (v/init-render-engine)]
    (d/listen! conn (fn [tx-report]
                      (if (should-render? tx-report)
                        (v/request-rerender render-engine
                                            {:db  (:db-after tx-report)
                                             :bus bus}))))
    render-engine))

(defn sync-loops! [db]
  (let [loops (->> (d/loops-for-current-video db)
                   (map d/loop-ent-to-storage))
        video-id (d/settings db :current-video)]
    (d/sync-loops-for-video! video-id loops)))

(defn wait-for-presence
  ([f] (wait-for-presence f 10))
  ([f delay]
   (go
     (while (not (f)) (<! (async/timeout delay)))
     (f))))

(defn ^:export init []
  (let [conn (d/create-conn)
        bus (chan 1024 (map (partial debug-input "flux message")))
        pub (async/pub bus first)
        looper {:conn conn :bus bus :pub pub}
        render-engine (setup-render-engine looper)]

    ; watch for video page changes
    (async/pipe (yt/watch-video-load
                  (chan 1024 (comp (filter #(not= % :yt/no-video))
                                   (map #(vector :video-load %)))))
                bus)

    (go-sub pub :request-rerender _
      (v/request-rerender render-engine {:db @conn :bus bus}))

    (go-sub* pub :video-load _ (chan 1 (take 1))
      ; on Firefox even after the video load is detected the video sometimes takes
      ; a little longer to be available
      (<! (wait-for-presence yt/get-video))

      (setup-video-time-update bus)
      (async/pipe (r/listen (v/looper-action-button) "click" (constantly-chan [:invoke-looper])) bus)
      (d/update-settings! conn {:ready? true}))

    (go-sub* pub :video-load [_ video-id] (chan 1 (distinct))
      (d/load-loops! conn (d/loops-for-video-on-storage video-id)))

    (go-sub pub :video-load [_ video-id]
      (d/set-current-video! conn video-id))

    (go-sub pub :time-update _
      (if-let [loop (d/current-loop @conn)]
        (loop-back (yt/get-video) loop)))

    (go-sub pub :show-dialog _
      (d/set-dialog-visibility! conn true))

    (go-sub pub :hide-dialog _
      (d/set-dialog-visibility! conn false))

    (go-sub pub :invoke-looper _
      (if-not (v/dialog-el)
        (put! bus [:show-dialog])))

    (go-sub pub :select-loop [_ loop]
      (d/select-loop! conn loop)
      (if loop (dom/video-seek! (yt/get-video) (:loop/start loop))))

    (go-sub pub :create-loop _
      (d/create-from-new-loop! conn)
      (sync-loops! @conn))

    (go-sub pub :rename-loop [_ loop]
      (when-let [new-label (js/prompt (t "new_loop_name") (:loop/label loop))]
        (d/rename-loop! conn loop new-label))
      (sync-loops! @conn))

    (go-sub pub :remove-loop [_ loop]
      (d/remove-loop! conn loop)
      (sync-loops! @conn))

    (go-sub pub :update-new-start [_ val]
      (d/update-new-loop! conn {:loop/start val}))

    (go-sub pub :update-new-finish [_ val]
      (d/update-new-loop! conn {:loop/finish val}))

    looper))
