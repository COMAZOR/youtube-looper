(ns youtube-looper.next.ui
  (:require [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [wilkerdev.util.dom :as wd]
            [goog.object :as gobj]
            [youtube-looper.next.parser :as p]
            [youtube-looper.next.styles :as s :refer [css]]))

; Helpers

(defn pd [f]
  (fn [e]
    (.preventDefault e)
    (f)))

(defn call-computed [c name & args]
  (when-let [f (om/get-computed c name)]
    (apply f args)))

(defn render-subtree-into-container [parent c node]
  (js/ReactDOM.unstable_renderSubtreeIntoContainer parent c node))

; Helper Components

(defui Portal
  Object
  (componentDidMount [this]
                     (let [props (om/props this)
                           node (doto (wd/create-element! "div")
                                  (wd/set-style! (:style props)))]
                       (cond
                         (:append-to props) (wd/append-to! node (wd/$ (:append-to props)))
                         (:insert-after props) (wd/insert-after! node (wd/$ (:insert-after props))))

                       (gobj/set this "node" node)
                       (render-subtree-into-container this (apply dom/div nil (om/children this)) node)))

  (componentWillUnmount [this]
                        (let [node (gobj/get this "node")]
                          (js/ReactDOM.unmountComponentAtNode node)
                          (wd/remove-node! node)))

  (render [this] (js/React.DOM.noscript)))

(def portal (om/factory Portal))

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

; Youtube Components

(defn youtube-progress-bar [{:keys [color offset scale]}]
  (dom/div #js {:style (css s/youtube-progress
                            {:background (or color "rgba(6, 255, 0, 0.35)")
                             :left       (str offset "%")
                             :transform  (str "scaleX(" scale ")")})}))

(defui TrackLoopOverlay
  static om/Ident
  (ident [this {:keys [db/id]}]
         [:entities/by-id id])
  
  static om/IQuery
  (query [this] [:track/duration {:track/loop [:loop/start :loop/finish]}])
  
  Object
  (render [this]
          (let [{:keys [track/duration] {:keys [loop/start loop/finish]} :track/loop} (om/props this)
                start-pct (-> (/ start duration) (* 100))
                size-pct (/ (- finish start) duration)]
            (youtube-progress-bar {:offset start-pct
                                   :scale size-pct}))))

(def track-loop-overlay (om/factory TrackLoopOverlay {:keyfn :db/id}))

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
              (dom/div nil finish)
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
              (state-input this {:name :loop/start :comp numeric-input})
              (state-input this {:name :loop/finish :comp numeric-input})
              (dom/button #js {:onClick
                               #(when (valid-loop? (om/get-state this))
                                 (on-submit (assoc (om/get-state this) :db/id (random-uuid)))
                                 (om/set-state! this {:loop/start "" :loop/finish ""}))}
                          "Add Loop")))))

(def new-loop-form (om/factory NewLoopForm))

(defn create-loop [c loop]
  (let [id (-> c om/props :db/id)]
    (om/transact! c `[(track/new-loop {:loop ~loop :db/id ~id})])))

(defn delete-loop [c loop]
  (let [id (-> c om/props :db/id)]
    (om/transact! c `[(track/remove-loop {:loop ~loop :db/id ~id})])))

(defn update-label [c loop label]
  (let [id (-> c om/props :db/id)
        new-loop (assoc loop :loop/label label)]
    (om/transact! c `[(track/remove-loop {:loop ~loop :db/id ~id})
                      (track/new-loop {:loop ~new-loop :db/id ~id})])))

(defui LoopManager
  static om/IQuery
  (query [this] [{:track/loops (om/get-query LoopRow)}])

  static om/Ident
  (ident [this {:keys [db/id]}] [:entities/by-id id])

  Object
  (render [this]
          (let [{:keys [track/loops]} (om/props this)]
            (dom/div nil
              (apply dom/div nil (->> (map #(om/computed % {:on-delete (partial delete-loop this %)
                                                            :update-label (partial update-label this %)}) loops)
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
  (query [this] [{:app/current-track (om/get-query LoopManager)}])

  Object
  (render [this]
          (let [{:keys [app/current-track] :as props} (om/props this)]
            (println "rendering page" props)
            (dom/div nil
              (portal {:append-to ".ytp-progress-list"}
                (track-loop-overlay {:track/duration 100 :track/loop {:loop/start 10 :loop/finish 20}}))
              (portal {:insert-after ".ytp-settings-button"
                       :style {:display "inline-block"
                               :verticalAlign "top"}}
                (dom/button #js {:className "ytp-button"
                                 :title "Show Loops"
                                 :style (css s/youtube-action-button)} "AB"))
              (loop-manager current-track)))))

(def loop-page (om/factory LoopPage))
