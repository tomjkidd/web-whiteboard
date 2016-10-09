(ns web-whiteboard.client.draw.line
  "A drawing mode that uses an svg paths to create lines"
  (:require [carafe.dom :as dom]
            [web-whiteboard.client.draw.core :as core]))

(defn init-draw-state
  "Creates state that is needed to keep track of which events
  belong to which client.

  :clients is a hash where keys are client-ids, and values are the
  state for that client. The state has a :prev key, which has a ui-action
  value so that when drawing, the draw-handler can determine which things
  to update."
  []
  (atom {:clients {}}))

(defn create-circle
  "Create a circle svg element"
  [app-state {:keys [id cx cy r fill]}]
  (dom/create-element [:circle
                       {:id id
                        :cx cx
                        :cy cy
                        :r r
                        :fill fill
                        :style "pointer-events: none;"
                        }
                       []]))

(defn create-path
  "Create an svg path element"
  [app-state draw-state {:keys [id cx cy r fill] :as pen-data}]
  (dom/create-element [:path
                         {:id id
                          :d (str "M " cx " " cy )
                          :fill "transparent"
                          :stroke fill
                          :stroke-width (* 2 r) ; d = 2 r
                          :stroke-linejoin "round"
                          :stroke-linecap "round"
                          :style "pointer-events: none;"
                          }
                         []]))

;; TODO: For now, just use cx,cy,r,fill. In the future, maybe
;; a change to x,y,radius,color would be more appropriate...
(defn event->data
  "Turn an event with clientX, clientY into path data"
  [app-state event]
  (let [x (.-offsetX event)
        y (.-offsetY event)
        s @app-state
        r (get-in s [:client :ui :pen :radius])
        c (get-in s [:client :ui :pen :color])]
    {:mode :line
     :id (random-uuid)
     :cx x
     :cy y
     :r r
     :fill c}))

(defn event-handler
  [app-state event]
  (core/base-pen-event-handler app-state event event->data))

(defn on-pen-down
  "Create a circle and path for starting a line
  
  NOTE: The path is given the same id as it's first point"
  [app-state draw-state ui-action]
  (let [s @app-state
        d @draw-state
        {:keys [client-id data]} ui-action
        {:keys [id cx cy r fill]} data
        path-id (.toString id)
        canvas-id (get-in s [:client :ui :canvas :id])
        canvas (dom/by-id canvas-id)
        updated-data (assoc-in data [:id] path-id)]
    (swap! draw-state (fn [prev]
                        (assoc-in prev [:clients client-id] {:path-id path-id
                                                             :prev ui-action})))
    
    (dom/append canvas
                (create-circle app-state (assoc-in data [:id] (random-uuid))))
    (dom/append canvas
                (create-path app-state draw-state updated-data))))

(defn on-pen-move
  [app-state draw-state ui-action]
  (let [s @app-state
        d @draw-state
        {:keys [client-id data]} ui-action
        {:keys [id cx cy r fill]} data
        path-id (get-in d [:clients client-id :path-id])
        path-element (dom/by-id path-id)
        d-attr (dom/get-attr path-element :d)
        new-d-attr (str d-attr " L " cx " " cy)]
    (swap! draw-state (fn [prev]
                        (assoc-in prev [:clients client-id] {:path-id path-id
                                                             :prev ui-action})))
    (dom/set-attr path-element :d new-d-attr)))

(defn draw-handler
  [app-state draw-state {:keys [type] :as ui-action}]
  (let [s @app-state
        canvas-id (get-in s [:client :ui :canvas :id])
        ]
    (case type
      :register (.log js/console "Ignore register event")
      :pen-down (on-pen-down app-state draw-state ui-action)
      :pen-move (on-pen-move app-state draw-state ui-action)
      :pen-up (.log js/console "TODO: ignore, that is your queue to stop drawing"))))

(defrecord LineMode []
  core/DrawingMode
  (init-draw-state [this]
    (init-draw-state))
  (event-handler [this app-state event]
    (event-handler app-state event))
  (draw-handler [this app-state draw-state ui-action]
    (draw-handler app-state draw-state ui-action)))
