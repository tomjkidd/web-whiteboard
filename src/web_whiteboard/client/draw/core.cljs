(ns web-whiteboard.client.draw.core
  "Utilities for creating different drawing modes"
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async
             :refer [>! <! put! chan alts!]]
            [web-whiteboard.client.handlers.websocket :as hws]
            [web-whiteboard.client.ui.core :refer [publish-ui-action]]))

(defn base-pen-event-handler
  "All drawing modes will need to put actions onto the ui-chan.
  This function exists to help create a drawing mode"
  [app-state event event->data]
  (when (= :verbose (:log-level @app-state))
    (let [to-log {:type (.-type event)
                  :x (.-offsetX event)
                  :y (.-offsetY event)}]
      (.log js/console (str ":event") (clj->js to-log))))
  
  (let [data (event->data app-state event)
        event-type (.-type event)
        event-keyword (keyword (str "on" event-type))
        action-type (case event-keyword
                      :onmousemove :pen-move
                      :onmousedown :pen-down
                      :onmouseup :pen-up
                      :onmouseleave :pen-up)
        action {:type action-type
                :data data}]
    (publish-ui-action app-state action)))

(defn base-draw-handler
  "All actual drawing will need to turn actions into dom manipulation
  This function exists to help create a drawing mode"
  [app-state action draw-handler]
  (when (= :verbose (:log-level @app-state))
    (.log js/console (str ":ui-action") action))
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
