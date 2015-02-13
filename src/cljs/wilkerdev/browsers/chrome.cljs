(ns wilkerdev.browsers.chrome)

(def chrome js/chrome)

(defn resource-url [path]
  (.. chrome -extension (getURL path)))
