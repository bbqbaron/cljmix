(ns cljmix.core
  (:require [re-frame.core :as rf]
            [reagent.core :as ra]
            [re-graph.core :as gql]
            [cljmix.gql :refer [to-query-string]]
            [cljmix.query :as query]
            cljmix.db
            cljmix.fx
            cljmix.sub
            [cljmix.view :as vw]))

(enable-console-print!)

(rf/dispatch [::gql/init {:ws-url nil}])

(rf/dispatch [::gql/query query/read-history
              {}
              [:read-history-result]])

(query/search-char "Colossus")

(defn ui
  []
  (let [search (ra/atom "")]
    (fn []
      (let [char @(rf/subscribe [:char])]
        [:div
         [vw/choose-character]
         [:form
          {:on-submit #(.preventDefault %)}
          [:input {:type       "text" :placeholder "Find a character!" :value @search
                   :auto-focus true
                   :on-change  #(reset! search (-> % .-target .-value))}]
          [:button {:on-click #(query/search-char @search)
                    :type     :submit}
           "GO"]]
         (when char
           [:div {:key (:name char)}
            [:h2 (str "Char: " (:name char))]
            [vw/show-comix
             (get-in char [:getComicsCharacterCollection :data])]])]))))


(defn ^:export run
  []
  (rf/dispatch-sync [:initialize])
  (ra/render [ui]
             (js/document.getElementById "app")))

