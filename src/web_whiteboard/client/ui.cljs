(ns web-whiteboard.client.ui
  "Responsible for generating the user interface for the client application"
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [carafe.dom :as dom]
            [carafe.file :as f]
            [goog.events :as events]
            [cljs.core.async :as async
             :refer [>! <! put! chan alts!]]
            [web-whiteboard.client.draw.core :refer [event-handler draw-handler]])
  (:import [goog.events EventType KeyHandler KeyCodes]))

(defn ui-action->chan
  "Puts a ui-action onto a channel"
  [chan ui-action]
  (go
    (>! chan ui-action)))

(defn stroke->ui-chan
  "Logically puts a stroke onto the ui-chan, as a series of puts for each ui-action in the stroke list"
  [app-state stroke]
  (let [s @app-state
        ui-chan (get-in s [:channels :ui :to])]
    (go
      (loop [cur (first stroke)
             rem (rest stroke)]
        (when (not (empty? cur))
          (>! ui-chan cur)
          (recur (first rem) (rest rem)))))))

(defn ui-action->chan-helper
  "Convenience function to specify which channel to put to through a list of keys in app-state"
  [app-state ui-action key-path]
  (let [s @app-state
        ch (get-in s key-path)]
    (ui-action->chan ch ui-action)))

(defn ui-action->ui-chan
  [app-state ui-action]
  (ui-action->chan-helper app-state ui-action [:channels :ui :to]))

(defn ui-action->ws-chan
  [app-state ui-action]
  (ui-action->chan-helper app-state ui-action [:channels :ws-server :to]))

(defn put-ui-action-on-ui-and-ws-chans
  [app-state ui-action]
  (ui-action->ws-chan app-state ui-action)
  (ui-action->ui-chan app-state ui-action))

(defn- publish-ui-action-wrapper
  "Returns a wrapper function that take a ui-action and publishes it
  to the ui-chan and ws-server-to-chan."
  [ui-action]
  (fn [app-state]
    (let [s @app-state
          cid (get-in s [:client :id])
          wid (get-in s [:whiteboard :id])
          ui-action (merge {:client-id cid
                            :whiteboard-id wid}
                           ui-action)]
      (put-ui-action-on-ui-and-ws-chans app-state ui-action))))

(def keyboard-mappings
  {KeyCodes.U {:doc "Undo the last stroke"
               :key "U"
               :key-code KeyCodes.U
               :command-name "Undo"
               :fn (publish-ui-action-wrapper {:type :undo-stroke
                                               :data nil})
               :args []}
   KeyCodes.R {:doc "Redo the last undone stroke"
               :key "R"
               :key-code KeyCodes.R
               :command-name "Redo"
               :fn (publish-ui-action-wrapper {:type :redo-stroke
                                               :data nil})
               :args []}
   KeyCodes.C {:doc "Clear the canvas"
               :key "C"
               :key-code KeyCodes.C
               :command-name "Clear"
               :fn (publish-ui-action-wrapper {:type :clear-canvas
                                               :data nil})
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

(defn listen-to-keybindings
  "Register to listen for keybinding events"
  [app-state]
  (let [kh (KeyHandler. js/document)]
    (events/listen kh
                   KeyHandler.EventType.KEY
                   (fn [e]
                     (when-let [{:keys [fn args]} (keyboard-mappings (.-keyCode e))]
                       (apply fn (concat [app-state] args)))))))

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
