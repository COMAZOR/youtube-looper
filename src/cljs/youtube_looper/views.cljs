(ns youtube-looper.views
  (:require-macros [enfocus.macros :as em])
  (:require [enfocus.core :as ef]
            [enfocus.events :as events]
            [cljs.core.async :refer [put! pipe]]
            [youtube-looper.util :refer [seconds->time]]
            [wilkerdev.util :refer [mapply]]
            [wilkerdev.browsers.chrome :refer [t]]))

(def blue "#167ac6")

(def yellow "rgb(232, 214, 20)")

(def link-style
  {:color "#fff"
   :cursor "pointer"
   :text-decoration "none"})

(def blue-link-style
  (merge link-style {:color blue}))

(defn set-style [styles] (mapply ef/set-style styles))

(defn loop-time [{:keys [start finish]}]
  (str (seconds->time start) " - " (seconds->time finish)))

(defn same-loop-time? [loop current-loop]
  (= (select-keys loop [:start :finish])
     (select-keys current-loop [:start :finish])))

(em/defsnippet loop-item :compiled "templates/yt-dialog.html"
  [".ytp-menu-row:first-child"]
  [{:keys [name] :as loop} {:keys [comm] {:keys [current-loop]} :app-state}]
  [".ytp-menu-title"] (ef/do->
                        (set-style (merge link-style
                                          (if (same-loop-time? loop current-loop) {:color yellow} {})))
                        (ef/content name)
                        (events/listen :click #(put! comm [:rename-loop loop])))
  [".ytl-loop-time"] (ef/do->
                       (set-style blue-link-style)
                       (ef/content (loop-time loop))
                       (events/listen :click #(put! comm [:select-loop loop])))
  [".ytl-loop-close"] (ef/do->
                        (set-style blue-link-style)
                        (events/listen :click #(put! comm [:remove-loop loop]))))

(em/defsnippet disable-loop-button :compiled "templates/yt-dialog.html"
  [".ytl-loop-disable"]
  [{:keys [comm]}]
  ["a"] (ef/do->
          (ef/content (t "disable_loop"))
          (set-style blue-link-style)
          (events/listen :click #(put! comm [:select-loop nil]))))

(em/defsnippet new-loop-row :compiled "templates/yt-dialog.html"
  [".ytl-new-loop"]
  [{:keys [comm]}]
  [".ytp-menu-title"] (ef/do->
                        (ef/content (t "new_loop"))
                        (events/listen :click #(put! comm [:pick-new-loop]))))

(em/deftemplate dialog-template :compiled "templates/yt-dialog.html"
  [{{:keys [loops current-loop]} :app-state :as looper}]
  [".ytp-menu-container"] (events/listen :click #(.stopPropagation %))
  [".ytp-menu-content"] (ef/do->
                          (ef/content (map loop-item (sort-by :start loops) (repeat looper)))
                          (ef/append (new-loop-row looper))
                          (ef/append (if current-loop (disable-loop-button looper)))))
