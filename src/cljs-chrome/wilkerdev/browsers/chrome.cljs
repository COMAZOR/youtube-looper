(ns wilkerdev.browsers.chrome)

(def chrome js/chrome)

(defn resource-url [path]
  (.. chrome -extension (getURL path)))

(def i18n (.-i18n chrome))

(defn i18n-message [name] (.getMessage i18n name))
(def t i18n-message)
