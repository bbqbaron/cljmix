(ns cljmix.schema
  (:require [clojure.java.io :as io]
            [com.walmartlabs.lacinia.util :as util]
            [com.walmartlabs.lacinia.schema :as schema]
            [clojure.edn :as edn]
            [com.stuartsierra.component :as component]
            [prevayler :as prv]))

(defn- edge-resolver [marvel-req entity-root sub-entity]
  (fn [_ args parent]
    (let [path
          ; TODO don't blow up on 404, for resilience
          (filter some? (list "" "v1" "public" entity-root (:id parent) sub-entity))]
      (marvel-req
        (clojure.string/join
          "/"
          path)
        args))))

(defn- resolver-map
  [marvel-req]
  ; TODO these are all just `edge-resolver` with no parent id!
  {:queries/getComicsCollection
                  (fn [_ args _]
                    (marvel-req "v1/public/comics"
                                args))
   :queries/getCharacterCollection
                  (fn [_ args _]
                    (marvel-req "v1/public/characters"
                                args))
   :queries/getCreatorCollection
                  (fn [_ _ _]
                    (marvel-req "v1/public/creators"))
   :queries/getSeriesCollection
                  (fn [_ _ _]
                    (marvel-req "v1/public/series"))
   :queries/getEventsCollection
                  (fn [_ _ _]
                    (marvel-req "v1/public/events"))
   :queries/getStoryCollection
                  (fn [_ _ _]
                    (marvel-req "v1/public/stories"))
   :edge-resolver (partial edge-resolver marvel-req)})


(defn get-schema-edn []
  (-> (io/resource "gen-schema.edn")
      slurp
      edn/read-string))

(defn get-object-resolves-needed
  "Lazy way to get resolve functions we don't have."
  [schema]
  (->>
    schema
    :objects
    vals
    (map :fields)
    (map vals)
    flatten
    (filter (comp some? :resolve))))

(def non-marvel-schema
  {:queries
   {:readHistory
    {:type    '(non-null (list (non-null Int)))
     :resolve :queries/readHistory}}
   :mutations
   {:markRead
    {:args    {:digitalId {:type '(non-null Int)}}
     :type    '(non-null (list (non-null Int)))
     :resolve :mutation/markRead}}})

(defn get-history [db]
  (fn [_ _ _]
    (:read @(:db db))))

(defn update-history [db]
  (fn [_ args _]
    (:read (first (prv/handle! (:db db) [:mark-read (:digitalId args)])))))

(defn raw-schema
  [db-provider marvel-req]
  (let [schema-edn (get-schema-edn)
        placeholders (->> (get-object-resolves-needed schema-edn)
                          (map (fn [{:keys [resolve] :as field}]
                                 [resolve (fn [_ args _]
                                            [])]))
                          (into {}))]
    (-> schema-edn
        (update :queries merge (:queries non-marvel-schema))
        (update :mutations merge (:mutations non-marvel-schema))
        (util/attach-resolvers
          (merge
            (resolver-map marvel-req)
            placeholders
            {:queries/readHistory            (get-history db-provider)
             :mutation/markRead              (update-history db-provider)
             :queries/getCharacterIndividual (fn [_ _ _] nil)})))))

(defrecord SchemaProvider [db-provider marvel-provider schema]

  component/Lifecycle

  (start [this]
    (assoc this :schema
                (schema/compile (raw-schema db-provider (:marvel marvel-provider)))))

  (stop [this]
    (assoc this :schema nil)))

(defn new-schema-provider
  []
  {:schema-provider (component/using (map->SchemaProvider {})
                                     [:db-provider
                                      :marvel-provider])})

