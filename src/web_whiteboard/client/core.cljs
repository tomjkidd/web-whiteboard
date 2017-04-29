(ns web-whiteboard.client.core
  "Code to support creating the web-whiteboard client app"
  
  (:require [web-whiteboard.client.state :as state]
            [web-whiteboard.client.ui :as ui]
            [web-whiteboard.client.handlers.websocket :as hws]))

(defn ^:export run
  "The main function to run the client app"
  []
  (let [app-state (state/create-app-state state/ws-url)]
    (hws/listen-to-websocket-to-chan app-state)
    (ui/create-ui app-state)))                                   
