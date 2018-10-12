(ns cljmix.fx
  (:require [re-frame.core :as rf]
            [cljmix.util :refer [tag]]))

(rf/reg-event-fx
  :subscribed
  (fn [world [_ payload]]
    (tag "hm" (get-in payload [:data]))
    {:dispatch (cljmix.query/get-feed
                 0)
     :db       (assoc (:db world)
                 :subscribed-characters
                 (->> (get-in payload [:data :subscribeCharacter])
                      (sort-by
                        #(get-in % [:data :results 0 :name]))))}))

(rf/reg-event-fx
  :unsubscribed
  (fn [world [_ payload]]
    {:db       (assoc
                 (:db world)
                 :subscribed-characters
                 (->> (get-in payload [:data :unsubscribeCharacter])
                      (sort-by
                        #(get-in % [:data :results 0 :name]))))
     :dispatch (cljmix.query/get-feed
                 0)}))

