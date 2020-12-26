(ns cljmix.view
  (:require [re-frame.core :as rf]
            [re-graph.core :as gql]
            [cljmix.query :as query]
            [cljmix.db :as db]
            cljmix.fx
            [cljmix.sub :as sub]
            [reagent.core :as r]))


(defn show-time []
  (let [state (r/atom nil)]
    (fn []
      (let [time @(rf/subscribe [:time])]
        [:div
         [:p "Comics after: " (.toLocaleDateString (js/Date. time) "en-US")]
         [:form {:on-submit (fn [e]
                              (.preventDefault e)
                              (let [val @state]
                                (rf/dispatch (query/set-time
                                               (+
                                                 (.valueOf (js/Date. val))
                                                 (*
                                                   ; TODO just get a date lib; i think my browser isn't even right about DST
                                                   (+ 60 (.getTimezoneOffset (js/Date.))
                                                      60 1000)))))))}
          [:input {:type  "date"
                   :on-change
                          (fn [e]
                            (let [val (.. e -target -value)]
                              (reset! state val)))
                   :value @state}]
          [:button {:type "submit"} "Submit"]]]))))

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

(defn show-char-sub
  [sub]
  (let [character (get-in sub [:data :results 0])]
    [:div.container {:key (:id character)}
     [:div.row>img {:src
                           (let [{:keys [extension path]} (:thumbnail character)]
                             (str path "." extension))
                    :style {:width 128 :height 128}}]
     [:div.row
      [:div.one-half.column>p (:name character)]
      [:div.one-half.column>button
       {:on-click #(rf/dispatch
                     (query/unsubscribe-character
                       (:id character)))}
       "X"]]]))

(defn show-char-subs []
  (let [subscribed @(rf/subscribe [:char-subs])]
    [:div
     [show-time]
     [:p "Subscribed to: "]
     [grid (map
             show-char-sub
             subscribed)]]))

(defn show-sub [sub-id s]
  (let [e-type (cond
                 (:title s) :series
                 (:lastName s) :creator
                 (:name s) :character)]
    [:div.container {:key (:id s)
                     :style {:border "1px solid black"}}
     [:div.row
      [:div.one-half.column>p
       (cond
         (:title s) "Series"
         (:lastName s) "Creator"
         (:name s) "Character")]
      [:div.one-half.column>p
       (or
         (:title s)
         (:lastName s)
         (:name s))]
      [:div.one-half.column>p (:id s)]
      [:div.one-half.column>button
       {:on-click #(rf/dispatch
                     (query/unsubscribe
                       sub-id
                       e-type
                       (:id s)))}
       "X"]]]))

(defn show-subs []
  (let [subscribed @(rf/subscribe [:subs])]
    [:div
     [show-time]
     [:p "Subscribed to: "]
     (map
      (fn
        [sset]
        [:div
         (:id sset)
         [grid
          (map
            (partial show-sub (:id sset)) (:entities sset))]])
      subscribed)]))

(defn search-results []
  (let [results @(rf/subscribe [:search-results])
        subs @(rf/subscribe [:subs])]
    [:div
     (mapcat
      (fn [[t es]]
        (map
         (fn [{id :id
               :as e}]
           (let [nm
                 (e
                  (case t
                    :character :name
                    :series :title
                    :creator :fullName))]
             [:div {:key id}
              [:p nm]
              (when (= t :character)
                [:button {:on-click #(rf/dispatch (query/subscribe-character id))} "Subscribe"])
              (for [s subs]
                [:button {:on-click #(rf/dispatch (query/subscribe 0 t id))} (str "Subscribe to " (:id s) "(beta)")])
              (let [nid (inc (apply max (map :id subs)))]
                [:button {:on-click #(rf/dispatch (query/subscribe nid t id))} (str "Subscribe to " nid "(beta)")])]))
                   (vals es)))
      results)]))

(defn char-search-results []
  (let [chars @(rf/subscribe [:char-search-result])
        subs @(rf/subscribe [:subs])]
    [:div
     (map
       (fn [{id :id char-name :name}]
         [:div {:key id}
          [:p char-name]
          [:button {:on-click #(rf/dispatch (query/subscribe-character id))} "Subscribe"]
          (for [s subs]
            [:button {:on-click #(rf/dispatch (query/subscribe 0 :character id))} (str "Subscribe to " (:id s) "(beta)")])
          (let [nid (inc (apply max (map :id subs)))]
            [:button {:on-click #(rf/dispatch (query/subscribe nid :character id))} (str "Subscribe to " nid "(beta)")])])
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
                              [:marked-read (:digitalId c)]])}
    "Dismiss"]])

(defn search-form []
  (let [etype (r/atom :character)
        search (r/atom "")]
    (fn []
      [:form
       {:on-submit (fn [e]
                     (.preventDefault e)
                     (rf/dispatch (query/search @etype @search)))}
       [:select
        {:on-change
         (fn [a]
           (reset! etype (keyword (.. a -target -value))))
         :value @etype}
        [:option {:value :character} "character"]
        [:option {:value :series} "series"]
        [:option {:value :creator} "creator"]]
       [:input {:type       "text" :placeholder "Find something" :value @search
                :auto-focus true
                :on-change  #(reset! search (-> % .-target .-value))}]
       [:button {:on-click #(rf/dispatch (query/search @etype @search))
                 :type     :submit}
        "GO"]])))

(defn char-search-form []
  (let [search (r/atom "")]
    (fn []
      [:form
       {:on-submit (fn [e]
                     (.preventDefault e)
                     (rf/dispatch (query/search-char @search)))}
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
     [:page/subs :page/queue])])

(defn subs []
  [:div
   [show-char-subs]
   [show-subs]
   [char-search-results]
   [search-results]
   [search-form]
   [char-search-form]])

(defn queue []
  (let [unread @(rf/subscribe [:unread-comics])
        subscriptions @(rf/subscribe [:subs])
        current-sub @(rf/subscribe [::sub/current-subscription])]
    [:div
     [:select
      {:on-change
       (fn [a]
         (rf/dispatch [:select-subscription (js/parseInt (.. a -target -value))]))
       :value current-sub}
      (map
        (fn [s]
          [:option {:value (:id s)} (str (:id s))])
        subscriptions)]
     [grid (doall (map-indexed
                    show-comic
                    unread))]]))

