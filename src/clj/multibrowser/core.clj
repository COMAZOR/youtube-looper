(ns multibrowser.core
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [me.raynes.fs :as fs]))

(def ^:dynamic *supported-browsers* [:chrome :firefox :safari])

(defn resource-file [path] (io/file (io/resource path)))

(defn locale-files []
  (->> (resource-file "locales")
       (file-seq)
       (filter #(.isFile %))))

(defn extract-locale-name [path]
  (fs/base-name path true))

(defn expand-language [lang]
  (let [[_ language country] (re-find #"^(\w+)-(\w+)$" lang)]
    {:raw      lang
     :language language
     :country  country}))

(defn read-locale [file]
  (let [mappings (edn/read-string (slurp file))
        locale-name (extract-locale-name (.toString file))]
    {:translations mappings
     :language     (expand-language locale-name)}))

(defn chrome-locale->path [{:keys [country language]}]
  (if (and (= country "US")
           (= language "en"))
    language
    (str language "_" country)))

(defn chrome-locale [{:keys [language translations]}]
  [{:target  (format "browsers/chrome/_locales/%s/messages.json"
                     (chrome-locale->path language))
    :content (->> translations
                  (map (fn [[k v]] [k {:message v}]))
                  (into {})
                  (json/write-str))}])

(defn firefox-locale [{:keys [language translations]}]
  [{:target  (format "browsers/firefox/locale/%s.properties"
                     (:raw language))
    :content (->> (map (fn [[k v]] (str (name k) "= " v)) translations)
                  (str/join "\n"))}
   {:target  "browsers/firefox/lib/l10n-keys.js"
    :content (format "module.exports = %s;"
                     (json/write-str (keys translations)))}])

(defn safari-locale [{:keys [language translations]}]
  [{:target  (format "browsers/safari/youtube-looper.safariextension/locale/%s.edn"
                     (str/lower-case (:raw language)))
    :content (pr-str translations)}])

(defmulti translate :browser)

(defmethod translate :chrome [t] (chrome-locale t))
(defmethod translate :firefox [t] (firefox-locale t))
(defmethod translate :safari [t] (safari-locale t))

(defn expand-translations [translations]
  (let [transforms (for [browser *supported-browsers*
                         translation translations]
                     (-> (assoc translation :browser browser)
                         (translate)))]
    (->> (apply concat transforms)
         (distinct))))

(defn store! [{:keys [target content]}]
  (let [f (fs/file target)]
    (fs/mkdirs (.getParent f))
    (spit f content)))

(defn sync-locales []
  (doseq [out (->> (locale-files)
                   (map read-locale)
                   (expand-translations))]
    (store! out)
    (println "Wrote" (:target out))))
