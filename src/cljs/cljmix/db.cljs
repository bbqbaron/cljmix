(ns cljmix.db
  (:require [re-frame.core :as rf]
            [cljmix.util :refer [uniq-by]]))

(rf/reg-event-db
  :initialize
  (fn [db _]
    (-> db
        (assoc :page :page/queue)
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
  :creator-search-result
  (fn [db [_ payload]]
    (let [xs (->> payload
                         (#(get-in % [:data :getCreatorCollection :data :results]))
                         (map (fn [char] [(:id char) char]))
                         (into {}))]
      (assoc-in db [:search-results :creator]
                xs))))

(rf/reg-event-db
  :series-search-result
  (fn [db [_ payload]]
    (let [xs (->> payload
                         (#(get-in % [:data :getSeriesCollection :data :results]))
                         (map (juxt :id identity))
                         (into {}))]
      (assoc-in db [:search-results :series] xs))))

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
    (assoc db :feed
              (get-in payload [:data :feed :results]))))

(rf/reg-event-db
  :subs-result
  (fn [db [_ payload]]
    (let [subscriptions (get-in payload
                                [:data :subscriptions])]
      (assoc db :subscribed subscriptions
                ::current-subscription
                (-> subscriptions first :id)))))

(rf/reg-event-db
  :char-subs-result
  (fn [db [_ payload]]
    (assoc db :subscribed-characters
              (get-in payload [:data :subscribedCharacters]))))

(rf/reg-event-db
  :marked-read
  (fn [db [_ id]]
    (update db :feed
            (partial filter #(not= (:digitalId %) id)))))

(rf/reg-event-db
  :get-time-result
  (fn [db [_ {:keys [data]}]]
    (assoc db :time (:getTime data))))

(rf/reg-event-db
  :set-time-result
  (fn [db [_ time]]
    (assoc db :time time)))
