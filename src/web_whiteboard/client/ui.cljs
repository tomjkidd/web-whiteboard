(ns web-whiteboard.client.ui
  "Responsible for generating the user interface for the client application"
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [carafe.dom :as dom]
            [goog.events :as events]
            [cljs.core.async :as async
             :refer [>! <! put! chan alts!]]
            [web-whiteboard.client.handlers.websocket :as hws])
  (:import [goog.events EventType KeyHandler KeyCodes]))

(def keyboard-mappings
  {KeyCodes.C {:doc "Clear the canvas"
               :key "C"
               :fn (fn [args]
                     (dom/remove-children (dom/by-id "canvas")))
               :args []}})

(defn- get-ui
  "Convenience for reading ui state

  state is expected in it's dereferenced form"
  [state keys]
  (get-in state (concat [:client :ui] keys)))

(defn- assoc-ui
  "Convenience for updating ui state"
  [state keys new-val]
  (assoc-in state (concat [:client :ui] keys) new-val))

(defn event->point-data
  "Turn an event with clientX, clientY into point data"
  [app-state event]
  (let [s @app-state
        r (get-ui s [:pen :radius])
        c (get-ui s [:pen :color])]
    {:shape :point
     :id (random-uuid)
     :x (.-clientX event)
     :y (.-clientY event)
     :radius r
     :color c}))

(defn pen-event-handler
  "Handle pen event, possibly updating the dom"
  ([app-state event]
   (pen-event-handler app-state event false))
  ([app-state event override]
   (.log js/console (str "pen-event-handler:" event (.-type event) override))
   (let [s @app-state
         canvas-id (get-ui s [:canvas :id])
         event-handler (get-ui s [:drawing-algorithm :event-handler])]
     (when (or override
               (get-ui s [:is-mouse-down?]))
       (event-handler app-state event)))))

(defn change-pen-config
  "Update's the app-state when the pen config changes

  Also updates the pen config ui"
  [app-state key]
  (fn [event]
    (let [s @app-state
          pen-example-id (get-ui s [:pen :example-id])
          target (.-target event)
          new-value (.-value target)]
      (swap! app-state (fn [prev]
                         (assoc-ui prev [:pen key] new-value)))
      (let [new-pen (get-ui @app-state [:pen])
            pen-example (dom/by-id pen-example-id)]
        (dom/process-attrs pen-example {:r (:radius new-pen)
                                        :fill (:color new-pen)})))))

(defn create-pen-config
  "Creates the user interface for changing the drawing pen"
  [app-state]
  (let [s @app-state
        color (get-ui s [:pen :color])
        radius (get-ui s [:pen :radius])
        example-id (get-ui s [:pen :example-id])
        color-picker [:input
                      {:id "color-picker"
                       :type "color"
                       :value color
                       :onchange (change-pen-config app-state :color)}
                      []]
        size-picker [:input
                    {:id "size-picker"
                     :type "range"
                     :min 1 :max 31 :value radius :step 3
                     :onchange (change-pen-config app-state :radius)}
                     []]
        pen-example [:svg
                     {:width 100
                      :height 100
                      :style "vertical-align: middle;"}
                     [[:circle
                       {:id "pen-example"
                        :cx 50
                        :cy 50
                        :r radius
                        :fill color}]]]]
    (dom/create-element
     [:div
      {:id "pen-options"}
      [color-picker size-picker pen-example]])))

(defn create-drawing-ui
  "Create the drawing user interface"
  [app-state]
  (let [app-element (dom/by-id "app")
        s @app-state
        canvas-id (get-in s [:client :ui :canvas :id])
        svg (dom/create-element
             [:svg
              {:id canvas-id
               :width 500
               :height 500
               :onmousedown
               (fn [e]
                 (swap! app-state
                        (fn [prev]
                          (assoc-ui prev [:is-mouse-down?] true))
                        (pen-event-handler app-state e true)))
               :onmouseup
               (fn [e]
                 (swap! app-state
                        (fn [prev]
                          (assoc-ui prev [:is-mouse-down?] false))
                        (pen-event-handler app-state e false)))
               :onmousemove (fn [e] (pen-event-handler app-state e))}
              ])
        pen-config (create-pen-config app-state)]
    (dom/append app-element svg)
    (dom/append app-element pen-config)))

(defn listen-to-keybindings
  "Register to listen for keybinding events"
  []
  (let [kh (KeyHandler. js/document)]
    (events/listen kh
                   KeyHandler.EventType.KEY
                   (fn [e]
                     (when-let [{:keys [fn args]} (keyboard-mappings (.-keyCode e))]
                       (apply fn args))))))

(defn ui-chan-handler
  "Handle messages to the ui"
  [app-state ui-action]
  (let [s @app-state
        draw-handler (get-in s [:client :ui :drawing-algorithm :draw-handler])
        draw-state (get-in s [:client :ui :drawing-algorithm :state])]
    (draw-handler app-state draw-state ui-action)))

(defn listen-to-ui-chan
  "Listen for messages coming from the to ui channel"
  [app-state] 
  (go
    (let [s @app-state
          ch (get-in s [:channels :ui :to])]
      (loop []
        (let [ui-action (<! ch)]
          (do
            (ui-chan-handler app-state ui-action)
            (recur)))))))

(defn create-ui
  [app-state]
  (create-drawing-ui app-state)
  (listen-to-keybindings)
  (listen-to-ui-chan app-state))
