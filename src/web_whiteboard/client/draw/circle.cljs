(ns web-whiteboard.client.draw.circle
  (:require [carafe.dom :as dom]
            [web-whiteboard.client.draw.core :as core]))

(def margin
  "TODO: Get this from the dom"
  8)

(defn event->data
  "Turn an event with clientX, clientY into circle data"
  [app-state event]
  (let [cx (.-clientX event)
        cy (.-clientY event)
        s @app-state
        r (get-in s [:client :ui :pen :radius])
        fill (get-in s [:client :ui :pen :color])]
    {:shape :circle
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
                        :cx (- cx margin)
                        :cy (- cy margin)
                        :r r
                        :fill fill}
                       []]))

(defn draw-handler
  [app-state draw-state ui-action]
  (let [s @app-state
        canvas-id (get-in s [:client :ui :canvas :id])]
    (dom/append (dom/by-id canvas-id)
                (create-circle app-state (:data ui-action)))))
