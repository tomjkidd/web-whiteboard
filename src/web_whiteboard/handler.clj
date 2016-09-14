(ns web-whiteboard.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]))

(use 'ring.middleware.resource
     'ring.middleware.content-type
     'ring.middleware.not-modified)

(defroutes app-routes
  (GET "/" [] "Hello World")
  (route/not-found "Not Found"))

(def app
  (-> (wrap-defaults app-routes site-defaults)
      (wrap-resource "public")
      (wrap-content-type)
      (wrap-not-modified)))
