(ns cljmix.server
  (:require [com.stuartsierra.component :as component]
            [com.walmartlabs.lacinia.pedestal :as lp]
            [io.pedestal.http :as http]))

(defrecord Server [schema-provider server]

  component/Lifecycle
  (start [this]
    (prn "starting?")
    (assoc this :server (-> schema-provider
                            :schema
                            (lp/service-map {:graphiql true
                                             :ide-path "/graphiql"})
                            (assoc ::http/resource-path "/public")
                            http/create-server
                            http/start
                            ((fn [x] (prn "hm" x) x)))))

  (stop [this]
    (http/stop server)
    (assoc this :server nil)))


(defn new-server
  []
  (prn "new server")
  {:server (component/using (map->Server {})
                            [:schema-provider])})