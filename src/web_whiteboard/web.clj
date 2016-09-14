(ns web-whiteboard.web
  (:gen-class)
  (:require  [ring.adapter.jetty9 :as jetty]
             [web-whiteboard.handler :as handler]))

;; TODO: In order to send to anyone that connects, caputure ws on connect
;; then update an atom to keep track of the connected sockets.

(def ws-handler {:on-text (fn [ws text]
                              (println text)
                              (jetty/send! ws text))})

(defn -main [& args]
  (jetty/run-jetty handler/app {:port 5000
                                :websockets {"/ws/whiteboard" ws-handler}}))
