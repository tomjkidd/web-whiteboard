(ns web-whiteboard.server.handlers.websocket
  "The websocket handlers for the web server"
  (:require [ring.adapter.jetty9 :as jetty]
            [cognitect.transit :as transit]
            [clojure.tools.logging :as log]))

(import [java.io ByteArrayInputStream ByteArrayOutputStream]
        [java.nio.charset StandardCharsets])

(defn register-handler
  "Handle a :register event to register a potentially new client and whiteboard"
  [app-state ws {:keys [client-id whiteboard-id]}]
  (log/info "Registered:" client-id ":" whiteboard-id)
  (let [s @app-state
        client (get-in s
                       [:clients client-id]
                       {:client-id client-id
                        :ws nil})
        whiteboard (get-in s
                           [:whiteboards whiteboard-id]
                           {:whiteboard-id whiteboard-id
                            :clients {}})
        updated-client (assoc-in client [:ws] ws)
        updated-whiteboard (assoc-in whiteboard [:clients client-id] client-id)]
    (swap! app-state (fn [prev]
                       (-> (assoc-in s [:clients client-id] updated-client)
                           (assoc-in [:whiteboards whiteboard-id] updated-whiteboard))))))

(defn msg-handler
  "Handle message by publishing it to the other connected clients"
  [app-state ws {:keys [client-id whiteboard-id data] :as msg}]
  (let [s @app-state
        client (get-in s [:clients client-id])
        whiteboard (get-in s [:whiteboards whiteboard-id])
        valid? (not (or (nil? client) (nil? whiteboard)))]
    (when (not valid?)
      (log/warn "Invalid message received by msg-handler, ignoring..."))

    (when valid?
      (let [client-ids (-> (get-in s [:whiteboards whiteboard-id :clients])
                           (keys))
            filtered (filter #(not= client-id %) client-ids)
            wss (->> filtered
                     (map #(get-in s [:clients % :ws]))
                     (filter #(.isConnected %)))
            out (when (not= 0 (count wss))
                  (ByteArrayOutputStream. 4096))
            tw (when (not (nil? out))
                 (transit/writer out :json))
            transit-msg (when (not (nil? tw))
                  (transit/write tw msg)
                  (.toString out))]
        (doall (map #(try
                       (jetty/send! % transit-msg)
                       (catch Exception e (log/warn "TODO: Handle msg-handler exception:" (.toString e)))) wss))))))

(defn unknown-handler
  "Do something with a message with an unknown type"
  [app-state ws msg]
  (log/warn ":ws:unknown-handler: Unknown type: (" (:type msg) "). Ignoring message."))

(def dispatch-map
  {:register register-handler
   :pen-move msg-handler
   :pen-down msg-handler
   :pen-up msg-handler
   :clear-canvas msg-handler
   :default unknown-handler})

(defn- create-dispatch-handler
  "Route an incoming Transit message to the proper handler"
  [app-state]
  (fn [ws msg]
    (let [t (:type msg)
          handler (get dispatch-map t unknown-handler)]
      (handler app-state ws msg))))

(defn transit-decode
  "Decode a transit :json message"
  [transit-msg]
  (let [source (-> (.getBytes transit-msg StandardCharsets/UTF_8)
                   (ByteArrayInputStream.))
        r (transit/reader source :json)
        msg (transit/read r)]
    msg))

;; NOTE: on-connect and on-close should let the server know which clients are actively connected
;; TODO: Cleanup :ws references on-close
(defn create-ws-handler
  [app-state]
  (let [dispatch-handler (create-dispatch-handler app-state)]
    {:on-connect (fn [ws]
                   (log/debug ":ws:on-connect: " (str ws)))
     :on-text (fn [ws transit-msg]
                (let [msg (transit-decode transit-msg)]
                  (log/debug ":ws:on-text: " msg)
                  (dispatch-handler ws msg)

                  ;; TODO: Can this be removed now?
                  (jetty/send! ws transit-msg)))
     :on-close (fn [ws status-code reason]
                 (log/debug ":ws:on-close: " (str {:status-code status-code
                                                   :reason reason})))
     :on-error (fn [ws e]
                 (log/error ":ws:on-error: " (str e)))
     :on-bytes (fn [ws bytes offset len]
                 )}))
