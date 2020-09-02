(ns cljmix.db
  (:require [prevayler :refer [prevayler!]]
            [com.stuartsierra.component :as component]
            [prevayler :as pv]))

(def db-reduce
  (atom (fn [state [event-type event-val]]
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
                  :clear-server-cache
                  (dissoc state :server-cache)
                  :server-cache
                  (let [{:keys [path body]} event-val]
                    (assoc-in state (cons :server-cache path) body))
                  :subscribe-character
                  (update state :subscribed-characters #(conj (or % #{}) event-val))
                  :unsubscribe-character
                  (update state :subscribed-characters
                          (partial filter #(not= % event-val)))
                  :subscribe
                  (let [{:keys [subId entityId entityType]} event-val]
                    (update-in state [:subscribed subId entityType]
                               #(conj (or % #{}) entityId)))
                  :unsubscribe
                  (let [{:keys [subId entityId entityType]} event-val]
                    (update-in state [:subscribed subId entityType]
                               #(disj (or % #{}) entityId)))
                  :set-time
                  (assoc state :time event-val)
                  state)]
            [new-state true]))))

(defn reducer [& args]
  (apply @db-reduce args))

(defonce db (ref nil))

(defn clear-db []
  (dosync
    (ref-set db nil)))

(defn reset-db []
  (dosync
   (let [new-db (prevayler! reducer)]
     (ref-set db new-db)
     new-db))
  nil)

(defrecord Db []
  component/Lifecycle
  (start [this]
    (dosync
      (when (nil? @db)
        (println "new db")
        (reset-db)))
    (assoc this :db @db))

  (stop [this]
    this))

(defn new-db []
  {:db-provider (component/using (map->Db {})
                                 [])})

(comment
  (dosync
  (when (nil? @db)
    (println "new db")
    (reset-db))))
