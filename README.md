# Timers

Provides sane alternatives to `setTimeout` and `setInterval` in web apps.

Timers in the browser can be somewhat imprecise. They are scheduled on the main
thread and and subject to throttling. For instance, when the tab in inactive,
all timers executes at most once per second, which is often not at all what is
intended. Even `core.async` timeouts suffers from this.

This libary overcomes such problems and improves the precision of timers by
executing on the main thread but scheduling using a web worker.


## Usage

Let us require the library:

```clj
(require '[dvlopt.timer :as timer])
```

First, we create a worker. Since all this worker handles is scheduling, one
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

## License

Copyright © 2020 Adam Helinski

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
