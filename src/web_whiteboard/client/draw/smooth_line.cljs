(ns web-whiteboard.client.draw.smooth-line
  "A drawing mode that uses an svg paths to create smooth lines"
  (:require [carafe.dom :as dom]
            [web-whiteboard.client.draw.core :as core]
            [web-whiteboard.client.draw.line :as l]))

(defn on-pen-move
  "Use a smoothing algorithm to modify the path slightly."
  [app-state draw-state ui-action]
  (let [s @app-state
        d @draw-state
        {:keys [client-id data]} ui-action
        {:keys [id cx cy r fill]} data
        prev-ui-action (get-in d [:clients client-id :prev])
        prev-x (get-in prev-ui-action [:data :cx])
        prev-y (get-in prev-ui-action [:data :cy])
        path-id (get-in d [:clients client-id :path-id])
        path-element (dom/by-id path-id)
        d-attr (if (nil? path-element)
                 nil
                 (dom/get-attr path-element :d))
        sf .75 ; smoothing-factor, see [http://jackschaedler.github.io/handwriting-recognition/]
        new-x (+ (* sf prev-x)
                 (* (- 1 sf) cx))
        new-y (+ (* sf prev-y)
                 (* (- 1 sf) cy))
        new-d-attr (if (nil? d-attr)
                     nil
                     (str d-attr " L " new-x " " new-y))
        new-ui-action (assoc ui-action :data (merge data {:cx new-x :cy new-y}))
        circle-element (dom/by-id (str "circle" path-id))]
    (when new-d-attr
      (swap! draw-state (fn [prev]
                          (assoc-in prev [:clients client-id] {:path-id path-id
                                                               :prev new-ui-action})))
      (dom/set-attr path-element :d new-d-attr)
      (when circle-element
        (.removeChild (.-parentNode circle-element) circle-element)))))

(defn on-undo-stroke
  "Remove the previous line (svg circle,path elements) from the dom"
  [app-state draw-state ui-action]
  (let [s @app-state
        {:keys [client-id]} ui-action
        entry (get-in s [:client :ui :history-map client-id])]
    (when entry
      (let [{:keys [stroke-stack]} entry
            ui-action (first (last stroke-stack))
            path-id (get-in ui-action [:data :id])
            path-element (dom/by-id (str path-id))
            circle-element (dom/by-id (str "circle" path-id))]
        (when path-element
          (.removeChild (.-parentNode path-element) path-element))
        (when circle-element
          (.removeChild (.-parentNode circle-element) circle-element))))))

(defn draw-handler
  [app-state draw-state {:keys [type] :as ui-action}]
  (let [s @app-state
        canvas-id (get-in s [:client :ui :canvas :id])]
    (case type
      :register (.log js/console "Ignore register event")
      :pen-down (l/on-pen-down app-state draw-state ui-action)
      :pen-move (on-pen-move app-state draw-state ui-action)
      :pen-up (.log js/console "TODO: ignore, that is your queue to stop drawing")
      :undo-stroke (on-undo-stroke app-state draw-state ui-action))))

(defrecord SmoothLineMode []
  core/DrawingMode
  (init-draw-state [this]
    (l/init-draw-state))
  (event-handler [this app-state event]
    (l/event-handler app-state event))
  (draw-handler [this app-state draw-state ui-action]
    (draw-handler app-state draw-state ui-action)))
