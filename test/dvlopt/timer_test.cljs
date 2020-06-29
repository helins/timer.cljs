(ns dvlopt.timer-test

  ""

  {:author "Adam Helinski"}

  (:require [clojure.test :as t]
            [dvlopt.timer :as timer]))


;;;;;;;;;;


(def tolerance-ms
     5)


(defn elapsed

  ""

  ([since target]

   (elapsed since
            target
            nil))


  ([since target string]
  
   (let [delay-ms (- (timer/now)
                     since
                     target)]
     (t/is (<= (- tolerance-ms)
               delay-ms
               tolerance-ms)
           string))))


(def worker
     (timer/worker))



(t/deftest cancel

  (t/async done
    (let [v*target (volatile! true)]
      (timer/cancel worker
                    (timer/in worker
                              50
                              #(vreset! v*target
                                        false)))
      (timer/in worker
                100
                (fn canceled? []
                  (t/is @v*target
                        "Target has not been changed")
                  (done))))))


(t/deftest in

  (t/async done
    (let [start (timer/now)]
      (timer/in worker
                50
                (fn in []
                  (elapsed start
                           50)
                  (done))))))
