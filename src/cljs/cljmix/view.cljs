(ns cljmix.view
  (:require [re-frame.core :as rf]
            [re-graph.core :as gql]
            [cljmix.gql :refer [to-query-string]]
            [cljmix.query :as query]
            cljmix.db
            cljmix.fx
            cljmix.sub))

(defn show-comic [i c]
  (let [read-history @(rf/subscribe [:read-history])]
    [:div
     {:key i}
     [:a {:key    i
          :href   (str "https://read.marvel.com/#/book/" (:digitalId c))
          :target "_blank"}
      (:title c)]
     [:button
      {:on-click #(rf/dispatch [::gql/mutate
                                query/mark-read
                                {:digitalId (:digitalId c)}
                                [:marked-read]])}
      "Already read it"]
     (when (contains? read-history (:digitalId c))
       "ALREADY READ IT")
     (let [thumb (:thumbnail c)]
       [:img {:src   (str (:path thumb) "." (:extension thumb))
              :style {
                      :width  "60px"
                      :height "85px"}
              :alt   (:title c)}])]))

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
  (println "uh" char)
  (let [comics (get-in char [:getComicsCharacterCollection :data :results])]
    [:div
     (doall (map-indexed
              show-comic
              comics))
     [comics-footer char]]))

