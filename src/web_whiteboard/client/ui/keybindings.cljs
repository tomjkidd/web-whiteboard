(ns web-whiteboard.client.ui.keybindings
  "Responsible for handling keyboard events for the client application"
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [carafe.dom :as dom]
            [carafe.file :as f]
            [goog.events :as events]
            [web-whiteboard.client.ui.core :refer [publish-ui-action-wrapper]]
            [goog.math])
  (:import [goog.events EventType KeyHandler KeyCodes]))

(def keyboard-menu-item-data-definitions
  "Data-based information about key-bindings that should be put into the keyboard shortcut menu"
  [{:doc "Undo the last stroke"
    :key "U"
    :key-binding #{KeyCodes.U}
    :command-name "Undo"
    :fn (publish-ui-action-wrapper {:type :undo-stroke
                                    :data nil})
    :args []}
   {:doc "Redo the last undone stroke"
    :key "R"
    :key-binding #{KeyCodes.R}
    :command-name "Redo"
    :fn (publish-ui-action-wrapper {:type :redo-stroke
                                    :data nil})
    :args []} 
   {:doc "Clear the canvas"
    :key "C"
    :key-binding #{KeyCodes.C}
    :command-name "Clear"
    :fn (publish-ui-action-wrapper {:type :clear-canvas
                                    :data nil})
    :args []}
   {:doc "Save the canvas as SVG"
    :key "S"
    :key-binding #{KeyCodes.S}
    :command-name "Save"
    :fn (fn [app-state]
          ;TODO: Should save come in through the ui-action channel?
          ;For now I didn't think it is necessary.
          (let [s @app-state
                canvas-id (get-in s [:client :ui :canvas :id])
                svg-element (dom/by-id canvas-id)]
            (f/save-as-svg svg-element "web-whiteboard.svg")))
    :args []}])

(defn- keybinding-applier
  "A private helper to make the menu items consistent in how they work"
  [f args]
  (fn
    [app-state e]
    (apply f (concat [app-state] args))))

(def keyboard-menu-keybinding-handlers
  "keybinding-handlers for the shortcut menu"
  (reduce (fn [acc {:keys [key-binding fn args]}]
            (assoc acc
                   key-binding
                   (keybinding-applier fn args)))
          {}
          keyboard-menu-item-data-definitions))

(def pen-keybindings
  (map #(set [%]) (-> (into [] (range KeyCodes.ONE (inc KeyCodes.NINE)))
                   (conj KeyCodes.ZERO))))

(def pen-color-values
  "Useful colors to use for pen-color"
  ["#1A1A18"
   "#945536"
   "#B0C902"
   "#00693A"
   "#BCE2F4"
   "#01439D"
   "#F161A4"
   "#E50516"
   "#EA7408"
   "#FEE704"])

(def pen-color-keybinding-handlers
  "keybinding-handlers for changing pen color
  
  These will change the value attribute of the color-picker element,
  and then fire a 'change' event."
  (let [kb-color-tuples (map vector pen-keybindings pen-color-values)]
    (reduce (fn [acc [kb c]]
              (assoc acc
                     kb
                     (fn [_ e]
                       (let [cp (dom/by-id "color-picker")
                             change-event (js/Event. "change")]
                         (set! (.-value cp) c)
                         (.dispatchEvent cp change-event)))))
            {}
            kb-color-tuples)))

