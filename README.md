# Timers

[![Clojars
Project](https://img.shields.io/clojars/v/dvlopt/timer.cljs.svg)](https://clojars.org/dvlopt/timer.cljs)

[![cljdoc
badge](https://cljdoc.org/badge/dvlopt/timer.cljs)](https://cljdoc.org/d/dvlopt/timer.cljs)

Scheduling asynchronous operations in Clojurescript can be tedious. There exists
quite a few ways depending on what is needed and they often have pitfalls (eg.
infamous `.setInterval` is clamped to run at most once per second when the tab
is not focused).

This library aims to clarify the various existing ways of async scheduling
while solving such problems.

## Usage

After reading the following overview, see the
[API](https://developer.mozilla.org/en-US/docs/Web/API/window/requestAnimationFrame).

Let us require the library:

```clj
(require '[dvlopt.timer :as timer])

```
### "Macro" tasks

Or simply "tasks", are units of computations enqueued in the event loop.

#### At some point in the future

A task can be scheduled to run in, say, 2000 milliseconds:

```clojure
(timer/in timer/main-thread
          2000
          (fn my-task []
            (println "Hello world!")))
```

Or every 2000 milliseconds:

```clojure
(timer/every timer/main-thread
             2000
             (fn repeated-task []
               (println "Hello... again, and again..."))
             (fn on-lag [delta]
               ;; Optional
               ))
               
```

This periodic execution will run some drift protection, trying to ensure as
precisely as possible that there will be a consistent time difference of 2000
milliseconds between each call, in this instance. If a run takes more time than
what has been requested, then the whole process is running late and nothing can
be done. In consequence, the execution stops and the optional `on-lag` function is
called with a value expressing how late it is running (eg. -143 means "143
milliseconds late"). The user can then decide what to do.

Both `in` and `every` return a token which can be used to cancel what has been
scheduled:

```clojure
(timer/cancel timer/main-thread
              my-token)
```

There is a caveat. Such timers scheduled on the main thread, as we did, may
exhibit unwanted behavior. For instance, as previously mentioned, intervals are
clamped to at least 1000 milliseconds while the tab is inactive. A solution is
to use a worker:

```clojure
(timer/worker)

;; Instead of

timer/main-thread
```

Using a worker, scheduling happens via a Web Worker and is typically more
reliable. However, the execution itself still happens on the main thread. Using
a worker in such a fashion is surprisingly robust, enough to handle
time-sensitive operation such as generating music.


#### As soon as possible

A task can be simply enqueued to the event loop and it will be executed when it
is its turn. One benefit of doing so would be to divide a long running
computation into more granular units as to not hog the main thread for too long.

```clojure
(timer/task (fn my-task []
              (println "Cool!")))
```

#### On the next frame

Especially useful for animation, a function can be scheduled to run prior to the
next screen refresh in order to compute what is needed for the next frame.
Anything scheduled will not run while the tab is not focused as there is nothing
to see, hence no frame to generate. The timestamp provided to the function must
be used for all time-dependent computations.

```clojure
(def token
     (timer/frame (fn on-frame [timestamp]
                    ;; Computes and draws the next frame
                    )))

;; If needed

(timer/cancel-frame token)
```

It is more common to run a loop in order to animate a series of frames. The
plural form is then useful, a cancelling function being provided as well:

```clojure
(def cancel
     (timer/frames (fn on-frame [timestamp cancel]
                     ;; Number crunching, animating
                     )))

;; When done
(cancel)
```

For the drawing itself, on canvas, we recommend
[dvlopt/draw](https://github.com/dvlopt/draw.cljs).


### Micro tasks

In between 2 regular tasks in the event loop (what has been discussed up to this
point), the Javascript engine runs the "micro task queue". A micro task can
schedule another micro task which will be appended to that same queue. Only when that queue is empty will the engine proceeed to the next regular task.

Micro tasks are useful for scheduling high-priority asynchronous operations
garanteed to execute in order of scheduling before the next regular task. While
this sounds esoteric, it can be particularly useful. For instance, the
ubiquitous
[Promise](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Promise)
is actually implemented using micro tasks.

```clojure
(timer/micro-task (fn my-micro-task []
                    (println "Pretty dope.")))
```

See [this
guide](https://developer.mozilla.org/en-US/docs/Web/API/HTML_DOM_API/Microtask_guide)
for a complete breakdown of the differences between micro tasks and regular
ones.

## Miscellaneous

When it comes to music, there are yet corners to explore.

The `Web Audio` and `Web MIDI` APIs provides accurate timers for specific tasks
such as generating a sound or sending a MIDI event. However, those events are
scheduled for a precise moment, and sometimes, once scheduled, cannot be
canceled. A proven method is to combine those precise, specific timers with this
library (using above-mentioned workers) in order the gain very fine control. This idea is not new, it has
already been described back in 2013 in [this
article](https://www.html5rocks.com/en/tutorials/audio/scheduling/). We like to
call it "look ahead scheduling", or "double scheduling".


## License

Copyright © 2020 Adam Helinski

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
