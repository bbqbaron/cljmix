(ns cljmix.system
  (:require [com.stuartsierra.component :as component]
            [cljmix.schema :as schema]
            [cljmix.server :as server]
            [cljmix.db :as db]
            [cljmix.marvel :as marvel]))

(defn new-system
  []
  (merge (component/system-map)
         (marvel/new-marvel-provider)
         (db/new-db)
         (server/new-server)
         (schema/new-schema-provider)))