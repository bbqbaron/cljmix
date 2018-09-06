(ns cljmix.fx
  (:require [re-frame.core :as rf]))

(rf/reg-event-fx
  :subscribed
  (fn [{db :db} [_ char-id _]]
    (let [new-db (update db :subscribed-characters conj char-id)]
      {:db       new-db
       :dispatch (cljmix.query/get-feed
                   0)})))


