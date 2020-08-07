(ns dvlopt.timer

  "Utilities for scheduling async operations using various APIs.
  
   See README."
  
  {:author "Adam Helinski"}

  (:import goog.structs.Queue))


;;;;;;;;;; Miscellaneous



(defn epoch

  "Returns the current Unix epoch in milliseconds."

  []

  (js/Date.now))



(defn now

  "High resolution timestamp garanteed to be monotonically increasing, usually preferred over [[epoch]].
  
   Returns the number of milliseconds elapsed since the time origin. Fractional part, if present, represents fractions
   of a millisecond and should be accurate to 5 microseconds. Garanteed precision is 1 millisecond unless the user-agent
   deliberately tweaked this option.
  
   Cf. [Time origin in MDN](https://developer.mozilla.org/en-US/docs/Web/API/DOMHighResTimeStamp#The_time_origin)"

  []

  (js/performance.now))


;;;;;;;;;; .setInterval and .setTimeout


(defn- -delta!

  ;; Used by [[Worker]] as well as [[main-thread]].

  [start v*n millis]

  (Math/ceil (- (+ start
                   (* (vswap! v*n
                              inc)
                      millis))
                (now))))



(def ^:private -worker-src

  ;; Source code for the web worker in charge of creating and cancelling timers.

  "onmessage=(e)=>{var x=e.data[1]; if (x){setTimeout(function(){postMessage(x)},e.data[0])} else{clearTimeout(e.data[0])}}")



(defprotocol ^:private -IWorker

  ;; Base method for creating a a timer in a worker.

  (-in [this token millis f]))



(defprotocol ITimer

  "Scheduling something at some relatively precise point in the future.

   Precision depends on current activity and can be improved by using a [[worker]] rather than the [[main-thread]].
  
   See README."

  (cancel [this token]
    "Cancels a timer by using its token.
    
     See [[in]] and [[every]].")

  (in [this millis f]
    "Executes `f` in `millis` milliseconds.
    
     Returns a token which can be used in [[cancel]] for effectively clearing this timer.")

  (every [this millis f]
         [this millis f on-lag]
    "Executes `f` every `millis` milliseconds.

     Offers drift protection so that each run is performed at a fixed interval, regarless of how long the previous run took.
     For instance, starting at 0 with an interval of 5000 milliseconds, the timeline should be: 5000, 10000, 15000, 20000, ...

     If a run takes more time than the given interval, execution is lagging, in which case any further scheduling is stopped and
     `on-lag` (if provided) is called with 1 argument: a negative value denotating the lag in milliseconds (eg. -143 means \"143 milliseconds
     late\").
    
     Returns a token for cancellation (see [[in]])."))



(def main-thread

  "Object implementing [[ITimer]] functions for scheduling on the main thread."

  (reify ITimer

    (cancel [this token]
      (js/clearTimeout (if (volatile? token)
                         @token
                         token))
      this)


    (in [this millis f]
      (js/setTimeout f
                     millis))


    (every [this millis f]
      (every this
             millis
             f
             nil))


    (every [this millis f on-lag]
      (let [v*n     (volatile! 1)
            v*token (volatile! nil)
            start   (now)]
        (vreset! v*token
                 (in this
                     millis
                     (fn each-time []
                       (f)
                       (let [delta (-delta! start
                                            v*n
                                            millis)]
                         (if (neg? delta)
                           (when on-lag
                             (on-lag delta))
                           (vreset! v*token
                                    (in this
                                        delta
                                        each-time)))))))
        v*token))))



(deftype Worker [^:mutable token-id
                 token->callbacks
                 worker]

  -IWorker


    (-in [this v*token millis f]
      (let [token-id-cached token-id]
        (.set token->callbacks
              token-id-cached
              f)
        (set! token-id
              (if (< token-id-cached
                     js/Number.MAX_SAFE_INTEGER)
                (inc token-id-cached)
                1))
        (.postMessage worker
                      #js [millis
                           token-id-cached])
        (vreset! v*token
                 token-id-cached)
        v*token))



  ITimer


    (cancel [this v*token]
      (let [token-id @v*token]
        (when (.get token->callbacks
                    token-id)
          (.delete token->callbacks
                   token-id)
          (.postMessage worker
                        #js [token-id])))
      this)


    (in [this millis f]
      (-in this
           (volatile! nil)
           millis
           f))


    (every [this millis f]
      (every this
             millis
             f
             nil))


    (every [this millis f on-lag]
      (let [v*n     (volatile! 1)
            v*token (volatile! nil)
            start   (now)]
        (-in this
             v*token
             millis
             (fn each-time []
               (f)
               (let [delta (-delta! start
                                    v*n
                                    millis)]
                 (if (neg? delta)
                   (when on-lag
                     (on-lag delta))
                   (-in this
                        v*token
                        delta
                        each-time))))))))



(defn worker

  "Creates a new worker which can be used for scheduling more precisely than on the main thread.
  
   Browser only.
  
   See [[ITimer]] functions."

  []

  (let [token->callbacks (js/Map.)]
    (Worker. 1
             token->callbacks
             (let [w (js/Worker. (js/window.URL.createObjectURL (js/Blob. #js [-worker-src])))]
               (set! (.-onmessage w)
                     (fn on-message [event]
                       (let [token (.-data event)]
                         (when-some [callback (.get token->callbacks
                                                    token)]
                           (.delete token->callbacks
                                    token)
                           (callback)))))
               w))))


;;;;;;;;;; "Immediate" async tasks


(defn micro-task

  "Enqueues `f` to run asynchronously as a micro task.

   See README for the difference between a regular task and a micro-task."

  [f]

  (js/queueMicrotask f)
  nil)



(def ^:private -task-queue

  ;; Queue for regular tasks.

  (Queue.))



(def ^:private -port

  ;; Used by [[task]] to schedule a task.

  (let [message-channel (js/MessageChannel.)
        port-1          (.-port1 message-channel)]
    (.addEventListener port-1
                       "message"
                       (fn next-task [_event]
                         (when-some [f (.peek -task-queue)]
                           (.dequeue -task-queue)
                           (f))))
    (.start port-1)
    (.-port2 message-channel)))



(defn task

  "Enqueues `f` to run asynchronously as a regular task.
  
   See README for the difference between a regular task and a micro-task."

  [f]

  (.enqueue -task-queue
            f)
  (.postMessage -port
                nil)
  nil)


;;;;;;;;;; .requestAnimationFrame



(defn cancel-frame

  "See [[frame]]."

  [token]

  (js/cancelAnimationFrame token)
  nil)



(defn frame

  "Schedules `f` to run when the screen is about to be refreshed. Current timestamp, as it would have been
   returned by [[now]], is provided as sole argument and any time-dependent computation should rely on it.

   Same timestamp is provided to all functions scheduled for a given frame so that that all computations are
   in sync.
  
   Returns a token which can be used to cancel this via [[cancel-frame]].

   Cf. [.requestAnimationFrame](https://developer.mozilla.org/en-US/docs/Web/API/window/requestAnimationFrame)"

  [f]

  (js/requestAnimationFrame f))



(defn frames

  "Like [[frame]], but schedules `f` repeatedly for every frame.

   Returns a no-arg function which can be called to cancel this. Same function is provided as a second argument
   to `f`."

  [f]

  (let [v*token       (volatile! nil)
        cancel-frames (fn cancel-frames []
                        (some-> @v*token
                                cancel-frame))]
    (frame (fn run [timestamp]
             (vreset! v*token
                      (frame run))
             (f timestamp
                cancel-frames)))
    cancel-frames))
