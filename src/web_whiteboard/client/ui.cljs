(ns web-whiteboard.client.ui
  "Responsible for generating the user interface for the client application"
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [carafe.dom :as dom]
            [carafe.file :as f]
            [goog.events :as events]
            [cljs.core.async :as async
             :refer [>! <! put! chan alts!]]
            [web-whiteboard.client.ui.core :refer [stroke->ui-chan
                                                   put-ui-action-on-ui-and-ws-chans
                                                   constants]]
            [web-whiteboard.client.draw.core :refer [event-handler draw-handler]]
            [web-whiteboard.client.ui.keybindings :refer [listen-to-keybindings
                                                          create-keyboard-shortcut-menu]])
  (:import [goog.events EventType KeyHandler KeyCodes]))

(defn- get-ui
  "Convenience for reading ui state

  state is expected in it's dereferenced form"
  [state keys]
  (get-in state (concat [:client :ui] keys)))

(defn- assoc-ui
  "Convenience for updating ui state"
  [state keys new-val]
  (assoc-in state (concat [:client :ui] keys) new-val))

(defn pen-event-handler
  "Handle pen event, possibly updating the dom"
  ([app-state event]
   (pen-event-handler app-state event false))
  ([app-state event override]
   (let [s @app-state
         canvas-id (get-ui s [:canvas :id])
         mode-record (get-ui s [:drawing-algorithm :mode])]
     (when (or override
               (get-ui s [:is-mouse-down?]))
       (event-handler mode-record app-state event)))))

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
                      :min (get-in constants [:size-picker :min])
                      :max (get-in constants [:size-picker :max])
                      :value radius
                      :step (get-in constants [:size-picker :step])
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

;TODO: onmouseout should generate a pen-up event. This will prevent leaving the
;svg area from hanging functionality up in weird ways
(defn create-drawing-ui
  "Create the drawing user interface"
  [app-state]
  (let [app-element (dom/by-id "app")
        s @app-state
        canvas-id (get-in s [:client :ui :canvas :id])
        set-mousedown-to (fn [val]
                           (fn [e]
                             (swap! app-state
                                    (fn [prev]
                                      (assoc-ui prev [:is-mouse-down?] val)))
                             (pen-event-handler app-state e val)))
        svg (dom/create-element
             [:svg
              {:id canvas-id
               :width (get-in s [:client :ui :width])
               :height (get-in s [:client :ui :height])
               :style "display: block; margin: 0 auto; background-color: white; cursor: default;"
               :onmousedown (set-mousedown-to true)
               :onmouseup (set-mousedown-to false)
               :onmouseleave (set-mousedown-to false)
               :onmousemove (fn [e] (pen-event-handler app-state e))}
              ])
        pen-config (create-pen-config app-state)
        keyboard-shortcut-menu (create-keyboard-shortcut-menu app-state)]

    (dom/set-attr app-element :style "padding-top: 20px;")
    (dom/append app-element svg)
    (dom/append app-element pen-config)
    (dom/append app-element keyboard-shortcut-menu)))

(defn update-history-state
  [app-state ui-action]
  (let [s @app-state
        {:keys [type client-id]} ui-action
        history-map (get-in s [:client :ui :history-map])
        client-entry (get history-map client-id {:stroke-stack []
                                                 :undo-stack []})
        print-client-entry (fn [entry]
                             (let [{:keys [stroke-stack undo-stack]} entry]
                               (str
                                "stroke-stack"
                                (map #(str (-> % first :data :id) \newline) stroke-stack)
                                \newline
                                "undo-stack"
                                (map #(str (-> % first :data :id) \newline) undo-stack))))
        
        start-new-stroke
        (fn [entry ui-action]
          (let [old-stack (get entry :stroke-stack)
                stroke [ui-action]
                new-stack (conj old-stack stroke)]
            (assoc-in entry [:stroke-stack] new-stack)))

        update-existing-stroke
        (fn [entry ui-action]
          (let [old-stack (get entry :stroke-stack)
                stroke (peek old-stack)
                updated-stroke (conj stroke ui-action)
                new-stack (conj (pop old-stack) updated-stroke)]
            (assoc-in entry [:stroke-stack] new-stack)))

        end-stroke
        (fn [entry ui-action]
          (update-existing-stroke entry ui-action))

        undo-stroke
        (fn [entry ui-action]
          (let [{:keys [stroke-stack undo-stack]} entry]
            (if (empty? stroke-stack)
              entry
              (let [remaining-strokes (pop stroke-stack)
                    last-stroke (peek stroke-stack)
                    updated-entry (-> entry
                                      (assoc-in [:stroke-stack] remaining-strokes)
                                      (assoc-in [:undo-stack] (conj undo-stack last-stroke)))]
                updated-entry))))

        redo-stroke
        (fn [entry ui-action]
          (let [{:keys [stroke-stack undo-stack]} entry]
            (if (empty? undo-stack)
              [nil entry]
              (let [to-redo (peek undo-stack)
                    remaining-undo (pop undo-stack)
                    updated-entry (-> entry
                                      (assoc-in [:undo-stack] remaining-undo))]
                [to-redo updated-entry]))))

        action-handler
        (fn [entry ui-action]
          (let [{:keys [type]} ui-action

                [to-redo redo-entry]
                (when (= type :redo-stroke) (redo-stroke entry ui-action))
                
                new-entry
                (cond (= type :pen-down) (start-new-stroke entry ui-action)
                      (= type :pen-move) (update-existing-stroke entry ui-action)
                      (= type :pen-up) (end-stroke entry ui-action)
                      (= type :clear-canvas) entry
                      (= type :undo-stroke) (undo-stroke entry ui-action)
                      (= type :redo-stroke) redo-entry)]
            {:updated-history-map (assoc-in history-map [client-id] new-entry)
             :after-update #(when to-redo
                              (stroke->ui-chan app-state to-redo))}))
        
        {:keys [updated-history-map after-update]}
        (action-handler client-entry ui-action)]
    (swap! app-state (fn [prev]
                       (assoc-in prev [:client :ui :history-map] updated-history-map)))
    (after-update)))

(defn ui-chan-handler
  "Handle messages to the ui"
  [app-state ui-action]
  (let [s @app-state
        action-type (:type ui-action)
        mode-record (get-in s [:client :ui :drawing-algorithm :mode])
        draw-state (get-in s [:client :ui :drawing-algorithm :state])]
    
    ; NOTE: :redo-stroke will use update-history state to put previous stroke's
    ; ui-actions onto the ui-chan, so it is ignored here.
    (cond
      (contains? #{:pen-move :pen-down :pen-up :undo-stroke} action-type)
      (draw-handler mode-record app-state draw-state ui-action)
      
      (= :clear-canvas action-type)
      (dom/remove-children (dom/by-id "canvas")))

    (update-history-state app-state ui-action)))

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
  (listen-to-keybindings app-state)
  (listen-to-ui-chan app-state))
