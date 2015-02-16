(ns youtube-looper.core
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [wilkerdev.util.macros :refer [dochan all-or-nothing->]])
  (:require [cljs.core.async :refer [chan put! <! >! close!] :as async]
            [cljs.core.match :refer-macros [match]]
            [goog.events :as events]
            [wilkerdev.browsers.chrome :refer [t]]
            [wilkerdev.local-storage :as store]
            [wilkerdev.util.dom :as dom]
            [wilkerdev.util.reactive :as r]
            [youtube-looper.youtube :as yt]
            [youtube-looper.util :refer [seconds->time time->seconds]]
            [youtube-looper.views :refer [dialog-template]]))

(defn constantly-chan [value] (chan 1 (map (constantly value))))

(defn loop-back [video {:keys [start finish] :as loop}]
  (if loop
    (let [position (dom/video-current-time video)]
      (when (> position finish)
        (dom/video-seek! video start)))))

(defn parse-time [time]
  (cond
    (nil? time) nil
    (re-find #"^(\d{1,2}):(\d{1,2}(?:\.\d+)?)$" time) (time->seconds time)
    (re-find #"^\d+(?:\.\d+)?$" time) (js/parseFloat time)))

(defn prompt-time [message current]
  (loop []
    (let [time (js/prompt message (or current ""))]
      (cond
        (nil? time) nil
        (re-find #"^(\d{1,2}):(\d{1,2}(?:\.\d+)?)$" time) (time->seconds time)
        (re-find #"^\d+(?:\.\d+)?$" time) (js/parseFloat time)
        :else (do (js/alert "Invalid time format, try again.") (recur))))))

(defn create-looper-action-button []
  (yt/create-player-action-button :class "ytp-button-ytlooper"
                                  :label "Youtube Looper"
                                  :html "AB"))

(defn append-or-update! [parent lookup element]
  (if-let [child (dom/$ parent lookup)]
    (dom/replace-node! element child)
    (dom/append! parent element)))

(defn show-dialog [looper-data]
  (let [dialog (dialog-template looper-data)]
    (append-or-update! (dom/$ ".html5-video-controls") ".ytl-dialog" dialog)))

(defn add-loop [app-state loop]
  (update-in app-state [:loops] conj loop))

(defn remove-loop [app-state loop]
  (update-in app-state [:loops] #(remove (partial = loop) %)))

(defn reset-loops [app-state loops]
  (assoc app-state :loops loops))

(defn set-current-loop [app-state loop]
  (assoc app-state :current-loop loop))

(defn store-key [video-id] (str "video-loops-" video-id))

(defn loops-for-video [video-id] (store/get (store-key video-id) []))

(defn sync-loops-for-video [video-id loops]
  (store/set! (store-key video-id) loops))

(defn normalize-new-loop [{:keys [start finish]}]
  (when-let [[start finish] (all-or-nothing-> (parse-time start) (parse-time finish))]
    {:start start :finish finish :name (t "unnamed_section")}))

(defn init-looper-process [video]
  (let [app-state (atom nil)
        comm (chan (async/sliding-buffer 1024))
        toggle-button (create-looper-action-button)
        loop-bar (yt/create-loop-bar "ytp-ab-looper-progress")
        player-element (partial yt/player-element video)
        dialog-el #(dom/$ (dom/$ ".html5-video-controls") ".ytl-dialog")
        show-dialog #(show-dialog {:app-state @app-state
                                   :comm      comm})]

    (add-watch app-state :watcher (fn [_ _ os ns]
                                    (if (not= (:loops os) (:loops ns))
                                      (sync-loops-for-video (yt/current-video-id) (:loops ns)))
                                    (if (= (:new-loop os) (:new-loop ns))
                                      (put! comm [:refresh-ui]))))

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
          (if (not (dialog-el))
            (put! comm [:show-dialog]))
        [:time-update]
          (loop-back video (:current-loop @app-state))
        [:refresh-ui]
          (do
            (yt/update-loop-representation loop-bar (:current-loop @app-state) (dom/video-duration video))

            (when-let [dialog (dialog-el)]
              (dom/remove-node! dialog)
              (dom/set-style! toggle-button :color nil))

            (when (:dialog-visible? @app-state)
              (show-dialog)
              (events/listenOnce dom/body "click" #(put! comm [:hide-dialog]))
              (dom/set-style! toggle-button :color "#fff")))
        [:show-dialog]
          (swap! app-state assoc :dialog-visible? true)
        [:hide-dialog]
          (swap! app-state assoc :dialog-visible? false)
        [:create-new-loop]
          (when-let [new-loop (normalize-new-loop (:new-loop @app-state))]
            (swap! app-state assoc :new-loop {})
            (swap! app-state add-loop new-loop)
            (put! comm [:select-loop new-loop]))
        [:select-loop new-loop]
          (do
            (swap! app-state set-current-loop new-loop)
            (if new-loop (dom/video-seek! video (:start new-loop))))
        [:rename-loop loop]
          (when-let [new-name (js/prompt "New loop name" (:name loop))]
            (swap! app-state remove-loop loop)
            (swap! app-state add-loop (assoc loop :name new-name)))
        [:remove-loop loop]
          (swap! app-state remove-loop loop)
        [:update-new-start time]
          (swap! app-state assoc-in [:new-loop :start] time)
        [:update-new-finish time]
          (swap! app-state assoc-in [:new-loop :finish] time)
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
