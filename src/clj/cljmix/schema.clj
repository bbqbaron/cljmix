(ns cljmix.schema
  (:require [clojure.java.io :as io]
            [com.walmartlabs.lacinia.util :as util]
            [com.walmartlabs.lacinia.schema :as schema]
            [clojure.edn :as edn]
            [com.stuartsierra.component :as component]
            [prevayler :as prv]))

(defn- edge-resolver
  ; TODO don't blow up on 404, for resilience
  [marvel-req [entity-root [path-type path-key] sub-entity]]
  (fn [_ args parent]
    (let [path-arg-provider (case path-type
                              :root parent
                              :args args
                              nil)
          arg-segment
          (when (some? path-arg-provider)
            (get path-arg-provider
                 path-key))
          path
          (filter some? (list "" "v1" "public"
                              entity-root
                              arg-segment
                              sub-entity))
          req-body (case path-type
                     :args (dissoc args path-key)
                     args)]
      (marvel-req
        (clojure.string/join
          "/"
          path)
        req-body))))

(defn- resolver-map
  [marvel-req]
  {:queries/getComicsCollection
                  (edge-resolver marvel-req ["comics"])
   :queries/getCharacterCollection
                  (edge-resolver marvel-req ["characters"])
   :queries/getCreatorCollection
                  (edge-resolver marvel-req ["creators"])
   :queries/getSeriesCollection
                  (edge-resolver marvel-req ["series"])
   :queries/getEventsCollection
                  (edge-resolver marvel-req ["events"])
   :queries/getStoryCollection
                  (edge-resolver marvel-req ["stories"])
   :queries/getCharacterIndividual
                  (edge-resolver marvel-req ["characters" [:args :characterId]])
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

(defn subscribe-character
  [db _ args _]
  (let [[new-db] (prevayler/handle! db [:subscribe-character (:charId args)])]
    (:subscribed-characters new-db)))

(def non-marvel-schema
  {:queries
   {:readHistory
    {:type    '(non-null (list (non-null Int)))
     :resolve :queries/readHistory}
    :feed
    ; TODO ComicDataContainer probably promises too much; we can't really count results
    {:type    '(non-null ComicDataContainer)
     :resolve :queries/getFeed
     :args
              {:limit  {:type 'Int}
               :offset {:type 'Int}}}
    :subscribedCharacters
    {:type    '(non-null (list (non-null Int)))
     :resolve :queries/subscribedCharacters}
    :getTime
    {:type    'Int
     :resolve :queries/getTime}}
   :mutations
   {:markRead
    {:args    {:digitalId {:type '(non-null Int)}}
     :type    '(non-null (list (non-null Int)))
     :resolve :mutation/markRead}
    :subscribeCharacter
    {:args    {:charId {:type '(non-null Int)}}
     :resolve :mutation/subscribeCharacter
     :type    '(non-null (list (non-null Int)))}
    :setTime
    {:args    {:time {:type 'Float}}
     :resolve :mutation/setTime
     :type    'Float}}})

(defn get-history [db]
  (fn [_ _ _]
    (:read @(:db db))))

(defn update-history [db]
  (fn [_ args _]
    (:read (first (prv/handle! (:db db) [:mark-read (:digitalId args)])))))

(defn get-subscribed-characters
  [db _ _ _]
  (:subscribed-characters @db))

(defn get-time
  [db _ _ _]
  (:time @db))

(defn set-time
  [db _ args _]
  (let [time (:time args)]
    (prevayler/handle! db [:set-time time])
    time))

(defn raw-schema
  [db-provider {marvel-req :marvel get-feed :get-feed}]
  (let [schema-edn (get-schema-edn)
        placeholders (->> (get-object-resolves-needed schema-edn)
                          (map (fn [{:keys [resolve]}]
                                 [resolve (fn [_ _ _]
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
             :mutation/subscribeCharacter    (partial subscribe-character (:db db-provider))
             :queries/getFeed                get-feed
             :queries/subscribedCharacters   (partial get-subscribed-characters (:db db-provider))
             :queries/getCharacterIndividual (fn [_ args _]
                                               (marvel-req (str
                                                             "v1/public/character"
                                                             (:id args))))
             :queries/getTime                (partial get-time (:db db-provider))
             :mutation/setTime               (partial set-time (:db db-provider))})))))

(defrecord SchemaProvider [db-provider marvel-provider schema]

  component/Lifecycle

  (start [this]
    (assoc this :schema
                (schema/compile (raw-schema db-provider marvel-provider))))

  (stop [this]
    (assoc this :schema nil)))

(defn new-schema-provider
  []
  {:schema-provider (component/using (map->SchemaProvider {})
                                     [:db-provider
                                      :marvel-provider])})

