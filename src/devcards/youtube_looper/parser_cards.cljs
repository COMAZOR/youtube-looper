(ns youtube-looper.parser-cards
  (:require [cljs.test :refer-macros [is async testing]]
            [om.next :as om :refer-macros [defui]]
            [youtube-looper.next.parser :as p :refer [mk-ref]])
  (:require-macros [devcards.core :as dc :refer [defcard deftest]]))

(defn mk-loop
  ([start finish] (mk-loop start finish ""))
  ([start finish label]
   {:db/id (random-uuid) :loop/start start :loop/finish finish :loop/label label}))

;; Local Parser Tests

(deftest test-app-current-track
  (is (= {} (p/parser {:state (atom {})} [:app/current-track])))

  (is (= '[({:app/current-track [:track/duration]} {:youtube/id "123"})]
         (p/parser {:state (atom {:youtube/current-video "123"})}
                   [{:app/current-track [:track/duration]}]
                   :remote))
      "remote call")

  (is (= {:app/current-track {:youtube/id  "123"
                              :track/loops [{:loop/start 1}]}}
         (p/parser {:state (atom {:app/current-track [:entities/by-id "123"]
                                  :entities/by-id    {"123" {:youtube/id  "123"
                                                             :track/loops [[:entities/by-id "l1"]]}
                                                      "l1"  {:loop/start 1}}})}
                   [:app/current-track]))))

(def sample-loop (mk-loop 10 20))

(deftest test-app-current-loop
  (is (= {:app/current-loop {:db/id "new-loop"}} (p/parser {:state (atom {})} [:app/current-loop])))
  (is (= {:app/current-loop {:loop true}} (p/parser {:state (atom {:app/current-loop {:loop true}})} [:app/current-loop]))))

