(ns youtube-looper.views-cards
  (:require [cljs.test :refer-macros [is async]]
            [goog.dom :as gdom]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom])
  (:require-macros [devcards.core :as dc :refer [defcard deftest]]))

(defprotocol KVSyncStore
  (kv-get [this key])
  (kv-set! [this key value]))

(defrecord MapKVStore [data]
  KVSyncStore
  (kv-get [_ key] (get @data key))
  (kv-set! [_ key value] (swap! data assoc key value)))

(defn map-kv-store
  ([] (map-kv-store {}))
  ([data] (MapKVStore. (atom data))))

(defui Hello
  Object
  (render [this]
    (dom/p nil (-> this om/props :text))))

(def hello (om/factory Hello))

(defui LoopRow
  static om/IQuery
  (query [this]
    [:loop/label :loop/start :loop/finish])

  static om/Ident
  (ident [this {:keys [:db/id]}]
    [:loop/by-id id])

  Object
  (render [this]
    (let [{:keys [:loop/label :loop/start :loop/finish]} (-> this om/props)]
      (dom/div nil
        (dom/div nil label)
        (dom/div nil start)
        (dom/div nil finish)))))

(def loop-row (om/factory LoopRow))

(defui LoopManager
  static om/IQuery
  (query [this]
    '[:video/title :video/loops])
  
  static om/Ident
  (ident [this {:keys [:video/youtube-id]}]
    [:videos/by-youtube-id youtube-id])

  Object
  (render [this]
    (let [{:keys [:video/loops] :as props} (om/props this)]
      (println "render loops" props)
      (apply dom/div nil (map loop-row loops)))))

(def loop-manager (om/factory LoopManager))

(defui LoopPage
  static om/IQuery
  (query [this] [{:videos/current (om/get-query LoopManager)}])
  
  Object
  (render [this]
    (let [{:keys [videos/current] :as props} (om/props this)]
      (println "manager props" props)
      (loop-manager current))))

(def loop-page (om/factory LoopPage))

(defmulti read om/dispatch)

(defmethod read :default [{:keys [state]} key params]
  (let [st @state]
    (if-let [[_ v] (find st key)]
      {:value v}
      {:value :not-found})))

(defmethod read :videos/current [{:keys [state]} k _]
  (let [st @state]
    (if (contains? st k)
      {:value (->> (get st k)
                   (get-in st))}
      {:remote true})))

(defmethod read :video/loops [{:keys [query store]} _ {:keys [video]}]
  {:value (or (kv-get store video) :not-found)})

(defmulti mutate om/dispatch)

(defmethod mutate 'video/new-loop [{:keys [store]} _ {:keys [video] :as loop}]
  (let [loop (dissoc loop :video)
        loops (or (kv-get store video) [])]
    {:value {:keys []}
     :action #(kv-set! store video (conj loops loop))}))

(def parser (om/parser {:read read :mutate mutate}))

(defmulti remote-read om/dispatch)

(defmethod remote-read :videos/current [{:keys [store current-video] :as env} _ _]
  {:value (kv-get store (current-video))})

(def remote-parser (om/parser {:read remote-read}))

(def init-data {})

(def fake-store
  (map-kv-store {"123" {:video/youtube-id "123"
                        :video/title      "sample"
                        :video/loops      [{:loop/label "full" :loop/start 5 :loop/finish 200}
                                           {:loop/label "intro" :loop/start 204 :loop/finish 205}]}}))

(def reconciler
  (om/reconciler
    {:state  init-data
     :parser parser
     :send   (fn [query cb]
               (println "REMOTE" query)
               (cb (remote-parser {:current-video #(str "123")
                                   :store         fake-store}
                                  (:remote query))))}))

(defcard loop-row-sample
  (loop-row {:loop/start 123
             :loop/finish 128
             :loop/label "Sample"
             :loop/id 1}))

(defcard basic-nested-component
  "Test that component nesting works"
  (let [reconciler_ (om/reconciler
                     {:state  {:videos/by-youtube-id {"123" {:video/title "sample"}}}
                      :parser parser
                      :send   (fn [query cb]
                                (println "REMOTE" query)
                                (println "resp" (remote-parser {} (:remote query)))
                                (cb (remote-parser {} (:remote query))))})]
    (om/mock-root reconciler LoopPage)))

(deftest test-indexer
  "Test indexer"
  (let [idxr (get-in reconciler [:config :indexer])]
    (is (not (nil? idxr)) "Indexer is not nil in the reconciler")
    (is (not (nil? @idxr)) "Indexer is IDeref")))

(deftest test-read-video-loops
  "Reads the stored loops"
  (let [loops [{:label "Hello"}]
        env {:state {} :store (map-kv-store {"1234" loops})}]
    (is (= (parser env '[(:video/loops {:video "1234"})]) {:video/loops loops}))
    (is (= (parser env '[(:video/loops {:video "abc"})]) {:video/loops :not-found}))))

(deftest test-parser-mutate-new-loop
  (let [store (map-kv-store)
        env {:state {} :store store}]
    (parser env '[(video/new-loop {:label "hello" :video "abc"})])
    (is (= [{:label "hello"}] (kv-get store "abc")))))
