(ns web-whiteboard.prototyping.websockets.simple-server
  "A simple example of how to create a websocket handler"
  (:gen-class)
  (:require  [ring.adapter.jetty9 :as jetty]
             [ring.middleware
             [resource :refer [wrap-resource]]
             [content-type :refer [wrap-content-type]]
             [not-modified :refer [wrap-not-modified]]
             [defaults :refer [wrap-defaults site-defaults]]]
             [cognitect.transit :as transit]
             [clojure.tools.logging :as log]
             ;[taoensso.timbre :as log]
             ))

(import [java.io ByteArrayInputStream ByteArrayOutputStream]
        [java.nio.charset StandardCharsets])

(defn simple-app [req] {:body (str "<h1>Http request works</h1>" req) :status 200})

;; TODO: Provide different log-levels so that the server can be quiet or chatty
(defn create-app-state
  []
  (atom {:port 5000
         :clients {}
         :whiteboards {}
         :ws-timeout-sec 60
         :log-level :info}))

(def app-state
  (create-app-state))

(defn register-handler
  "Handle a :register event to register a potentially new client and whiteboard"
  [app-state ws {:keys [client-id whiteboard-id]}]
  (let [s @app-state
        client (get-in s [:clients client-id] {:client-id client-id
                                               :ws ws})
        whiteboard (get-in s [:whiteboards whiteboard-id] {:whiteboard-id whiteboard-id
                                                           :clients {}})
        updated-whiteboard (assoc-in whiteboard [:clients client-id] client-id)]
    (swap! app-state (fn [prev]
                       (-> (assoc-in s [:clients client-id] client)
                           (assoc-in [:whiteboards whiteboard-id] updated-whiteboard))))))

(defn pen-move-handler
  "Handle a :pen-move message to publish it to other clients"
  [app-state ws {:keys [client-id whiteboard-id data] :as msg}]
  (let [s @app-state
        client (get-in s [:clients client-id])
        whiteboard (get-in s [:whiteboards whiteboard-id])
        valid? (not (or (nil? client) (nil? whiteboard)))]
    (when (not valid?)
      (log/warn "Invalid message received by pen-move-handler, ignoring..."))

    (when valid?
      (let [client-ids (-> (get-in s [:whiteboards whiteboard-id :clients])
                           (keys))
            filtered (filter #(not= client-id %) client-ids)
            wss (map #(get-in s [:clients % :ws]) filtered)
            out (when (not= 0 (count wss))
                  (ByteArrayOutputStream. 4096))
            tw (when (not (nil? out))
                 (transit/writer out :json))
            raw-msg (when (not (nil? tw))
                      (transit/write tw msg)
                      (.toString out))]
        (doall (map #(jetty/send! % raw-msg) wss))))))

(defn unknown-handler
  "Do something with a message with an unknown type"
  [app-state ws msg]
  (log/warn ":ws:unknown-handler: Unknown type: (" (:type msg) "). Ignoring message."))

(def dispatch-map
  {:register register-handler
   :pen-move pen-move-handler
   :default unknown-handler})

(defn- create-dispatch-handler
  "Route an incoming Transit message to the proper handler"
  [app-state]
  (fn [ws msg]
    (let [t (:type msg)
          handler (get dispatch-map t unknown-handler)]
      (handler app-state ws msg))))

;; NOTE: on-connect and on-close should let the server know which clients are actively connected
;; TODO: Cleanup :ws references on-close
(defn- create-ws-handler
  [app-state]
  (let [dispatch-handler (create-dispatch-handler app-state)]
    {:on-connect (fn [ws]
                   (log/debug ":ws:on-connect: " (str ws)))
     :on-text (fn [ws text]

                (let [source (-> (.getBytes text StandardCharsets/UTF_8)
                                 (ByteArrayInputStream.))
                      r (transit/reader source :json)
                      data (transit/read r)]
                  (log/debug ":ws:on-text: " data)
                  (dispatch-handler ws data)
                                        ;(println (jetty/req-of ws))
                  ;; TODO: Can this be removed now?
                  (jetty/send! ws text)))
     :on-close (fn [ws status-code reason]
                 (log/debug ":ws:on-close: " (str {:status-code status-code
                                                   :reason reason})))
     :on-error (fn [ws e]
                 )
     :on-bytes (fn [ws bytes offset len]
                 )}))

(def app
  (-> (wrap-defaults simple-app site-defaults)
      (wrap-resource "public")
      (wrap-content-type)
      (wrap-not-modified)))

(defn run [app-state]
  (let [{:keys [port ws-timeout-sec log-level]} @app-state
        timestamp-opts {:pattern  "yyyy-MM-dd HH:mm:ss.SSS"
                        :locale   :jvm-default
                        :timezone :jvm-default}]
    ;(log/set-level! log-level)
    ;(log/merge-config! {:timestamp-opts timestamp-opts})
    (jetty/run-jetty app {:port port
                          :websockets {"/echo" (create-ws-handler app-state)}
                          :ws-max-idle-time (* ws-timeout-sec 1000)})))

(defn -main [& args]
  (run (create-app-state)))
