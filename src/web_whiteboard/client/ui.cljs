(ns web-whiteboard.client.ui
  "Responsible for generating the user interface for the client application"
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [carafe.dom :as dom]
            [goog.events :as events]
            [cljs.core.async :as async
             :refer [>! <! put! chan alts!]]
            [web-whiteboard.client.handlers.websocket :as hws])
  (:import [goog.events EventType KeyHandler KeyCodes]))

(def margin
  "TODO: Get this from the dom"
  8)

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

(defn create-dot
  "Create a circle svg element"
  [app-state {:keys [id cx cy r fill]}]
  (dom/create-element [:circle
                       {:id id
                        :cx (- cx margin)
                        :cy (- cy margin)
                        :r r
                        :fill fill}
                       []]))

(defn event->circle-data
  "Turn an event with clienX, clientY into circle data"
  [app-state event]
  (let [cx (.-clientX event)
        cy (.-clientY event)
        s @app-state
        r (get-ui s [:pen :radius])
        fill (get-ui s [:pen :color])]
    {:shape :circle
     :id (random-uuid)
     :cx cx
     :cy cy
     :r r
     :fill fill}))

(defn draw-circle-to-canvas
  [app-state circle-data]
  (let [s @app-state
        canvas-id (get-ui s [:canvas :id])]
    (dom/append (dom/by-id canvas-id)
                (create-dot app-state circle-data))))

(defn pen-event-handler
  "Handle pen event, possibly updating the dom"
  ([app-state event]
   (pen-event-handler app-state event false))
  ([app-state event override]
   (let [s @app-state
         canvas-id (get-ui s [:canvas :id])
                                        ;TODO: This could be given many strategies based on the mode...
         handler (fn [e]
                   (let [s @app-state
                         cid (get-in s [:client :id])
                         wid (get-in s [:whiteboard :id])
                         circle-data (event->circle-data app-state event)
                         action {:type :pen-move
                                 :client-id cid
                                 :whiteboard-id wid
                                 :data circle-data}
                         ui-chan (get-in s [:channels :ui :to])]
                     (hws/send app-state action)
                     (go
                       (>! ui-chan action))))]
     (when (or override
               (get-ui s [:is-mouse-down?]))
       (handler event)))))

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
                          (assoc-ui prev [:is-mouse-down?] false))))
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

(defn pen-move-handler
  [app-state msg]
  (draw-circle-to-canvas app-state (:data msg)))

(defn ui-chan-handler
  "Handle messages to the ui"
  [app-state msg]
  (cond
    (= (:type msg) :pen-move) (pen-move-handler app-state msg)
    :else (.log js/console (str "unknown ui-chan message received:\n" msg))))

(defn listen-to-ui-chan
  "Listen for messages coming from the to ui channel"
  [app-state] 
  (go
    (let [s @app-state
          ch (get-in s [:channels :ui :to])]
      (loop []
        (let [data (<! ch)]
          (do
            (ui-chan-handler app-state data)
            (recur)))))))

(defn create-ui
  [app-state]
  (create-drawing-ui app-state)
  (listen-to-keybindings)
  (listen-to-ui-chan app-state))
