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

(defn init-looper [video]
  (let [current-loop (atom nil)
        video-loops (atom [])
        comm (chan (async/sliding-buffer 1024))
        toggle-button (create-looper-action-button)
        loop-bar (yt/create-loop-bar "ytp-ab-looper-progress")
        player-element (partial yt/player-element video)
        dialog-el #(dom/$ (dom/$ ".html5-video-controls") ".ytl-dialog")
        loop-data #(hash-map :loops @video-loops
                             :current-loop @current-loop
                             :comm comm)]

    (add-watch current-loop :watcher (fn [_ _ _ _] (put! comm [:refresh-dialog])))
    (add-watch video-loops :watcher (fn [_ _ _ _] (put! comm [:refresh-dialog])))

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
          (if (> (count @video-loops) 0)
            (put! comm [:toggle-dialog])
            (put! comm [:pick-loop]))
        [:time-update]
          (loop-back video @current-loop)
        [:refresh-dialog]
          (if-let [dialog (dialog-el)]
            (if (> (count @video-loops) 0)
              (show-dialog (loop-data))
              (dom/remove-node! dialog)))
        [:show-dialog]
          (if (> (count @video-loops) 0)
            (show-dialog (loop-data))
            (put! comm [:pick-loop]))
        [:toggle-dialog]
          (if-let [dialog (dialog-el)]
            (dom/remove-node! dialog)
            (show-dialog (loop-data)))
        [:pick-loop]
          (let [new-loop (pick-loop-prompt (or @current-loop (loop-from-current-time video)))]
            (swap! video-loops conj new-loop)
            (put! comm [:select-loop new-loop]))
        [:select-loop new-loop]
          (do
            (yt/update-loop-representation loop-bar new-loop (dom/video-duration video))
            (reset! current-loop new-loop)
            (if new-loop (dom/video-seek! video (:start new-loop))))
        [:remove-loop loop]
          (swap! video-loops #(remove (partial = loop) %))
        [:reset loops]
          (do
            (reset! video-loops loops)
            (put! comm [:select-loop nil]))

        :else (.log js/console "Got invalid message" (clj->js msg))))

    comm))

(defn init []
  (let [looper (atom nil)]
    (dochan [video-id (yt/watch-video-load (chan 1024 (filter #(not= % :yt/no-video))))]
      (if-not @looper (reset! looper (init-looper (dom/$ "video"))))
      (>! @looper [:reset [{:name "Sample Loop" :start 10 :finish 180}]]))))

(init)
