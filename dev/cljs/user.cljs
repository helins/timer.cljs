(ns cljs.user

  "For daydreaming in the REPL."

  (:require [dvlopt.timer :as timer]))


;;;;;;;;;;


(defonce w
         (timer/worker))


(comment
  

  (do
    (timer/task #(println "task 1"))
    (timer/task #(println "task 2"))
    (timer/micro-task #(println "micro 1"))
    (timer/micro-task #(println "micro 2"))
    )
  )
