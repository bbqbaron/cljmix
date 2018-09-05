(ns cljmix.view
  (:require [re-frame.core :as rf]
            [re-graph.core :as gql]
            [cljmix.gql :refer [to-query-string]]
            [cljmix.query :as query]
            cljmix.db
            cljmix.fx
            cljmix.sub))

(def button-link-style
  {:font             "bold 11px Arial"
   :text-decoration  "none"
   :background-color "#EEEEEE"
   :color            "#333333"
   :padding          "2px 6px 2px 6px"
   :border-top       "1px solid #CCCCCC"
   :border-right     "1px solid #333333"
   :border-bottom    "1px solid #333333"
   :border-left      "1px solid #CCCCCC"})

(defn grid [els]
  [:div
   {:style {:display               "grid"
            :grid-template-columns "1fr 1fr 1fr 1fr"
            :grid-template-rows    "1fr 1fr 1fr 1fr"}}
   els])

(defn choose-character []
  (let [char-id (:id @(rf/subscribe [:char]))
        chars @(rf/subscribe [:chars])]
    [:select
     {:value     (or char-id "")
      :on-change (fn [e]
                   (let [val (-> e .-target .-value
                                 (js/parseInt 10))]
                     (rf/dispatch [:pick-character val])))}
     (cons
       [:option {:value "" :key "empty"} ""]
       (map
         (fn [{id :id char-name :name :as char}]
           (println "hm" char)
           [:option {:value id :key id} char-name])
         (vals chars)))]))

(defn show-comic [i c]
  (let [read-history @(rf/subscribe [:read-history])]
    [:div
     {:style {:display "flex" :flexDirection "column"}
      :key   i}
     (let [thumb (:thumbnail c)]
       [:img {:src   (str (:path thumb) "." (:extension thumb))
              :style {
                      :width  "220px"
                      :height "340px"}
              :alt   (:title c)}])
     [:a {:key    i
          :href   (str "https://read.marvel.com/#/book/" (:digitalId c))
          :target "_blank"
          :style  button-link-style}
      (:title c)]
     [:button
      {:on-click #(rf/dispatch [::gql/mutate
                                query/mark-read
                                {:digitalId (:digitalId c)}
                                [:marked-read]])}
      "Already read it"]
     (when (contains? read-history (:digitalId c))
       "ALREADY READ IT")]))

(defn comics-footer [char]
  (let [comics-query (get-in char [:getComicsCharacterCollection :data])
        has-more (> (:total comics-query)
                    (+ (:limit comics-query)
                       (:offset comics-query)))]
    [:div (if has-more
            [:button {:on-click #(query/get-comics (:id char)
                                                   20)}
             "More"]
            "Done")]))

(defn show-comix [char]
  (let [comics (get-in char [:getComicsCharacterCollection :data :results])]
    [:div
     [grid (doall (map-indexed
                    show-comic
                    comics))]
     [comics-footer char]]))

