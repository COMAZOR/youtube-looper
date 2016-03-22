(require '[figwheel-sidecar.repl :as r]
         '[figwheel-sidecar.repl-api :as ra])

(ra/start-figwheel!
  {:figwheel-options {:server-port 3449}
   :build-ids        ["chrome" "cards"]
   :all-builds       [{:id           "chrome"
                       :source-paths ["src/cljs" "src/cljs-chrome"]
                       :compiler     {:output-to            "browsers/chrome/js/youtube-looper.js"
                                      :source-map-timestamp true
                                      :optimizations        :whitespace
                                      :verbose              true
                                      :pretty-print         true}}

                      {:id           "cards"
                       :figwheel     {:devcards true}
                       :source-paths ["src/cljs" "src/devcards"]
                       :compiler     {:main                 'youtube-looper.devcards
                                      :source-map-timestamp true
                                      :asset-path           "js/out-devcards"
                                      :output-to            "resources/public/js/devcards.js"
                                      :output-dir           "resources/public/js/out-devcards"
                                      :verbose              true}}]})

(ra/cljs-repl)
