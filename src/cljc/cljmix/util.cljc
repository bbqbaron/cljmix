(ns cljmix.util)

(defn uniq-by [key-fn ls]
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

