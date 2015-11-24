(ns youtube-looper.ui-cards
  (:require [cljs.test :refer-macros [is async]]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [wilkerdev.util.dom :as wd]
            [youtube-looper.next.parser :as p]
            [youtube-looper.next.ui :as ui])
  (:require-macros [devcards.core :as dc :refer [defcard deftest dom-node]]))

(def fake-store
  (p/map-kv-store {"123" {:youtube/id  "123"
                          :db/id       (random-uuid)
                          :track/loops [{:db/id (random-uuid) :loop/label "full" :loop/start 5 :loop/finish 50}
                                        {:db/id (random-uuid) :loop/label "intro" :loop/start 60 :loop/finish 70}]}}))

(def reconciler
  (om/reconciler
    {:state  {:youtube/current-video "123"}
     :parser p/parser
     :send   (fn [{:keys [remote]} cb]
               (println "REMOTE" remote)
               (cb (p/remote-parser {:store fake-store}
                                    remote)))}))

(def reconciler-local-storage
  (om/reconciler
    {:state  {}
     :parser p/parser
     :send   (fn [query cb]
               (println "REMOTE" query)
               (cb (p/remote-parser {:current-track #(str "123")
                                     :store         (p/local-storage-kv-store "cards-")}
                                    (:remote query))))}))

(defcard numeric-input
  (ui/numeric-input {:value "" :onChange #(print "numeric input update" %)}))

(defcard track-loop-overlay-sample
  (ui/track-loop-overlay {:track/duration 100 :track/loop {:loop/start 10 :loop/finish 20}}))

(defcard loop-row-sample
  (ui/loop-row {:loop/start  123
                :loop/finish 128
                :loop/label  "Sample"}))

(defcard youtube-ui
  (dom-node (fn [_ node]
              (wd/set-html! node "")
              (doto (wd/create-element! "div")
                (wd/set-html! "
                  <div>
                    <div class=\"ytp-progress-list\">
                    </div>
                    <div>
                      <button class=\"ytp-settings-button\">Settings</button>
                    </div>
                  </div>
                ")
                (wd/append-to! node)))))

(defcard loop-page-card
  "Display the loop manager dialog"
  (om/mock-root reconciler ui/LoopPage))

#_(defcard loop-page-card-local-storage
    "Display the loop manager dialog using local storage."
    (om/mock-root reconciler-local-storage ui/LoopPage))
