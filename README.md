# web-whiteboard

A clojure/clojurescript whiteboard for interactive drawing in the browser

## Overview

This project is meant to create a clojure system that can allow multiple
clients to collaboratively draw together.

Currently, I am working out the kinks in going from a JavaScript version
to a ClojureScript version on a single client.

The next pieces will be:
1. Create a web server that can stream svg events via websockets
2. Use [tangoclj]() on the server to handle coordinating with multiple
   clients.
3. Grow out the idea some more to allow saving, replay, etc.

## Setup

In order to build

```
lein cljsbuild once
```

Currently, just use a web server to server the resources/public directory.
The two demos should show the evolution, and I might follow up with a
blog about my insights in doing the migration.

## License

Copyright © 2016 Tom Kidd

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
