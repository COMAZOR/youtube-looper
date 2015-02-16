(ns youtube-looper.views
  (:require-macros [enfocus.macros :as em])
  (:require [enfocus.core :as ef]
            [enfocus.events :as events]
            [cljs.core.async :refer [put! pipe]]
            [youtube-looper.util :refer [seconds->time]]
            [wilkerdev.util :refer [mapply]]))

(defn loop-time [{:keys [start finish]}]
  (str (seconds->time start) " - " (seconds->time finish)))

(def blue "#167ac6")

(def link-style
  {:color "#fff"
   :cursor "pointer"
   :text-decoration "none"})

(def blue-link-style
  (merge link-style {:color blue}))

(defn set-style [styles] (mapply ef/set-style styles))

(em/defsnippet loop-item :compiled "templates/yt-dialog.html"
  [".ytp-menu-row:first-child"]
  [{:keys [name] :as loop} {:keys [comm]}]
  [".ytp-menu-title"] (ef/do->
                        (set-style link-style)
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
          (set-style blue-link-style)
          (events/listen :click #(put! comm [:select-loop nil]))))

(em/defsnippet new-loop-button :compiled "templates/yt-dialog.html"
  [".ytl-new-loop"]
  [{:keys [comm]}]
  ["a"] (ef/do->
          (set-style blue-link-style)
          (events/listen :click #(put! comm [:pick-new-loop]))))

(em/deftemplate dialog-template :compiled "templates/yt-dialog.html"
  [{{:keys [loops current-loop]} :app-state :as looper}]
  [".ytp-menu-container"] (events/listen :click #(.stopPropagation %))
  [".ytp-menu-content"] (ef/do->
                          (ef/content (map loop-item (sort-by :start loops) (repeat looper)))
                          (ef/append (new-loop-button looper))
                          (ef/append (if current-loop (disable-loop-button looper)))))
