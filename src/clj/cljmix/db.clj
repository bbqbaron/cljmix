(ns cljmix.db
  (:require [prevayler :refer [prevayler!]]
            [com.stuartsierra.component :as component]))

(declare db)

(defn reducer [state [event-type event-val]]
  (println "reduce db" state event-type event-val)
  (let [new-state (case event-type
                    :mark-read (update state :read
                                       (fn [old]
                                         (filter some?
                                                 (set
                                                   (conj old event-val)))))
                    state)]
    [new-state true]))

(defrecord Db [db]
  component/Lifecycle
  (start [this]
    (assoc this :db (prevayler! reducer)))

  (stop [_]
    (when (some? db)
      (.close db))))

(defn new-db []
  {:db-provider (component/using (map->Db {})
                                 [])})
