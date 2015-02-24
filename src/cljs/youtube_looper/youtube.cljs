(ns youtube-looper.youtube
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [wilkerdev.util.macros :refer [dochan]])
  (:require [cljs.core.async :refer [chan put! <! >! close! pipe]]
            [wilkerdev.util :refer [format distinct-consecutive]]
            [wilkerdev.util.dom :as dom :refer [$]]))

(defn create-player-action-button [& {:keys [class label tabindex html]
                                      :or   {tabindex "6500"}}]
  (doto (dom/create-element! "div")
    (dom/add-class! (str "ytp-button " class))
    (dom/set-properties! {:role       "button"
                          :aria-label label
                          :tabindex   tabindex})
    (dom/set-html! html)))

(defn ensure-number [n] (if (js/isNaN n) 0 n))

(defn update-loop-representation [bar {:keys [loop/start loop/finish]} video-duration]
  (let [start-pct (/ start video-duration)
        size-pct (/ (- finish start) video-duration)]
    (doto bar
      (dom/set-style! {:left      (str (ensure-number (* start-pct 100)) "%")
                       :transform (str "scaleX(" (ensure-number size-pct) ")")}))))

(defn create-loop-bar [class]
  (doto (dom/create-element! "div")
    (dom/add-class! class)
    (dom/set-style! {:left      "0%"
                     :transform "scaleX(0)"})))

(defn item-prop [name]
  (if-let [node ($ (str "meta[itemprop=" name "]"))]
    (.-content node)))

(defn extract-video-id [watch-url]
  (if-let [[_ video-id] (re-find #"watch\?.*&?v=([^&]+)" watch-url)]
    video-id))

(defn current-video-id-canonical []
  (some-> ($ "link[rel=canonical]")
          (aget "href")
          (extract-video-id)))

(defn current-video-id []
  (or (item-prop "videoId")
      (current-video-id-canonical)))

(defn watch-video-load [c]
  (let [in-chan (dom/observe-mutation {:container dom/body
                                       :options   {:childList false :characterData false}}
                                      (chan 1024 (comp (map (fn [_] (or (current-video-id) :yt/no-video)))
                                                       (distinct-consecutive))))]
    (put! in-chan :go)
    (pipe in-chan
          c)))

(defn get-video [] ($ "video"))
