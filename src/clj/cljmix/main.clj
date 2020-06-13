(ns cljmix.main
  (:require
    [cljmix.system :as system]
    [com.stuartsierra.component.repl :refer [start stop reset set-init]]))

(defn new-system [_] (system/new-system))

(set-init new-system)

(defn -main [& args]
  (start))
