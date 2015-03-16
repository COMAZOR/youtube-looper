(ns youtube-looper.test
  (:require [figwheel.client :as fw]
            [youtube-looper.data-test]
            [youtube-looper.util-test]))

(enable-console-print!)

(fw/start {
           :websocket-url "ws://localhost:3449/figwheel-ws"
           :on-jsload (fn []
                        ;; (stop-and-start-my app)
                        )})

#_ (t/test-ns 'youtube-looper.data-test)
