(ns cljmix.view
  (:require [re-frame.core :as rf]
            [re-graph.core :as gql]
            [cljmix.query :as query]
            [cljmix.db :as db]
            cljmix.fx
            [cljmix.sub :as sub]
            [reagent.core :as r]
            [clojure.string :as str]))

(def button-link-style
  {:font "bold 11px Arial"
   :text-decoration "none"
   :background-color "#EEEEEE"
   :color "#333333"
   :padding "2px 6px 2px 6px"
   :border-top "1px solid #CCCCCC"
   :border-right "1px solid #333333"
   :border-bottom "1px solid #333333"
   :border-left "1px solid #CCCCCC"})

(defn grid [els]
  [:div
   {:style {:display "grid"
            :grid-template-columns "1fr 1fr 1fr 1fr"
            :grid-template-rows "auto"}}
   els])

(defn- sub-name [s]
  (or
    (:title s)
    (:lastName s)
    (:name s)))

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
       (sub-name s)]
      [:div.one-half.column>p (:id s)]
      [:div.one-half.column>button
       {:on-click #(rf/dispatch
                     (query/unsubscribe
                       sub-id
                       e-type
                       (:id s)))
        :type :button}
       "X"]]]))

(defn- sub->display [sset]
  (update
   sset
   :time
   (comp
     first
     #(str/split % #"T")
     #(.toISOString
        (js/Date. %)))))

(defn sub-set [sset]
  (let [st (r/atom (sub->display sset))]
    (fn [sset]
      (let [dirty (not= (sub->display sset) @st)]
        [:form.pure-form
         {:style {:border "1px solid gray"}}
         [:input.pure-input-1-2
          {:type "text" :placeholder "subname" :value (:name @st)
           :on-change #(swap! st assoc :name (-> % .-target .-value))}]
         [:input.pure-input-1-2
          {:type "date"
           :on-change
           (fn [e]
             (let [val (.. e -target -value)]
               (swap! st assoc :time val)))
           :value (:time @st)}]
         [:button.pure-button
          {:disabled (not dirty)
           :on-click #(rf/dispatch (query/update-sub
                                     (update
                                       @st
                                       :time
                                       (fn [d] (.valueOf (js/Date. d))))))
           :type :button}
          "Save"]
         [:button.pure-button
          {:on-click #(rf/dispatch [::db/toggle-sub (:id sset)])
           :type :button}
          (if (:toggled? sset)
            "v" ">")]
         (if (:toggled? sset)
           [:div
            (:id sset)
            [grid
             (doall
               (map
                 (partial show-sub (:id sset))
                 (sort-by sub-name
                          (:entities sset))))]]
           [:div])]))))

(defn show-subs []
  (let [subscribed @(rf/subscribe [:subs])]
    [:div
     [:p "Subscribed to: "]
     (for [s subscribed]
       ^{:key (:id s)}
       [sub-set s])]))

(defn- paginate [{:keys [limit total offset]}
                 back forward]
  [:div
   [:p (str offset "-" (+ limit offset) "/" total)]
   [:button.pure-button
    {:disabled
     (zero? offset)
     :on-click
     back}
    "<"]
   [:button.pure-button
    {:disabled
     (<= total (+ limit offset))
     :on-click
     forward}
    ">"]])

(defn search-results [txt]
  (let [results @(rf/subscribe [:search-results])
        subscriptions @(rf/subscribe [:subs])]
    [:div
     (when (empty? results) "No matched results")
     (map
       (fn [[t {es :results
                offset :offset
                :as r}]]
         [:div
          [paginate r
           #(rf/dispatch
              (query/search t txt {:offset (- offset 20)}))
           #(rf/dispatch
              (query/search t txt {:offset (+ offset 20)}))]
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
                 (for [s subscriptions]
                   [:button.pure-button {:on-click #(rf/dispatch (query/subscribe (:id s) t id))} (str "Subscribe to " (:id s) "(beta)")])
                 (let [nid (inc (apply max (map :id subscriptions)))]
                   [:button.pure-button {:on-click #(rf/dispatch (query/subscribe nid t id))} (str "Subscribe to " nid "(beta)")])]))
            es)])
       results)]))

(defn show-comic [i c]
  [:div.flex-column
   {:key i}
   (let [thumb (:thumbnail c)]
     [:img {:src (str (:path thumb) "." (:extension thumb))
            :style {:width "220px"
                    :height "340px"}
            :alt (:title c)}])
   [:a {:key i
        :href (str "https://read.marvel.com/#/book/" (:digitalId c))
        :target "_blank"
        :style button-link-style}
    (:title c)]
   [:button.pure-button
    {:on-click #(rf/dispatch [::gql/mutate
                              query/mark-read
                              {:digitalId (:digitalId c)}
                              [:marked-read (:digitalId c)]])}
    "Dismiss"]])

(defn search-form []
  (let [etype (r/atom :character)
        search (r/atom "")]
    (fn []
      [:div
       [:form.pure-form
        {:on-submit (fn [e]
                      (.preventDefault e)
                      (rf/dispatch (query/search @etype @search nil)))}
        [:select
         {:on-change
          (fn [a]
            (reset! etype (keyword (.. a -target -value))))
          :value @etype}
         [:option {:value :character} "character"]
         [:option {:value :series} "series"]
         [:option {:value :creator} "creator"]]
        [:input {:type "text" :placeholder "Find something" :value @search
                 :on-change #(reset! search (-> % .-target .-value))}]
        [:button.pure-button {:on-click #(rf/dispatch (query/search @etype @search nil))
                              :type :submit}
         "GO"]]
       [search-results @search]])))

(defn pages []
  (let [cur-page @(rf/subscribe [:page])]
    [:nav.flex-row
     (map
       (fn [page]
         [:button.pure-button {:key page :on-click #(rf/dispatch [:page page]) :class (if (= cur-page page) "pure-button-primary" "") :role "link" :style {:margin "0 8px"}} page])
       [:page/subs :page/queue])]))

(defn header []
  [:header
   [:div.flex-row
    [:img {:src "/images/pow.svg", :height "42px"}]
    [:h3 {:style {:margin-left "12px"}} "Marvel Subscription Manager"]]
   [pages]])

(defn subs-view []
  [:div
   [show-subs]
   [search-form]])

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

