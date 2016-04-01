(defproject youtube-looper "0.7.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/clj" "src/cljs" "src/index" "src/devcards" "src/cljs-chrome"]
  :test-paths ["test/clj" "test/cljs"]

  :dependencies [[org.clojure/clojure "1.8.0" :scope "provided"]
                 [org.clojure/clojurescript "1.7.228" :scope "provided"]
                 [org.clojure/core.async "0.2.374"]
                 [org.omcljs/om "1.0.0-alpha32"]
                 [me.raynes/fs "1.4.6"]
                 [com.cognitect/transit-cljs "0.8.232"]
                 [figwheel-sidecar "0.5.2"]
                 [navis/untangled-client "0.4.7-SNAPSHOT"]
                 [devcards "0.2.1" :exclusions [org.omcljs/om]]]

  :profiles {:dev {:dependencies []}}

  :cljsbuild {:builds
              [{:id           "chrome"
                :figwheel     {:websocket-url "wss://localhost:443/figwheel-ws"}
                :source-paths ["src/cljs" "src/cljs-chrome"]
                :compiler     {:output-to     "browsers/chrome/js/youtube-looper.js"
                               :output-dir    "browsers/chrome/js"
                               :optimizations :whitespace
                               :pretty-print  true
                               :asset-path    "js"}}

               {:id           "cards"
                :figwheel     {:devcards true}
                :source-paths ["src/cljs" "src/devcards"]
                :compiler     {:main       'youtube-looper.devcards
                               :asset-path "js/out-devcards"
                               :output-to  "resources/public/js/devcards.js"
                               :output-dir "resources/public/js/out-devcards"}}]})
