(ns web-whiteboard.client.ui.core
  "Common functions needed by different parts of the user interface for the client application"
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async
             :refer [>! <! put! chan alts!]]))

(def constants
  "Useful configuration constants"
  {:size-picker {:min 1
                 :max 31
                 :step 3}})

(defn ui-action->chan
  "Puts a ui-action onto a channel"
  [chan ui-action]
  (go
    (>! chan ui-action)))

(defn stroke->ui-chan
  "Logically puts a stroke onto the ui-chan, as a series of puts for each ui-action in the stroke list"
  [app-state stroke]
  (let [s @app-state
        ui-chan (get-in s [:channels :ui :to])]
    (go
      (loop [cur (first stroke)
             rem (rest stroke)]
        (when (not (empty? cur))
          (>! ui-chan cur)
          (recur (first rem) (rest rem)))))))

(defn ui-action->chan-helper
  "Convenience function to specify which channel to put to through a list of keys in app-state"
  [app-state ui-action key-path]
  (let [s @app-state
        ch (get-in s key-path)]
    (ui-action->chan ch ui-action)))

(defn ui-action->ui-chan
  [app-state ui-action]
  (ui-action->chan-helper app-state ui-action [:channels :ui :to]))

(defn ui-action->ws-chan
  [app-state ui-action]
  (ui-action->chan-helper app-state ui-action [:channels :ws-server :to]))

(defn put-ui-action-on-ui-and-ws-chans
  [app-state ui-action]
  (ui-action->ws-chan app-state ui-action)
  (ui-action->ui-chan app-state ui-action))

(defn publish-ui-action
  "Use app-state to associate :client-id and :whiteboard-id with a desired ui-action

  Puts the ui-action on the ui and ws channels."
  [app-state ui-action]
  (let [s @app-state
        cid (get-in s [:client :id])
        wid (get-in s [:whiteboard :id])
        ui-action (merge {:client-id cid
                          :whiteboard-id wid}
                         ui-action)]
    (put-ui-action-on-ui-and-ws-chans app-state ui-action)))
