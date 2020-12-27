(ns cljmix.scrape
  (:require [cljmix.marvel :refer [marvel-url
                                   date-format]]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [cljmix.db :as db]
            [clojure.java.io :as io]
            [com.stuartsierra.component :as component]))

(defn gitem [file-offset page page-limit url filename]
  (loop [current-page page
         ds []]
    (println "ok" current-page)
    (let [r (try
              (http/get
                (marvel-url
                  url
                  #_"v1/public/comics")
                        {:query-params
                         {:limit 100
                          :offset (* 100 current-page)
                  #_#_#_#_        :modifiedSince "2020-09-02T00:00:00-0500"
                          :orderBy "onsaleDate,title,issueNumber"}})
              (catch Exception e
                (println e)
                (println "skipped " current-page)))
          body
          (case (:status r)
            200
            (-> r :body (json/parse-string true))
            (do
              (println "fuuuuuck " (select-keys r [:status :body]))
              nil))]
      (if body
        (do
          (spit (str filename (+ file-offset current-page) ".edn") body)
          (if (or
                (<= (-> body :data :total)
                    (dec (* 100 (inc current-page))))
                (and page-limit (>= current-page page-limit)))
            (do (println "exit at " current-page " " (:total (:data body)))
                ds)
            (do
              (println "loop")
              (recur (inc current-page)
                     ds
                     #_(conj ds body)))))
        (case (:status r)
          200
          (recur (inc current-page) ds)
          nil)))))

(defrecord Scraper [marvel-provider]
  component/Lifecycle
  (start [this]
    (assoc this :marvel marvel-provider))
  (stop [this]
    this))

(defn new-scraper []
  {:scraper (component/using
              (map->Scraper {})
              [:marvel-provider]
              )})

#_
(let [data (mapcat
             (comp :results :data) (map
                                     (comp read-string slurp)
                                     (filter
                                       (comp (partial re-find #"comics\d+.edn") #(.getName %))
                                       (file-seq (io/file ".")))))
      creators
      (->> data
           (map (comp :collectionURI :creators))
           set)]
  (doseq [[i u] (map-indexed vector creators)]
    (gitem i 0 0 (re-find #"v1.+" u) "creators")))

#_(let [data (map #(read-string (slurp (str "comics" % ".edn")))
                  (range 484))
        characters (set (:subscribed-characters @@db/db))
        already-read (set (:read @@db/db))]
    (->> data
         (mapcat (comp :results :data))
         (filter
           (fn [{{:keys [items]} :characters}]
             (some
               (comp
                 characters
                 read-string
                 second
                 (partial re-find #"\/(\d+)$")
                 :resourceURI)
               items)))
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
                        (.parse date-format
                                "1997-01-01")))))
         (sort-by
           (fn [c]
             (when-let [onsale-date
                        (->> c :dates
                             (filter #(= (:type %) "onsaleDate"))
                             first
                             :date)]
               (.parse date-format onsale-date))))
         (take 50)))
