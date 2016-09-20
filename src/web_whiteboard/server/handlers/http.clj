(ns web-whiteboard.server.handlers.http
  "The http handlers for the web server"
  (:require [ring.middleware
             [resource :refer [wrap-resource]]
             [content-type :refer [wrap-content-type]]
             [not-modified :refer [wrap-not-modified]]
             [defaults :refer [wrap-defaults site-defaults]]]
            [compojure.core :refer [defroutes GET]]
            [compojure.route :as route]))

(defroutes rest-api
  (route/not-found "<h1>Page not found</h1>"))

(def http-handler
  (-> (wrap-defaults rest-api site-defaults)
      (wrap-resource "public")
      (wrap-content-type)
      (wrap-not-modified)))
