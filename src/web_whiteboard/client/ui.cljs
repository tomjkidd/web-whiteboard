(ns web-whiteboard.client.ui
  "Responsible for generating the user interface for the client application"
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [carafe.dom :as dom]
            [carafe.file :as f]
            [goog.events :as events]
            [cljs.core.async :as async
             :refer [>! <! put! chan alts!]]
            [web-whiteboard.client.handlers.websocket :as hws]
            [web-whiteboard.client.draw.core :refer [event-handler draw-handler]])
  (:import [goog.events EventType KeyHandler KeyCodes]))

(defn ui-action->chan
  [app-state ui-action]
  (let [s @app-state
        ui-chan (get-in s [:channels :ui :to])]
    (go
      (>! ui-chan ui-action))))

(def keyboard-mappings
  {KeyCodes.C {:doc "Clear the canvas"
               :key "C"
               :key-code KeyCodes.C
               :command-name "Clear"
               :fn (fn [app-state]
                     (let [s @app-state
                           cid (get-in s [:client :id])
                           wid (get-in s [:whiteboard :id])
                           to-ws-server-chan (get-in s [:channels :ws-server :to])
                           action {:type :clear-canvas
                                   :client-id cid
                                   :whiteboard-id wid
                                   :data nil}]
                       (go
                         (>! to-ws-server-chan action))
                       (ui-action->chan app-state action)))
               :args []}
   KeyCodes.S {:doc "Save the canvas as SVG"
               :key "S"
               :key-code KeyCodes.S
               :command-name "Save"
               :fn (fn [app-state]
                     ;TODO: Should save come in through the ui-action channel?
                     ;For now I didn't think it is necessary.
                     (let [s @app-state
                           canvas-id (get-in s [:client :ui :canvas :id])
                           svg-element (dom/by-id canvas-id)]
                       (f/save-as-svg svg-element "web-whiteboard.svg")))}})

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

;; TODO: Is there a better way to manage the CSS stuff than this?
(defn create-keyboard-shortcut-menu
  "Create the user interface for the keyboard shortcut menu"
  [app-state]
  (let [light-text "color: #FAFFFA;"
        less-light-text "color: #EFEFEF;"
        font-common "font-family: 'Lato', sans-serif; font-weight: 300;"
        font-accent "font-family: monospace; font-weight: 300;"
        header-background "background-color: #353535;"
        item-background "background-color: #444444;"
        border-style "border-bottom: 1px #777777 solid;"
        padding-style "padding: 3px;"

        header-style (str "font-size: 20px;"
                          light-text
                          font-common
                          padding-style
                          header-background
                          border-style)

        item-style (str "padding: 8px;"
                        item-background
                        border-style)

        badge-style (str light-text
                         font-accent
                         padding-style
                         "font-size: 15px;"
                         "border: 2px #DDDDDD solid;"
                         "border-radius: 5px;"
                         "cursor: pointer;")

        command-style (str less-light-text
                           font-accent
                           padding-style
                           "padding-left: 6px;"
                           "font-size: 15px;")
        
        doc-style (str font-common
                       padding-style
                       "font-size: 12px;"
                       "color: #999999;")
                        
        create-menu-item (fn [{:keys [key key-code command-name doc] :as menu-item}]
                           [:div {:class "kb-menu-item" :style item-style}
                            [[:span
                              {:class "kb-menu-key" :style badge-style
                               :onclick (fn [e]
                                          (when-let [{:keys [fn args]} (keyboard-mappings key-code)]
                                            (apply fn (concat [app-state] args))))}
                              [[:text {} key]]]
                             [:span {:class "kb-menu-command-name" :style command-style} [[:text {} command-name]]]
                             [:span {:class "kb-menu-doc" :style doc-style} [[:text {} doc]]]]])
        header [:div {:id "kb-menu-header" :style header-style} [[:text {} "Keyboard Shortcuts"]]]
        menu-items (vec (map create-menu-item (vals keyboard-mappings)))]
    (dom/create-element
     [:div
      {:id "keyboard-shortcut-menu"}
      (cons header menu-items)])))

;TODO: onmouseout should generate a pen-up event. This will prevent leaving the
;svg area from hanging functionality up in weird ways
(defn create-drawing-ui
  "Create the drawing user interface"
  [app-state]
  (let [app-element (dom/by-id "app")
        s @app-state
        canvas-id (get-in s [:client :ui :canvas :id])
        svg (dom/create-element
             [:svg
              {:id canvas-id
               :width 1000
               :height 500
               :style "display: block; margin: 0 auto; background-color: white;"
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
        pen-config (create-pen-config app-state)
        keyboard-shortcut-menu (create-keyboard-shortcut-menu app-state)]

    (dom/set-attr app-element :style "padding-top: 20px;")
    (dom/append app-element svg)
    (dom/append app-element pen-config)
    (dom/append app-element keyboard-shortcut-menu)))

(defn listen-to-keybindings
  "Register to listen for keybinding events"
  [app-state]
  (let [kh (KeyHandler. js/document)]
    (events/listen kh
                   KeyHandler.EventType.KEY
                   (fn [e]
                     (when-let [{:keys [fn args]} (keyboard-mappings (.-keyCode e))]
                       (apply fn (concat [app-state] args)))))))

(defn ui-chan-handler
  "Handle messages to the ui"
  [app-state ui-action]
  (let [s @app-state
        action-type (:type ui-action)
        mode-record (get-in s [:client :ui :drawing-algorithm :mode])
        draw-state (get-in s [:client :ui :drawing-algorithm :state])]
    (cond
      (contains? #{:pen-move :pen-down :pen-up} action-type)
      (draw-handler mode-record app-state draw-state ui-action)
      
      (= :clear-canvas action-type)
      (dom/remove-children (dom/by-id "canvas")))))

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
