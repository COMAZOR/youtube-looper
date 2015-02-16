(defproject youtube-looper "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/clj" "src/cljs"]

  :dependencies [[org.clojure/clojure "1.7.0-alpha5"]
                 [org.clojure/clojurescript "0.0-2727"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [camel-snake-kebab "0.2.4"]
                 [enfocus "2.1.1"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [datascript "0.9.0"]]

  :plugins [[lein-cljsbuild "1.0.4"]]

  :cljsbuild {:builds {:dev     {:source-paths ["src/cljs"]
                                 :compiler     {:output-to     "browsers/chrome/js/youtube-looper.js"
                                                :optimizations :none
                                                :pretty-print  true}}
                       :release {:source-paths ["src/cljs"]
                                 :compiler     {:output-to     "browsers/chrome/js/youtube-looper.js"
                                                :output-dir    "browsers/chrome/js"
                                                :optimizations :advanced
                                                :pretty-print  false
                                                :source-map    "browsers/chrome/js/youtube-looper.js.map"}}}})
