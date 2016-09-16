(ns web-whiteboard.prototyping.websockets.core
  (:require [carafe.dom :as dom]
            [carafe.websockets :as ws]))

(declare app-state)

(def ws (ws/create-websocket
         "ws://localhost:5000/echo/"
         {:onopen (fn [event]
                    (.log js/console (str "Open: " event))
                    (.send (:ws @app-state) "Message from client"))
          :onerror (fn [err]
                     (.log js/console (str "Error: " err)))
          :onclose (fn [event]
                     (.log js/console (str "Close: " event)))
          :onmessage (fn [event]
                       (.log js/console (str "Message: " event.data)))}))

(def app-state
  (atom {:msg "default message"
         :ws ws}))

(def ui (dom/create-element
         [:span
          {}
          [[:input
            {:id "ws-send"
             :type "text"
             :onchange (fn [e]
                         (let [new-val (.-value (.-target e))]
                           (.log js/console (str "new input: " new-val))
                           (swap! app-state (fn [prev]
                                              (assoc prev :msg new-val)))))}
            []]
           [:button
            {:onclick
             (fn [e]
               (let [{:keys [msg ws]} @app-state]
                 (.send ws msg)))}
            [[:text {} "Send"]]]]]))

(dom/append
 (dom/by-id "app")
 ui)

