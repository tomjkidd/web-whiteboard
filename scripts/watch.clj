(require '[cljs.build.api :as b])

(b/watch "src"
  {:main 'web-whiteboard.core
   :output-to "out/web_whiteboard.js"
   :output-dir "out"})
