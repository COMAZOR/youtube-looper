(ns youtube-looper.youtube
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [wilkerdev.util.macros :refer [dochan]])
  (:require [cljs.core.async :refer [chan put! <! >! close!] :as async]
            [cljs.core.match :refer-macros [match]]
            [wilkerdev.util :refer [format]]
            [wilkerdev.util.dom :as dom]
            [wilkerdev.util.reactive :as r]))

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

(defn ensure-number [n] (if (js/isNaN n) 0 n))

(defn update-loop-representation [{:keys [el video]} {:keys [start finish]}]
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
             (dom/insert-after! (player-element video ".ytp-load-progress")))]
    {:el el :video video}))

(defn item-prop [name]
  (if-let [node (dom/$ (str "meta[itemprop=" name "]"))]
    (.-content node)))

(defn current-video-id [] (item-prop "videoId"))

(defn watch-video-change []
  (-> (dom/observe-mutation {:container dom/body
                             :options   {:childList false :characterData false}}
                            (chan 1024 (map (fn [_] (or (current-video-id) ::no-video)))))
      (r/distinct)))
