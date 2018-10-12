(ns cljmix.util)

(defn uniq-by
  "Produce a vector of objects unique by a supplied function,
  preserving order. The first object with a given key wins, naturally."
  [key-fn ls]
  (loop [acc [] rem ls seen #{}]
    (let [next (first rem)]
      (if (nil? next)
        acc
        (let [key (key-fn next)]
          (recur
            (if (contains? seen key)
              acc
              (conj acc next))
            (rest rem)
            (conj seen key)))))))

(defn tag
  "Log and return a value"
  ([x]
   (fn [y]
     (tag x y)))
  ([x y] #?(:cljs (js/console.log x y) :default (println x y))
   y))

(defn log
  "Log all traffic into and out of `f`,
  prefixed by `tag`."
  ([tag]
   (fn [f]
     (tag tag f)))
  ([tag f]
   (fn [& args]
     (println tag "got" args)
     (let [res (apply f args)]
       (println tag "produced" res)
       res))))
