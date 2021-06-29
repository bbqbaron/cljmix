(ns ^:figwheel-hooks cljmix.game
  (:require [reagent.core :as r]
            [cljs.core.async :as a]
            [malli.core :as m]))

(defn ok? [spec val]
  (or
    (m/validate spec val)
    (do
      (println (m/explain spec val))
      false)))

;; 1. monster types
;; 2. rate of production
;; 3. rate of consumption by contract
;; 4. contract payments
;; 5. theft & secret plots
;; 6. rumors of artifacts to be stolen
;; 7. worlds to be conquered or undermined
;; 8. worshippers
;; 9. monster templates & abilities
;; 10. word of mouth

(def kill-chan (a/chan))

(def contract-spec
  [:map
   [::id string?]
   [::objective
    [:map]]
   [::reward
    [:sequential
     [:map]]]
   [::conditions
    [:sequential
     [:map]]]])

(def state-spec
  [:map
   [::resources
    [:map-of keyword? number?]]
   [::beasts
    [:map-of
     string?
     [:map]]]
   [::contracts
    [:sequential contract-spec]]
   [::offers
    [:sequential [:map]]]
   [::parts
    [:map-of
     number? number?]]
   [::templates
    [:set [:map]]]])

(def init-state
  {::resources {}
   ::beasts {}
   ::contracts []
   ::offers #{}
   ::parts {}
   ::templates
   #{{::name "Shade"
      ::parts
      {}}}})

(defn- mk-contract []
  {:post [(ok? contract-spec %)]}
  {::id (.toString (random-uuid))
   ::objective
   (condp > (rand-int 100)
     50 {::kind ::time
         ::duration (rand-int 1000)}
     {::kind ::kills
      ::target (rand-int 50)})
   ::reward
   (condp > (rand-int 100)
     [{::kind ::resource
       ::resource ::vanity
       ::amount (rand-int 100)}])
   ::conditions []})

(defonce state (r/atom init-state))

(defn fill-offers [s]
  (update s
          ::offers
          (fn [os]
            (take 5
                  (concat os
                          (repeatedly mk-contract))))))

(defn- update-state [s]
  (-> s
      fill-offers
      (update-in [::resources ::vanity]
                 #(inc (or % 0)))))

(defn- tick []
  (swap!
    state
    update-state))

(defonce mounted
         (do
           (println "mount")
           (a/go-loop []
                      (case
                        (a/alt!
                          kill-chan :kill
                          (a/timeout 1000) :tick)
                        :kill nil
                        :tick
                        (do (tick)
                            (recur))))))

(defn ^:after-load redef []
  (println "redef")
  (when-not (m/validate state-spec @state)
    (reset! state init-state)))

(defn check [spec val]
  (or
    (m/validate spec val)
    (println (m/explain spec val)))
  val)

(defn clj->json [v]
  (.stringify js/JSON (clj->js v)))

(defn- offer-view [v]
  [:div
   (clj->json v)
   [:button.pure-button
    {:on-click
     #(swap! state
             update ::offers
             (partial remove (comp #{(::id v)} ::id)))}]])

(defn view []
  (let [s (check state-spec @state)]
    [:div (clj->json s)
     (for [v (::offers s)]
       ^{:key (::id v)}
       [offer-view v])]))