(ns youtube-looper.next.ui
  (:require [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [youtube-looper.next.parser :as p]))

(defn pd [f]
  (fn [e]
    (.preventDefault e)
    (f)))

(defn call-computed [c name & args]
  (when-let [f (om/get-computed c name)]
    (apply f args)))

(defui LoopRow
  static om/IQuery
  (query [this]
         [:loop/label :loop/start :loop/finish])

  Object
  (render [this]
          (let [{:keys [:loop/label :loop/start :loop/finish] :as loop} (-> this om/props)]
            (dom/div nil
              (dom/div #js {:onClick #(if-let [label (js/prompt "New Label")]
                                       (call-computed this :update-label label))}
                (if label
                  label
                  (dom/i nil "No Label")))
              (dom/div nil start)
              (dom/div nil finish)
              (dom/a #js {:href "#" :onClick (pd #(call-computed this :on-delete))} "Delete")))))

(def loop-row (om/factory LoopRow))

(defn input [{:keys [value onChange]}]
  (dom/input
    #js {:value    (or value "")
         :onChange #(onChange (.. % -target -value))}))

(defn numeric-input [{:keys [value onChange] :as props}]
  (js/React.createElement "input"
                          #js {:value    (or value "")
                               :onChange #(let [value (.. % -target -value)]
                                           (cond
                                             (re-find #"^\d+(\.\d+)?$" value) (onChange (js/parseFloat value))
                                             (= "" value) (onChange "")))}))

(defn state-input [c {:keys [name] :as options}]
  (let [value (om/get-state c name)
        comp (get options :comp input)]
    (comp {:value    value
           :onChange #(om/update-state! c merge {name %})})))

(defn valid-loop? [{:keys [loop/start loop/finish] :as loop}]
  (and (number? start) (number? finish)
       (< start finish)))

(defui NewLoopForm
  Object
  (render [this]
          (let [{:keys [on-submit]} (om/props this)]
            (dom/div nil
              (state-input this {:name :loop/start :comp numeric-input})
              (state-input this {:name :loop/finish :comp numeric-input})
              (dom/button #js {:onClick
                               #(when (valid-loop? (om/get-state this))
                                 (on-submit (om/get-state this))
                                 (om/set-state! this {:loop/start "" :loop/finish ""}))}
                          "Add Loop")))))

(def new-loop-form (om/factory NewLoopForm))

(defn create-loop [c loop]
  (let [id (-> c om/props :youtube/id)]
    (om/transact! c `[(track/new-loop {:loop ~loop :youtube/id ~id})])))

(defn delete-loop [c loop]
  (let [id (-> c om/props :youtube/id)]
    (om/transact! c `[(track/remove-loop {:loop ~loop :youtube/id ~id})])))

(defn update-label [c loop label]
  (let [id (-> c om/props :youtube/id)
        new-loop (assoc loop :loop/label label)]
    (om/transact! c `[(track/remove-loop {:loop ~loop :youtube/id ~id})
                      (track/new-loop {:loop ~new-loop :youtube/id ~id})])
    #_ (om/transact! c `[(track/remove-loop {:loop ~loop :youtube/id ~id})])))

(defui LoopManager
  static om/IQuery
  (query [this]
         [:youtube/id
          {:track/loops (om/get-query LoopRow)}])

  static om/Ident
  (ident [this {:keys [youtube/id]}]
         [:tracks/by-youtube-id id])

  Object
  (render [this]
          (let [{:keys [track/loops]} (om/props this)]
            (dom/div nil
              (apply dom/div nil (->> (map #(om/computed % {:on-delete (partial delete-loop this %)
                                                            :update-label (partial update-label this %)}) loops)
                                      (map loop-row)))
              (new-loop-form {:on-submit #(create-loop this %)})))))

(def loop-manager (om/factory LoopManager))

(defui LoopPage
  static om/IQuery
  (query [this] [{:app/current-track (om/get-query LoopManager)}])

  Object
  (render [this]
          (let [{:keys [app/current-track] :as props} (om/props this)]
            (loop-manager current-track))))

(def loop-page (om/factory LoopPage))
