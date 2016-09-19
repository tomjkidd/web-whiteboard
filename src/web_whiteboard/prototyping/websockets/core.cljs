(ns web-whiteboard.prototyping.websockets.core
  (:require [carafe.dom :as dom]
            [carafe.websockets :as ws]
            [clojure.browser.repl :as repl]
            [cognitect.transit :as transit]))

(enable-console-print!)

(declare app-state)
(declare send)
(declare recv)

(def ws (ws/create-websocket
         "ws://localhost:5000/echo/"
         {:onopen (fn [event]
                    (let [s @app-state
                          ws (get-in s [:server :ws])
                          tw (get-in s [:transit :writer])]
                      (.log js/console (str "Open: " event))
                      (send ws {:type :ignore
                                :msg "Message from client"})))
          :onerror (fn [err]
                     (.log js/console (str "Error: " err)))
          :onclose (fn [event]
                     (.log js/console (str "Close: " event)))
          :onmessage (fn [event]
                       (let [data (recv event)]
                         (.log js/console (str "Message: " data))))}))

(def app-state
  (atom {:msg "default message"
         :server {:ws ws}
         :client {:id "client-1"}
         :whiteboard {:id "whiteboard-1"}
         :transit {:writer (transit/writer :json)
                   :reader (transit/reader :json)}}))

(def send
  "Send a message to a websocket as transit data"
  (let [s @app-state
        tw (get-in s [:transit :writer])]
    (fn [ws msg]
      (.send ws (transit/write tw msg)))))

(def recv
  "Receive a message from a websocket as transit data"
  (let [s @app-state
        tr (get-in s [:transit :reader])]
    (fn [event]
      (transit/read tr event.data))))

(def ui (dom/create-element
         [:span
          {}
          [[:input
            {:id "ws-send"
             :type "text"
             :onchange (fn [e]
                         (let [new-val (.-value (.-target e))]
                           (swap! app-state (fn [prev]
                                              (assoc prev :msg new-val)))))}
            []]
           [:button
            {:onclick
             (fn [e]
               (let [{:keys [msg server]} @app-state]
                 (send (:ws server) msg)))}
            [[:text {} "Send"]]]
           [:button
            {:onclick
             (fn [e]
               (defonce conn
                 (repl/connect "http://localhost:9000/repl"))
               (println "REPL conn should exist now..."))}
            [[:text {} "Connect to REPL"]]]
           [:button
            {:onclick
             (fn [e]
               (let [s @app-state
                     ws (get-in s [:server :ws])
                     cid (get-in s [:client :id])
                     wid (get-in s [:whiteboard :id])
                     tw (get-in s [:transit :writer])]
                 (.send ws (transit/write tw {:type :register
                                             :client-id cid
                                             :whiteboard-id wid}))))}
            [[:text {} ":register"]]]]]))

(dom/append
 (dom/by-id "app")
 ui)

