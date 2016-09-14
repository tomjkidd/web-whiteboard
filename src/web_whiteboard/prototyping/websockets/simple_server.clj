(ns web-whiteboard.prototyping.websockets.simple-server
  "A simple example of how to create a websocket handler"
  (:gen-class)
  (:require  [ring.adapter.jetty9 :as jetty]))

;; This is based on the [example code](https://github.com/sunng87/ring-jetty9-adapter/blob/master/examples/rj9a/websocket.clj) provided in the ring-jetty9-adapter source

(defn simple-app [req] {:body "<h1>Http request works</h1>" :status 200})

(def echo-handler
  {:on-connect (fn [ws]
                 (println ":on-connect ws: " (str ws)))
   :on-text (fn [ws text]
              (println ":on-text ws: " (str ws \newline text))
              (jetty/send! ws text))
   :on-close (fn [ws status-code reason]
               (println ":on-close ws: " (str ws \newline status-code \newline reason)))
   :on-error (fn [ws e]
               )
   :on-bytes (fn [ws bytes offset len]
               )})

(defn websocket-accept [req]
  echo-handler)

(defn websocket-reject [req]
  {:error {:code 403 :message "Forbidden"}})

(defn -main [& args]
  (jetty/run-jetty simple-app {:port 5000 :websockets {"/echo" echo-handler
                                                       "/accept" websocket-accept
                                                       "/reject" websocket-reject}}))
