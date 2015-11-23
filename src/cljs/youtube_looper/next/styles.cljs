(ns youtube-looper.next.styles)

(defn css [& styles]
  (clj->js (apply merge styles)))

(def flex-row {:display "flex"})

(defn justify-content [pos] {:justifyContent pos})

(def youtube-action-button
  {:verticalAlign "top"
   :textAlign "center"
   :fontWeight "bold"
   :fontSize 14})

(def youtube-progress
  {:background      "#c0c"
   :position        "absolute"
   :width           "100%"
   :height          "100%"
   :bottom          0
   :zIndex          1000
   :transformOrigin "0 0"})
