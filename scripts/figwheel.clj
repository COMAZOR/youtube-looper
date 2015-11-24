(require '[figwheel-sidecar.repl :as r]
         '[figwheel-sidecar.repl-api :as ra])

(ra/start-figwheel!
  {:figwheel-options {:server-port 3450}
   :build-ids        ["chrome" "cards" "demo"]
   :all-builds
                     [
                      {:id           "chrome"
                       :source-paths ["src/cljs" "src/cljs-chrome"]
                       :compiler     {:output-to     "browsers/chrome/js/youtube-looper.js"
                                      :optimizations :whitespace
                                      :verbose    true
                                      :pretty-print  true}}

                      {:id           "demo"
                       :figwheel     true
                       :source-paths ["src/cljs" "src/demo"]
                       :compiler     {:main                 'youtube-looper.demo
                                      :source-map-timestamp true
                                      :asset-path           "js/out-demo"
                                      :output-to            "resources/public/js/demo.js"
                                      :output-dir           "resources/public/js/out-demo"
                                      :verbose              false}}
                      
                      {:id           "cards"
                       :figwheel     {:devcards true}
                       :source-paths ["src/cljs" "src/devcards"]
                       :compiler     {:main                 'youtube-looper.devcards
                                      :source-map-timestamp true
                                      :asset-path           "js/out-devcards"
                                      :output-to            "resources/public/js/devcards.js"
                                      :output-dir           "resources/public/js/out-devcards"
                                      :verbose              false}}]})

(ra/cljs-repl)
