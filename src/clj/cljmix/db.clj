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
          :unsubscribe-character
          (update state :subscribed-characters
                  (partial filter #(not= % event-val)))
          :set-time
          (assoc state :time event-val)
          state)]
    [new-state true]))

(defonce db (ref nil))

(defn reset-db []
  (dosync
    (ref-set db (prevayler! reducer))))

(defrecord Db []
  component/Lifecycle
  (start [this]
    (dosync
      (let [prev (or
                   @db
                   (do (println "new db")
                       (let [new-db (prevayler! db)]
                         (ref-set db new-db)
                         new-db)))]
        (assoc this :db prev))))

  (stop [this]
    this))

(defn new-db []
  {:db-provider (component/using (map->Db {})
                                 [])})
