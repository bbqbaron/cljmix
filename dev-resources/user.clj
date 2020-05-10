(ns user
  (:require
    [com.walmartlabs.lacinia :as lacinia]
    [cljmix.schema-gen :as sg]
    [cljmix.system :as system]
    [clojure.java.browse :refer [browse-url]]
    [clojure.walk :as walk]
    [com.stuartsierra.component.repl :refer [start stop reset set-init]])
  (:import (clojure.lang IPersistentMap)))

(defn reduce-to
  "Remove all entries from a nested object that aren't a certain key,
  or don't eventually contain that key."
  [m key]
  (when (map? m)
    (let [members
          (->> m
               (map
                 (fn [[k v]]
                   (if (= k key)
                     [k v]
                     (let [newval (reduce-to v key)]
                       (when (not (empty? newval))
                         [k newval])))))
               (filter
                 (fn [entry]
                   (and
                     entry
                     (or
                       (not (seq? (second entry)))
                       (not (empty? (second entry))))))))]
      (into {} members))))

(defn attempt [fn]
  (try (fn)
       (catch Exception e
         (println e))))

; http://lacinia.readthedocs.io/en/latest/tutorial/game-data.html#id1
(defn simplify
  [m]
  (walk/postwalk
    (fn [node]
      (cond
        (instance? IPersistentMap node)
        (into {} node)

        (seq? node)
        (vec node)

        :else
        node))
    m))

(defn new-system [_] (system/new-system))

(set-init new-system)

(defn q
  [query-string]
  (-> new-system
      :schema-provider
      :schema
      (lacinia/execute query-string nil nil)
      simplify))

#_(sg/create-schema)
