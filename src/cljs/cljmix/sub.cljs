(ns cljmix.sub
  (:require [re-frame.core :as rf]
            [cljmix.util :refer [uniq-by]]))

(rf/reg-sub :db
            (fn [db _]
              db))

(rf/reg-sub
  :char-search-result
  (fn [db _]
    (:char-search-result db)))

(rf/reg-sub
  :char
  (fn [db _]
    (get-in db [:characters (:char-id db)])))

(rf/reg-sub :page (fn [db _] (:page db)))

(rf/reg-sub
  :unread-comics
  (fn [db _]
    (:feed db)))

(rf/reg-sub
  :subs
  (fn [db _]
    (->> (:subscribed-characters db)
         (sort-by
           #(get-in % [:data :results 0 :name])))))

(rf/reg-sub
  :time
  (fn [db _] (:time db)))