(ns cljmix.server
  (:require [com.stuartsierra.component :as component]
            [com.walmartlabs.lacinia.pedestal :as lp]
            [io.pedestal.http :as http]))

(defn build-server [schema-provider]
  (-> schema-provider
      :schema
      (lp/service-map {:graphiql true
                       :ide-path "/graphiql"})
      (assoc ::http/resource-path "/public")
      (assoc ::http/port 8292)
      http/create-server
      http/start))

(defrecord Server [schema-provider server]
  component/Lifecycle
  (start [this]
    (assoc this :server (build-server schema-provider)))

  (stop [this]
    (http/stop server)
    (assoc this :server nil)))


(defn new-server
  []
  {:server (component/using (map->Server {})
                            [:schema-provider])})