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

  "onmessage=(e)=>{var x=e.data[1]; if (x) {setTimeout(function(){postMessage(x)},e.data[0])} else {clearTimeout(e.data[0])}}")



(defprotocol ITimer

  ""

  (cancel [this token]
    "")

  (in [this interval f]
    "")

  #_(every [this interval f]
    "")
  )



(deftype Worker [^:mutable token
                 token->callbacks
                 worker]

  ITimer


    (cancel [this token]
      (.delete token->callbacks
               token)
      (.postMessage worker
                    #js [token])
      this)


    (in [this interval f]
      (let [token-cached token]
        (.set token->callbacks
              token
              f)
        (set! token
              (if (< token
                     js/Number.MAX_SAFE_INTEGER)
                (inc token)
                1))
        (.postMessage worker
                      #js [interval
                           token-cached])
        token-cached))
    )



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
