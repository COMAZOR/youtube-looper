(require '[figwheel-sidecar.repl :as r]
         '[figwheel-sidecar.repl-api :as ra])

(ra/start-figwheel!
  {:figwheel-options {:server-port 3450}
   :build-ids        ["chrome" "cards"]
   :all-builds
                     [
                      {:id           "chrome"
                       :source-paths ["src/cljs" "src/cljs-chrome"]
                       :compiler     {:output-to     "browsers/chrome/js/youtube-looper.js"
                                      :optimizations :whitespace
                                      :verbose    true
                                      :pretty-print  true}}

                      #_ {:id           "dev"
                       :figwheel     true
                       :source-paths ["src"]
                       :compiler     {:main       'om-tutorial.core
                                      :asset-path "js"
                                      :output-to  "resources/public/js/main.js"
                                      :output-dir "resources/public/js"
                                      :verbose    true}}
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
