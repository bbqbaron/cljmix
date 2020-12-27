(ns cljmix.marvel
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.core.async :refer [<!! >! chan close! go alt!! timeout]]
            [cheshire.core :as json]
            [clj-http.client :as http]
            [prevayler :as prv]
            [cljmix.db :as db])
  (:import (java.security MessageDigest)
           (java.text SimpleDateFormat)
           (jetbrains.exodus.entitystore StoreTransactionalExecutable StoreTransactionalComputable)
           (java.util ArrayList)))

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

(defn- with-server-cache
  "Uncritically caches anything we got back, for totally offline work."
  ; TODO add a TTL
  [c db xodus url args otherwise]
  (let [hash-path (pr-str [(hash url) (hash args)])
        found (.computeInTransaction xodus
                                     (reify StoreTransactionalComputable
                                       (compute [_ txn]
                                         (when-let [e (first (vec
                                                               (.find txn "server-cache"
                                                                      "id" hash-path)))]
                                           (read-string
                                             (.getBlobString
                                               e
                                               "response"))))))]
    (if (some? found)
      (do
        (println "returning" url args "from cache")
        (go (>! c found)))
      (otherwise))
    nil))

(defn process-result [db xodus path query resp]
  (let [etag (get-in resp [:headers "ETag"])
        body (-> resp
                 (get :body)
                 (json/parse-string true))]
    (case (:status resp)
      304
      (do
        (let [cached-body
              (.computeInTransaction xodus
                                     (reify StoreTransactionalComputable
                                       (compute [_ txn]
                                         (:body
                                           (first
                                            (.find txn "tag-cache" "id" path))))))]
          (let [hash-path (pr-str [(hash path) (hash query)])]
            (.executeInTransaction xodus
                                   (reify StoreTransactionalExecutable
                                     (execute [_ txn]
                                       (let [e (or
                                                 (first (vec
                                                         (.find txn "server-cache"
                                                                "id" hash-path)))
                                                 (.newEntity txn "server-cache"))]
                                         (println "storing" hash-path)
                                         (.setProperty e "id" hash-path)
                                         (.setBlobString e "response" (pr-str cached-body))))))
            #_
            (prv/handle! db [:server-cache
                             {:path hash-path :body cached-body}])
            cached-body)))
      ; implicit: got a response with real data
      (do
        (let [hash-path [(hash path) (hash query)]]
          (.executeInTransaction xodus
                                 (reify StoreTransactionalExecutable
                                   (execute [_ txn]
                                     (let [e (or
                                               (first (vec
                                                        (.find txn "server-cache"
                                                               "id" (pr-str hash-path))))
                                               (.newEntity txn "server-cache"))]
                                       (println "storing 200" hash-path)
                                       (.setProperty e "id" (pr-str hash-path))
                                       (.setBlobString e "response" (pr-str body)))))))
        (.executeInTransaction xodus
                               (reify StoreTransactionalExecutable
                                 (execute [_ txn]
                                   (let [e (.newEntity txn "tag-cache")]
                                     (.setProperty e "id" path)
                                     (.setBlobString e "result" (pr-str {:tag etag
                                                                          :body body}))))))
        body))))

(defn marvel-req-async
  ([c db xodus path] (marvel-req-async c db xodus path nil))
  ([c db xodus path query]
   (println "marvel req: " path query)
   (with-server-cache
     c db xodus path query
     (fn []
       (println "miss" path query)
       (let [last-tag (.computeInTransaction xodus
                                             (reify StoreTransactionalComputable
                                               (compute [_ txn]
                                                 (:tag
                                                   (first
                                                     (.find txn "tag-cache" "id" path))))))
             headers (if (some? last-tag)
                       {"If-None-Match" last-tag})]
         (http/get
           (marvel-url path)
           {:async? true
            :headers headers
            :query-params query}
           (fn [result]
             (go (>! c (process-result db xodus path query result))))
           (fn [err]
             (go (>! c err))))
         nil)))))

(defn marvel-req
  ([db xodus path]
   (marvel-req db xodus path nil))
  ([db xodus path query]
   (let [out (chan)]
     (marvel-req-async out db xodus path query)
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

(defonce file-data
         (mapcat
           (comp :results :data)
           (map #(read-string (slurp (str "comics" % ".edn")))
                (range 484))))

(defonce creator->comics
         (read-string
           (slurp "creator->comics.edn")))

(defn get-feed
  ([db]
   (get-feed db nil nil nil))
  ([db _ {:keys [subscriptionId] :as args} _]
   {:results (let [subscriptions (cond-> (:subscribed @db)
                                         subscriptionId
                                         (select-keys [subscriptionId]))
                   characters (set (concat (when (= subscriptionId 0)
                                             (:subscribed-characters @db))
                                           (mapcat :character (vals subscriptions))))
                   series (set (mapcat :series (vals subscriptions)))
                   ;; TODO use these; need to fetch another index :/
                   creators (set (mapcat :creator (vals subscriptions)))
                   creator-comics
                   (set (mapcat creator->comics creators))
                   already-read (set (:read @db))
                   time (:time @db)]
               (->> file-data
                    (filter
                      (fn [{{:keys [items]} :characters
                            issue-series :series
                            id :id}]
                        (or
                          (creator-comics id)
                          ((comp
                             series
                             read-string
                             second
                             (partial re-find #"\/(\d+)$")
                             :resourceURI)
                           issue-series)
                          (some
                            (comp
                              characters
                              read-string
                              second
                              (partial re-find #"\/(\d+)$")
                              :resourceURI)
                            items))))
                    (remove (comp zero? :digitalId))
                    (remove
                      (comp already-read :digitalId))
                    (remove
                      (fn [c]
                        (when-let [onsale-date
                                   (->> c :dates
                                        (filter #(= (:type %) "onsaleDate"))
                                        first
                                        :date)]
                          (.before (.parse date-format onsale-date)
                                   (java.util.Date. ^Long time)))))
                    (sort-by
                      (fn [c]
                        (when-let [onsale-date
                                   (->> c :dates
                                        (filter #(= (:type %) "onsaleDate"))
                                        first
                                        :date)]
                          onsale-date
                          #_(.parse date-format onsale-date))))
                    (take 50)))}))

(defrecord MarvelProvider [marvel db-provider xodus-cache]
  com.stuartsierra.component/Lifecycle
  (start [this]
    (assoc this :marvel
                (partial marvel-req (:db db-provider)
                         (:xodus xodus-cache))
                :get-feed
                (partial get-feed (:db db-provider))))
  (stop [this]
    this))

(defn new-marvel-provider
  []
  {:marvel-provider (com.stuartsierra.component/using (map->MarvelProvider {})
                                                      [:db-provider :xodus-cache])})

