(ns cljmix.schema
  (:require [clojure.java.io :as io]
            [com.walmartlabs.lacinia.util :as util]
            [com.walmartlabs.lacinia.schema :as schema]
            [clojure.edn :as edn]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [com.stuartsierra.component :as component])
  (:import (java.security MessageDigest)))

(defn get-keys
  []
  (-> (io/resource "private.edn")
      slurp
      edn/read-string
      :keys))

(def api-keys
  (get-keys))

(def host "http://gateway.marvel.com/")

; https://gist.github.com/jizhang/4325757
(defn md5 [^String s]
  (let [algorithm (MessageDigest/getInstance "MD5")
        raw (.digest algorithm (.getBytes s))]
    (format "%032x" (BigInteger. 1 raw))))

(defn marvel-url
  [path]
  (let [{pub-key :public private-key :private} api-keys
        ts (System/currentTimeMillis)
        hash-in (str
                  ts
                  private-key
                  pub-key)
        hashed (md5 hash-in)
        url
        (str host
             path
             "?ts="
             ts
             "&apikey="
             pub-key
             "&hash="
             hashed)]
    url))

(defonce caches (atom {}))
(deref caches)

(defn marvel-req [path]
  (let [cache (deref caches)
        last-tag (get-in cache [path :tag])
        resp (http/get
               (marvel-url path)
               (if (some? last-tag)
                 {:headers {"If-None-Match" last-tag}}
                 {}))
        etag (get-in resp [:headers "ETag"])
        body (-> resp
                 (get :body)
                 (json/parse-string true))]
    (case (:status resp)
      304 (get-in cache [path :body])
      (do
        (swap! caches
               (fn [v]
                 (assoc v path
                          {:tag  etag
                           :body body})))
        body))))

(defn edge-resolver [entity-root sub-entity]
  (fn [_ args parent]
    (let [path
          ; TODO don't blow up on 404, for resilience
          (filter some? (list "" "v1" "public" entity-root (:id parent) sub-entity))]
      (marvel-req
        (clojure.string/join
          "/"
          path)))))

(defn resolver-map
  [_]
  {:queries/getComicsCollection
                  (fn [_ _ _]
                    (marvel-req "v1/public/comics"))
   :queries/getCharacterCollection
                  (fn [_ _ _]
                    (marvel-req "v1/public/characters"))
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
   :edge-resolver edge-resolver})


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

; TODO remove this
(defn load-schema
  [component]
  (let [schema-edn (get-schema-edn)
        placeholders (->> (get-object-resolves-needed schema-edn)
                          (map (fn [{:keys [resolve] :as field}]
                                 [resolve (fn [_ args _]
                                            [])]))
                          (into {}))]
    (-> schema-edn
        (util/attach-resolvers
          (merge (resolver-map component) placeholders))
        schema/compile)))


(defrecord SchemaProvider [schema]

  component/Lifecycle

  (start [this]
    (assoc this :schema (load-schema this)))

  (stop [this]
    (assoc this :schema nil)))

(defn new-schema-provider
  []
  {:schema-provider (map->SchemaProvider {})})

(new-schema-provider)