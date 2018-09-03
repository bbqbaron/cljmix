(ns cljmix.sub
  (:require [re-frame.core :as rf]))

(rf/reg-sub
  :query-result
  (fn [db _]
    (:data db)))

(rf/reg-sub
  :read-history
  (fn [db _]
    (:read-history db)))