(ns web-whiteboard.prototyping.websockets.simple-server
  "A simple example of how to create a websocket handler"
  (:gen-class)
  (:require  [ring.adapter.jetty9 :as jetty]
             [ring.middleware
             [resource :refer [wrap-resource]]
             [content-type :refer [wrap-content-type]]
             [not-modified :refer [wrap-not-modified]]
             [defaults :refer [wrap-defaults site-defaults]]]
             [cognitect.transit :as transit]))

(import [java.io ByteArrayInputStream]
        [java.nio.charset StandardCharsets])


;; This is based on the [example code](https://github.com/sunng87/ring-jetty9-adapter/blob/master/examples/rj9a/websocket.clj) provided in the ring-jetty9-adapter source

(defn simple-app [req] {:body (str "<h1>Http request works</h1>" req) :status 200})

;; NOTE: on-connect and on-close should let the server know which clients are actively connected
(def echo-handler
  {:on-connect (fn [ws]
                 (println ":on-connect ws: " (str ws)))
   :on-text (fn [ws text]
              
              (let [source (-> (.getBytes text StandardCharsets/UTF_8)
                          (ByteArrayInputStream.))
                    r (transit/reader source :json)
                    data (transit/read r)]
                (println ":on-text ws: " (str ws \newline data))
                                        ;(println (jetty/req-of ws))
                (jetty/send! ws text)))
   :on-close (fn [ws status-code reason]
               (println ":on-close ws: " (str ws \newline status-code \newline reason)))
   :on-error (fn [ws e]
               )
   :on-bytes (fn [ws bytes offset len]
               )})

;; TODO: Provide different log-levels so that the server can be quiet or chatty
(def default-server-options
  {:ws-timeout-sec 10
   :log-level :info})

(defn websocket-accept [req]
  echo-handler)

(defn websocket-reject [req]
  {:error {:code 403 :message "Forbidden"}})

(def app
  (-> (wrap-defaults simple-app site-defaults)
      (wrap-resource "public")
      (wrap-content-type)
      (wrap-not-modified)))

(defn -main [& args]
  (let [{:keys [ws-timeout-sec]} default-server-options]
    (jetty/run-jetty app {:port 5000
                          :websockets {"/echo" echo-handler
                                       "/accept" websocket-accept
                                       "/reject" websocket-reject}
                          :ws-max-idle-time (* ws-timeout-sec 1000)})))
