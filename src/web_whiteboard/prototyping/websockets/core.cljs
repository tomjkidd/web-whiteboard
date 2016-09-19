(ns web-whiteboard.prototyping.websockets.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [carafe.dom :as dom]
            [carafe.websockets :as ws]
            [clojure.browser.repl :as repl]
            [cognitect.transit :as transit]
            [cljs.core.async :as async
             :refer [>! <! put! chan alts!]]))

(enable-console-print!)

(declare app-state)
(declare open-handler)
(declare close-handler)
(declare data->chan)
(declare send)
(declare recv)

(defn create-ws
  [init-fn]
  (ws/create-websocket
   "ws://localhost:5000/echo/"
   {:onopen (fn [event]
              (let [s @app-state
                    ws (get-in s [:server :ws])
                    tw (get-in s [:transit :writer])]
                (.log js/console (str "Open: " event))
                (open-handler app-state nil)
                (when init-fn
                  (init-fn ws))))
    :onerror (fn [err]
               (.log js/console (str "Error: " err)))
    :onclose (fn [event]
               (.log js/console (str "Close: " event))
               (close-handler app-state nil))
    :onmessage (fn [event]
                 (data->chan (recv event)))}))

(def app-state
  (atom {:msg "default message"
         :server {:ws (create-ws (fn [w] (send w {:type :ignore
                                           :msg "Message from client"})))}
         :client {:id (str "client:" (random-uuid))}
         :whiteboard {:id (str "whiteboard:" (random-uuid))}
         :transit {:writer (transit/writer :json)
                   :reader (transit/reader :json)}
         :channels {:ws {:from (chan)}}
         :connected false}))

(defn register-handler
  [app-state msg]
  (.log js/console (str "register-handler called" msg @app-state)))

(defn open-handler
  [app-state msg]
  (swap! app-state (fn [prev] (assoc prev :connected true))))

(defn close-handler
  [app-state msg]
  (swap! app-state (fn [prev] (assoc prev :connected false))))

(defn websocket-handler
  [app-state msg]
  (case (:type msg)
    :register (register-handler app-state msg)
    :none))

(defn listen-to-websocket
  "Listen for messages from the websocket"
  [app-state] 
  (go
    (let [s @app-state
          ch (get-in s [:channels :ws :from])]
      (loop []
        (let [data (<! ch)]
          (do
            (.log js/console (str "Message: " data))
            (websocket-handler app-state data)
            (recur)))))))

(defn data->chan
  "Put data onto the [:channels :ws :from] channel"
  [data]
  (let [s @app-state
        ch (get-in s [:channels :ws :from])]
    (put! ch data)))

(def send
  "Send a message to a websocket as transit data

  Ensures that a websocket is available, in case the server was closed due to timeout"
  (fn [ws msg]
    (let [s @app-state
          tw (get-in s [:transit :writer])]
      (if (not (= WebSocket/OPEN (.-readyState ws)))
        (let [new-ws (create-ws (fn [w] (send w msg)))]
          (swap! app-state (fn [prev]
                             (assoc-in prev [:server :ws] new-ws))))
        (.send ws (transit/write tw msg))))))

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
                     wid (get-in s [:whiteboard :id])]
                 (send ws {:type :register
                           :client-id cid
                           :whiteboard-id wid})))}
            [[:text {} ":register"]]]]]))

(dom/append
 (dom/by-id "app")
 ui)

(listen-to-websocket app-state)

