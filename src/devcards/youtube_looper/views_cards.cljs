(ns youtube-looper.views-cards
  (:require [cljs.test :refer-macros [is async]]
            [goog.dom :as gdom]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [youtube-looper.next.parser :as p])
  (:require-macros [devcards.core :as dc :refer [defcard deftest]]))

(defn pd [f]
  (fn [e]
    (.preventDefault e)
    (f)))

(defn call-computed [c name & args]
  (if-let [f (om/get-computed c name)]
    (apply f args)))

(defui LoopRow
  static om/IQuery
  (query [this]
    [:loop/label :loop/start :loop/finish])

  Object
  (render [this]
    (let [{:keys [:loop/label :loop/start :loop/finish] :as loop} (-> this om/props)]
      (dom/div nil
        (dom/div nil label)
        (dom/div nil start)
        (dom/div nil finish)
        (dom/a #js {:href "#" :onClick (pd #(call-computed this :on-delete))} "Delete")))))

(def loop-row (om/factory LoopRow))

(defn state-input [c key]
  (let [value (om/get-state c key)]
    (dom/input #js {:value    value
                    :onChange #(let [input-value (.. % -target -value)]
                                (om/update-state! c merge {key input-value}))})))

(defui NewLoopForm
  static om/IQuery
  (query [this] [:loop/start :loop/finish])

  Object
  (render [this]
          (let [props (om/props this)]
            (dom/div nil
              (state-input this :loop/start)
              (state-input this :loop/finish)
              (dom/button #js {:onClick #(do
                                          (call-computed this :on-submit (om/get-state this))
                                          (om/set-state! this {:loop/start "" :loop/finish ""}))} "Add Loop")))))

(def new-loop-form (om/factory NewLoopForm))

(defn create-loop [c loop]
  (let [id (-> c om/props :youtube/id)]
    (om/transact! c `[(track/new-loop {:loop ~loop :youtube/id ~id})])))

(defn delete-loop [c loop]
  (let [id (-> c om/props :youtube/id)]
    (om/transact! c `[(track/remove-loop {:loop ~loop :youtube/id ~id})])))

(defui LoopManager
  static om/IQuery
  (query [this]
    [:youtube/id
     {:track/loops (om/get-query LoopRow)}
     {:track/new-loop (om/get-query NewLoopForm)}])
  
  static om/Ident
  (ident [this {:keys [youtube/id]}]
    [:tracks/by-youtube-id id])
 
  Object
  (render [this]
    (let [{:keys [youtube/id track/loops track/new-loop]} (om/props this)]
      (dom/div nil
        (apply dom/div nil (->> (map #(om/computed % {:on-delete (partial delete-loop this %)}) loops)
                                (map loop-row)))
        (new-loop-form (om/computed new-loop {:on-submit #(create-loop this %)}))))))

(def loop-manager (om/factory LoopManager))

(defui LoopPage
  static om/IQuery
  (query [this] [{:app/current-track (om/get-query LoopManager)}])
  
  Object
  (render [this]
    (let [{:keys [app/current-track] :as props} (om/props this)]
      (loop-manager current-track))))

(def loop-page (om/factory LoopPage))

(defui SampleComponent
  static om/IQuery
  (query [this]
         [:title])

  static om/Ident
  (ident [this {:keys [db/id]}]
         [:entity-by-id id])
  
  Object
  (render [this]
          (let [{:keys [title]} (om/props this)
                {:keys [other]} (om/get-computed this)]
            (dom/div nil
              title " - " other " - "
              (dom/button #js {:onClick #(om/set-state! this {:anything "value"})} "Add State")))))

(def sample-comp (om/factory SampleComponent))

(defui ContainerComponent
  static om/IQuery
  (query [this]
         [{:node (om/get-query SampleComponent)}])
  
  Object
  (render [this]
          (let [{:keys [node]} (om/props this)]
            (sample-comp (om/computed node {:other "value"})))))

(defmulti issue-parser om/dispatch)

(defmethod issue-parser :node [_ _ _]
  {:value {:title "Sample" :db/id 2}})

(def issue-reconsiler
  (om/reconciler
    {:parser (om/parser {:read issue-parser})}))

(defcard state-issue
  (om/mock-root issue-reconsiler ContainerComponent))

(def init-data {})

(def fake-store
  (p/map-kv-store {"123" {:youtube/id "123"
                          :track/new-loop {}
                          :track/loops      [{:loop/label "full" :loop/start 5 :loop/finish 200}]}}))

(def reconciler
  (om/reconciler
    {:state  init-data
     :parser p/parser
     :send   (fn [query cb]
               (println "REMOTE" query)
               (cb (p/remote-parser {:current-track #(str "123")
                                     :store         fake-store}
                                    (:remote query))))}))

(defcard loop-row-sample
  (loop-row {:loop/start 123
             :loop/finish 128
             :loop/label "Sample"
             :loop/id 1}))

(defcard loop-page-card
  "Display the loop manager dialog"
  (om/mock-root reconciler LoopPage))
