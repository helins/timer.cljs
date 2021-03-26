;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at https://mozilla.org/MPL/2.0/.


(ns helins.timer.test

  ""

  {:author "Adam Helinski"}

  (:require [clojure.test :as t]
            [helins.timer :as timer]))


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



(defn cancel

  [done timer-obj]

  (let [v*target (volatile! true)]
    (timer/cancel timer-obj
                  (timer/in timer-obj
                            50
                            #(vreset! v*target
                                      false)))
    (timer/in timer-obj
              100
              (fn canceled? []
                (t/is @v*target
                      "Target has not been changed")
                (done)))))



(t/deftest cancel-main

  (t/async done
    (cancel done
            timer/main-thread)))



(t/deftest cancel-worker

  (t/async done
    (cancel done
            worker)))



(defn in

  [done timer-obj]

  (let [start (timer/now)]
    (timer/in timer-obj
              50
              (fn f []
                (elapsed start
                         50)
                (done)))))



(t/deftest in-main

  (t/async done
    (in done
        timer/main-thread)))



(t/deftest in-worker

  (t/async done
    (in done
        worker)))


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


;;;;;;;;;; .requestAnimationFrame


(t/deftest frames
  (t/async done
    (let [v*i (volatile! 0)]
      (timer/frames (fn [timestamp cancel-frames]
                      (when (= (vswap! v*i
                                       inc)
                               4)
                        (cancel-frames)
                        (timer/in timer/main-thread
                                  1000
                                  (fn []
                                    (t/is (= 4
                                             @v*i)
                                          "Frames were indeed cancelled")
                                    (done)))))))))
