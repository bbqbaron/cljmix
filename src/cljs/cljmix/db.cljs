(ns cljmix.db
  (:require [re-frame.core :as rf]
            [cljmix.util :refer [uniq-by]]))

(rf/reg-event-db
  :initialize
  (fn [db _]
    db))

(rf/reg-event-db
  :char-result
  (fn [db [_ payload]]
    (let [new-chars (->> payload
                         (#(get-in % [:data :getCharacterCollection :data :results]))
                         (map (fn [char] [(:id char) char]))
                         (into {}))
          first-id (first (keys new-chars))]
      (-> db
          (update :characters
                  merge
                  new-chars)
          (assoc :char-id
                 first-id)))))

(rf/reg-event-db
  :read-history-result
  (fn [db [_ payload]]
    (assoc db :read-history (set (:readHistory (:data payload))))))

(rf/reg-event-db
  :marked-read
  (fn [db [_ payload]]
    (assoc db
      :read-history
      (set (get-in payload [:data :markRead])))))

(defn add-comics
  [c1 c2]
  (->> (concat c1 c2)
       (uniq-by :digitalId)))

(rf/reg-event-db
  :comics-result
  (fn [db [_ char-id payload]]
    (update-in db
               ; TODO not this
               [:characters char-id :getComicsCharacterCollection :data :results]
               #(add-comics
                  %
                  (get-in payload [:data :getComicsCollection :data :results])))))

(rf/reg-event-db
  :pick-character
  (fn [db [_ char-id]]
    (assoc db :char-id char-id)))

