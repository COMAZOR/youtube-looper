(ns youtube-looper.next.ui
  (:require [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [goog.object :as gobj]
            [goog.dom :as gdom]
            [goog.style :as style]
            [youtube-looper.util :as u]
            [youtube-looper.next.styles :as s :refer [css]]))

; Helpers

(defn pd [f]
  (fn [e]
    (.preventDefault e)
    (.stopPropagation e)
    (f)))

(defn call-computed [c name & args]
  (when-let [f (om/get-computed c name)]
    (apply f args)))

; Helper Components

(defn icon
  ([icon] (dom/i #js {:className (str "fa fa-" icon)}))
  ([icon style] (dom/i #js {:className (str "fa fa-" icon) :style (css style)})))

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

(defn loop-time-updater [c prop]
  (let [value (get (om/props c) prop)
        {:keys [selected]} (om/get-computed c)]
    (dom/div #js {:style (css s/flex-row-center {:padding "0 5px"})}
      (dom/a #js {:href "#" :onClick (pd #(om/transact! c `[(entity/set {~prop ~(dec value)}) :app/current-track]))}
        (icon "chevron-circle-down" s/fs-15))
      (dom/div #js {:style (css {:padding "0 7px"})} (u/seconds->time value))
      (dom/a #js {:href "#" :onClick (pd #(om/transact! c `[(entity/set {~prop ~(inc value)}) :app/current-track]))}
        (icon "chevron-circle-up" s/fs-15)))))

(defui LoopRow
  static om/Ident
  (ident [this {:keys [db/id]}] [:db/id id])

  static om/IQuery
  (query [this] [:db/id :loop/label :loop/start :loop/finish])

  Object
  (render [this]
          (let [{:keys [loop/label]} (-> this om/props)
                {:keys [selected]} (om/get-computed this)]
            (dom/div #js {:style (css s/loop-row (if selected s/selected-loop-row))}
              (if selected
                (dom/a #js {:href "#" :onClick (pd #(call-computed this :on-clean-selection))} 
                  (icon "pause-circle" s/fs-23))
                (dom/a #js {:href "#" :onClick (pd #(call-computed this :on-select))}
                  (icon "play-circle" s/fs-23)))
              (dom/div #js {:style (css {:padding "0 10px"
                                         :width 168})
                            :onClick #(if-let [label (js/prompt "New Label")]
                                       (om/transact! this `[(entity/set {:loop/label ~label}) :app/current-track]))}
                (if label
                  label
                  (dom/i nil "No Label")))
              (dom/div #js {:style (css s/flex-row-center (s/justify-content "space-between"))}
                (loop-time-updater this :loop/start)
                (dom/div nil "/")
                (loop-time-updater this :loop/finish)
                
                (dom/a #js {:href "#" :onClick (pd #(call-computed this :on-delete))
                            :style (css {:paddingLeft 10})}
                  (icon "trash" s/fs-18)))))))

(def loop-row (om/factory LoopRow {:keyfn :db/id}))

(defn valid-loop? [{:keys [loop/start loop/finish] :as loop}]
  (and (number? start) (number? finish)
       (< start finish)))

(defui NewLoopForm
  static om/Ident
  (ident [this {:keys [db/id]}] [:db/id id])
  
  static om/IQuery
  (query [this] '[:db/id :loop/start :loop/finish :video/current-time])
  
  Object
  (componentDidUpdate [this props state]
                             (let [loop (dissoc (om/props this) :video/current-time)]
                               (when (valid-loop? loop)
                                 (js/setTimeout
                                   (fn []
                                     (om/transact! this '[(entity/set {:loop/start nil :loop/finish nil})])
                                     (call-computed this :on-submit (assoc loop :db/id (random-uuid))))
                                   10))))
  
  (render [this]
          (let [{:keys [loop/start loop/finish video/current-time] :as loop} (om/props this)]
            (dom/div #js {:style (css s/flex-row (s/justify-content "space-between"))}
              (dom/div nil "Time - " current-time)
              (dom/div nil (if start (str "Start at " (u/seconds->time start))))
              
              (if-not start
                (dom/button #js {:onClick #(om/transact! this '[(loop/set-current-video-time {:at :loop/start})])}
                  "Start new loop")

                (if (< current-time start)
                  (dom/button #js {:onClick #(om/transact! this '[(loop/set-current-video-time {:at :loop/start})])}
                    "Reset initial time")
                  
                  (dom/button #js {:onClick #(om/transact! this '[(loop/set-current-video-time {:at :loop/finish})])}
                    "Create loop")))))))

(def new-loop-form (om/factory NewLoopForm))

(defui TemporaryLoop
  static om/Ident
  (ident [this {:keys [db/id]}] [:db/id id])
  
  static om/IQuery
  (query [this] '[:db/id :loop/start :video/current-time])
  
  Object
  (render [this]
          (let [{:keys [loop/start video/current-time] :as loop} (om/props this)]
            (dom/div nil
              start " - " current-time))))

(def temporary-loop (om/factory TemporaryLoop))

(defn create-loop [c loop]
  (let [props (-> c om/props)]
    (om/transact! c `[(track/new-loop ~loop) :app/current-track])))

(defn delete-loop [c loop]
  (let [id (-> c om/props :db/id)]
    (om/transact! c `[(track/remove-loop ~loop) :app/current-track])))

(defn select-loop [c loop]
  (let [id (-> c om/props :db/id)]
    (om/transact! c `[(track/select-loop ~loop) :app/current-track])))

(defn dbg [value] (println "debug" value) value)

(defui LoopManager
  static om/IQuery
  (query [this] [:db/id
                 :track/duration
                 {:track/new-loop (om/get-query NewLoopForm)}
                 {:track/new-loop2 (om/get-query TemporaryLoop)}
                 {:track/loops (om/get-query LoopRow)}
                 {:track/selected-loop [:db/id :loop/start :loop/finish]}])

  static om/Ident
  (ident [this {:keys [db/id]}] [:db/id id])

  Object
  (render [this]
          (let [{:keys [track/loops track/new-loop track/new-loop2] :as track} (om/props this)]
            (dom/div #js {:style (css s/popup-container s/body-text)}
              (dom/div #js {:style (css {:padding 6})}
                (dom/div #js {:style (css s/header-text)} "My Loops (" (count loops) ")")

                (new-loop-form (om/computed new-loop {:on-submit #(create-loop this %)})))

              (dom/hr #js {:style (css s/hr)})
              
              (temporary-loop new-loop2)
              (apply dom/div #js {:style (css {:padding 6})}
                (->> (map #(om/computed % {:on-delete          (partial delete-loop this %)
                                           :on-select          (partial select-loop this %)
                                           :on-clean-selection (partial select-loop this nil)
                                           :selected           (= (get-in track [:track/selected-loop :db/id]) (:db/id %))})
                          loops)
                     (map loop-row)))))))

(def loop-manager (om/factory LoopManager {:keyfn :db/id}))

(defui LoopPage
  static om/IQuery
  (query [this] [:app/visible?
                 {:app/current-track (om/get-query LoopManager)}])

  Object
  (render [this]
          (let [{:keys [app/current-track app/visible? app/new-loop] :as props} (om/props this)]
            (dom/div nil
              (portal {:append-to ".ytp-progress-list"}
                (track-loop-overlay current-track))
              (portal {:insert-after ".ytp-settings-button"
                       :style        {:display       "inline-block"
                                      :verticalAlign "top"}}
                (dom/button #js {:className "ytp-button"
                                 :title     "Show Loops"
                                 :style     (css s/youtube-action-button)
                                 :onClick   #(om/transact! this `[(entity/set {:app/visible? ~(not visible?)}) :app/current-track])} "AB"))
              (if visible? (loop-manager current-track))))))

(def loop-page (om/factory LoopPage))
