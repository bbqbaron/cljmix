(ns cljmix.schema
  (:require [clojure.java.io :as io]
            [com.walmartlabs.lacinia.util :as util]
            [com.walmartlabs.lacinia.schema :as schema]
            [clojure.edn :as edn]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [com.stuartsierra.component :as component]))

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
  (let [algorithm (java.security.MessageDigest/getInstance "MD5")
        raw (.digest algorithm (.getBytes s))]
    (format "%032x" (BigInteger. 1 raw))))

(defn marvel-url
  [path]
  (let [{pub-key :public priv-key :private} api-keys
        ts (System/currentTimeMillis)
        hash-in (str
                  ts
                  priv-key
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
    (prn (:status resp) etag last-tag)
    (:data (case (:status resp)
             304 (get-in cache [path :body])
             (do
               (swap! caches
                 (fn [v]
                   (assoc v path
                     {:tag etag
                      :body body})))
               body)))))

(defn resolver-map
  [component]
  {:query/comics (fn [cxt args val]
                   (let [resp (marvel-req "v1/public/comics")]
                     resp))})

(defn load-schema
  [component]
  (-> (io/resource "marvel-schema.edn")
      slurp
      edn/read-string
      (util/attach-resolvers (resolver-map component))
      schema/compile))

(defrecord SchemaProvider [schema]

  component/Lifecycle

  (start [this]
    (assoc this :schema (load-schema this)))

  (stop [this]
    (assoc this :schema nil)))

(defn new-schema-provider
  []
  {:schema-provider (map->SchemaProvider {})})
