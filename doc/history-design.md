# The Why

When I am drawing, I sometimes goof on the picture, and the ability to be able
to undo a goof would be a nice feature.

# The What

I would like to add the ability to undo strokes in drawings.

To implement this feature might require re-thinking drawing modes a little.

NOTE: Is it valuable to have multiple modes? For now they are there, but they
can be reverted if they get in the way.

NOTE: Do the same events happen for each mode? Yes, they do. So it should not 
matter.

## Current design description

The `client.ui/create-drawing-ui` function creates a ui that will create 
[:pen-down :pen-move :pen-up] events.
The `client.ui/pen-event-handler` depends on the mode, which is usually
the :smooth-line mode, which defers to `client.draw.line/event-handler`,
which defers to `client.draw.core/base-pen-event-handler` using `event->data`
to create our pen event.

`PenEvent` data is a HashMap with the following keys

```
PenEvent :: HashMap
  :mode :: Keyword
  :id :: String
  :cx :: Number
  :cy :: Number
  :r :: Number
  :fill :: String
```

The `base-pen-event-handler` wraps the PenEvent data into a `UiAction`, and
sends the UiAction to the local ui-chan and to the to-ws-server-chan so that
other connected clients can update.

`UiAction` data is a HashMap with the following keys

```
UiAction :: HashMap
  :type :: Keyword
  :client-id :: String
  :whiteboard-id :: String
  :data :: PenEvent
```

The `client.ui/listen-to-ui-chan` registers a channel to respond to `UiAction`s.
with the `ui-chan-handler` function. The local and remote events all come in
on ui-chan, so the interface has a unified response to each client.

## Strokes

Based on the current design, every stroke the user takes can be defined as
a list of `UiAction`s, staring with a :pen-down, followed by zero or more
:pen-move, ending with a :pen-up.

`Stroke` data is a List with the following structure

```
Stroke :: List [UiAction]
  :pen-down `UiAction`
  [:pen-move `UiAction`]*
  :pen-up `UiAction`
```

## History state

Based on the definition of `Stroke`, we can partition each client, by their
:client-id, and keep a stack of `Stroke`s for each. By keeping an initially
empty stack for undo/redo, that should be all of the state required to provide
enough information to the system.

`HistoryMap` is a HashMap where keys correspond to :client-id and values
are instances of `HistoryMapEntry`.

`HistoryMapEntry` is also a HashMap with the following keys

```
HistroyMapEntry :: HashMap
  :stroke-stack :: Stack[Stroke]
  :undo-stack :: Stack[Stroke]
```

At this point, I figured it might be nice to actually capture some of this
structure as code, so I added Prismatic/schema to the project and a [data definition file](../src/web_whiteboard/client/schema/core.cljc).

# The How

In order to achieve undo, a new UiAction needs to be created to communicate
when a client wants to perform one, give it the type :undo-stroke.

Also, each client will have to store each of the existing UiAction types
in the HistroyMap as Strokes in the :stroke-stack for each client.

Once the ui can send :undo-stroke UiActions, we can use the :client-id of
the UiAction to determine which client in the HistroyMap to update. The update
consists of popping the ui-action from the :stroke-stack and pushing it
into the :undo-stack. It also requires undoing the ui-action in the actual
canvas. I think this will have to be specific to each drawing mode... For
smooth-line mode, which is where I'll focus the effort, there is an svg path
created for each line. With the pen-down, `(first stroke)`, we should have
access to the id of the path, and we can grad and remove it from the drawing.

Redo should also work in a simple manner. All that is needed is to pop the 
stroke off of the :undo-stack and put all of the ui-actions of the stroke
onto the ui-chan.
