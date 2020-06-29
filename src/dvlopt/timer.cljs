(ns dvlopt.timer

  "Rather accurate timers for the browser.
  
   All timers are handled asynchronously by a [[worker]], which offers some benefits over scheduling them
   on the main thread. For instance, timers scheduled on the main thread are throttled when the tab is
   inactive. "

  {:author "Adam Helinski"})


;;;;;;;;;; Miscellaneous



(defn epoch

  "Returns the current Unix epoch in milliseconds."

  []

  (js/Date.now))



(defn now

  "High resolution timestamp garanteed to be monotonically increasing, usually preferred over [[epoch]].
  
   Returns the number of milliseconds elapsed since the time origin. Fractional part, if present, represents fractions
   of a millisecond and should be accurate to 5 microseconds.
  
   Cf. [Time origin in MDN](https://developer.mozilla.org/en-US/docs/Web/API/DOMHighResTimeStamp#The_time_origin)"

  []

  (js/performance.now))


;;;;;;;;;;


(def ^:private -worker-src

  ;; Source code for the web worker in charge of creating and cancelling timers.

  "onmessage=(e)=>{var x=e.data[1]; if (x){setTimeout(function(){postMessage(x)},e.data[0])} else{clearTimeout(e.data[0])}}")



(defprotocol ^:private -ITimer

  ;; Base method for creating a a timer.

  (-in [this token interval f]))



(defprotocol ITimer

  "See [[worker]]."

  (cancel [this token]
    "Cancels a timer by using its token.
    
     See [[in]] and [[every]].")

  (in [this millis f]
    "Executes `f` in `millis` milliseconds using the given worker.
    
     Returns a token which can be used in [[cancel]] for effectively clearing this timer.")

  (every [this millis f]
         [this millis f on-lag]
    "Executes `f` every `millis` milliseconds.

     Offers drift protection so that each run is performed at a fixed interval, regarless of how long the previous run took.
     For instance, starting at 0 with an interval of 5000 milliseconds, the timeline should be: 5000, 10000, 15000, 20000, ...

     If a run takes more time than the given interval, execution is lagging, in which case any further scheduling is stopped and
     `on-lag` (if provided) is called with 1 argument: a negative value denotating the lag in milliseconds (eg. -143 means \"143 milliseconds
     late\").
    
     Returns a token for cancellation (akin to [[in]])."))



(deftype Worker [^:mutable token-id
                 token->callbacks
                 worker]

  -ITimer


    (-in [this v*token interval f]
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
                      #js [interval
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


    (in [this interval f]
      (-in this
           (volatile! nil)
           interval
           f))


    (every [this interval f]
      (every this
             interval
             f
             nil))


    (every [this interval f on-lag]
      (let [v*n     (volatile! 1)
            v*token (volatile! nil)
            start   (now)]
        (-in this
             v*token
             interval
             (fn each-time []
               (f)
               (let [next-timestamp (+ start
                                       (* (vswap! v*n
                                                  inc)
                                          interval))
                     delta          (Math/ceil (- next-timestamp
                                                  (now)))]
                 (if (neg? delta)
                   (when on-lag
                     (on-lag delta))
                   (-in this
                        v*token
                        delta
                        each-time))))))))



(defn worker

  "Creates a new worker which can be used for scheduling."

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
