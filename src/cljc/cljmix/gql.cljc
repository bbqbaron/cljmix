(ns cljmix.gql
  (:require [clojure.walk :as walk]))

(defn to-query-string
  [q]
  (walk/postwalk
    (fn [x]
      (cond
        (keyword? x)
        (name x)
        (or (list? x) (vector? x))
        (if (= "args" (first x))
          (str " ( "
               (apply str
                      (interpose ", "
                                 (map
                                   (fn [[arg-name arg-value]]
                                     (str arg-name ": \"" arg-value "\""))
                                   (partition 2 (rest x)))))
               " ) ")
          (str " { "
               (apply str
                      (interpose " "
                        (map str x)))
               " } "))
        true
        x))
   q))
