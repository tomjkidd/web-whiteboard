(ns web-whiteboard.server.core
  "The application server used to handle http and ws communication with clients"
  (:gen-class)
  (:require [ring.adapter.jetty9 :as jetty]
            [immuconf.config :as config]
            [com.stuartsierra.component :as component]
            [web-whiteboard.server.handlers.http :as http]
            [web-whiteboard.server.handlers.websocket :as ws]))

(def conf
  "Get the values to use to configure the web server"
  (config/load "resources/config.edn"
               "resources/dev.edn"))

(defn create-app-state
  "Create an atom for the state that the web server needs to maintain"
  []
  (atom {:port (config/get conf :server :port)
         :clients {}
         :whiteboards {}
         :ws-timeout-sec (config/get conf :server :ws-timeout-sec)
         :log-level (config/get conf :server :log-level)
         :join? (config/get conf :server :join?)}))

(defn run 
  "Start a Jetty9 web server"
  [app-state]
  (let [{:keys [port ws-timeout-sec log-level join?]} @app-state]
    (jetty/run-jetty http/http-handler
                     {:port port
                      :join? join?
                      :websockets {"/ws/whiteboard" (ws/create-ws-handler app-state)}
                      :ws-max-idle-time (* ws-timeout-sec 1000)})))

(defrecord Server [state]
  component/Lifecycle
  (start [component]
    (swap! (:state component) (fn [prev] (assoc-in prev [:server] (run state))))
    component)
  (stop [component]
    (let [state (:state component)
          server (:server @state)]
      (jetty/stop-server server)
      (swap! state (fn [prev]
                     (-> (assoc prev :clients {})
                         (assoc :whiteboards {})
                         (dissoc :server))))
      component)))

(defn create-server
  "Create a Server component"
  []
  (map->Server {:state (create-app-state)}))

(defn -main
  "Create and start the web server"
  [& args]
  (let [server (create-server)]
    (.start server)))