(defn get-high-contrast-color
  "Determines a high-contrast color relative to a given one.

  Averages each RGB component, and uses a cutoff at 128 to pick"
  [css-hex]
  (let [segments (->> (subs css-hex 1)
                      (seq)
                      (partition 2)
                      (map #(goog.string/parseInt (apply str %))))
        avg (apply goog.math/average segments)]
    (if (< avg 128)
      "#DDDDDD"
      "#333333")))

(def keybinding-handlers
  "All of the available keybinding handlers for the user interface"
  (merge pen-color-keybinding-handlers
         keyboard-menu-keybinding-handlers))

(defn event->key-binding
  "Determines the key-binding, based on a DOM event"
  [e]
  (reduce (fn [acc [include? k]]
            (if include?
              (conj acc k)
              acc))
          #{(.-keyCode e)}
          (map vector
               [e.ctrlKey e.shiftKey]
               [KeyCodes.CTRL KeyCodes.SHIFT])))

(defn keybinding-dispatcher
  "Routes a key-binding to the proper keybinding-handler function"
  [app-state event key-binding]
  (when-let [handler (keybinding-handlers key-binding)]
    (handler app-state event)))

(defn keybinding-event-handler
  "Determines key-binding, based on a DOM event, and calls keybinding-dispatcher with it"
  [app-state]
  (fn [e]
    (let [key-binding (event->key-binding e)]
      (keybinding-dispatcher app-state e key-binding))))

(defn create-pen-color-tiles
  "Creates a visual aid to show how numbers map to colors"
  [app-state]
  (let [tile-style ""
        create-pen-color-tile (fn [[n color key-binding]]
                                (let [font-common "font-family: 'Lato', sans-serif; font-weight: 300;"
                                      font-accent "font-family: monospace; font-weight: 300;"
                                      letter-color (get-high-contrast-color color)
                                      swatch-style (str "background-color: " color ";"
                                                        "color: " letter-color ";"
                                                        "width: 25px;"
                                                        "height: 25px;"
                                                        "display: inline-block;"
                                                        "text-align: center;"
                                                        "border: 2px #DDDDDD solid;"
                                                        "border-radius: 5px;"
                                                        "margin: 0px 3px;"
                                                        "cursor: pointer;")]
                                  [:div
                                   {:class "kb-color-tile"
                                    :style swatch-style
                                    :onclick (fn [e]
                                               (keybinding-dispatcher app-state e key-binding))}
                                   [[:span {:style (str "vertical-align: middle; line-height: 25px;"
                                                        font-accent
                                                        "font-weight: 800;")}
                                     [[:text {} (str n)]]]]]))
        ns (map #(mod % 10) (range 1 (inc 10)))
        kb-color-tuples (map vector  ns pen-color-values pen-keybindings)
        pen-color-tiles (map create-pen-color-tile kb-color-tuples)]
    [:div
     {:class "kb-color-tiles"
      :style tile-style}
     pen-color-tiles]))

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
                        
        create-menu-item (fn [{:keys [key key-binding command-name doc] :as menu-item}]
                           [:div {:class "kb-menu-item" :style item-style}
                            [[:span
                              {:class "kb-menu-key" :style badge-style
                               :onclick (fn [e]
                                          (keybinding-dispatcher app-state e key-binding))}
                              [[:text {} key]]]
                             [:span {:class "kb-menu-command-name" :style command-style} [[:text {} command-name]]]
                             [:span {:class "kb-menu-doc" :style doc-style} [[:text {} doc]]]]])
        header [:div {:id "kb-menu-header" :style header-style} [[:text {} "Keyboard Shortcuts"]]]
        menu-items (vec (map create-menu-item keyboard-menu-item-data-definitions))
        color-tiles (create-pen-color-tiles app-state)
        create-tile-footer (fn [color-tiles]
                             [:div {:class "kb-menu-item" :style (str item-background border-style)}
                              [[:span
                                {:class "kb-menu-key" :style ""}
                                [[:text {} ""]]]
                               [:span {:class "kb-menu-command-name" :style (str command-style
                                                                                 "line-height: 35px;"
                                                                                 "vertical-align: middle;")} [[:text {} "Palette"]]]
                               [:span {:class "kb-menu-doc" :style (str doc-style
                                                                        "display: inline-block;"
                                                                        "line-height: 35px;"
                                                                        "vertical-align: middle;")} [color-tiles]]]])
        color-tile-footer (create-tile-footer color-tiles)]
    (dom/create-element
     [:div
      {:id "keyboard-shortcut-menu"}
      ;(cons header menu-items)
      (concat (cons header menu-items)
              [color-tile-footer])])))

(defn listen-to-keybindings
  "Register the user interface to listen for keybinding events"
  [app-state]
  (let [kh (KeyHandler. js/document)]
    (events/listen kh
                   KeyHandler.EventType.KEY
                   (keybinding-event-handler app-state))))
