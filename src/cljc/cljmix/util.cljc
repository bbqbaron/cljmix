(ns cljmix.util)

(defn uniq-by [key-fn ls]
  (loop [acc {} rem ls]
    (let [next (first rem)]
      (if (nil? next)
        (vals acc)
        (let [key (key-fn next)]
          (recur
            (if (contains? acc key)
              acc
              (assoc acc key next))
            (rest rem)))))))

