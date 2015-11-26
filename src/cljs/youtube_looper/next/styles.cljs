(ns youtube-looper.next.styles)

(defn css [& styles]
  (clj->js (apply merge styles)))

(def flex-row {:display "flex"})

(defn align-items [pos] {:alignItems pos})

(def flex-row-center
  (merge flex-row (align-items "center")))

(defn justify-content [pos] {:justifyContent pos})

(def c-abaaaa-grey "#abaaaa")
(def c-d3d3d3-grey "#d3d3d3")
(def c-989898-grey "#989898")
(def c-f12b24-red "#f12b24")
(def fs-13 {:fontSize 13})
(def fs-15 {:fontSize 15})
(def fs-18 {:fontSize 18})
(def fs-23 {:fontSize 23})

(def youtube-action-button
  {:verticalAlign "top"
   :textAlign "center"
   :fontWeight "bold"
   :fontSize 14})

(def hr
  {:background c-989898-grey
   :height 1
   :border "none"
   :margin 0})

(def youtube-progress
  {:background      "#c0c"
   :position        "absolute"
   :width           "100%"
   :height          "100%"
   :bottom          0
   :zIndex          1000
   :transformOrigin "0 0"})

(def popup-container
  {:background "rgba(28,28,28,0.8)"
   :borderRadius "4px"
   :display "inline-block"})

(def time-input
  {:border "1px solid #000"
   :width 30})

(def selected-loop-row
  {:background "rgba(241, 43, 36, 0.2)"})

(def header-text
  (merge fs-13 {:color c-abaaaa-grey}))

(def body-text 
  (merge fs-13 {:color c-d3d3d3-grey}))

(def loop-row
  (merge flex-row-center
         (justify-content "space-between")
         {:borderRadius "3px"
          :padding "3px 9px"}))

(def loop-label 
  {:padding "0 10px"
   :width 168})
