(ns web-whiteboard.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware
             [resource :refer [wrap-resource]]
             [content-type :refer [wrap-content-type]]
             [not-modified :refer [wrap-not-modified]]
             [defaults :refer [wrap-defaults site-defaults]]]))

(defroutes app-routes
  (GET "/" [] "Hello World")
  (route/not-found "Not Found"))

(def app
  (-> (wrap-defaults app-routes site-defaults)
      (wrap-resource "public")
      (wrap-content-type)
      (wrap-not-modified)))
