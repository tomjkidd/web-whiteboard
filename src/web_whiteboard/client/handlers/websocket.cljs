(ns web-whiteboard.client.handlers.websocket
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [carafe.websockets :as ws]
            [cognitect.transit :as transit]
            [cljs.core.async :as async
             :refer [>! <! put! chan alts!]]))

(declare open-handler)
(declare close-handler)
(declare message-handler)
(declare ws-msg->chan)

(defn create-ws
  "Create a client-side websocket"
  ([app-state init-fn]
   (let [s @app-state
         ws-url (get-in s [:server :url])]
     (ws/create-websocket
      ws-url
      {:onopen (fn [event]
                 (let [s @app-state
                       ws (get-in s [:server :ws])
                       tw (get-in s [:transit :writer])]
                   (.log js/console (str "Open: " event))
                   (open-handler app-state event)
                   (when init-fn
                     (init-fn ws))))
       :onerror (fn [err]
                  ;; TODO: Queue events on error so that they can be
                  ;; made available once reconnected...
                  (.log js/console (str "Error: " err)))
       :onclose (fn [event]
                  (.log js/console (str "Close: " event))
                  (close-handler app-state nil))
       :onmessage (fn [event]
                    (message-handler app-state event))}))))

(defn- reconnect-ws
  "Create a new websocket to communicate through"
  [app-state init-fn]
  (let [s @app-state
        ws-url (get-in s [:server :url])
        new-ws (create-ws app-state init-fn)]
    (swap! app-state (fn [prev]
                       (assoc-in prev [:server :ws] new-ws)))))

(defn send
  "Send a message to a websocket server as transit data

  Ensures that a websocket is available, in case the server was closed due to timeout"
  [app-state msg]
  (let [s @app-state
        ws (get-in s [:server :ws])
        tw (get-in s [:transit :writer])]
    (if (not= (.-OPEN js/WebSocket) (.-readyState ws))
      (reconnect-ws app-state (fn [w] (send app-state msg)))
      (.send ws (transit/write tw msg)))))

(defn recv
  "Receive a message from a websocket server as transit data"
  [app-state event]
  (let [s @app-state
        tr (get-in s [:transit :reader])]
    (transit/read tr event.data)))

;TODO: register new clients that are also drawing on the whiteboard
(defn register-handler
  [app-state msg]
  (.log js/console (str "register-handler called" msg)))

;TODO: handle drawing to the ui for connected clients
(defn pen-move-handler
  [app-state msg]
  (.log js/console (str "pen-move-handler" msg)))

(defn open-handler
  "A handler for :onopen event of WebSocket"
  [app-state msg]
  (swap! app-state (fn [prev] (assoc prev :connected true))))

(defn close-handler
  "A handler for :onclose event of WebSocket"
  [app-state msg]
  (swap! app-state (fn [prev] (assoc prev :connected false))))

(defn message-handler
  "A handler for :onmessage event of WebSocket"
  [app-state event]
  (let [msg (recv app-state event)]
    (ws-msg->chan app-state msg)))

(defn ws-msg->chan
  "Put data onto the [:channels :ws :from] channel"
  [app-state data]
  (let [s @app-state
        ch (get-in s [:channels :ws :from])]
    (put! ch data)))

(defn websocket-chan-handler
  "A handler for :onmessage event of WebSocket, to distinguish between them"
  [app-state msg]
  (case (:type msg)
    :register (register-handler app-state msg)
    :pen-move (pen-move-handler app-state msg)
    :none))

(defn listen-to-websocket-chan
  "Listen for messages coming from the websocket server"
  [app-state] 
  (go
    (let [s @app-state
          ch (get-in s [:channels :ws :from])]
      (loop []
        (let [data (<! ch)]
          (do
            (websocket-chan-handler app-state data)
            (recur)))))))

(defn register-client
  "Send a :registermessage to the websocket, which initializes state"
  [app-state]
  (let [s @app-state
        cid (get-in s [:client :id])
        wid (get-in s [:whiteboard :id])]
    (send app-state {:type :register
                     :client-id cid
                     :whiteboard-id wid})))
