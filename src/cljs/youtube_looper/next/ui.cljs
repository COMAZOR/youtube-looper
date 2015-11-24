(ns youtube-looper.next.ui
  (:require [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [goog.object :as gobj]
            [goog.dom :as gdom]
            [goog.style :as style]
            [youtube-looper.next.styles :as s :refer [css]]))

; Helpers

(defn pd [f]
  (fn [e]
    (.preventDefault e)
    (f)))

(defn call-computed [c name & args]
  (when-let [f (om/get-computed c name)]
    (apply f args)))

; Helper Components

(defn render-subtree-into-container [parent c node]
  (js/ReactDOM.unstable_renderSubtreeIntoContainer parent c node))

(defn $ [s] (.querySelector js/document s))

(defn create-portal-node [props]
  (let [node (doto (gdom/createElement "div")
               (style/setStyle (clj->js (:style props))))]
    (cond
      (:append-to props) (gdom/append ($ (:append-to props)) node)
      (:insert-after props) (gdom/insertSiblingAfter node ($ (:insert-after props))))
    node))

(defn portal-render-children [children]
  (apply dom/div nil children))

(defui Portal
  Object
  (componentDidMount [this]
                     (let [props (om/props this)
                           node (create-portal-node props)]
                       (gobj/set this "node" node)
                       (render-subtree-into-container this (portal-render-children (:children props)) node)))

  (componentWillUnmount [this]
                        (when-let [node (gobj/get this "node")]
                          (js/ReactDOM.unmountComponentAtNode node)
                          (gdom/removeNode node)))

  (componentWillReceiveProps [this props]
                             (let [node (gobj/get this "node")]
                               (render-subtree-into-container this (portal-render-children (:children props)) node)))

  (render [this] (js/React.DOM.noscript)))

(def portal-factory (om/factory Portal))

(defn portal [props & children]
  (portal-factory (assoc props :children children)))

(defn input [{:keys [value onChange] :as props}]
  (dom/input
    (clj->js (merge props
                    {:value    (or value "")
                     :onChange #(onChange (.. % -target -value))}))))

(defn numeric-input [{:keys [value onChange] :as props}]
  (js/React.createElement "input"
                          (clj->js (merge props
                                          {:value    (or value "")
                                           :onChange #(let [value (.. % -target -value)]
                                                       (cond
                                                         (re-find #"^\d+(\.\d+)?$" value) (onChange (js/parseFloat value))
                                                         (= "" value) (onChange "")))}))))

(defn state-input [c {:keys [name style] :as options}]
  (let [value (om/get-state c name)
        comp (get options :comp input)]
    (comp {:value    value
           :style style
           :onChange #(om/update-state! c merge {name %})})))

; Youtube Components

(defn youtube-progress-bar [{:keys [color offset scale]}]
  (dom/div #js {:style (css s/youtube-progress
                            {:background (or color "rgba(6, 255, 0, 0.35)")
                             :left       (str (* offset 100) "%")
                             :transform  (str "scaleX(" scale ")")})}))

(defn track-loop-overlay [{:keys                                     [track/duration]
                           {:keys [loop/start loop/finish] :as loop} :track/selected-loop}]
  (if (and loop duration)
    (let [start-pct (-> (/ start duration))
          size-pct (/ (- finish start) duration)]
      (youtube-progress-bar {:offset start-pct
                             :scale  size-pct}))
    (js/React.DOM.noscript)))

; Looper Components

(defui LoopRow
  static om/Ident
  (ident [this {:keys [db/id]}]
         [:entities/by-id id])

  static om/IQuery
  (query [this]
         [:loop/label :loop/start :loop/finish])

  Object
  (render [this]
          (let [{:keys [:loop/label :loop/start :loop/finish]} (-> this om/props)]
            (dom/div #js {:style (css s/flex-row (s/justify-content "space-between")
                                      {:width 300})}
              (dom/div #js {:onClick #(if-let [label (js/prompt "New Label")]
                                       (call-computed this :update-label label))}
                (if label
                  label
                  (dom/i nil "No Label")))
              (dom/div nil start)
              (dom/a #js {:href "#" :onClick (pd #(call-computed this :inc-start))} "Inc start")
              (dom/div nil finish)
              (dom/a #js {:href "#" :onClick (pd #(call-computed this :on-select))} "Select")
              (dom/a #js {:href "#" :onClick (pd #(call-computed this :on-delete))} "Delete")))))

(def loop-row (om/factory LoopRow {:keyfn :db/id}))

(defn valid-loop? [{:keys [loop/start loop/finish] :as loop}]
  (and (number? start) (number? finish)
       (< start finish)))

(defui NewLoopForm
  Object
  (render [this]
          (let [{:keys [on-submit]} (om/props this)]
            (dom/div nil
              (state-input this {:name :loop/start :comp numeric-input :style (css s/time-input)})
              (state-input this {:name :loop/finish :comp numeric-input :style (css s/time-input)})
              (dom/button #js {:onClick
                               #(when (valid-loop? (om/get-state this))
                                 (on-submit (assoc (om/get-state this) :db/id (random-uuid)))
                                 (om/set-state! this {:loop/start "" :loop/finish ""}))}
                          "Add Loop")))))

(def new-loop-form (om/factory NewLoopForm))

(defn create-loop [c loop]
  (let [props (-> c om/props)
        data (assoc props :loop loop)]
    (om/transact! c `[(track/new-loop ~data)])))

(defn delete-loop [c loop]
  (let [id (-> c om/props :db/id)]
    (om/transact! c `[(track/remove-loop {:loop ~loop :db/id ~id}) :app/current-track])))

(defn update-label [c loop label]
  (let [id (-> c om/props :db/id)
        new-loop (assoc loop :loop/label label)]
    (om/transact! c `[(track/update-loop {:loop ~new-loop :db/id ~id})])))

(defn inc-start [c loop]
  (let [id (-> c om/props :db/id)
        new-loop (update loop :loop/start inc)]
    (om/transact! c `[(track/update-loop {:loop ~new-loop :db/id ~id}) :app/current-track])))

(defn select-loop [c loop]
  (let [id (-> c om/props :db/id)]
    (om/transact! c `[(track/select-loop {:loop ~loop :db/id ~id}) :app/current-track])))

(defui LoopManager
  static om/IQuery
  (query [this] [{:track/loops (om/get-query LoopRow)} :track/duration {:track/selected-loop [:loop/start :loop/finish]}])

  static om/Ident
  (ident [this {:keys [db/id]}] [:entities/by-id id])

  Object
  (render [this]
          (let [{:keys [track/loops] :as track} (om/props this)]
            (dom/div #js {:style (css s/popup-container)}
              (apply dom/div nil (->> (map #(om/computed % {:on-delete    (partial delete-loop this %)
                                                            :on-select    (partial select-loop this %)
                                                            :update-label (partial update-label this %)
                                                            :inc-start    (partial inc-start this %)}) loops)
                                      (map loop-row)))
              (new-loop-form {:on-submit #(create-loop this %)})))))

(def loop-manager (om/factory LoopManager {:keyfn :db/id}))

(defui LoopDisplay
  Object
  (render [this]
          (portal {:append-to ".ytp-progress-list"}
            (track-loop-overlay {:track/duration 100 :track/loop {:loop/start 10 :loop/finish 20}}))))

(defui LoopPage
  static om/IQuery
  (query [this] [:app/visible? {:app/current-track (om/get-query LoopManager)}])

  Object
  (render [this]
          (let [{:keys [app/current-track app/visible?] :as props} (om/props this)]
            (dom/div nil
              (portal {:append-to ".ytp-progress-list"}
                (track-loop-overlay current-track))
              (portal {:insert-after ".ytp-settings-button"
                       :style        {:display       "inline-block"
                                      :verticalAlign "top"}}
                (dom/button #js {:className "ytp-button"
                                 :title     "Show Loops"
                                 :style     (css s/youtube-action-button)
                                 :onClick   #(om/transact! this '[(app/toggle-visibility)])} "AB"))
              (if visible? (loop-manager current-track))))))

(def loop-page (om/factory LoopPage))
