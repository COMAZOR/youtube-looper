(ns youtube-looper.views-cards
  (:require [cljs.test :refer-macros [is async]]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [youtube-looper.next.parser :as p]
            [youtube-looper.next.ui :as ui])
  (:require-macros [devcards.core :as dc :refer [defcard deftest]]))

(def fake-store
  (p/map-kv-store {"123" {:youtube/id  "123"
                          :track/loops #{{:loop/label "full" :loop/start 5 :loop/finish 200}}}}))

(def reconciler
  (om/reconciler
    {:state  {}
     :parser p/parser
     :send   (fn [query cb]
               (cb (p/remote-parser {:current-track #(str "123")
                                     :store         fake-store}
                                    (:remote query))))}))

(def reconciler-local-storage
  (om/reconciler
    {:state  {}
     :parser p/parser
     :send   (fn [query cb]
               (println "REMOTE" query)
               (cb (p/remote-parser {:current-track #(str "123")
                                     :store         (p/local-storage-kv-store "cards-")}
                                    (:remote query))))}))

(defcard loop-row-sample
  (ui/loop-row {:loop/start  123
                :loop/finish 128
                :loop/label  "Sample"}))

(defcard loop-page-card
  "Display the loop manager dialog"
  (om/mock-root reconciler ui/LoopPage))

(defcard loop-page-card-local-storage
  "Display the loop manager dialog using local storage."
  (om/mock-root reconciler-local-storage ui/LoopPage))
