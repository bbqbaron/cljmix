(ns cljmix.sub
  (:require [re-frame.core :as rf]))

(rf/reg-sub
  :characters
  (fn [db _]
    (:characters db)))

(rf/reg-sub
  :read-history
  (fn [db _]
    (:read-history db)))

(rf/reg-sub
  :char
  (fn [db _]
    (get-in db [:characters (:char-id db)])))

(rf/reg-sub :char-ids (fn [db _] (keys (:characters db))))
(rf/reg-sub :chars (fn [db _] (:characters db)))
