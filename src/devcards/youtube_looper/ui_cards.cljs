(ns youtube-looper.ui-cards
  (:require [cljs.test :refer-macros [is async]]
            [cljs.core.async :as async]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [youtube-looper.next.kv-stores :as kv]
            [youtube-looper.next.parser2 :as p]
            [youtube-looper.next.ui :as ui]
            [wilkerdev.util.dom :as wd])
  (:require-macros [devcards.core :as dc :refer [defcard deftest dom-node]]))

(def video-position (atom 0))

(def track
  {:youtube/id     "123"
   :db/id          (random-uuid)
   :track/loops    [{:db/id (random-uuid) :loop/label "full" :loop/start 5 :loop/finish 50}
                    {:db/id (random-uuid) :loop/label "intro" :loop/start 60 :loop/finish 70}]
   :track/new-loop {:db/id (random-uuid)}})

(def fake-store
  (kv/map-kv-store {"123" track}))

(def initial-state
  {:youtube/current-video "123"
   :app/visible?          true})

(defonce reconciler
  (p/reconciler {:state  initial-state
                 :shared {:current-position #(deref video-position)
                          :current-duration #(-> 100)
                          :bus (async/chan 1024)}
                 :send (partial p/send fake-store)}))

#_ (def reconciler-local-storage
  (om/reconciler
    {:state  {:youtube/current-video "123"}
     :parser p/parser
     :send   (fn [query cb]
               (println "REMOTE" query)
               (cb (p/remote-parser {:current-track #(str "123")
                                     :store         (p/local-storage-kv-store "cards-")}
                                    (:remote query))))}))

(defcard new-loop-black
  (ui/new-loop-row {}))

(defcard new-loop-with-start
  (ui/new-loop-row {:loop/start 90 :video/current-time 110}))

(defcard new-loop-with-start-under
  (ui/new-loop-row {:loop/start 90 :video/current-time 80}))

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

(defcard video-position-control
  "Use to control the current video position when picking time"
  (fn [data]
    (let [pos @video-position]
      (dom/div nil
        (dom/input #js {:type     "range"
                        :min      0
                        :max      100
                        :value    pos
                        :onChange #(let [value (js/parseInt (.. % -target -value))]
                                    (om/transact! reconciler `[(app/update-current-time {:value ~value}) :video/current-time])
                                    (reset! video-position value))}))))
  video-position)

(defcard loop-page-card
  "Display the loop manager dialog"
  (om/mock-root reconciler ui/LoopPage))

#_(defcard loop-page-card-local-storage
    "Display the loop manager dialog using local storage."
    (om/mock-root reconciler-local-storage ui/LoopPage))
