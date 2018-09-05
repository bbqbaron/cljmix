(ns cljmix.sub
  (:require [re-frame.core :as rf]
            [cljmix.util :refer [uniq-by]]))

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
(rf/reg-sub :unread-comics
            (fn [db _]
              (let [already-read (:read-history db)]
                (->> db :characters
                     (map vals)
                     (map (fn [char]))
                     (get-in char [:getComicsCharacterCollection :data :results])
                     (apply concat)
                     (uniq-by :id)
                     (filter
                       (fn [{:keys [id]}]
                         (not (contains? already-read id))))))))
