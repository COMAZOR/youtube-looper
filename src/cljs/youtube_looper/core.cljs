(ns youtube-looper.core
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [wilkerdev.util.macros :refer [dochan]])
  (:require [cljs.core.async :refer [chan put! <! close!] :as async]
            [cljs.core.match :refer-macros [match]]
            [wilkerdev.util.dom :as dom]
            [wilkerdev.util.reactive :as r]))

(defn constantly-chan [value] (chan 1 (map (constantly value))))

(defn player-element [video query]
  (some-> (dom/ancestor video (dom/query-matcher ".html5-video-player"))
          (dom/$ query)))

(defn create-looper-button []
  (doto (dom/create-element! "div")
    (dom/add-class! "ytp-button ytp-button-ytlooper")
    (dom/set-properties! {:role       "button"
                          :aria-label "Youtube Looper"
                          :tabindex   "6500"})
    (dom/set-html! "AB")))

(defn add-button-on-player [video button]
  (when-let [ref-button (player-element video ".ytp-settings-button")]
    (dom/insert-after! button ref-button)))

(defn loop-back [video {:keys [start finish] :as loop}]
  (if loop
    (let [position (.-currentTime video)]
      (when (> position finish)
        (set! (.-currentTime video) start)))))

(defn prompt-time [message current]
  (loop []
    (let [time (js/prompt message (or current ""))]
      (cond
        (nil? time) (recur)

        (re-find #"^(\d{1,2}):(\d{1,2}(?:\.\d+)?)$" time)
          (let [[_ minutes seconds] (re-find #"^(\d{1,2}):(\d{1,2}(?:\.\d+)?)$" time)]
            (+ (* (js/parseInt minutes) 60)
               (js/parseInt seconds)))

        (re-find #"^\d+(?:\.\d+)?$" time) (js/parseInt time)

        :else (do
                (js/alert "Invalid time format, try again.")
                (recur))))))

(defn pick-loop-prompt [current]
  (let [start (prompt-time "Which time the loop should start? (eg: 0:34)" (:start current))
        finish (prompt-time "Which time the loop should end? (eg: 3:44)" (:finish current))]
    {:start start :finish finish}))

(defn update-loop-representation [{:keys [el video]} {:keys [start finish]}]
  (let [duration (.-duration video)
        start-pct (/ start duration)
        size-pct (/ (- finish start) duration)]
    (doto el
      (dom/set-css! "left" (str (* start-pct 100) "%"))
      (dom/set-css! "transform" (str "scaleX(" size-pct ")")))))

(defn create-loop-bar [video]
  (let [el (doto (dom/create-element! "div")
             (dom/add-class! "ytp-ab-looper-progress")
             (dom/set-css! "left" "0%")
             (dom/set-css! "transform" "scaleX(0)")
             (dom/insert-after! (player-element video ".ytp-load-progress")))]
    {:el el :video video}))

(defn init-looper [video]
  (let [loop-ref (atom nil)
        comm (chan (async/sliding-buffer 1024))
        toggle-button (create-looper-button)
        loop-bar (create-loop-bar video)]

    ; ui setup
    (add-button-on-player video toggle-button)

    ; event setup
    (async/pipe (r/listen video "timeupdate" (constantly-chan [:time-update])) comm)
    (async/pipe (r/listen toggle-button "click" (constantly-chan [:show-dialog])) comm)

    ; processing
    (dochan [msg comm]
      (match msg
        [:time-update]
          (loop-back video @loop-ref)
        [:show-dialog]
          (let [new-loop (pick-loop-prompt @loop-ref)]
            (update-loop-representation loop-bar new-loop)
            (reset! loop-ref new-loop))))))

(defn init []
  (init-looper (dom/$ "video")))

(init)
