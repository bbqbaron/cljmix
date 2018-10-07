(ns cljmix.runner
  (:require [cljs.test :as t]
            [cljmix.core-test]))

(enable-console-print!)

(defn start
  []
  (t/run-tests
    'cljmix.runner
    'cljmix.core-test))

(start)
