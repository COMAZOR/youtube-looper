(defproject youtube-looper "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/clj" "src/cljs"]

  :dependencies [[org.clojure/clojure "1.7.0-alpha5"]
                 [org.clojure/clojurescript "0.0-3058"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [camel-snake-kebab "0.2.4"]
                 [enfocus "2.1.1"]
                 [datascript "0.9.0"]
                 [me.raynes/fs "1.4.6"]
                 [cljs-ajax "0.3.10"]]

  :plugins [[lein-cljsbuild "1.0.4"]]

  :cljsbuild {:builds {:chrome-dev  {:source-paths ["src/cljs" "src/cljs-chrome"]
                                     :compiler     {:output-to     "browsers/chrome/js/youtube-looper.js"
                                                    :optimizations :whitespace
                                                    :pretty-print  true}}

                       :firefox-dev {:source-paths ["src/cljs" "src/cljs-firefox"]
                                     :compiler     {:output-to     "browsers/firefox/data/js/youtube-looper.js"
                                                    :optimizations :whitespace
                                                    :pretty-print  true}}

                       :safari-dev  {:source-paths ["src/cljs" "src/cljs-safari"]
                                     :compiler     {:output-to     "browsers/safari/youtube-looper.safariextension/js/youtube-looper.js"
                                                    :optimizations :whitespace
                                                    :pretty-print  true}}

                       :chrome-release {:source-paths ["src/cljs" "src/cljs-chrome"]
                                       :compiler     {:output-to     "browsers/chrome/js/youtube-looper.js"
                                                      :output-dir    "browsers/chrome/js"
                                                      :optimizations :advanced
                                                      :pretty-print  false
                                                      :externs       ["externs/chrome_extensions.js"]
                                                      :source-map    "browsers/chrome/js/youtube-looper.js.map"}}}})
