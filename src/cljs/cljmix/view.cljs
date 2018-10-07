(ns cljmix.view
  (:require [re-frame.core :as rf]
            [re-graph.core :as gql]
            [cljmix.query :as query]
            cljmix.db
            cljmix.fx
            cljmix.sub
            [reagent.core :as ra]))

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

(defn subscriptions []
  (let [subscribed @(rf/subscribe [:subs])]
    [:div
     [:p "Subscribed to: "]
     (map
       (fn [sub]
         (let [character (get-in sub [:data :results 0])]
           [:div {:key (:id character)}
            [:p (:name character)]
            [:button {:on-click #(rf/dispatch
                                   (query/unsubscribe-character
                                     (:id character)))}
             "Unsubscribe"]]))
       subscribed)]))

(defn char-search-results []
  (let [chars @(rf/subscribe [:char-search-result])]
    [:div
     (map
       (fn [{id :id char-name :name}]
         [:div {:key id}
          [:p char-name]
          [:button {:on-click #(rf/dispatch (query/subscribe-character id))} "Subscribe"]])
       (vals chars))]))

(defn show-comic [i c]
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
    "Already read it"]])

(defn char-search-form []
  (let [search (ra/atom "")]
    (fn []
      [:form
       {:on-submit #(.preventDefault %)}
       [:input {:type       "text" :placeholder "Find a character!" :value @search
                :auto-focus true
                :on-change  #(reset! search (-> % .-target .-value))}]
       [:button {:on-click #(rf/dispatch (query/search-char @search))
                 :type     :submit}
        "GO"]])))

(defn pages []
  [:div
   (map
     (fn [page]
       [:button {:key page :on-click #(rf/dispatch [:page page])} page])
     [:page/char :page/queue])])

(defn char-search []
  [:div
   [subscriptions]
   [char-search-results]
   [char-search-form]])

(defn queue []
  (let [unread @(rf/subscribe [:unread-comics])]
    [:div
     [char-search-form]
     [grid (doall (map-indexed
                    show-comic
                    unread))]]))

