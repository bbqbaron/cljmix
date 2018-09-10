(ns cljmix.query
  (:require [venia.core :as v]
            [re-frame.core :as rf]
            [re-graph.core :as gql]))

(def mark-read
  (v/graphql-query {:venia/queries   [[:markRead {:digitalId :$digitalId}]]
                    :venia/variables [{:variable/name "digitalId"
                                       :variable/type :Int!}]
                    :venia/operation {:operation/type :mutation
                                      :operation/name "MarkRead"}}))

(def get-subs
  (v/graphql-query {:venia/queries [[:subscribedCharacters]]
                    :venia/operation {:operation/type :query
                                      :operation/name "GetSubs"}}))

(def subscribe-character-mutation
  (v/graphql-query {:venia/queries   [[:subscribeCharacter {:charId :$charId}]]
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
                    :venia/queries   [[:getCharacterCollection
                                       {:nameStartsWith :$charName
                                        :offset         :$offset}
                                       [[:data
                                         [
                                          :total
                                          :count
                                          :limit
                                          :offset
                                          [:results
                                           [:name
                                            :id
                                            [:thumbnail
                                             [:extension :path]]]]]]]]]
                    :venia/variables [{:variable/name "charName"
                                       :variable/type :String!}
                                      {:variable/name "offset"
                                       :variable/type :Int}]}))

(defn search-char
  [char-name]
  (rf/dispatch [::gql/query
                char-query
                {:charName char-name}
                [:char-search-result]]))

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
   [:subscribed char-id]])

