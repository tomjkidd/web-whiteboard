(ns web-whiteboard.core
  (:require [clojure.browser.repl :as repl]
            [carafe.dom :as dom]
            [goog.events :as events])
  (:import [goog.events EventType KeyHandler KeyCodes]))

(defonce conn
  (repl/connect "http://localhost:9000/repl"))

(enable-console-print!)

(println "Hello world! 2")

(def app-state
  (atom {:is-mouse-down false
         :canvas {:id "canvas"}
         :pen {:color "#0000FF"
               :radius 3
               :example-id "pen-example"}}))

(def keyboard-mappings
  {KeyCodes.C {:doc "Clear the canvas"
               :key "C"
               :fn (fn [args]
                     (dom/remove-children (dom/by-id "canvas")))
               :args []}})

(let [kh (KeyHandler. js/document)]
  (events/listen kh
                 KeyHandler.EventType.KEY
                 (fn [e]
                   (when-let [{:keys [fn args]} (keyboard-mappings (.-keyCode e))]
                     (apply fn args)))))

(defn create-dot
  "Draws a dot to the canvas"
  [event]
  (let [pen (:pen @app-state)
        color (:color pen)
        radius (:radius pen)]
    (dom/create-element [:circle
                         {:id (random-uuid)
                          :cx (.-clientX event)
                          :cy (.-clientY event)
                          :r radius
                          :fill color}
                         []])))

(defn change-pen-config
  "Update's the app-state when the pen config changes

  Also updates the config example."
  [pen-example-id key]
  (fn [event]
    (let [target (.-target event)
          new-value (.-value target)]
      (swap! app-state (fn [prev]
                         (assoc-in prev [:pen key] new-value)))
      (let [new-pen (:pen @app-state)
            pen-example (dom/by-id pen-example-id)]
        (dom/process-attrs pen-example {:r (:radius new-pen)
                                        :fill (:color new-pen)})))))

(defn create-pen-config
  "Creates the html for configuring the pen to draw with"
  []
  (let [s @app-state
        pen-color (get-in s [:pen :color])
        pen-radius (get-in s [:pen :radius])
        pen-example-id (get-in s [:pen :example-id])
        color-picker [:input
                      {:id "color-picker"
                       :type "color"
                       :value pen-color
                       :onchange (change-pen-config pen-example-id :color)}
                      []]
        size-picker [:input
                     {:id "size-picker"
                      :type "range"
                      :min 1 :max 31 :value pen-radius :step 3
                      :onchange (change-pen-config pen-example-id :radius)}
                     []]
        pen-example [:svg
                     {:width 100
                      :height 100
                      :style "vertical-align: middle;"}
                     [[:circle
                       {:id "pen-example"
                        :cx 50
                        :cy 50
                        :r pen-radius
                        :fill pen-color}]]]]
    (dom/create-element
     [:div
      {:id "pen-options"}
      [color-picker size-picker pen-example]])))

(defn pen-event-handler
  "Determines if event is a drawing event"
  [canvas-id e]
  (when (:is-mouse-down? @app-state)
    (dom/append (dom/by-id canvas-id) (create-dot e))))

(defn dom-test
  "Creates a canvas and pen config in the DOM"
  []
  (let [app (dom/by-id "app")
        s @app-state
        canvas-id (get-in s [:canvas :id])
        svg (dom/create-element
             [:svg
              {:id canvas-id
               :width 500
               :height 500
               :onmousedown (fn [e]
                              (swap! app-state
                                     (fn [prev]
                                       (assoc-in prev [:is-mouse-down?] true)))
                              (pen-event-handler canvas-id e))
               :onmouseup (fn [e]
                            (swap! app-state
                                   (fn [prev]
                                     (assoc-in prev [:is-mouse-down?] false))))
               :onmousemove (partial pen-event-handler canvas-id)}
              [[:circle
                {:id (random-uuid)
                 :cx 250
                 :cy 250
                 :r 3}
                []]
               [:circle
                {:id (random-uuid)
                 :cx 275
                 :cy 275
                 :r 3}
                []]
               ]
              ])
        pen-config (create-pen-config)]
    (dom/append app svg)
    (dom/append app pen-config)))

(dom-test)
