(ns cljmix.system
  (:require [com.stuartsierra.component :as component]
            [cljmix.schema :as schema]
            [cljmix.server :as server]))

(defn new-system
  []
  (merge (component/system-map)
         (server/new-server)
         (schema/new-schema-provider)))