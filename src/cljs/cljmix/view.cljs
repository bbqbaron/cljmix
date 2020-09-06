(ns cljmix.view
  (:require [re-frame.core :as rf]
            [re-graph.core :as gql]
            [cljmix.query :as query]
            cljmix.db
            cljmix.fx
            cljmix.sub
            [reagent.core :as r]))


(defn show-time []
  (let [state (r/atom nil)]
    (fn []
      (let [time @(rf/subscribe [:time])]
        [:div
         [:p "Comics after: " (.toLocaleDateString (js/Date. time) "en-US")]
         [:form.pure-form {:on-submit (fn [e]
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
          [:button.pure-button {:type "submit"} "Submit"]]]))))

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
            :grid-template-rows    "auto"}}
   els])

(defn show-sub
  [sub]
  (let [character (get-in sub [:data :results 0])]
    [:div.container {:key (:id character)}
     [:div.row>img {:src
                    (let [{:keys [extension path]} (:thumbnail character)]
                      (str path "." extension))
                    :style {:width 128 :height 128}}]
     [:div.flex-row
      [:div>p (:name character)]
      [:div>button.pure-button.button-xsmall
       {:on-click #(rf/dispatch
                    (query/unsubscribe-character
                     (:id character)))}
       "X"]]]))

(defn show-subs []
  (let [subscribed @(rf/subscribe [:subs])]
    [:div
     [show-time]
     [:p "Subscribed to: "]
     [grid (map
            show-sub
            subscribed)]]))

(defn search-results []
  (let [chars @(rf/subscribe [:char-search-result])]
    [:div
     (cond (empty? chars) "No matched results")
     (map
      (fn [{id :id char-name :name}]
        [:div {:key id}
         [:p char-name]
         [:button.pure-button {:on-click #(rf/dispatch (query/subscribe-character id))} "Subscribe"]
         [:button.pure-button {:on-click #(rf/dispatch (query/subscribe 0 :character id))} "Subscribe to 0 (beta)"]])
      (vals chars))]))

(defn char-search-results []
  (let [chars @(rf/subscribe [:char-search-result])]
    [:div
     (map
      (fn [{id :id char-name :name}]
        [:div {:key id}
         [:p char-name]
         [:button.pure-button {:on-click #(rf/dispatch (query/subscribe-character id))} "Subscribe"]
         [:button.pure-button {:on-click #(rf/dispatch (query/subscribe 0 :character id))} "Subscribe to 0 (beta)"]])
      (vals chars))]))

(defn show-comic [i c]
  [:div.flex-column
   {:key   i}
   (let [thumb (:thumbnail c)]
     [:img {:src   (str (:path thumb) "." (:extension thumb))
            :style {:width  "220px"
                    :height "340px"}
            :alt   (:title c)}])
   [:a {:key    i
        :href   (str "https://read.marvel.com/#/book/" (:digitalId c))
        :target "_blank"
        :style  button-link-style}
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
      [:form.pure-form
       {:on-submit (fn [e]
                     (.preventDefault e)
                     (rf/dispatch (query/search @etype @search)))}
       [:select
        {:on-change
         (fn [a]
           (reset! etype (keyword (.. a -target -value))))}
        [:option {:value :character :selected (= @etype :character)} "character"]
        [:option {:value :series :selected (= @etype :series)} "series"]]
       [:input {:type       "text" :placeholder "Find something" :value @search
                :on-change  #(reset! search (-> % .-target .-value))}]
       [:button.pure-button {:on-click #(rf/dispatch (query/search @etype @search))
                             :type     :submit}
        "GO"]])))

(defn char-search-form []
  (let [search (r/atom "")]
    (fn []
      [:form.pure-form
       {:on-submit (fn [e]
                     (.preventDefault e)
                     (rf/dispatch (query/search-char @search)))}
       [:input {:type       "text" :placeholder "Find a character!" :value @search
                :on-change  #(reset! search (-> % .-target .-value))}]
       [:button.pure-button {:on-click #(rf/dispatch (query/search-char @search))
                             :type     :submit}
        "GO"]])))


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

(defn subs []
  [:div
   [show-subs]
   [char-search-results]
   [search-results]
   [search-form]
   [char-search-form]])

(defn queue []
  (let [unread @(rf/subscribe [:unread-comics])]
    [:div
     [grid (doall (map-indexed
                   show-comic
                   unread))]]))

