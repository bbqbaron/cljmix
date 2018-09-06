(ns cljmix.marvel
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [cheshire.core :as json]
            [clj-http.client :as http]
            [prevayler :as prv])
  (:import (java.security MessageDigest)))

(defn- get-keys
  []
  (-> (io/resource "private.edn")
      slurp
      edn/read-string
      :keys))

(def api-keys
  (get-keys))

(def host "http://gateway.marvel.com/")

; https://gist.github.com/jizhang/4325757
(defn- md5 [^String s]
  (let [algorithm (MessageDigest/getInstance "MD5")
        raw (.digest algorithm (.getBytes s))]
    (format "%032x" (BigInteger. 1 raw))))

(defn- marvel-url
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

(defn- with-server-cache [db url args otherwise]
  (let [hash-path [:server-cache (hash url) (hash args)]
        found (get-in @db hash-path)]
    (when (some? found)
      (println "returning" url args "from cache"))
    (or found
        (otherwise))))

(defn marvel-req
  ([db path] (marvel-req db path nil))
  ([db path query]
   (println "marvel req: " path query)
   (with-server-cache
     db path query
     (fn []
       (let [tag-cache (:tag-cache @db)
             last-tag (get-in tag-cache [path :tag])
             headers (if (some? last-tag)
                       {"If-None-Match" last-tag})
             resp (try
                    (http/get
                      (marvel-url path)
                      {:headers      headers
                       :query-params query})
                    (catch Exception e
                      (println "bad request" e)))
             etag (get-in resp [:headers "ETag"])
             body (-> resp
                      (get :body)
                      (json/parse-string true))]
         (case (:status resp)
           304
           (do
             (let [cached-body (get-in tag-cache [path :body])]
               (let [hash-path [(hash path) (hash query)]]
                 (prv/handle! db [:server-cache
                                  {:path hash-path :body cached-body}])
                 cached-body)))
           ; implicit: got a response with real data
           (do
             (let [hash-path [(hash path) (hash query)]]
               (prv/handle! db [:server-cache
                                {:path hash-path :body body}]))
             (prv/handle! db [:tag-cache
                              {:path path :result {:tag  etag
                                                   :body body}}])
             body)))))))

(defn get-feed
  [db _ _ _]
  (let [state @db
        already-read (set (:read state))
        characters (:subscribed-characters state)]
    ; TODO can i just use my own generated GQL resolvers? why not? is that weird?
    (loop [got [] offset 0]
      (let [resp (marvel-req db
                             "v1/public/comics"
                             {:characters      (vec characters)
                              :offset          offset
                              :orderBy         "onsaleDate"
                              :hasDigitalIssue true})
            total (concat got
                          (-> resp
                              (get-in [:data :results])
                              (#(filter
                                  (fn [comic]
                                    (not (contains? already-read (:digitalId comic))))
                                  %))))]
        (println "hrm" (keys resp) resp)
        (if (> (count total) 0)
          {:results total :offset offset}
          (recur total (+ offset 20)))))))


(defrecord MarvelProvider [marvel db-provider]
  com.stuartsierra.component/Lifecycle
  (start [this]
    (assoc this :marvel
                (partial marvel-req (:db db-provider))
                :get-feed
                (partial get-feed (:db db-provider))))
  (stop [this]
    this))

(defn new-marvel-provider
  []
  {:marvel-provider (com.stuartsierra.component/using (map->MarvelProvider {})
                                                      [:db-provider])})
