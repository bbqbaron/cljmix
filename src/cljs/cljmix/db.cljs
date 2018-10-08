(ns cljmix.db
  (:require [re-frame.core :as rf]
            [cljmix.util :refer [uniq-by]]))

(rf/reg-event-db
  :initialize
  (fn [db _]
    (-> db
        (assoc :page :page/char)
        (assoc :feed []))))

(rf/reg-event-db
  :char-search-result
  (fn [db [_ payload]]
    (let [new-chars (->> payload
                         (#(get-in % [:data :getCharacterCollection :data :results]))
                         (map (fn [char] [(:id char) char]))
                         (into {}))]
      (assoc db :char-search-result
                new-chars))))

(rf/reg-event-db
  :page
  (fn [db [_ page]]
    (assoc db :page page)))

(rf/reg-event-db
  :pick-character
  (fn [db [_ char-id]]
    (assoc db :char-id char-id)))

(rf/reg-event-db
  :feed-result
  (fn [db [_ payload]]
    (update db :feed
            concat
            (get-in payload [:data :feed :results]))))

(rf/reg-event-db
  :subs-result
  (fn [db [_ payload]]
    (assoc db :subscribed-characters (get-in payload [:data :subscribedCharacters]))))


