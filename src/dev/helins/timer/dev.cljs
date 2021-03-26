;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at https://mozilla.org/MPL/2.0/.


(ns helins.timer.dev

  "For daydreaming at the REPL."

  (:require [helins.timer :as timer]))


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
