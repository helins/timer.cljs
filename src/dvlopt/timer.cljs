(ns dvlopt.timer

  ""

  {:author "Adam Helinski"})


;;;;;;;;;; Miscellaneous



(defn epoch

  ""

  []

  (js/Date.now))



(defn now

  ""

  []

  (js/performance.now))


;;;;;;;;;;


(def ^:private -worker-src

  ;;

  "onmessage=(e)=>{var x=e.data[1]; if (x){setTimeout(function(){postMessage(x)},e.data[0])} else{clearTimeout(e.data[0])}}")



(defprotocol ^:private -ITimer

  ;;

  (-in [this token interval f]))



(defprotocol ITimer

  ""

  (cancel [this token]
    "")

  (in [this interval f]
    "")

  (every [this interval f]
         [this interval f on-lag]
    ""))



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

  ""

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
