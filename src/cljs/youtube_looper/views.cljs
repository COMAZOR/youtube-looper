(ns youtube-looper.views
  (:require-macros [enfocus.macros :as em])
  (:require [enfocus.core :as ef]
            [enfocus.events :as events]
            [youtube-looper.util :refer [seconds->time]]))

(defn loop-time [{:keys [start finish]}]
  (str (seconds->time start) " - " (seconds->time finish)))

(em/defsnippet loop-item :compiled "templates/yt-dialog.html"
  [".ytp-menu-row:first-child"]
  [{:keys [name] :as loop}]
  [".ytp-menu-title"] (ef/content name)
  [".ytl-loop-time"] (ef/content (loop-time loop))
  [".ytl-loop-close"] (ef/set-style :cursor "pointer"))

(em/deftemplate dialog-template :compiled "templates/yt-dialog.html"
  [loops]
  [".ytp-menu-content"] (ef/content (map loop-item loops)))
