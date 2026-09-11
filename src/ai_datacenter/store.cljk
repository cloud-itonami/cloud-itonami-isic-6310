(ns ai-datacenter.store
  (:require #?(:clj [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [langchain.db :as d]))

(defprotocol Store
  (entity [store entity-type id])
  (entities [store entity-type])
  (put-entity! [store entity-type id value])
  (append-event! [store event])
  (events [store])
  (enqueue-effect! [store effect])
  (effects [store]))

(defrecord MemStore [state]
  Store
  (entity [_ entity-type id] (get-in @state [:entities entity-type id]))
  (entities [_ entity-type] (-> @state :entities entity-type vals vec))
  (put-entity! [_ entity-type id value]
    (swap! state assoc-in [:entities entity-type id] value) value)
  (append-event! [_ event]
    (swap! state update :events conj event) event)
  (events [_] (:events @state))
  (enqueue-effect! [_ effect]
    (swap! state update :effects conj effect) effect)
  (effects [_] (:effects @state)))

(defn mem-store []
  (->MemStore (atom {:entities {} :events [] :effects []})))

(def ^:private datomic-schema
  {:record/key {:db/unique :db.unique/identity}
   :event/seq {:db/unique :db.unique/identity}
   :effect/seq {:db/unique :db.unique/identity}})

(defn- enc [x] (pr-str x))
(defn- dec* [x] (when x (edn/read-string x)))
(defn- record-key [entity-type id] (str (name entity-type) "|" id))

(defrecord DatomicStore [conn]
  Store
  (entity [_ entity-type id]
    (some-> (d/pull (d/db conn) [:record/value]
                    [:record/key (record-key entity-type id)])
            :record/value dec*))
  (entities [_ entity-type]
    (->> (d/q '[:find ?id ?value :in $ ?type
                :where [?e :record/type ?type]
                       [?e :record/id ?id]
                       [?e :record/value ?value]]
              (d/db conn) entity-type)
         (sort-by first) (mapv (comp dec* second))))
  (put-entity! [_ entity-type id value]
    (d/transact! conn [{:record/key (record-key entity-type id)
                        :record/type entity-type :record/id id
                        :record/value (enc value)}])
    value)
  (append-event! [s event]
    (d/transact! conn [{:event/seq (count (events s)) :event/value (enc event)}])
    event)
  (events [_]
    (->> (d/q '[:find ?seq ?value
                :where [?e :event/seq ?seq] [?e :event/value ?value]] (d/db conn))
         (sort-by first) (mapv (comp dec* second))))
  (enqueue-effect! [s effect]
    (d/transact! conn [{:effect/seq (count (effects s)) :effect/value (enc effect)}])
    effect)
  (effects [_]
    (->> (d/q '[:find ?seq ?value
                :where [?e :effect/seq ?seq] [?e :effect/value ?value]] (d/db conn))
         (sort-by first) (mapv (comp dec* second)))))

(defn datomic-store []
  (->DatomicStore (d/create-conn datomic-schema)))
