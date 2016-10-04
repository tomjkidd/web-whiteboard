# web-whiteboard

A clojure/clojurescript whiteboard for interactive drawing in the browser

## Overview

This project is meant to create a clojure system that can allow multiple
clients to collaboratively draw together.

It is not meant to be a drawing tool like Photoshop, but simply a drawing
surface that allows you access to color and size with little else.

![web-whiteboard demo gif](./demo.gif)

The next pieces will be:

1. Create a web server that can stream svg events via websockets -> DONE

2. Use [tangoclj](https://github.com/tomjkidd/tangoclj/tree/atomic-runtime)
   on the server to handle coordinating with multiple clients.

3. Grow out the idea some more to allow saving, replay, etc.

4. Improve the line drawing mode. [Jack Schaedler](https://jackschaedler.github.io/handwriting-recognition/)
   wrote a great article that demonstrates a few neat algorithms that
   are simple and would improve the perceived smoothness of strokes. -> DONE as :smooth-line mode
   
5. Create more drawing modes.

## Setup

Build the ClojureScript

```
lein cljsbuild once
```

Run the web server (which serves resources/public)

```
lein run
```

Then just go to the link

[http://localhost:5000/index.html?wid=a&cid=a](http://localhost:5000/index.html?wid=a&cid=a)

Open another tab and go to the link

[http://localhost:5000/index.html?wid=a&cid=b](http://localhost:5000/index.html?wid=a&cid=b)

This will connect two different clients (cid) to the same whiteboard (wid).
Drawing done in one should stream to the other.

## License

Copyright © 2016 Tom Kidd

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
