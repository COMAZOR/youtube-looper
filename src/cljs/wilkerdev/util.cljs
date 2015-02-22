(ns wilkerdev.util
  (:require [camel-snake-kebab.core :as csk]
            [goog.string]
            [goog.string.format]))

(defn mapply
  "Applies a function f to the argument list formed by concatenating
  everything but the last element of args with the last element of
  args. This is useful for applying a function that accepts keyword
  arguments to a map."
  {:arglists '([f & args])}
  ([f m]        (apply f (apply concat m)))
  ([f a & args] (apply f a (apply concat (butlast args) (last args)))))

(defn format
  "Formats a string using goog.string.format."
  [fmt & args]
  (apply goog.string/format fmt args))

(defn js->map [obj]
  (->> (js-keys obj)
       (map #(vector % (aget obj %)))
       (map #(update-in % [0] (comp keyword csk/->kebab-case)))
       (into {})))

(defn map->query [m]
  (->> (clj->js m)
       (.createFromMap goog.Uri.QueryData)
       (.toString)))

(defn quote-regexp [string]
  (.replace string (js/RegExp "[-\\^$*+?.()|[\\]{}]" "g") "\\$&"))

(defn distinct-consecutive
  "Returns a lazy sequence of the elements of coll with consecutive duplicates removed"
  ([]
   (fn [rf]
     (let [last-seen (volatile! ::unseen)]
       (fn
         ([] (rf))
         ([result] (rf result))
         ([result input]
          (if (= @last-seen input)
            result
            (do (vreset! last-seen input)
                (rf result input))))))))
  ([coll]
   (let [step (fn step [xs seen]
                (lazy-seq
                  ((fn [[f :as xs] last-seen]
                     (when-let [s (seq xs)]
                       (if (= last-seen f)
                         (recur (rest s) last-seen)
                         (cons f (step (rest s) f)))))
                    xs seen)))]
     (step coll ::unseen))))
