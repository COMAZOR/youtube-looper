(ns youtube-looper.core
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [wilkerdev.util.macros :refer [dochan]])
  (:require [cljs.core.async :refer [chan put! <! >! close!] :as async]
            [cljs.core.match :refer-macros [match]]
            [wilkerdev.util.dom :as dom]
            [wilkerdev.util.reactive :as r]
            [youtube-looper.youtube :as yt]
            [youtube-looper.util :refer [seconds->time time->seconds]]
            [youtube-looper.views :as v]))

(defn constantly-chan [value] (chan 1 (map (constantly value))))

(defn loop-back [video {:keys [start finish] :as loop}]
  (if loop
    (let [position (dom/video-current-time video)]
      (when (> position finish)
        (dom/video-seek! video start)))))

(defn prompt-time [message current]
  (loop []
    (let [time (js/prompt message (or current ""))]
      (cond
        (nil? time) (recur)
        (re-find #"^(\d{1,2}):(\d{1,2}(?:\.\d+)?)$" time) (time->seconds time)
        (re-find #"^\d+(?:\.\d+)?$" time) (js/parseFloat time)
        :else (do (js/alert "Invalid time format, try again.") (recur))))))

(defn pick-loop-prompt [current]
  (let [start (prompt-time "Which time the loop should start? (eg: 0:34)" (seconds->time (get current :start 0)))
        finish (prompt-time "Which time the loop should end? (eg: 3:44)" (seconds->time (get current :finish 0)))]
    {:start start :finish finish :name "Unnamed section"}))

(defn loop-from-current-time [video]
  {:start (dom/video-current-time video)
   :finish (inc (dom/video-current-time video))})

(defn create-looper-action-button []
  (yt/create-player-action-button :class "ytp-button-ytlooper"
                                  :label "Youtube Looper"
                                  :html "AB"))

(defn append-or-update! [parent lookup element]
  (if-let [child (dom/$ parent lookup)]
    (dom/replace-node! element child)
    (dom/append! parent element)))

(defn show-dialog [looper-data]
  (let [dialog (v/dialog-template looper-data)]
    (append-or-update! (dom/$ ".html5-video-controls") ".ytl-dialog" dialog)))

(defn add-loop [app-state loop]
  (update-in app-state [:loops] conj loop))

(defn remove-loop [app-state loop]
  (update-in app-state [:loops] #(remove (partial = loop) %)))

(defn reset-loops [app-state loops]
  (assoc app-state :loops loops))

(defn set-current-loop [app-state loop]
  (assoc app-state :current-loop loop))

(defn loops-for-video [video-id]
  [{:name "Sample Loop" :start 10 :finish 180}])

(defn init-looper-process [video]
  (let [app-state (atom nil)
        comm (chan (async/sliding-buffer 1024))
        toggle-button (create-looper-action-button)
        loop-bar (yt/create-loop-bar "ytp-ab-looper-progress")
        player-element (partial yt/player-element video)
        dialog-el #(dom/$ (dom/$ ".html5-video-controls") ".ytl-dialog")
        show-dialog #(show-dialog {:app-state @app-state
                                   :comm      comm})]

    (add-watch app-state :watcher (fn [_ _ _ _] (put! comm [:refresh-dialog])))

    ; ui setup
    (dom/insert-after! toggle-button (player-element ".ytp-settings-button"))
    (dom/insert-after! loop-bar (player-element ".ytp-load-progress"))

    ; event setup
    (async/pipe (r/listen video "timeupdate" (constantly-chan [:time-update])) comm)
    (async/pipe (r/listen toggle-button "click" (constantly-chan [:invoke-looper])) comm)

    ; processing
    (dochan [msg comm]
      (match msg
        [:invoke-looper]
          (if (> (count (:loops @app-state)) 0)
            (put! comm [:toggle-dialog])
            (put! comm [:pick-loop]))
        [:time-update]
          (loop-back video (:current-loop @app-state))
        [:refresh-dialog]
          (if-let [dialog (dialog-el)]
            (if (> (count (:loops @app-state)) 0)
              (show-dialog)
              (dom/remove-node! dialog)))
        [:show-dialog]
          (if (> (count (:loops @app-state)) 0)
            (show-dialog)
            (put! comm [:pick-loop]))
        [:toggle-dialog]
          (if-let [dialog (dialog-el)]
            (dom/remove-node! dialog)
            (show-dialog))
        [:pick-loop]
          (let [new-loop (pick-loop-prompt (or (:current-loop @app-state) (loop-from-current-time video)))]
            (swap! app-state add-loop new-loop)
            (put! comm [:select-loop new-loop]))
        [:select-loop new-loop]
          (do
            (yt/update-loop-representation loop-bar new-loop (dom/video-duration video))
            (swap! app-state set-current-loop new-loop)
            (if new-loop (dom/video-seek! video (:start new-loop))))
        [:remove-loop loop]
          (swap! app-state remove-loop loop)
        [:reset loops]
          (do
            (swap! app-state reset-loops loops)
            (put! comm [:select-loop nil]))

        :else (.log js/console "Got invalid message" (clj->js msg))))

    comm))

(defn init []
  (let [looper (atom nil)]
    (dochan [video-id (yt/watch-video-load (chan 1024 (filter #(not= % :yt/no-video))))]
      (if-not @looper (reset! looper (init-looper-process (dom/$ "video"))))
      (>! @looper [:reset (loops-for-video video-id)]))))

(init)
