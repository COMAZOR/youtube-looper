(defproject youtube-looper "0.7.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/clj" "src/cljs"]
  :test-paths ["test/clj" "test/cljs"]

  :dependencies [[org.clojure/clojure "1.7.0-beta1"]
                 [me.raynes/fs "1.4.6"]
                 [org.clojure/clojurescript "0.0-3269"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [camel-snake-kebab "0.2.4"]
                 [enfocus "2.1.1"]
                 [datascript "0.9.0"]
                 [cljs-ajax "0.3.10"]]

  :profiles {:dev {:dependencies [[com.cemerick/clojurescript.test "0.3.3"]
                                  [figwheel "0.2.5-SNAPSHOT"]]

                   :plugins [[lein-cljsbuild "1.0.4"]
                             [com.cemerick/clojurescript.test "0.3.3"]
                             [lein-figwheel "0.2.5-SNAPSHOT"]]}}


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

                       :test {:source-paths ["src/cljs" "test/cljs"]
                              :compiler     {:output-to     "out/test/youtube-looper-test.js"
                                             :optimizations :whitespace
                                             :pretty-print  true}}

                       :test-repl {:source-paths ["src/cljs" "test/cljs"]
                                   :compiler     {:output-to            "resources/repl/js/youtube-looper-test.js"
                                                  :output-dir           "resources/repl/js/out"
                                                  :optimizations        :none
                                                  :main                 youtube-looper.test
                                                  :asset-path           "js/out"
                                                  :source-map           true
                                                  :source-map-timestamp true
                                                  :cache-analysis       true}}

                       :chrome-release {:source-paths ["src/cljs" "src/cljs-chrome"]
                                       :compiler     {:output-to     "browsers/chrome/js/youtube-looper.js"
                                                      :output-dir    "browsers/chrome/js"
                                                      :optimizations :advanced
                                                      :pretty-print  false
                                                      :externs       ["externs/chrome_extensions.js"
                                                                      "externs/parse.js"]
                                                      :source-map    "browsers/chrome/js/youtube-looper.js.map"}}}
              :test-commands {"unit-test" ["slimerjs" :runner "out/test/youtube-looper-test.js"]}}

  :figwheel {:http-server-root "repl" ;; default and assumes "resources"
             :server-port 3449 ;; default

             ;; Start an nREPL server into the running figwheel process
             :nrepl-port 7888})
