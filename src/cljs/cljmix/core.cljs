(ns cljmix.core
  (:require [re-frame.core :as rf]
            [reagent.core :as ra]
            [re-graph.core :as gql]
            [cljmix.gql :refer [to-query-string my-query]]))

(enable-console-print!)

(rf/dispatch [::gql/init {:ws-url nil}])

(rf/reg-event-db
  :initialize
  (fn [db _]
    db))

(rf/reg-event-db
  ::query-result
  (fn [db [_ payload]]
    (assoc db :data (:data payload))))

(defn search-char [char-name]
  (rf/dispatch [::gql/query
                (to-query-string (my-query char-name))
                {}
                [::query-result]]))

(rf/reg-sub
  :query-result
  (fn [db _]
    (:data db)))

(defn show-comix [char]
  (let [comics (get-in char [:getComicsCharacterCollection :data :results])]
    (map-indexed
      (fn [i c]
        [:div
         [:a {:key i :href (str "https://read.marvel.com/#/book/" (:digitalId c))
              :target "_blank"}
          (:title c)]
         (let [thumb (:thumbnail c)]
           [:img {:src (str (:path thumb) "." (:extension thumb))
                  :style {
                          :width "60px"
                          :height "85px"}
                  :alt (:title c)}])])
      comics)))

(defn ui
  []
  (let [search (ra/atom "")]
    (fn []
      (let [data @(rf/subscribe [:query-result])]
        (println "using data" data)
        [:div
         [:input {:type       "text" :placeholder "Find a character!" :value @search
                  :auto-focus true
                  :on-change #(reset! search (-> % .-target .-value))}]
         [:button {:on-click #(search-char @search)}
          "GO"]
         (map
           (fn [char]
             [:div {:key (:name char)}
              [:h2 (str "Char: " (:name char))]
              (show-comix char)])
           (get-in data [:getCharacterCollection :data :results]))]))))

(defn ^:export run
  []
  (rf/dispatch-sync [:initialize])
  (ra/render [ui]
             (js/document.getElementById "app")))

