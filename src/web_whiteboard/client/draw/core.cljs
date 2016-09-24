(ns web-whiteboard.client.draw.core
  "Utilities for creating different drawing modes"
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async
             :refer [>! <! put! chan alts!]]
            [web-whiteboard.client.handlers.websocket :as hws]))

(defn base-pen-event-handler
  "All drawing modes will need to put actions onto the ui-chan.
  This function exists to help create a drawing mode"
  [app-state event event->data]
  (let [s @app-state
        cid (get-in s [:client :id])
        wid (get-in s [:whiteboard :id])
        data (event->data app-state event)
        event-type (.-type event)
        event-keyword (keyword (str "on" event-type))
        action-type (case event-keyword
                      :onmousemove :pen-move
                      :onmousedown :pen-down
                      :onmouseup :pen-up)
        action {:type action-type
                :client-id cid
                :whiteboard-id wid
                :data data}
        ui-chan (get-in s [:channels :ui :to])]
    (hws/send app-state action)
    (go
      (>! ui-chan action))))

(defn base-draw-handler
  "All actual drawing will need to turn actions into dom manipulation
  This function exists to help create a drawing mode"
  [app-state action draw-handler]
  (let [s @app-state
        mode (get-in s [:client :ui :drawing-algorithm :mode])
        draw-state (get-in s [:client :ui :drawing-algorithm :state])]
    (draw-handler app-state draw-state action)))

(defprotocol DrawingMode
  "The set of operations needed to support new drawing modes."
  (init-draw-state [this]
    "Initializes state the the drawing mode depends on")
  (event-handler [this app-state event]
    "Handles turning ui events into ui-actions")
  (draw-handler [this app-state draw-state ui-action]
    "Updates the dom, if necessary, in response to ui-actions"))
