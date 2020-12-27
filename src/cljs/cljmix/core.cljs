(ns ^:figwheel-hooks cljmix.core
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

(rf/dispatch (query/get-feed 0 nil))
(rf/dispatch [::gql/query
              query/get-char-subs
              {}
              [:char-subs-result]])

(rf/dispatch [::gql/query
              query/get-subs
              {}
              [:subs-result]])

(rf/dispatch (query/get-time))

(defn ui
  []
  (let [page @(rf/subscribe [:page])]
    [:div
     [vw/header]
     [:main
     (case page
       :page/subs [vw/subs-view]
       :page/queue [vw/queue])]]))

(defn ^:after-load re-render []
  (ra/render [ui]
             (js/document.getElementById "app")))

(defn ^:export run
  []
  (rf/dispatch-sync [:initialize])
  (re-render))

