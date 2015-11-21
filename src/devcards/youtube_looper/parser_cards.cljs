(ns youtube-looper.parser-cards
  (:require [cljs.test :refer-macros [is async]]
            [om.next :as om :refer-macros [defui]]
            [youtube-looper.next.parser :as p])
  (:require-macros [devcards.core :as dc :refer [defcard deftest]]))

;; Local Parser Tests

(deftest test-app-current-track
  (is (= {} (p/parser {:state (atom {})} [:app/current-track])))
  (is (= [:app/current-track] (p/parser {:state (atom {})} [:app/current-track] :remote)))
  (is (= {:app/current-track {:youtube/id "123"}}
         (p/parser {:state (atom {:app/current-track       [:tracks/by-youtube-id "123"]
                                  :tracks/by-youtube-id {"123" {:youtube/id "123"}}})}
                   [:app/current-track]))))

(def sample-loop {:loop/start 10 :loop/finish 20})

(deftest test-new-loop
  (let [state (atom {:tracks/by-youtube-id {"123" {:youtube/id "123" :track/loops #{}}}})]
    (p/parser {:state state}
              `[(track/new-loop {:youtube/id "123" :loop ~sample-loop})])
    (is (= @state
           {:tracks/by-youtube-id
            {"123"
             {:youtube/id  "123",
              :track/loops #{{:loop/start 10, :loop/finish 20}}}}}))
    (is (= (p/parser {:state state}
                     [(list 'track/new-loop {:youtube/id "123" :loop sample-loop})]
                     :remote)
           [(list 'track/new-loop {:youtube/id "123" :loop sample-loop})]))))

(deftest test-remove-loop
  (let [state (atom {:tracks/by-youtube-id {"123" {:youtube/id "123" :track/loops #{sample-loop}}}})]
    (p/parser {:state state}
              `[(track/remove-loop {:youtube/id "123" :loop ~sample-loop})])
    (is (= @state
           {:tracks/by-youtube-id
            {"123"
             {:youtube/id "123",
              :track/loops #{}}}}))
    (is (= (p/parser {:state state}
                     [(list 'track/remove-loop {:youtube/id "123" :loop sample-loop})]
                     :remote)
           [(list 'track/remove-loop {:youtube/id "123" :loop sample-loop})]))))

;; Remote Parser Tests

(def sample-track
  {:youtube/id  "123"
   :track/loops #{{:loop/label "full" :loop/start 5 :loop/finish 200}
                  {:loop/label "intro" :loop/start 204 :loop/finish 205}}})

(def fake-store
  (p/map-kv-store {"123" sample-track}))

(deftest test-remote-app-current-track
  (let [remote-env #(-> {:store         fake-store
                         :current-track (partial str %)})]
    (is (= (p/remote-parser (remote-env "abc") [:app/current-track])
           {:app/current-track {:youtube/id "abc" :track/loops #{}}}))
    
    (is (= (p/remote-parser (remote-env "123") [:app/current-track])
           {:app/current-track sample-track}))))
 
(deftest test-remote-track-new-loop
  (let [store (p/map-kv-store {"123" sample-track})]
    (p/remote-parser {:store store} `[(track/new-loop {:youtube/id "123" :loop ~sample-loop})])
    (is (= (p/kv-get store "123")
           {:youtube/id "123",
            :track/loops
                        #{{:loop/label "full", :loop/start 5, :loop/finish 200}
                          {:loop/label "intro", :loop/start 204, :loop/finish 205}
                          sample-loop}}))
    (p/remote-parser {:store store} `[(track/new-loop {:youtube/id "12" :loop ~sample-loop})])
    (is (= (p/kv-get store "12")
           {:youtube/id "12",
            :track/loops #{sample-loop}}))))

(deftest test-remote-track-remove-loop
  (let [store (p/map-kv-store {"123" sample-track})
        loop {:loop/label "full" :loop/start 5 :loop/finish 200}]
    (p/remote-parser {:store store} `[(track/remove-loop {:youtube/id "123" :loop ~loop})])
    (is (= (p/kv-get store "123")
           {:youtube/id  "123",
            :track/loops #{{:loop/label "intro", :loop/start 204, :loop/finish 205}}}))))
