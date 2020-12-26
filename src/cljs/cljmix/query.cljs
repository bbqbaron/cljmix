(ns cljmix.query
  (:require [venia.core :as v]
            [re-graph.core :as gql]))

(defn inline-fragment
  [type fields]
  [(keyword (str "... on " (name type))) fields])

(def get-time-query
  (v/graphql-query {:venia/queries [[:getTime]]
                    :venia/operation {:operation/type :query
                                      :operation/name "GetTime"}}))

(def set-time-mutation
  (v/graphql-query {:venia/queries [[:setTime {:time :$time}]]
                    :venia/variables [{:variable/name "time"
                                       :variable/type :Float!}]
                    :venia/operation {:operation/type :mutation
                                      :operation/name "SetTime"}}))

(def character-fragment [[:data
                          [:total
                           :count
                           :limit
                           :offset
                           [:results
                            [:name
                             :id
                             [:thumbnail
                              [:extension :path]]]]]]])

(def series-fragment [[:data
                       [:total
                        :count
                        :limit
                        :offset
                        [:results
                         [:title
                          :id
                          [:thumbnail
                           [:extension :path]]]]]]])

(def creator-fragment [[:data
                        [:total
                         :count
                         :limit
                         :offset
                         [:results
                          [:fullName
                           :id
                           [:thumbnail
                            [:extension :path]]]]]]])

(def mark-read
  (v/graphql-query {:venia/queries [[:markRead {:digitalId :$digitalId}]]
                    :venia/variables [{:variable/name "digitalId"
                                       :variable/type :Int!}]
                    :venia/operation {:operation/type :mutation
                                      :operation/name "MarkRead"}}))

(def get-char-subs
  (v/graphql-query {:venia/queries [[:subscribedCharacters
                                     character-fragment]]
                    :venia/operation {:operation/type :query
                                      :operation/name "GetCharSubs"}}))

(def get-subs
  (v/graphql-query {:venia/queries [[:subscriptions
                                     [:id
                                      [:entities
                                       [(inline-fragment
                                          :Character
                                          [:id
                                           :name])
                                        (inline-fragment
                                          :Creator
                                          [:id
                                           :lastName
                                           :firstName])
                                        (inline-fragment
                                          :Series
                                          [:id
                                           :title])]]]]]
                    :venia/operation {:operation/type :query
                                      :operation/name "GetSubs"}}))

(def unsubscribe-mutation
  (v/graphql-query {:venia/queries [[:unsubscribe {:subId :$subId
                                                   :entityType :$entityType
                                                   :entityId :$entityId}
                                     [:id
                                      [:entities
                                       [(inline-fragment
                                          :Character
                                          [:id
                                           :name])
                                        (inline-fragment
                                          :Creator
                                          [:id
                                           :lastName
                                           :firstName])
                                        (inline-fragment
                                          :Series
                                          [:id
                                           :title])]]]]]
                    :venia/variables [{:variable/name "subId"
                                       :variable/type :Int!}
                                      {:variable/name "entityId"
                                       :variable/type :Int!}
                                      {:variable/name "entityType"
                                       :variable/type :entityType}]
                    :venia/operation {:operation/type :mutation
                                      :operation/name "Unsubscribe"}}))

(def subscribe-mutation
  (v/graphql-query {:venia/queries [[:subscribe {:subId :$subId
                                                 :entityType :$entityType
                                                 :entityId :$entityId}
                                     [:id
                                      [:entities
                                       [(inline-fragment
                                          :Character
                                          [:id
                                           :name])
                                        (inline-fragment
                                          :Creator
                                          [:id
                                           :lastName
                                           :firstName])
                                        (inline-fragment
                                          :Series
                                          [:id
                                           :title])]]]]]
                    :venia/variables [{:variable/name "subId"
                                       :variable/type :Int!}
                                      {:variable/name "entityId"
                                       :variable/type :Int!}
                                      {:variable/name "entityType"
                                       :variable/type :entityType}]
                    :venia/operation {:operation/type :mutation
                                      :operation/name "Subscribe"}}))

(def unsubscribe-character-mutation
  (v/graphql-query {:venia/queries [[:unsubscribeCharacter {:charId :$charId}
                                     character-fragment]]
                    :venia/variables [{:variable/name "charId"
                                       :variable/type :Int!}]
                    :venia/operation {:operation/type :mutation
                                      :operation/name "UnsubscribeFromCharacter"}}))

