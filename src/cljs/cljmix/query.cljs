(ns cljmix.query
  (:require [venia.core :as v]
            [re-graph.core :as gql]))

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
                          [
                           :total
                           :count
                           :limit
                           :offset
                           [:results
                            [:name
                             :id
                             [:thumbnail
                              [:extension :path]]]]]]])

(def mark-read
  (v/graphql-query {:venia/queries   [[:markRead {:digitalId :$digitalId}]]
                    :venia/variables [{:variable/name "digitalId"
                                       :variable/type :Int!}]
                    :venia/operation {:operation/type :mutation
                                      :operation/name "MarkRead"}}))

(def get-subs
  (v/graphql-query {:venia/queries   [[:subscribedCharacters
                                       character-fragment]]
                    :venia/operation {:operation/type :query
                                      :operation/name "GetSubs"}}))

(def unsub-mutation
  (v/graphql-query {:venia/queries   [[:unsubscribeCharacter {:charId :$charId}
                                       character-fragment]]
                    :venia/variables [{:variable/name "charId"
                                       :variable/type :Int!}]
                    :venia/operation {:operation/type :mutation
                                      :operation/name "UnsubscribeFromCharacter"}}))

(def subscribe-character-mutation
  (v/graphql-query {:venia/queries   [[:subscribeCharacter {:charId :$charId}
                                       character-fragment]]
                    :venia/variables [{:variable/name "charId"
                                       :variable/type :Int!}]
                    :venia/operation {:operation/type :mutation
                                      :operation/name "SubscribeToCharacter"}}))

(def feed
  (v/graphql-query {:venia/operation {:operation/name "GetFeed"
                                      :operation/type :query}
                    :venia/variables [{:variable/name "offset"
                                       :variable/type :Int}]
                    :venia/queries   [[:feed
                                       [
                                        :total
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
                    :venia/queries   [[:getCharacterIndividual
                                       {:charId :$charId}
                                       character-fragment]]
                    :venia/variables [{:variable/name "charId" :variable/type :String!}]}))

(def char-search-query
  (v/graphql-query {:venia/operation {:operation/name "SearchCharacter"
                                      :operation/type :query}
                    :venia/queries   [[:getCharacterCollection
                                       {:nameStartsWith :$charName
                                        :offset         :$offset}
                                       character-fragment]]
                    :venia/variables [{:variable/name "charName"
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

(defn search-char
  [char-name]
  [::gql/query
   char-search-query
   {:charName char-name}
   [:char-search-result]])

(defn get-feed
  [offset]
  [::gql/query
   feed
   {:offset offset}
   [:feed-result]])

(defn subscribe-character [char-id]
  [::gql/mutate
   subscribe-character-mutation
   {:charId char-id}
   [:subscribed]])

(defn unsubscribe-character [char-id]
  [::gql/mutate
   unsub-mutation
   {:charId char-id}
   [:unsubscribed]])
