(ns youtube-looper.next.styles)

(defn css [& styles]
  (clj->js (apply merge styles)))

(def flex-row {:display "flex"})

(defn justify-content [pos] {:justifyContent pos})
