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

; super lazy; holds outright results by the exact hash of the request
; mostly for testing over and over
(defonce lazy-cache (atom {}))

(defn with-lazy-cache [url args otherwise]
  (let [hash-path [(hash url) (hash args)]
        found (get-in @lazy-cache hash-path)]
    (when (some? found)
      (println "returning" url args "from cache"))
    (or found
        (otherwise))))

(defn marvel-req
  ([path] (marvel-req path nil))
  ([path query]
   (println "marvel req: " path query)
   (with-lazy-cache path query
     (fn [] (let [cache (deref caches)
                  last-tag (get-in cache [path :tag])
                  headers (if (some? last-tag)
                           {"If-None-Match" last-tag})
                  resp (http/get
                         (marvel-url path)
                         {:headers headers
                          :query-params query})
                  etag (get-in resp [:headers "ETag"])
                  body (-> resp
                           (get :body)
                           (json/parse-string true))]
              (case (:status resp)
                304
                (do
                  (let [hash-path [(hash path) (hash query)]]
                    (swap! lazy-cache assoc-in hash-path (get-in cache [path :body])))
                  (get-in cache [path :body]))
                (do
                  (let [hash-path [(hash path) (hash query)]]
                    (swap! lazy-cache assoc-in hash-path body))
                  (swap! caches
                         (fn [v]
                           (assoc v path
                                    {:tag  etag
                                     :body body})))
                  body)))))))

(defn edge-resolver [entity-root sub-entity]
  (fn [_ args parent]
    (let [path
          ; TODO don't blow up on 404, for resilience
          (filter some? (list "" "v1" "public" entity-root (:id parent) sub-entity))]
      (marvel-req
        (clojure.string/join
          "/"
          path)
        args))))

(defn resolver-map
  [_]
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