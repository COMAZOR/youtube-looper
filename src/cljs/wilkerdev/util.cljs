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
