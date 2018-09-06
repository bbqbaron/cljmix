(ns cljmix.db
  (:require [prevayler :refer [prevayler!]]
            [com.stuartsierra.component :as component]))

(defn reducer [state [event-type event-val]]
  (let [new-state
        (case event-type
          :mark-read (update state :read
                             (fn [old]
                               (filter some?
                                       (set
                                         (conj old event-val)))))
          :tag-cache
          (let [{:keys [path result]} event-val]
            (assoc-in state (cons :tag-cache path) result))
          :server-cache
          (let [{:keys [path body]} event-val]
            (assoc-in state (cons :server-cache path) body))
          :subscribe-character
          (update state :subscribed-characters #(conj (or % #{}) event-val))
          state)]
    [new-state true]))

(defrecord Db []
  component/Lifecycle
  (start [this]
    (assoc this :db (prevayler! reducer)))

  (stop [this]
    (let [db (:db this)]
      (when (some? db)
        (.close db)))
    this))

(defn new-db []
  {:db-provider (component/using (map->Db {})
                                 [])})
