(ns cljmix.marvel
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.core.async :refer [<!! >! chan close! go alt!! timeout]]
            [cheshire.core :as json]
            [clj-http.client :as http]
            [prevayler :as prv]
            [clojure.core.async :as async]
            [cljmix.db :as db])
  (:import (java.security MessageDigest)
           (java.text SimpleDateFormat)
           (java.util Date)))

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

(defn- with-server-cache
  "Uncritically caches anything we got back, for totally offline work."
  ; TODO add a TTL
  [c db url args otherwise]
  (let [hash-path [:server-cache (hash url) (hash args)]
        found (get-in @db hash-path)]
    (if (some? found)
      (do
        (println "returning" url args "from cache")
        (go (>! c found)))
      (otherwise))
    nil))

(defn process-result [db path query resp]
  (let [tag-cache (:tag-cache @db)
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
        body))))

(defn marvel-req-async
  ([c db path] (marvel-req-async c db path nil))
  ([c db path query]
   (println "marvel req: " path query)
   (with-server-cache
     c db path query
     (fn []
       (let [tag-cache (:tag-cache @db)
             last-tag (get-in tag-cache [path :tag])
             headers (if (some? last-tag)
                       {"If-None-Match" last-tag})]
         (http/get
           (marvel-url path)
           {:async?       true
            :headers      headers
            :query-params query}
           (fn [result]
             (go (>! c (process-result db path query result))))
           (fn [err]
             (go (>! c err))))
         nil)))))

(defn marvel-req
  ([db path]
   (marvel-req db path nil))
  ([db path query]
   (let [out (chan)]
     (marvel-req-async out db path query)
     (let [result (alt!!
                    (timeout 20000) :timeout
                    out ([v] v))]
       (close! out)
       (when (= :timeout result)
         (throw (ex-info "Timeout" {})))
       (when (instance? Throwable result)
         (throw result))
       result))))

(def page-size 100)
(def feed-tries 10)

(defn- get-results [already-read resp]
  (-> resp
      (get-in [:data :results])
      (#(filter
          (fn [comic]
            (not (contains? already-read (:digitalId comic))))
          %))))

(defn uniq-by
  "Is this not a thing yet?"
  [f coll]
  (->> coll
       (map (fn [i] [(f i) i]))
       (into {})
       vals))

(def date-format (SimpleDateFormat. "yyyy-MM-dd"))

(defn get-feed
  ([db]
   (get-feed db nil nil nil))
  ([db _ _ _]
   (let [state @db
         already-read (set (:read state))
         characters (sort (set (:subscribed-characters state)))]
     (if (empty? characters)
       {:results []}
       (let [time-point (:time state)
             raw (chan)
             out (async/transduce
                   (comp
                     (map (partial get-results already-read))
                     (take (count characters)))
                   concat
                   []
                   raw)]
         (loop [page 0]
           (if (> page feed-tries)
             {:results [] :offset (* page page-size)}
             (do
               (doseq [character characters]
                 (marvel-req-async
                   raw db
                   "v1/public/comics"
                   (let [time-params
                         (when (some? time-point)
                           {:dateRange [(.format
                                          date-format
                                          time-point)
                                        (.format
                                          date-format
                                          #inst "2020-06-01")]})]
                     (merge time-params {:characters      [character]
                                         :orderBy         "onsaleDate,title,issueNumber"
                                         :hasDigitalIssue true
                                         :offset          (* page page-size)
                                         :limit           page-size}))))
               (let [results (async/<!! out)]
                 (close! raw)
                 (if (not (empty? results))
                   {:results
                    (->> results
                         (uniq-by :digitalId)
                         (sort-by
                           (fn [c]
                             (when-let [onsale-date
                                        (->> c :dates
                                             (filter #(= (:type %) "onsaleDate"))
                                             first
                                             :date)]
                               (.parse date-format onsale-date))))
                         (take 50))}
                   (recur (inc page))))))))))))

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

