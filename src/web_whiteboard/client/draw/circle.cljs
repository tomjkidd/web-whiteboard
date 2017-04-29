(ns web-whiteboard.client.draw.circle
  (:require [carafe.dom :as dom]
            [web-whiteboard.client.draw.core :as core]))

(defn event->data
  "Turn an event with clientX, clientY into circle data"
  [app-state event]
  (let [cx (.-offsetX event)
        cy (.-offsetY event)
        s @app-state
        r (get-in s [:client :ui :pen :radius])
        fill (get-in s [:client :ui :pen :color])]
    {:mode :circle
     :id (random-uuid)
     :cx cx
     :cy cy
     :r r
     :fill fill}))

(defn event-handler
  [app-state event]
  (core/base-pen-event-handler app-state event event->data))

(defn create-circle
  "Create a circle svg element"
  [app-state {:keys [id cx cy r fill]}]
  (dom/create-element [:circle
                       {:id id
                        :cx cx
                        :cy cy
                        :r r
                        :fill fill}
                       []]))

(defn draw-handler
  [app-state draw-state ui-action]
  (when (= :verbose (:log-level @app-state))
    (.log js/console (str ":ui-action") (clj->js ui-action)))
  (let [s @app-state
        canvas-id (get-in s [:client :ui :canvas :id])]
    (dom/append (dom/by-id canvas-id)
                (create-circle app-state (:data ui-action)))))

(defrecord CircleMode []
  core/DrawingMode
  (init-draw-state [this]
    nil)
  (event-handler [this app-state event]
    (event-handler app-state event))
  (draw-handler [this app-state draw-state ui-action]
    (draw-handler app-state draw-state ui-action)))
