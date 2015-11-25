(ns youtube-looper.views
  (:require-macros [enfocus.macros :as em])
  (:require [enfocus.core :as ef]
            [enfocus.events :as events]
            [cljs.core.async :refer [put! pipe]]
            [goog.events :as gevents]
            [youtube-looper.browser :refer [t]]
            [youtube-looper.data :as d]
            [youtube-looper.youtube :as yt]
            [youtube-looper.util :refer [seconds->time]]
            [wilkerdev.util :refer [mapply]]
            [wilkerdev.util.dom :as dom :refer [$]]))

; styles

(def blue "#167ac6")

(def yellow "rgb(232, 214, 20)")

(def link-style
  {:color "#fff"
   :cursor "pointer"
   :text-decoration "none"})

(def blue-link-style
  (merge link-style {:color blue})) 

(def input-style
  {:width     "27px"
   :border    "1px solid #2F2F2D"
   :font-size "11px"
   :padding   "2px 4px"})

(defn set-style [styles] (mapply ef/set-style styles))

; helpers

(defn pretty-loop-time [{:keys [loop/start loop/finish]}]
  (str (seconds->time start) " - " (seconds->time finish)))

(defn same-loop-time? [loop current-loop]
  (= (select-keys loop [:loop/start :loop/finish])
     (select-keys current-loop [:loop/start :loop/finish])))

; templates

(def MAIN_CONTAINER_SELECTOR "#movie_player")

