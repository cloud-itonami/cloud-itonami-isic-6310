(ns ai-datacenter.actor
  "One governed business operation per checkpointed StateGraph run."
  (:refer-clojure :exclude [run!])
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [ai-datacenter.governor :as governor]
            [ai-datacenter.store :as store]))

(defn- proposal->governor-input [proposal context approval]
  (merge proposal
         {:approval approval
          :now (:now context)
          :proposal-digest (:proposal/digest proposal)}))

(defn- commit! [st proposal]
  (if-let [effect (:effect proposal)]
    (store/enqueue-effect! st effect)
    (store/put-entity! st (:entity-type proposal) (:entity-id proposal)
                       (:entity proposal)))
  (store/append-event!
   st {:event/type :operation-committed
       :event/operation (:operation proposal)
       :event/proposal-digest (:proposal/digest proposal)
       :event/entity-type (:entity-type proposal)
       :event/entity-id (:entity-id proposal)})
  {:record (or (:effect proposal) (:entity proposal))})

(defn build
  [st & [{:keys [checkpointer] :or {checkpointer (cp/mem-checkpointer)}}]]
  (-> (g/state-graph
       {:channels {:proposal {:default nil}
                   :context {:default nil}
                   :approval {:default nil}
                   :verdict {:default nil}
                   :disposition {:default nil}
                   :record {:default nil}
                   :audit {:reducer into :default []}}})
      (g/add-node :intake (fn [s] s))
      (g/add-node :govern
                  (fn [{:keys [proposal context approval]}]
                    {:verdict (governor/check
                               (proposal->governor-input proposal context approval))}))
      (g/add-node :decide
                  (fn [{:keys [verdict]}]
                    {:disposition (:disposition verdict)
                     :audit [{:event/type :operation-decided :verdict verdict}]}))
      (g/add-node :request-approval
                  (fn [{:keys [proposal context approval]}]
                    (let [verdict (governor/check
                                   (proposal->governor-input proposal context approval))]
                      {:verdict verdict :disposition (:disposition verdict)
                       :audit [{:event/type :approval-evaluated
                                :approval/id (:approval/id approval)
                                :verdict verdict}]})))
      (g/add-node :commit (fn [{:keys [proposal]}] (commit! st proposal)))
      (g/add-node :hold
                  (fn [{:keys [proposal verdict]}]
                    (store/append-event!
                     st {:event/type :operation-held
                         :event/operation (:operation proposal)
                         :event/proposal-digest (:proposal/digest proposal)
                         :event/reason (:reason verdict)})
                    {}))
      (g/set-entry-point :intake)
      (g/add-edge :intake :govern)
      (g/add-edge :govern :decide)
      (g/add-conditional-edges
       :decide (fn [{:keys [disposition]}]
                 (case disposition
                   :commit :commit
                   :request-approval :request-approval
                   :hold)))
      (g/add-conditional-edges
       :request-approval (fn [{:keys [disposition]}]
                           (if (= :commit disposition) :commit :hold)))
      (g/set-finish-point :commit)
      (g/set-finish-point :hold)
      (g/compile-graph {:checkpointer checkpointer
                        :interrupt-before #{:request-approval}})))

(defn run! [graph proposal context thread-id]
  (g/run* graph {:proposal proposal :context context} {:thread-id thread-id}))

(defn resume! [graph approval thread-id]
  (g/run* graph {:approval approval} {:thread-id thread-id :resume? true}))
