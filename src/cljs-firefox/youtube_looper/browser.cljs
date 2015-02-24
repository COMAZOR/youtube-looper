(ns youtube-looper.browser
  (:require-macros [wilkerdev.util.macros :refer [go-sub*]])
  (:require [cljs.core.async :refer [chan put!]]))

(def translation-cache (atom {}))

(defn t [key] (get @translation-cache key ""))

(defn ^:export load-translations [callback]
  (let [port (.-port js/self)]
    (.on port "locale-map"
         (fn [locale]
           (reset! translation-cache (js->clj locale))
           (callback)))

    (.emit port "locale")))
