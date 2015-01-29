(ns youtube-looper.core
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [wilkerdev.util.macros :refer [dochan]])
  (:require [cljs.core.async :refer [chan put! <! close!] :as async]
            [cljs.core.match :refer-macros [match]]
            [wilkerdev.util :refer [format]]
            [wilkerdev.util.dom :as dom]
            [wilkerdev.util.reactive :as r]))

(defn video-current-time [video] (.-currentTime video))
(defn video-duration [video] (.-duration video))
(defn video-seek! [video time] (set! (.-currentTime video) time))

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
    (let [position (video-current-time video)]
      (when (> position finish)
        (video-seek! video start)))))

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

(defn update-loop-representation [{:keys [el video]} {:keys [start finish]}]
  (let [duration (video-duration video)
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

(defn loop-from-current-time [video]
  {:start (video-current-time video)
   :finish (inc (video-current-time video))})

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
          (let [new-loop (pick-loop-prompt (or @loop-ref (loop-from-current-time video)))]
            (put! comm [:update-loop new-loop]))
        [:update-loop new-loop]
          (do
            (update-loop-representation loop-bar new-loop)
            (video-seek! video (:start new-loop))
            (reset! loop-ref new-loop))
        [:reset]
          (put! comm [:update-loop nil])

        :else (.log js/console "Got invalid message" (clj->js msg))))

    comm))

(defn init []
  (dom/observe-mutation {:container dom/body
                         :options {:childList false :characterData false}
                         :callback (fn [& args]
                                     (.log js/console "attributes modified on body" (clj->js args)))})
  (init-looper (dom/$ "video")))

(init)
