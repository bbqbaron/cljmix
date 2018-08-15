(ns user
  (:require
    [com.walmartlabs.lacinia :as lacinia]
    [cljmix.system :as system]
    [clojure.java.browse :refer [browse-url]]
    [clojure.walk :as walk]
    [com.stuartsierra.component :as component])
  (:import (clojure.lang IPersistentMap)))

; http://lacinia.readthedocs.io/en/latest/tutorial/game-data.html#id1
(defn simplify
  [m]
  (walk/postwalk
    (fn [node]
      (cond
        (instance? IPersistentMap node)
        (into {} node)

        (seq? node)
        (vec node)

        :else
        node))
    m))

(defonce system (system/new-system))

(defn q
  [query-string]
  (-> system
      :schema-provider
      :schema
      (lacinia/execute query-string nil nil)
      simplify))

(defn start
  []
  (alter-var-root #'system component/start-system)
  (browse-url "http://localhost:8888/graphiql")
  :started)

(defn stop
  []
  (alter-var-root #'system component/stop-system)
  :stopped)

