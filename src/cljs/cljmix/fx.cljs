(ns cljmix.fx
  (:require [re-frame.core :as rf]))

(rf/reg-event-fx
  :mark-read
  (fn [{db :db} [_ value]]
    {:db         (assoc db :loading? true)}))

