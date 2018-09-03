(ns cljmix.schema-gen
  (:require [clojure.java.io :as io]
            [cheshire.core :as json]))

(defn get-json []
  (-> (io/resource "marvel-schema.json")
      slurp
      (json/parse-string true)))

(defn print-schema [schema]
  (spit "./resources/gen-schema.edn" schema))

(defn prop-type-to-field-type [prop]
  (case (:type prop)
    "string" 'String
    "int" 'Int
    "float" 'Float
    "double" 'Float
    "boolean" 'Boolean
    "Date" 'String
    "Array" `(~(symbol "list")
               ~(prop-type-to-field-type
                  {:type (get-in prop [:items :$ref])}))
    (keyword (:type prop))))

(defn arg-type-to-field-type [arg-type]
  (case arg-type
    "string" 'String
    "int" 'Int
    "float" 'Float
    "double" 'Float
    "boolean" 'Boolean
    "Date" 'String
    (keyword arg-type)))

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

(defn api-map
  "TODO this mashes the op itself and subops into one object.
   you have to implicitly exclude :op and :path to get subops.
   It's also order-dependent in that a subop before a parent
   will be overwritten."
  [apis]
  (reduce
    (fn [acc {:keys [path operations]}]
      (let [path-segs (clojure.string/split path #"\/")
            [first-op] operations
            base-path (->> path-segs
                           (drop 1)
                           (map keyword)
                           vec)
            api-path (interpose :subops base-path)]
        (assoc-in acc api-path {:op first-op :path path})))
    {}
    apis))

(defn parse-arg
  [{arg-name :name :keys [dataType required allowMultiple]}]
  (let [arg-type (arg-type-to-field-type dataType)
        cardinality (if allowMultiple
                      (fn [x] `(~(symbol "list") ~(symbol x)))
                      identity)
        requirement (if required
                      (fn [x] `(~(symbol "non-null") ~(symbol x)))
                      identity)]
    [(keyword arg-name)
     {:type (cardinality (requirement arg-type))}]))

(defn parse-args [{:keys [parameters]}]
  (->> parameters
       (filter (fn [arg] (not= "path" (:paramType arg))))
       (map parse-arg)
       (into {})))

(defn field-from-op [node]
  (let [{:keys [nickname responseClass]} node]
    [(keyword nickname)
     {:type (keyword responseClass)
      :args (parse-args node)}]))

(defn unpath [path]
  (->> path
       (#(clojure.string/split % #"/"))
       (drop 3)
       (partition-by (fn [s] (clojure.string/starts-with? s "{")))
       ((fn [l]
          (flatten (list (first l)
                         (if (> (count l) 2)
                           (nth l 2)
                           nil)))))))


(defn field-resolver-args
  "Derive the name for the resolver of a given field."
  [path]
  (let [[root after-path] (unpath path)]
    [:edge-resolver
     root
     after-path]))

(def parent-to-relevant-type
  "Map type names by their ending strings
  to getters for their typedefs
  that contain the reference to the next lower level of
  actual _data_ they return,
  ie not limit/count/other meta fields.

  e.g. ComicDataWrapper -> ComicDataContainer,
  ComicDataContainer -> Comic."
  {"Wrapper"   #(get-in % [:data :type])
   "Container" #(second (get-in % [:results :type]))})


(defn type-for-type-wrapper
  "Some ops return wrappers around wrappers,
  ultimately resolving to a list of a second type.
  Derive what type to target when attaching sub-pathed
  operations. Eg, operations like /v1/public/comics/{comicId}/foo,
  pathed under /v1/public/comics,
  which returns ComicsDataWrapper, ultimately need to be attached to Comic."
  [schema parent-type-name]
  ; for now, cheat. there aren't many cases
  (let [get-child-path (->> parent-to-relevant-type
                            (filter
                              (fn [[ending]]
                                (clojure.string/ends-with? parent-type-name ending)))
                            vals
                            first)]
    (if (nil? get-child-path)
      [parent-type-name]
      (let [parent-type (get-in schema [:objects parent-type-name])]
        (type-for-type-wrapper schema (get-child-path (get parent-type :fields)))))))

(defn add-node-fields
  " TODO: implicit path vars like {characterId} are not acknowledged as params
  in :op. Need to infer from path construction. Look for {} :/ ?"
  [schema parent-type node]
  (let [[field-name field-val] (field-from-op (:op node))
        return-type (keyword (get-in node [:op :responseClass]))
        subfields (vals (:subops node))]
    (as-> schema parent
          (let [type-path (type-for-type-wrapper schema parent-type)
                subpath (flatten [:objects type-path :fields field-name])]
            (assoc-in
              parent
              subpath
              (assoc
                field-val
                :resolve
                (field-resolver-args (:path node)))))
          (reduce
            (fn [acc subnode]
              (add-node-fields
                acc
                return-type
                subnode))
            parent
            subfields))))

(defn resolver-name
  "Derive the name for the resolver of a given query."
  [field-name]
  (keyword "queries" (name field-name)))

(defn add-query-resolvers
  [schema]
  (update
    schema
    :queries
    (fn [queries]
      (into {}
            (map
              (fn [[k v]]
                [k (assoc v :resolve (resolver-name k))])
              queries)))))

(defn api-node-for-path
  [schema elements]
  (->> elements
       (interpose :subops)
       (cons :apis)
       (get-in schema)))

(defn add-fields
  "add fields to Query and all other types."
  [schema]
  (let [base-ops (-> (api-node-for-path schema [:v1 :public])
                     :subops
                     vals)
        char-comics (dissoc
                      (api-node-for-path schema
                                         [:v1 :public :characters (keyword "{characterId}")])
                      :subops)]
    (reduce
      (fn [acc node]
        (add-node-fields
          acc
          :Query
          node))
      schema
      (cons char-comics base-ops))))

(defn xform-raw
  "Convert marvel json to a gql-ish representation
  of pure objects and top-level operations,
  without resolvers or edges. Ie, not connected to each other."
  [{:keys [models] :as schema}]
  (let [objects (into {} (map xform-model (vals models)))]
    {:objects objects
     :apis    (api-map (:apis schema))}))

(defn xform-schema [schema]
  (-> (xform-raw schema)
      add-fields
      (dissoc :apis)
      ; handle special case of :queries
      ((fn [x]
         (-> x
             (assoc :queries
                    (get-in x [:objects :Query :fields]))
             (update :objects dissoc :Query))))
      add-query-resolvers))

(defn create-schema []
  (let [s (xform-schema (get-json))]
    (println (cheshire.core/generate-string s) "\n")
    (print-schema s)))