(deftest test-app-update-current-loop
  (let [state (atom {:app/current-loop {:loop/start 10}})]
    (p/parser {:state  state
               :shared {:current-position #(-> 20)}} '[(app/current-loop-update {:key :loop/finish})])
    (is (= {:app/current-loop {:loop/start 10
                               :loop/finish 20}}
           @state))))

(deftest test-new-loop
  (let [state (atom {:entities/by-id {"123" {:db/id "123" :youtube/id "yid" :track/loops []}}})]
    (p/parser {:state state}
              `[(track/new-loop {:db/id "123" :loop ~sample-loop})])
    (is (= {:entities/by-id
            {"123"
                                  {:db/id       "123"
                                   :youtube/id  "yid"
                                   :track/loops [(mk-ref sample-loop)]}
             (:db/id sample-loop) sample-loop}}
           @state))

    (is (= [(list 'track/new-loop {:db/id "123" :youtube/id "yid" :loop sample-loop})]
           (p/parser {:state state}
                     [(list 'track/new-loop {:db/id "123" :loop sample-loop})]
                     :remote)))))

(deftest test-remove-loop
  (let [state (atom {:entities/by-id {"123"                {:db/id "123" :youtube/id "a12" :track/loops [(mk-ref sample-loop)]}
                                      (:db/id sample-loop) sample-loop}})]
    (p/parser {:state state}
              `[(track/remove-loop {:db/id "123" :loop ~sample-loop})])
    (is (= {:entities/by-id
            {"123"
             {:db/id       "123"
              :youtube/id  "a12"
              :track/loops []}}}
           @state))
    (is (= [(list 'track/remove-loop {:db/id "123" :youtube/id "a12" :loop sample-loop})]
           (p/parser {:state state}
                     [(list 'track/remove-loop {:db/id "123" :loop sample-loop})]
                     :remote)))))

(deftest test-update-loop
  (let [state (atom {:entities/by-id {"123"                {:db/id "123" :youtube/id "a12" :track/loops [(mk-ref sample-loop)]}
                                      (:db/id sample-loop) sample-loop}})
        updated-loop (assoc sample-loop :loop/label "new label")]
    (p/parser {:state state}
              `[(track/update-loop {:db/id "123" :loop ~updated-loop})])
    (is (= {:entities/by-id
            {"123"
                                  {:db/id       "123"
                                   :youtube/id  "a12"
                                   :track/loops [(mk-ref sample-loop)]}
             (:db/id sample-loop) updated-loop}}
           @state))

    (is (= [(list 'track/update-loop {:db/id "123" :youtube/id "a12" :loop updated-loop})]
           (p/parser {:state state}
                     [(list 'track/update-loop {:db/id "123" :loop updated-loop})]
                     :remote)))))

(deftest test-select-loop
  (let [state (atom {:entities/by-id {"123"                {:db/id "123" :youtube/id "a12" :track/loops [(mk-ref sample-loop)]}
                                      (:db/id sample-loop) sample-loop}})]
    (p/parser {:state state}
              `[(track/select-loop {:db/id "123" :loop ~sample-loop})])
    (is (= {:entities/by-id
            {"123"                {:db/id               "123"
                                   :youtube/id          "a12"
                                   :track/selected-loop (mk-ref sample-loop)
                                   :track/loops         [(mk-ref sample-loop)]}
             (:db/id sample-loop) sample-loop}}
           @state))))

(deftest test-toggle-visibility
  (let [state (atom {:app/visible? true})]
    (p/parser {:state state} '[(app/toggle-visibility)])
    (is (= false (:app/visible? @state)))
    (p/parser {:state state} '[(app/toggle-visibility)])
    (is (= true (:app/visible? @state)))))

;; Remote Parser Tests

(def sample-track
  {:youtube/id  "123"
   :db/id       "abc"
   :track/loops [(mk-loop 5 200 "full")
                 (mk-loop 204 205 "intro")]})

(def fake-store
  (p/map-kv-store {"123" sample-track}))

(deftest test-remote-app-current-track
  (let [remote-env {:store fake-store}]
    (is (= {:app/current-track {:youtube/id "abc" :track/loops []}}
           (-> (p/remote-parser remote-env '[(:app/current-track {:youtube/id "abc"})])
               (update :app/current-track dissoc :db/id))))

    (is (= (p/remote-parser remote-env '[(:app/current-track {:youtube/id "new"})])
           (p/remote-parser remote-env '[(:app/current-track {:youtube/id "new"})]))
        "creates and keeps the same for the same youtube id")

    (is (= {:app/current-track sample-track}
           (p/remote-parser remote-env '[({:app/current-track [:track/duration]} {:youtube/id "123"})])))))

(deftest test-remote-track-new-loop
  (let [store (p/map-kv-store {"123" sample-track})]
    (p/remote-parser {:store store} `[(track/new-loop {:db/id "abc" :youtube/id "123" :loop ~sample-loop})])
    (is (= {:youtube/id  "123",
            :db/id       "abc"
            :track/loops (-> (:track/loops sample-track)
                             (conj sample-loop))}
           (p/kv-get store "123")))

    (p/remote-parser {:store store} `[(track/new-loop {:db/id "yid" :youtube/id "12" :loop ~sample-loop})])
    (is (= {:youtube/id  "12",
            :db/id       "yid"
            :track/loops [sample-loop]}
           (p/kv-get store "12")))))

(deftest test-remote-track-remove-loop
  (let [store (p/map-kv-store {"123" sample-track})
        loop (get-in sample-track [:track/loops 0])]
    (p/remote-parser {:store store} `[(track/remove-loop {:youtube/id "123" :loop ~loop})])
    (is (= {:youtube/id  "123"
            :db/id       "abc"
            :track/loops [(get-in sample-track [:track/loops 1])]}
           (p/kv-get store "123")))))

(deftest test-remote-track-update-loop
  (let [store (p/map-kv-store {"123" sample-track})
        loop (get-in sample-track [:track/loops 0])
        loop (update loop :loop/start inc)]
    (println "updating loop" loop)
    (p/remote-parser {:store store} `[(track/update-loop {:youtube/id "123" :loop ~loop})])
    (is (= 6
           (get-in (p/kv-get store "123") [:track/loops 0 :loop/start])))))
