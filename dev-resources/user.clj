(ns user
  (:require [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [clojure.set :as set]
            [cljmix.marvel :as marvel])
  (:import (jetbrains.exodus.entitystore PersistentEntityStore PersistentEntityStores StoreTransactionalExecutable)))
(require
  '[com.walmartlabs.lacinia :as lacinia]
  '[clojure.tools.namespace.repl :as r]
  '[cljmix.db :as db]
  '[cljmix.schema-gen :as sg]
  '[cljmix.system :as system]
  '[cljmix.main :refer [-main]]
  '[clojure.java.browse :refer [browse-url]]
  '[clojure.walk :as walk]
  '[prevayler :as pv]
  '[com.stuartsierra.component.repl :as crepl :refer [start stop reset set-init]])
(import '(clojure.lang IPersistentMap))

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

(defn q
  [query-string]
  (-> new-system
      :schema-provider
      :schema
      (lacinia/execute query-string nil nil)
      simplify))

#_(defonce system (new-system nil))


(comment
  (defonce file-data
           (mapcat
             (comp :results :data)
             (map #(read-string (slurp (str "comics" % ".edn")))
                  (range 484))))

  (def need-supplements
    (->> file-data
         (filterv (comp (fn [{:keys [returned available]}]
                          (> available returned)) :creators))))

  (first need-supplements))


#_
(def creator->comics
  (reduce
   (fn [acc {:keys [id]
             {:keys [available items returned]} :creators}]
     (let [remote-ids (when (> available returned)
                        (->>
                          (marvel/marvel-req
                            nil
                            (:xodus (:xodus-cache system))
                            (str
                              "/v1/public/comics/" id "/creators"))
                          :data :results
                          (map :id)))
           local-ids
           (for [c items]
             (read-string
               (second (re-find #"creators\/(\d+)" (:resourceURI c)))))]
       (apply
         merge-with set/union
         acc
         (for [creator-id (concat local-ids remote-ids)]
           {creator-id #{id}}))))
   {}
   file-data))

#_
(spit "creator->comics.edn" (pr-str creator->comics))

#_(defn repl-reset
  "Stops the system, reloads modified source files, and restarts the
  system."
  []
  (alter-var-root #'system component/stop)
  (let [ret (r/refresh :after `start)]
    (if (instance? Throwable ret)
      (throw ret)  ; let the REPL's exception handling take over
      ret)))

(comment
  (alter-var-root #'system component/start)
  (alter-var-root #'system component/stop)
  (r/set-refresh-dirs)
  (r/refresh)
  (start)
  (reset)
  (stop)
  (-main))

#_(let [es (PersistentEntityStores/newInstance "./.loltest3")]
  (let [tc (:tag-cache @@db/db)
        tc2
        ((fn go [x]
           (reduce
             (fn [acc [k v]]
               (if (:body v)
                 (assoc acc k v)
                 (into acc
                       (for [[k2 v2] (go v)]
                         [(str k k2) v2]))))
             {} x))
         tc)]
    (try
      #_(.getEntity es (str [pk qk]))
      #_(.executeInTransaction es
                               (reify StoreTransactionalExecutable
                                 (execute [_ txn]
                                   (println (read-string
                                              (.getBlobString
                                                (first (vec (.find txn "server-cache" "id" (str [pk qk]))))
                                                "response"))))))
      (.executeInTransaction es
                             (reify StoreTransactionalExecutable
                               (execute [_ txn]
                                 (doseq [[k v] tc2]
                                   (let [e (.newEntity txn "tag-cache")]
                                     (.setProperty e "id" k)
                                     (.setBlobString e "result" (pr-str v)))))))
      (finally
        (.close es)))))
