(ns youtube-looper.browser)

(def translation-cache (atom {}))

(defn t [key] (get @translation-cache key ""))

(defn ^:export load-translations [callback]
  (let [port (.-port js/self)]
    (.on port "locale-map"
         (fn [locale]
           (reset! translation-cache (js->clj locale))
           (callback)))

    (.emit port "locale")))
