(ns youtube-looper.track
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [chan put! close!] :as async]
            [goog.userAgent :as ua]))

(def parse js/Parse)

(def analytics (.-Analytics parse))

(defn initialize [app-id key] (.initialize parse app-id key))

(defn promise->chan [promise]
  (let [c (chan)
        cb #(do (put! c %)
                (close! c))]
    (.then promise cb cb)
    c))

(def track-base-dimensions
  {:user-agent             (ua/getUserAgentString)
   :youtube-looper-version "0.7.0" ; TODO: get the version dynamically
   })

(defn track
  ([event] (track event {}))
  ([event dimensions]
   (->> (merge track-base-dimensions dimensions)
        (clj->js)
        (.track analytics event)
        (promise->chan))))

(defn track-extension-loaded [] (track "injected"))
(defn track-video-load [video-id] (track "video-loaded" {:video-id video-id}))
(defn track-loop-selected [] (track "loop-selected"))
(defn track-loop-disabled [] (track "loop-disabled"))
(defn track-loop-created [] (track "loop-created"))
(defn track-loop-renamed [] (track "loop-renamed"))
(defn track-loop-removed [] (track "loop-removed"))
(defn track-playback-rate-changed [rate] (track "playback-rate-changed" {:playback-rate rate}))
