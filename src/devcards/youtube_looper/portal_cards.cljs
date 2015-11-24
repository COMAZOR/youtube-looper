(ns youtube-looper.portal-cards
  (:require [cljs.test :refer-macros [is async]]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [goog.object :as gobj]
            [wilkerdev.util.dom :as wd])
  (:require-macros [devcards.core :as dc :refer [defcard deftest dom-node]]))

(defn render-subtree-into-container [parent c node]
  (js/ReactDOM.unstable_renderSubtreeIntoContainer parent c node))

(defn create-portal-node [props]
  (let [node (doto (wd/create-element! "div")
               (wd/set-style! (:style props)))]
    (cond
      (:append-to props) (wd/append-to! node (wd/$ (:append-to props)))
      (:insert-after props) (wd/insert-after! node (wd/$ (:insert-after props))))

    node))

(defn portal-render-children [this]
  (apply dom/div nil (om/children this)))

(defui Portal
  Object
  (componentDidMount [this]
                     (let [props (om/props this)
                           node (create-portal-node props)]
                       (gobj/set this "node" node)
                       (render-subtree-into-container this (portal-render-children this) node)))

  (componentWillUnmount [this]
                        (let [node (gobj/get this "node")]
                          (js/ReactDOM.unmountComponentAtNode node)
                          (wd/remove-node! node)))

  (componentWillReceiveProps [this props]
                             (let [node (gobj/get this "node")]

                               (render-subtree-into-container this (portal-render-children this) node)))

  (render [this] (js/React.DOM.noscript)))

(def portal (om/factory Portal))

(defui App
  static om/IQuery
  (query [this] [:counter])

  Object
  (render [this]
          (let [{:keys [counter]} (om/props this)]
            (println "render" counter)
            (dom/div nil
              "App - " counter
              (dom/br nil)
              (dom/button #js {:onClick #(om/transact! this '[(inc-counter)])} "Increment")
              #_ (portal {:append-to ".external-container"}
                   "Portal - " counter)))))

(defn read [{:keys [state]} _ _] {:value (get @state :counter)})

(defn mutate [{:keys [state]} _ _]
  {:value  {:keys [:counter]}
   :action #(swap! state update :counter inc)})

(def reconciler
  (om/reconciler
    {:state  {:counter 0}
     :parser (om/parser {:read read :mutate mutate})}))

(defcard external-ui
  (dom-node (fn [_ node]
              (wd/set-html! node "")
              (doto (wd/create-element! "div")
                (wd/set-html! "
                  <div class=\"external-container\">
                  </div>
                ")
                (wd/append-to! node)))))

(defcard loop-page-card
  "Display the loop manager dialog"
  (om/mock-root reconciler App))