(em/defsnippet loop-item :compiled "templates/yt-dialog.html"
  [".ytp-menu-row:first-child"]
  [{:keys [loop/label] :as loop} {:keys [bus db]}]
  [".ytp-menu-title"] (ef/do->
                        (set-style (merge link-style
                                          (if (same-loop-time? loop (d/current-loop db))
                                            {:color yellow} {})))
                        (ef/content label)
                        (events/listen :click #(put! bus [:rename-loop loop])))
  [".ytl-loop-time"] (ef/do->
                       (set-style blue-link-style)
                       (ef/content (pretty-loop-time loop))
                       (events/listen :click #(put! bus [:select-loop loop])))
  [".ytl-loop-close"] (ef/do->
                        (set-style blue-link-style)
                        (events/listen :click #(put! bus [:remove-loop loop]))))

(em/defsnippet disable-loop-button :compiled "templates/yt-dialog.html"
  [".ytl-loop-disable"]
  [{:keys [bus]}]
  ["a"] (ef/do->
          (ef/content (t "disable_loop"))
          (set-style blue-link-style)
          (events/listen :click #(put! bus [:select-loop nil]))))

(defn pick-double []
  (loop []
    (let [time (js/prompt (t "input_playback_rate"))]
      (cond
        (nil? time) nil
        (re-find #"^\d+(?:\.\d+)?$" time) (js/parseFloat time)
        :else (do (js/alert (t "invalid_number")) (recur))))))

(em/defsnippet playbackrate-button :compiled "templates/yt-dialog.html"
  [".ytl-loop-playbackrate"]
  [{:keys [bus]}]
  ["a"] (ef/do->
          (ef/content (t "set_playback_rate"))
          (set-style blue-link-style)
          (events/listen :click #(if-let [new-rate (pick-double)]
                                  (put! bus [:set-playback-rate new-rate])))))

(em/defsnippet new-loop-row :compiled "templates/yt-dialog.html"
  [".ytl-new-loop"]
  [{:keys [db bus]}]
  [".ytp-menu-title"] (ef/content (t "new_loop"))

  ["input"] (ef/do->
              (set-style input-style)
              (events/listen :keypress (fn [e]
                                         (.stopPropagation e)
                                         (when (and (.-ctrlKey e)
                                                    (= (.-keyCode e) 3))
                                           (let [target (.-target e)]
                                             (set! (.-value target) (seconds->time (.-currentTime (yt/get-video))))
                                             (.dispatchEvent target (js/Event. "input"))))
                                         (when (= (.-keyCode e) 13)
                                           (put! bus [:create-loop])))))

  ["input[name=start]"] (ef/do->
                          (ef/set-attr :value (:loop/start (d/new-loop db)))
                          (events/listen :input #(put! bus [:update-new-start (.. % -target -value)])))

  ["input[name=finish]"] (ef/do->
                           (ef/set-attr :value (:loop/finish (d/new-loop db)))
                           (events/listen :input #(put! bus [:update-new-finish (.. % -target -value)])))

  ["a"] (ef/do->
          (set-style blue-link-style)
          (events/listen :click #(put! bus [:create-loop]))))

(em/deftemplate dialog-template :compiled "templates/yt-dialog.html"
  [{:keys [db] :as flux-info}]
  [".ytp-menu-container"] (events/listen :click #(.stopPropagation %))
  [".ytp-menu-content"] (let [current-loop (d/current-loop db)]
                          (ef/do->
                            (ef/content (map loop-item (->> (d/loops-for-current-video db)
                                                            (sort-by :loop/start))
                                                       (repeat flux-info)))
                            (ef/append (new-loop-row flux-info))
                            (ef/append (playbackrate-button flux-info))
                            (ef/append (if current-loop (disable-loop-button flux-info))))))

; other elements

(defn create-looper-action-button []
  (yt/create-player-action-button :class "ytp-ytlooper"
                                  :label "Youtube Looper"
                                  :html "AB"))

(defn dialog-el [] ($ ($ MAIN_CONTAINER_SELECTOR) ".ytl-dialog"))

(defn block-key-propagation [el]
  (doto el
    (dom/listen "keydown" #(.stopPropagation %))
    (dom/listen "keyup" #(.stopPropagation %))
    (dom/listen "keypress" #(.stopPropagation %))))

(defn dialog-container []
  (or ($ ".ytp-looper-container")
      (doto (dom/create-element! "div")
        (dom/add-class! "ytp-looper-container")
        (dom/set-properties! {"data-layer" 9})
        (dom/set-style! {:z-index 20
                         :position "absolute"
                         :bottom "132px" :right "12px"})
        (block-key-propagation)
        (dom/append-to! ($ "#movie_player")))))

(defn loop-bar []
  (or ($ ".ytp-ab-looper-progress")
      (doto (yt/create-loop-bar "ytp-ab-looper-progress")
        (dom/insert-after! ($ ".ytp-load-progress")))))

(defn looper-action-button []
  (or ($ ".ytp-button-ytlooper")
      (doto (create-looper-action-button)
        (dom/insert-after! ($ ".ytp-settings-button")))))

(defn append-or-update! [parent lookup element]
  (if-let [child ($ parent lookup)]
    (dom/replace-node! element child)
    (dom/append! parent element)))

(defn show-dialog [flux-info]
  (let [dialog (dialog-template flux-info)]
    (append-or-update! ($ MAIN_CONTAINER_SELECTOR) ".ytl-dialog" dialog)))

; render engine

(defn render* [{:keys [db bus] :as flux-info}]
  (let [{:keys [ready? show-dialog?]} (d/settings db)]
    (when ready?
      (let [video (yt/get-video)]
        (yt/update-loop-representation (loop-bar) (d/current-loop db) (dom/video-duration video)))

      (when-let [dialog (dialog-el)]
        (dom/remove-node! dialog)
        (dom/set-style! (looper-action-button) :color nil))

      (when show-dialog?
        (show-dialog flux-info)
        (gevents/listenOnce dom/body "click" #(put! bus [:hide-dialog]))
        (dom/set-style! (looper-action-button) :color "#fff")))))

(defn request-rerender [render-data flux-info]
  (reset! render-data flux-info))

(defn render [render-data]
  (when-let [flux-info @render-data]
    (render* flux-info)
    (reset! render-data nil)))

(defn init-render-engine []
  (let [render-data (atom nil)]
    (add-watch render-data :render (fn [_ _ old-val new-val]
                                     (when (and (nil? old-val) new-val)
                                       (js/requestAnimationFrame (partial render render-data)))))))
