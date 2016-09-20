(ns user
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :refer (refresh)]
            [web-whiteboard.prototyping.websockets.simple-server :as ss]))
;; TODO: Integrate all of the system stuff...
;; (def system nil)

;; (defn init []
;;   (alter-var-root #'system
;;     (constantly (app/example-system {:host "dbhost.com" :port 123}))))

;; (defn start []
;;   (alter-var-root #'system component/start))

;; (defn stop []
;;   (alter-var-root #'system
;;     (fn [s] (when s (component/stop s)))))

;; (defn go []
;;   (init)
;;   (start))

;; (defn reset []
;;   (stop)
;;   (refresh :after 'user/go))

(def server
  (ss/create-server))

(def running-state
  (atom :stopped))

(defn starts
  []
  (.start server)
  (reset! running-state :started))

(defn stops
  []
  (.stop server)
  (reset! running-state :stopped))

(defn toggle
  []
  (let [f (if (= :stopped @running-state)
            starts
            stops)]
    (f)))
