(ns youtube-looper.core
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [wilkerdev.util.macros :refer [dochan]])
  (:require [cljs.core.async :refer [chan put! <! >! close!] :as async]
            [cljs.core.match :refer-macros [match]]
            [wilkerdev.util :refer [format]]
            [wilkerdev.util.dom :as dom]
            [wilkerdev.util.reactive :as r]
            [youtube-looper.youtube :as yt]))

(defn constantly-chan [value] (chan 1 (map (constantly value))))

(defn seconds->time [seconds]
  (let [minutes (->> (/ seconds 60)
                     (.floor js/Math))
        seconds (mod seconds 60)]
    (format "%02d:%02d" minutes seconds)))

(defn time->seconds [time]
  (let [[_ minutes seconds] (re-find #"^(\d{1,2}):(\d{1,2}(?:\.\d+)?)$" time)]
    (+ (* (js/parseInt minutes) 60)
       (js/parseFloat seconds))))

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
    {:start start :finish finish}))

(defn ensure-number [n] (if (js/isNaN n) 0 n))

(defn update-loop-representation [{:keys [el video]} {:keys [start finish] :as loop}]
  (let [duration (dom/video-duration video)
        start-pct (/ start duration)
        size-pct (/ (- finish start) duration)]
    (doto el
      (dom/set-css! "left" (str (ensure-number (* start-pct 100)) "%"))
      (dom/set-css! "transform" (str "scaleX(" (ensure-number size-pct) ")")))))

(defn create-loop-bar [video]
  (let [el (doto (dom/create-element! "div")
             (dom/add-class! "ytp-ab-looper-progress")
             (dom/set-css! "left" "0%")
             (dom/set-css! "transform" "scaleX(0)")
             (dom/insert-after! (yt/player-element video ".ytp-load-progress")))]
    {:el el :video video}))

(defn loop-from-current-time [video]
  {:start (dom/video-current-time video)
   :finish (inc (dom/video-current-time video))})

(defn init-looper [video]
  (let [loop-ref (atom nil)
        comm (chan (async/sliding-buffer 1024))
        toggle-button (yt/create-looper-button)
        loop-bar (create-loop-bar video)]

    ; ui setup
    (yt/add-button-on-player video toggle-button)

    ; event setup
    (async/pipe (r/listen video "timeupdate" (constantly-chan [:time-update])) comm)
    (async/pipe (r/listen toggle-button "click" (constantly-chan [:show-dialog])) comm)

    ; processing
    (dochan [msg comm]
      (match msg
        [:time-update]
          (loop-back video @loop-ref)
        [:show-dialog]
          (let [new-loop (pick-loop-prompt (or @loop-ref (loop-from-current-time video)))]
            (put! comm [:update-loop new-loop]))
        [:update-loop new-loop]
          (do
            (update-loop-representation loop-bar new-loop)
            (reset! loop-ref new-loop)
            (if new-loop (dom/video-seek! video (:start new-loop))))
        [:reset]
          (put! comm [:update-loop nil])

        :else (.log js/console "Got invalid message" (clj->js msg))))

    comm))

(defn init []
  (let [looper (atom nil)]
    (dochan [video-id (yt/watch-video-change)]
      (when (not= video-id :yt/no-video)
        (if-not @looper (reset! looper (init-looper (dom/$ "video"))))
        (>! @looper [:reset])))))

(init)
