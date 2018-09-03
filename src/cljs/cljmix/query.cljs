(ns cljmix.query
  (:require [venia.core :as v]
            [re-frame.core :as rf]
            [re-graph.core :as gql]))

(def mark-read
  (v/graphql-query {:venia/queries   [[:markRead {:digitalId :$digitalId}]]
                    :venia/variables [{:variable/name "digitalId"
                                       :variable/type :Int!}]
                    :venia/operation {:operation/type :mutation
                                      :operation/name "MarkComicRead"}}))

(def read-history
  (v/graphql-query {:venia/operation {:operation/name "GetReadHistory"
                                      :operation/type :query}
                    :venia/queries   [[:readHistory]]}))

(def char-comics
  (v/graphql-query {:venia/operation {:operation/name "GetComicsForCharacter"
                                      :operation/type :query}
                    :venia/variables [{:variable/name "charIds"
                                       :variable/type (keyword "[Int!]!")}
                                      {:variable/name "offset"
                                       :variable/type :Int!}]
                    :venia/queries   [[:getComicsCollection
                                       {:characters      :$charIds
                                        :offset          :$offset
                                        :hasDigitalIssue true :orderBy "onsaleDate"}
                                       [[:data
                                         [
                                          :total
                                          :count
                                          :limit
                                          :offset
                                          [:results
                                           [:digitalId
                                            :description
                                            :title
                                            [:series [:name :resourceURI]]
                                            [:thumbnail [:extension :path]]
                                            [:images [:extension :path]]]]]]]]]}))

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
                                             [:extension :path]]
                                            [:getComicsCharacterCollection
                                             {:hasDigitalIssue true :orderBy "onsaleDate"}
                                             [[:data
                                               [
                                                :total
                                                :count
                                                :limit
                                                :offset
                                                [:results
                                                 [:digitalId
                                                  :description
                                                  :title
                                                  [:series [:name :resourceURI]]
                                                  [:thumbnail [:extension :path]]
                                                  [:images [:extension :path]]]]]]]]]]]]]]]
                    :venia/variables [{:variable/name "charName"
                                       :variable/type :String!}
                                      {:variable/name "offset"
                                       :variable/type :Int}]}))

(defn search-char
  [char-name]
  (rf/dispatch [::gql/query
                char-query
                {:charName char-name}
                [:query-result]]))

(defn get-comics
  [char-id offset]
  (rf/dispatch [::gql/query
                char-comics
                {:charIds [char-id]
                 :offset  offset}
                [:comics-result]]))