(def subscribe-character-mutation
  (v/graphql-query {:venia/queries [[:subscribeCharacter {:charId :$charId}
                                     character-fragment]]
                    :venia/variables [{:variable/name "charId"
                                       :variable/type :Int!}]
                    :venia/operation {:operation/type :mutation
                                      :operation/name "SubscribeToCharacter"}}))

(def feed
  (v/graphql-query {:venia/operation {:operation/name "GetFeed"
                                      :operation/type :query}
                    :venia/variables [{:variable/name "offset"
                                       :variable/type :Int}
                                      {:variable/name "subscriptionId"
                                       :variable/type :Int}]
                    :venia/queries [[:feed
                                     {:subscriptionId :$subscriptionId
                                      :offset :$offset}
                                     [:total
                                      :count
                                      :limit
                                      :offset
                                      [:results
                                       [:digitalId
                                        :description
                                        :title
                                        [:dates [:type :date]]
                                        [:series [:name :resourceURI]]
                                        [:thumbnail [:extension :path]]]]]]]}))



(def char-query
  (v/graphql-query {:venia/operation {:operation/name "GetCharacter"
                                      :operation/type :query}
                    :venia/queries [[:getCharacterIndividual
                                     {:charId :$charId}
                                     character-fragment]]
                    :venia/variables [{:variable/name "charId" :variable/type :String!}]}))

(def char-search-query
  (v/graphql-query {:venia/operation {:operation/name "SearchCharacter"
                                      :operation/type :query}
                    :venia/queries [[:getCharacterCollection
                                     {:nameStartsWith :$charName
                                      :offset :$offset}
                                     character-fragment]]
                    :venia/variables [{:variable/name "charName"
                                       :variable/type :String!}
                                      {:variable/name "offset"
                                       :variable/type :Int}]}))

(def series-search-query
  (v/graphql-query {:venia/operation {:operation/name "SearchComics"
                                      :operation/type :query}
                    :venia/queries [[:getSeriesCollection
                                     {:titleStartsWith :$seriesName
                                      :offset :$offset}
                                     series-fragment]]
                    :venia/variables [{:variable/name "seriesName"
                                       :variable/type :String!}
                                      {:variable/name "offset"
                                       :variable/type :Int}]}))

(def creator-search-query
  (v/graphql-query {:venia/operation {:operation/name "SearchCreators"
                                      :operation/type :query}
                    :venia/queries [[:getCreatorCollection
                                     {:nameStartsWith :$creatorName
                                      :offset :$offset}
                                     creator-fragment]]
                    :venia/variables [{:variable/name "creatorName"
                                       :variable/type :String!}
                                      {:variable/name "offset"
                                       :variable/type :Int}]}))

(defn set-time
  [time]
  [::gql/mutate set-time-mutation
   {:time time}
   [:set-time-result time]])

(defn get-time
  []
  [::gql/query get-time-query
   {}
   [:get-time-result]])

(defn get-chars
  [ids]
  (map
    #([::gql/query
       char-query
       {:charId %}
       [:char-result %]])
    ids))

(defn search-series
  [series-name]
  [::gql/query
   series-search-query
   {:seriesName series-name}
   [:series-search-result]])

(defn search-creator
  [nm]
  [::gql/query
   creator-search-query
   {:creatorName nm}
   [:creator-search-result]])

(defn search-char
  [char-name]
  [::gql/query
   char-search-query
   {:charName char-name}
   [:char-search-result]])

(defn search
  [etype text]
  (case etype
    :character (search-char text)
    :series (search-series text)
    :creator (search-creator text)))

(defn get-feed
  ([offset]
   (get-feed nil offset))
  ([id offset]
   [::gql/query
    feed
    {:offset offset
     :subscriptionId id}
    [:feed-result]]))

(defn subscribe [sub-id ent-type ent-id]
  [::gql/mutate
   subscribe-mutation
   {:subId sub-id
    :entityType ent-type
    :entityId ent-id}
   [:subscribed]])

(defn unsubscribe [sub-id ent-type ent-id]
  [::gql/mutate
   unsubscribe-mutation
   {:subId sub-id
    :entityType ent-type
    :entityId ent-id}
   [:unsubscribed]])

(defn subscribe-character [char-id]
  [::gql/mutate
   subscribe-character-mutation
   {:charId char-id}
   [:subscribed-character]])

(defn unsubscribe-character [char-id]
  [::gql/mutate
   unsubscribe-character-mutation
   {:charId char-id}
   [:unsubscribed-character]])


