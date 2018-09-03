(ns cljmix.db
  (:require [re-frame.core :as rf]
            [cljmix.util :refer [uniq-by]]))

(rf/reg-event-db
  :initialize
  (fn [db _]
    db))

(rf/reg-event-db
  :query-result
  (fn [db [_ payload]]
    (assoc db :data (:data payload))))

(rf/reg-event-db
  :read-history-result
  (fn [db [_ payload]]
    (assoc db :read-history (set (:readHistory (:data payload))))))

(rf/reg-event-db
  :marked-read
  (fn [db [_ payload]]
    (println "marked-read" payload)
    db))

(defn add-comics
  [c1 c2]
  (->> (concat c1 c2)
       (uniq-by :digitalId)))

(rf/reg-event-db
  :comics-result
  (fn [db [_ payload]]
    (println "new comics" payload)
    (update-in db
               ; TODO not this
               [:data :getCharacterCollection :data :results 0 :getComicsCharacterCollection :data :results]
               (partial add-comics
                        (get-in payload [:data :getComicsCollection :data :results])))))
