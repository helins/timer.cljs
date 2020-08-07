(ns dvlopt.timer-test

  ""

  {:author "Adam Helinski"}

  (:require [clojure.test :as t]
            [dvlopt.timer :as timer]))


;;;;;;;;;; .setInterval and .setTimeout


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


;;;;;;;;;; "Immediate" async tasks


(t/deftest micro-task

  (t/async done
    (timer/micro-task done)))



(t/deftest task

  (t/async done
    (timer/task done)))



(defn async-order

  ;; Ensuring order is correct when mixing tasks with micro-tasks

  [tasks+values]

  (let [v*resolve (volatile! nil)
        p         (js/Promise. (fn [success]
                                 (vreset! v*resolve
                                          success)))
        v*values  (volatile! [])]
    (doseq [[task
             value] tasks+values]
      (task (fn []
              (when (= (count (vswap! v*values
                                      conj
                                      value))
                       4)
                (@v*resolve (t/is (= @v*values
                                [:micro-1
                                 :micro-2
                                 :task-1
                                 :task-2])))))))
    p))




(t/deftest task-ordering

  (t/async done
    (.then (js/Promise.allSettled
             (clj->js (map async-order
                           [[[timer/micro-task :micro-1]
                             [timer/micro-task :micro-2]
                             [timer/task       :task-1]
                             [timer/task       :task-2]]
                            [[timer/task       :task-1]
                             [timer/task       :task-2]
                             [timer/micro-task :micro-1]
                             [timer/micro-task :micro-2]]
                            [[timer/micro-task :micro-1]
                             [timer/task       :task-1]
                             [timer/task       :task-2]
                             [timer/micro-task :micro-2]]
                            [[timer/micro-task :micro-1]
                             [timer/task       :task-1]
                             [timer/micro-task :micro-2]
                             [timer/task       :task-2]]
                            [[timer/task       :task-1]
                             [timer/micro-task :micro-1]
                             [timer/micro-task :micro-2]
                             [timer/task       :task-2]]
                            [[timer/task       :task-1]
                             [timer/micro-task :micro-1]
                             [timer/task       :task-2]
                             [timer/micro-task :micro-2]]])))
           (fn [_ _]
             (done)))))
