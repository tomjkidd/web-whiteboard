(ns web-whiteboard.client.file-export
  (:require [carafe.dom :as dom]))

(defn save-file
  "Save a file"
  [{:keys [filename type data] :as blob}]
  (let [b (js/Blob. #js [data] #js {"type" type})
        url (.createObjectURL js/URL b)
        link (.createElement js/document "a")]
    (set! (.-href link) url)
    (set! (.-download link) filename)
    (.appendChild js/document.body link)
    (.click link)
    (.removeChild js/document.body link)))

(defn save-svg
  "Save a file as an SVG"
  [{:keys [filename data] :as blob}]
  (save-file {:filename filename
              :type "image/svg+xml;charset=utf-8"
              :data data}))

(defn save-as-svg
  "Save an svg element as an SVG file"
  [element filename]
  (let [xml-serializer (js/XMLSerializer.)
        data (.serializeToString xml-serializer element)]
    (save-svg {:filename filename
               :data data})))
