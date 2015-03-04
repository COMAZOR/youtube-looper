(ns youtube-looper.browser
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [wilkerdev.util.macros :refer [dochan]])
  (:require [cljs.core.async :refer [put! chan <! close!]]
            [cljs.reader :refer [read-string]]))

(def safari (some-> js/window .-safari))

(def translations (atom {}))

(defn t [key] (get @translations (keyword key) key))

(defn listen-browser-messages []
  (let [c (chan 1024)]
    (some-> js/window .-safari .-self (.addEventListener "message" #(put! c %) false))
    c))

(defn send-safari-message [name value]
  (.. js/window -safari -self -tab (dispatchMessage name value)))

(defn ^:export load-translations [callback]
  (when safari
    (dochan [msg (listen-browser-messages)]
      (reset! translations (read-string (.-message msg)))
      (callback))
    (send-safari-message "loadLocale" nil)))
