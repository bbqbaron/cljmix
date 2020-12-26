(ns cljmix.fx
  (:require [re-frame.core :as rf]
            [cljmix.db :as db]
            [cljmix.util :refer [tag]]))

(rf/reg-event-fx
  :marked-read
  (fn [{:keys [db]}
       [_ id]]
    {:dispatch
     (cljmix.query/get-feed nil)
     :db
     (update db :feed
             (partial filter #(not= (:digitalId %) id)))}))

(rf/reg-event-fx
  :subscribed
  (fn [world [_ payload]]
    {:dispatch (cljmix.query/get-feed nil)
     :db       (assoc (:db world)
                 :subscribed
                 (get-in payload
                         [:data :subscribe]))}))

(rf/reg-event-fx
  :unsubscribed
  (fn [world [_ payload]]
    {:dispatch (cljmix.query/get-feed nil)
     :db       (assoc (:db world)
                 :subscribed
                 (get-in payload
                         [:data :unsubscribe]))}))

(rf/reg-event-fx
  :subscribed-character
  (fn [world [_ payload]]
    {:dispatch (cljmix.query/get-feed nil)
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
     :dispatch (cljmix.query/get-feed nil)}))

(rf/reg-event-fx
  :select-subscription
  (fn [{db :db} [_ id]]
    {:db
     (assoc db ::db/current-subscription id)
     :dispatch
     (cljmix.query/get-feed id nil)}))