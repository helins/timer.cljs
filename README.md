# Timers

[![Clojars
Project](https://img.shields.io/clojars/v/dvlopt/timer.cljs.svg)](https://clojars.org/dvlopt/timer.cljs)

[![cljdoc
badge](https://cljdoc.org/badge/dvlopt/timer.cljs)](https://cljdoc.org/d/dvlopt/timer.cljs)

Timers in the browser can be somewhat imprecise. They are scheduled on the main
thread and subject to throttling. For instance, when the tab in inactive,
all timers execute at most once per second, which is often not at all what is
intended. Even `core.async` timeouts suffer from this.

This libary overcomes such problems and improves the precision of timers by
executing on the main thread but scheduling in a web worker. It still relies on
the ol' `.setTimeout` and `.setInterval`, but in a way that makes them more
reliable.

## Usage

Let us require the library:

```clj
(require '[dvlopt.timer :as timer])
```

First, we create a worker. Since all this worker will handle is scheduling, one
worker will typically be more than enough for a whole application.

```clj
(def worker
     (timer/worker))
```

Here, we schedule a function in 2000 milliseconds:


```clj
(timer/in worker
          2000
          (fn execute []
            (println "Hello world!")))
```

We can schedule a function to be executed periodically. It will offer drift
protection so that each run is performed at a fixed interval, regarless of how
long the previous run took.  For instance, imagining we start at 0 with an
interval of 5000 milliseconds, the timeline should be: 5000, 10000, 15000,
20000, ...

If a run takes more time than the given interval, execution is lagging, in which
case any further scheduling is stopped and `on-lag` (if provided) is called with
1 argument: a negative value denotating the lag in milliseconds (eg. -143 means
"143 milliseconds late").

```clj
(timer/every worker
             5000
             (fn execute []
               (println "Hello again!"))
             (fn on-lag [delay-ms]
               (println "Lag!")))
```

Each call to `in` and `every` returns a token which can be used for cancelling
what has been scheduled:

```clj
(let [token (timer/in worker
                      500
                      (fn []
                        (println "I will never be called")))]
  (timer/cancel worker
                token))
```

Finally, `dvlopt.timer/now` returns a high resolution timestamp garanteed to be
monotonically increasing. More precisely, it returns the number of milliseconds
elapsed since the [time
origin](https://developer.mozilla.org/en-US/docs/Web/API/DOMHighResTimeStamp#The_time_origin).
The fractional part, if present, represents fractions of a millisecond and
should be accurate to 5 microseconds.

## Few notes and miscellaneous ideas

This method, while being simple, is probably the best way for having
general-purpose timers in the browser.

`requestAnimationFrame` has been known for being used to complete repetitive,
somewhat fine-grained tasks. As its main purpose is animation, timing is fixed
but it can be remarkbly predictable if the main thread is not overwhelmed.
However, execution is stopped as soon as the tab becomes inactive, making it
often unsuitable for any other purpose than animation.

The `Web Audio` and `Web MIDI` APIs provides accurate timers for specific tasks
such as generating a sound or sending a MIDI event. However, those events are
scheduled for a precise moment, and sometimes, once scheduled, cannot be
canceled. A proven method is to combine those precise, specific timers with this
library in order the gain very fine control. This idea is not new, it has
already been described back in 2013 in [this
article](https://www.html5rocks.com/en/tutorials/audio/scheduling/). We like to
call it "look ahead scheduling", or "double scheduling".


## License

Copyright © 2020 Adam Helinski

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
