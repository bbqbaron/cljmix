(ns cljmix.gql
  (:require [clojure.walk :as walk]))

(defn my-query [char-name]
  [:getCharacterCollection
   [:args :nameStartsWith char-name]
   [:data
    [:results
     [:name
      :thumbnail
      [:extension :path]
      :getComicsCharacterCollection
      ; TODO enum of orderBy allowedValues
      [:args :hasDigitalIssue true :orderBy "onsaleDate"]
      [:data
       [:results
        [:digitalId
         :description
         :title
         :series [:name :resourceURI]
         :thumbnail [:extension :path]
         :images [:extension :path]]]]]]]])

(defn to-query-string
  [q]
  (walk/postwalk
    (fn [x]
      (cond
        (keyword? x)
        (name x)
        (or (list? x) (vector? x))
        (if (= "args" (first x))
          (str " ( "
               (apply str
                      (interpose ", "
                                 (map
                                   (fn [[arg-name arg-value]]
                                     (str arg-name ": \"" arg-value "\""))
                                   (partition 2 (rest x)))))
               " ) ")
          (str " { "
               (apply str
                      (interpose " "
                        (map str x)))
               " } "))
        true
        x))
   q))

(prn (to-query-string (my-query "Cyclops")))
