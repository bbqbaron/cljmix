(ns cljmix.db
  (:require [prevayler :refer [prevayler!]]
            [com.stuartsierra.component :as component]
            [prevayler :as pv])
  (:import (jetbrains.exodus.entitystore PersistentEntityStores PersistentEntityStore EntityStore)
           (java.io Closeable)))

(def db-reduce
  (atom (fn [state [event-type event-val]]
          (let [new-state
                (case event-type
                  :mark-read (update state :read
                                     (fn [old]
                                       (filter some?
                                               (set
                                                 (conj old event-val)))))
                  :skip
                  (let [{:keys [comicId subId]} event-val]
                    (update-in state
                               [:subscribed subId :skip]
                               #(conj (or % #{}) comicId)))
                  :unsubscribe-characters
                  (assoc  state
                    :subscribed-characters
                    #{})
                  :update-sub
                  (update-in state [:subscribed (:id event-val)]
                             #(merge % (dissoc event-val
                                               :id)))
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

(defonce xodus-db (atom nil))

(defrecord Xodus []
  component/Lifecycle
  (start [this]
    (let [xdb (swap! xodus-db
                     (fn [d]
                       (or d
                           (PersistentEntityStores/newInstance "./.loltest3"))))]
      (assoc this
        :xodus
        xdb)))
  (stop [this]
    (swap! xodus-db
           #(.close %))
    this))

(defn new-db []
  {:db-provider (component/using (map->Db {})
                                 [])
   :xodus-cache (component/using
                  (map->Xodus {})
                  [])})

(comment
  (dosync
  (when (nil? @db)
    (println "new db")
    (reset-db))))
