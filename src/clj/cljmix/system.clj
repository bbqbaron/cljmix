(ns cljmix.system
  (:require [com.stuartsierra.component :as component]
            [cljmix.schema :as schema]
            [cljmix.server :as server]
            [cljmix.db :as db]))

(defn new-system
  []
  (merge (component/system-map)
         (db/new-db)
         (server/new-server)
         (schema/new-schema-provider)))