(ns cljmix.schema-gen
  (:require [clojure.java.io :as io]
            [cheshire.core :as json]))

(defn get-json []
  (-> (io/resource "marvel-schema.json")
      slurp
      (json/parse-string true)))

(defn print-schema [schema]
  (spit "./gen-schema.edn" schema))

(defn prop-type-to-field-type [prop]
  (case (:type prop)
    "string" "String"
    "int" "Int"
    "Array" (str
              "(list "
              (prop-type-to-field-type
                {:type (get-in prop [:items :$ref])})
              ")")
    (:type prop)))

(defn arg-type-to-field-type [arg-type]
  (case arg-type
    "string" "String"
    "int" "Int"
    arg-type))

(defn props-to-fields [props]
  (into {}
        (map
          (fn [[k v]]
            [k
             {:type (prop-type-to-field-type v)}])
          props)))

(defn xform-model [model]
  [(keyword (:id model))
   {:fields
    (props-to-fields
      (:properties model))}])

(defn type-map [apis]
  (reduce
    (fn [acc {:keys [path operations]}]
      (let [path-segs (clojure.string/split path #"\/")
            [op] operations]
        (assoc-in acc
                  (->> path-segs
                       (drop 1)
                       (map keyword)
                       vec
                       (#(conj % :op)))
                  op)))
    {}
    apis))

(defn parse-arg
  [{arg-name :name :keys [dataType required allowMultiple]}]
  (let [arg-type (arg-type-to-field-type dataType)
        cardinality (if allowMultiple
                      (fn [x] `(~'list ~x))
                      identity)
        requirement (if required
                      (fn [x] `(~'non-null ~x))
                      identity)]
    [(keyword arg-name)
     {:type (cardinality (requirement arg-type))}]))

(defn parse-args [{:keys [parameters]}]
  (into {}
        (map parse-arg parameters)))

(defn field-from-op [node]
  (let [{:keys [nickname responseClass]} node]
    [(keyword nickname)
     {:type (keyword responseClass)
      :args (parse-args node)}]))

(defn add-node-fields [schema parent-type node]
  (let [[field-name field-val] (field-from-op (:op node))
        return-type (keyword (get-in node [:op :responseClass]))
        subfields (vals (dissoc node :op))]
    (as-> schema parent
          (assoc-in parent [:objects parent-type :fields field-name] field-val)
          (reduce
            (fn [acc subnode]
              (add-node-fields
                acc
                return-type
                subnode))
            parent
            subfields))))

(defn add-fields [schema]
  (let [base-ops (vals (get-in schema [:apis :v1 :public]))]
    (reduce
      (fn [acc node]
        (add-node-fields
          acc
          :Query
          node))
      schema
      base-ops)))

(defn xform-schema [{:keys [models] :as schema}]
  (let [objects (into {} (map xform-model (vals models)))]
    (-> {:objects objects
         :apis    (type-map (:apis schema))}
        add-fields
        ; handle special case of :queries
        ((fn [x]
           (-> x
               (assoc :queries (get-in x [:objects :Query]))
               (update :objects dissoc :Query))))
        (dissoc :apis))))

(let [s (xform-schema (get-json))]
  (println (cheshire.core/generate-string s) "\n")
  (print-schema s))
