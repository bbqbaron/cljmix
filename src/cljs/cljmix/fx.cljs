(ns cljmix.fx
  (:require [re-frame.core :as rf]
            [cljmix.util :refer [tag]]))

(rf/reg-event-fx
  :subscribed-character
  (fn [world [_ payload]]
    {:dispatch (cljmix.query/get-feed
                 0)
     :db       (assoc (:db world)
                 :subscribed-characters
                 (get-in payload [:data :subscribeCharacter]))}))

(rf/reg-event-fx
  :unsubscribed-character
  (fn [world [_ payload]]
    {:db       (assoc
                 (:db world)
                 :subscribed-characters
                 (get-in payload [:data :unsubscribeCharacter]))
     :dispatch (cljmix.query/get-feed
                 0)}))

