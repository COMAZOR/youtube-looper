(ns youtube-looper.parser-cards
  (:require [cljs.test :refer-macros [is async testing]]
            [clojure.walk :as walk]
            [om.next :as om :refer-macros [defui]]
            [youtube-looper.next.stores :as kv]
            [youtube-looper.next.ui :as ui]
            [youtube-looper.next.parser :as p])
  (:require-macros [devcards.core :as dc :refer [defcard deftest]]))

(defn mk-loop
  ([start finish] (mk-loop start finish ""))
  ([start finish label]
   {:db/id (random-uuid) :loop/start start :loop/finish finish :loop/label label}))

(defn app-state [data] (atom (om/tree->db ui/LoopPage data)))

(def sample-loop (mk-loop 10 20))

(deftest test-read-app
  (is (= {:app/visible? true}
         (p/parser {:state  (atom {:app/visible?          true
                                   :app/current-track     [:db/id nil]
                                   :youtube/current-video "123"})
                    :shared {:current-position #(-> 0)
                             :current-duration #(-> 40)}}
           (om/get-query ui/LoopPage))))

  (is (= (p/parser {:state  (atom {:app/visible?          true
                                   :youtube/current-video "123"})
                    :shared {:current-position #(-> 0)
                             :current-duration #(-> 40)}}
           (om/get-query ui/LoopPage))
         {:app/visible? true}))

  (is (= []
         (p/parser {:state  (atom {:app/visible?          true
                                   :app/current-track     [:db/id nil]
                                   :youtube/current-video "123"
                                   :db/id                 {nil {:db/id nil}}})
                    :shared {:current-position #(-> 0)
                             :current-duration #(-> 40)}}
           (om/get-query ui/LoopPage)
           :remote))))

(deftest test-read-app-current-track
  (is (= {}
         (p/parser {:state (atom {})}
           [:app/current-track])))

  (is (= {:app/current-track {:some "value", :track/loops [{:start 10} {:finish 20}]}}
         (p/parser {:state (atom {:app/current-track [:db/id 10]
                                  :db/id             {10 {:db/id       10
                                                          :some        "value"
                                                          :track/loops [[:db/id 11]
                                                                        [:db/id 12]]}
                                                      11 {:db/id 11
                                                          :start 10}
                                                      12 {:db/id  12
                                                          :finish 20}}})}
           [{:app/current-track [:some {:track/loops [:start :finish]}]}])))

  (is (= [[{:app/current-track [{:track/loops [:start :finish]}]}
           {:youtube/id nil}]]
         (p/parser {:state (atom {})}
           [{:app/current-track [{:track/loops [:start :finish]}]}]
           :remote))))

(defn remote-call [parser env query]
  (p/parser env query)
  (p/parser env query :remote))

(deftest test-new-loop
  (is (= ['(track/save
             {:db/id       10,
              :youtube/id  "123",
              :track/loops [{:db/id 4, :loop/start 5, :loop/finish 20}]})]
         (remote-call p/parser {:state (atom {:app/current-track [:db/id 10]
                                              :db/id             {10 {:db/id       10
                                                                      :youtube/id  "123"
                                                                      :track/loops []}}})
                                :ref   [:db/id 10]}
                      [(list 'track/new-loop {:db/id 4 :loop/start 5 :loop/finish 20})]))))

(deftest test-app-visible?
  (is (= {:app/visible? true}
         (p/parser {:state (atom {:app/visible? true})}
           [:app/visible?]))))

(deftest test-select-loop
  (let [state (atom {:db/id {"123" {:db/id               "123"
                                    :track/selected-loop {}}}})]
    (p/parser {:state state :ref [:db/id "123"]}
      '[(track/select-loop nil)])
    (is (= {:db/id {"123" {:db/id "123", :track/selected-loop nil}}}
           @state))))

(deftest test-remove-loop
  (let [sample-id (:db/id sample-loop)
        state (atom {:db/id {"123"     {:db/id "123" :youtube/id "a12" :track/loops [[:db/id sample-id]]}
                             sample-id sample-loop}})]
    (p/parser {:state state
               :ref   [:db/id "123"]}
      `[(track/remove-loop ~sample-loop)])
    (is (= {:db/id
            {"123"
             {:db/id       "123"
              :youtube/id  "a12"
              :track/loops []}}}
           @state))))

(deftest test-update-current-video
  (is (= (p/parser {:state (app-state {:youtube/current-video "123"
                                       :app/current-track     {:db/id      321
                                                               :youtube/id "123"}})}
           '[(app/change-video {:youtube/id "abc"})])
         {'app/change-video
          {:keys   [:app/current-track :youtube/current-video],
           :result {:youtube/current-video "abc"}}}))

  (is (= (p/parser {:state (app-state {:youtube/current-video "123"
                                       :app/current-track     {:db/id      321
                                                               :youtube/id "123"}})}
           '[(app/change-video {:youtube/id "123"})])
         {'app/change-video
          {:keys   [:app/current-track :youtube/current-video],
           :result {:youtube/current-video "123", :app/current-track [:db/id 321]}}})))

(defn without-ids [m]
  (walk/postwalk (fn [x] (if (map? x) (dissoc x :db/id) x)) m))

(deftest test-migration-store
  (let [main (kv/map-kv-store {"ab" :main-loop})
        secondary (kv/map-kv-store {"cd" [{:loop/start 5
                                           :loop/finish 10
                                           :loop/label "a"}
                                          {:start 1
                                           :finish 10
                                           :name "b"}]})
        mstore (p/migration-store main secondary)]
    (kv/kv-set! mstore "k" "v")
    
    (is (= (kv/kv-get mstore "123") nil))
    (is (= (kv/kv-get mstore "ab") :main-loop))
    (is (= (without-ids (kv/kv-get mstore "cd"))
           {:youtube/id "cd"
            :track/new-loop {}
            :track/loops
            [{:loop/start 5 :loop/finish 10 :loop/label "a"}
             {:loop/label "b" :loop/start 1 :loop/finish 10}]}))

    (is (= (kv/kv-get main "k") "v"))))
