(ns cljmix.core
  (:require [re-frame.core :as rf]
            [reagent.core :as ra]
            [re-graph.core :as gql]
            [clojure.walk :as walk]))

(enable-console-print!)

(rf/dispatch [::gql/init {:ws-url nil}])

(def my-query
  [:getComicsCollection
   [:results
    [:id :digitalId :description :title
     :images [:path :extension]
     :thumbnail [:path :extension]
     :textObjects [:type :text :language]]]])

(defn to-query-string
  [q]
  (walk/postwalk
    (fn [x]
      (cond
        (keyword? x)
        (name x)
        (coll? x)
        (str " "
             (apply str
                    (interpose " "
                      (cons "{" (map str x))))
             " } ")))
   q))

(rf/reg-event-db
  :initialize
  (fn [db _]
    (assoc db :name "hi")))

(rf/reg-event-db
  ::query-result
  (fn [db [_ payload]]
    (prn payload)
    (assoc db :data (:data payload))))

(rf/dispatch [::gql/query
              (to-query-string my-query)
              {}
              [::query-result]])

(rf/reg-sub
  :name
  (fn [db _]
    (:name db)))

(rf/reg-sub
  :data
  (fn [db _]
    (:data db)))

(defn ui
  []
  [:div
   [:h1 @(rf/subscribe [:name])]
   (let [data @(rf/subscribe [:data])]
     (prn "hm" data)
     (map-indexed
       (fn [i c] [:p {:key i} "Comic: " (:title c)])
       (get-in data [:comics :results])))])

(defn ^:export run
  []
  (rf/dispatch-sync [:initialize])
  (ra/render [ui]
             (js/document.getElementById "app")))

