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

(rf/dispatch (query/get-feed 0))

(defn ui
  []
  (let [page @(rf/subscribe [:page])]
    [:div
     [vw/pages]
     (case page
       :page/char [vw/char-search]
       :page/queue [vw/queue])]))

(defn ^:export run
  []
  (rf/dispatch-sync [:initialize])
  (ra/render [ui]
             (js/document.getElementById "app")))

