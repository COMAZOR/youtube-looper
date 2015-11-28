(ns youtube-looper.parser2-cards
  (:require [cljs.test :refer-macros [is async testing]]
            [om.next :as om :refer-macros [defui]]
            [youtube-looper.next.ui :as ui]
            [youtube-looper.next.parser2 :as p])
  (:require-macros [devcards.core :as dc :refer [defcard deftest]]))

(defn mk-loop
  ([start finish] (mk-loop start finish ""))
  ([start finish label]
   {:db/id (random-uuid) :loop/start start :loop/finish finish :loop/label label}))

(def sample-loop (mk-loop 10 20))

(deftest test-read-app
  (is (= {:app/visible?          true}
         (p/parser {:state (atom {:app/visible?          true
                                  :app/current-track [:db/id nil]
                                  :youtube/current-video "123"})
                    :shared {:current-position #(-> 0)
                             :current-duration #(-> 40)}}
           (om/get-query ui/LoopPage))))
  
  (is (= {}
         (p/parser {:state (atom {:app/visible?          true
                                  :app/current-track [:db/id nil]
                                  :youtube/current-video "123"
                                  :db/id {nil {:db/id nil}}})
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

  (is (= {}
         (p/parser {:state (atom {})}
           [{:app/current-track [{:track/loops [:start :finish]}]}]
           :remote))))

(deftest test-new-loop
  (is (= ['(track/save
             {:db/id       10,
              :youtube/id  "123",
              :track/loops [{:db/id 4, :loop/start 5, :loop/finish 20}]})]
         (p/parser {:state (atom {:app/current-track [:db/id 10]
                                  :db/id {10 {:db/id 10
                                              :youtube/id "123"
                                              :track/loops []}}})
                    :ref [:db/id 10]}
           [(list 'track/new-loop {:db/id 4 :loop/start 5 :loop/finish 20})]
           :remote))))

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
           @state))
    #_ (is (= [(list 'track/remove-loop {:db/id "123" :youtube/id "a12" :loop sample-loop})]
           (p/parser {:state state}
                     [(list 'track/remove-loop {:db/id "123" :loop sample-loop})]
                     :remote)))))

(deftest test-read-app-remote)
