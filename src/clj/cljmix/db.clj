(ns cljmix.db
  (:require [prevayler :refer [prevayler!]]
            [com.stuartsierra.component :as component]))

(declare db)

(defn log [tag f]
  (fn [& args]
    (println tag "got" args)
    (let [res (apply f args)]
      (println tag "produced" res)
      res)))

(defn reducer [state [event-type event-val]]
  (let [new-state (case event-type
                    :mark-read (update state :read
                                       (comp
                                         (partial filter some?)
                                         #(cons event-val %)
                                         #(or % #{})))
                    state)]
    [new-state true]))

(defrecord Db [db]
  component/Lifecycle
  (start [this]
    (assoc this :db (prevayler! reducer)))

  (stop [this]
    (when (some? db)
      (.close db))))

(defn new-db []
  {:db-provider (component/using (map->Db {})
                                 [])})
