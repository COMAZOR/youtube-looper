(defproject youtube-looper "0.7.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/clj" "src/cljs"]
  :test-paths ["test/clj" "test/cljs"]

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.170"]
                 [org.clojure/core.async "0.2.374"]
                 [org.omcljs/om "1.0.0-alpha22"]
                 [me.raynes/fs "1.4.6"]
                 [enfocus "2.1.1"]
                 [datascript "0.13.3"]
                 [cljs-ajax "0.3.10"]
                 [com.cognitect/transit-cljs "0.8.232"]]

  :profiles {:dev {:dependencies [[figwheel "0.5.0-1"]
                                  [figwheel-sidecar "0.5.0-1"]
                                  [com.cemerick/piggieback "0.2.2-SNAPSHOT"]
                                  [devcards "0.2.1"]]

                   :plugins [[lein-cljsbuild "1.1.1"]
                             [lein-figwheel "0.5.0-1"]]}}


  :cljsbuild {:builds {:devcards {:source-paths ["src/cljs" "src/devcards"]
                                  :figwheel     {:devcards true
                                                 :websocket-host "localhost"}
                                  :compiler     {:asset-path           "/js/out-devcards"
                                                 :output-to            "resources/public/js/devcards.js"
                                                 :output-dir           "resources/public/js/out-devcards"
                                                 :source-map           "resources/public/js/out-devcards.js.map"
                                                 :main                 youtube-looper.devcards
                                                 :warnings             {:single-segment-namespace false}
                                                 :source-map-timestamp true}}
                       
                       :chrome-dev  {:source-paths ["src/cljs" "src/cljs-chrome"]
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
                                                  ;; :source-map-timestamp true
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

  :figwheel {:http-server-root "public" ;; default and assumes "resources"
             :server-port 3449 ;; default

             ;; Start an nREPL server into the running figwheel process
             :nrepl-port 7888})
