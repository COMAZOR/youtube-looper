(ns youtube-looper.browser
  (:require [wilkerdev.browsers.chrome :as chrome]))

(defn t [key] (chrome/i18n-message key))
