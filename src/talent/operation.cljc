(ns talent.operation
  "OperationActor — one HR operation = one supervised actor run, expressed
  as a langgraph-clj StateGraph. The HR-LLM is sealed into a single node
  (:advise); its proposal is ALWAYS routed through the PolicyGovernor
  (:govern) before anything commits to the SSoT.

  One graph run = one HR operation (intake → advise → govern → decide →
  commit | hold | approval). No unbounded inner loop — each operation is
  auditable and checkpointed.

  Human-in-the-loop = real approval workflow:
  `interrupt-before #{:request-approval}` pauses the actor and hands the
  decision to the ApprovalActor (an HRBP / manager), exactly like
  robotaxi's teleop handoff. The approver resumes with
  `{:approval {:status :approved}}` (or :rejected)."
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [talent.hrllm :as hrllm]
            [talent.policy :as policy]
            [talent.store :as store]))

(defn- commit-fact [request context proposal]
  {:t          :committed
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :commit
   :basis      (:cites proposal)
   :summary    (:summary proposal)})

(defn build
  "Compiles an OperationActor graph bound to a `db` (the SSoT atom).
  opts: {:checkpointer cp}  (default: in-mem checkpointer)."
  [db & [{:keys [checkpointer]
          :or   {checkpointer (cp/mem-checkpointer)}}]]
  (-> (g/state-graph
       {:channels
        {:request     {:default nil}
         :context     {:default nil}   ; injected RBAC/purpose/consent — LLM has none
         :proposal    {:default nil}
         :verdict     {:default nil}
         :disposition {:default nil}   ; :commit | :hold | :escalate
         :record      {:default nil}   ; what a commit writes to the SSoT
         :approval    {:default nil}   ; human approver resolution
         :audit       {:reducer into :default []}}})

      ;; 1. Intake — request + injected context arrive via input; passthrough.
      (g/add-node :intake (fn [s] s))

      ;; 2. HR-LLM inference (the contained intelligence node) — proposal only.
      (g/add-node :advise
        (fn [{:keys [request]}]
          (let [p (hrllm/infer db request)]
            {:proposal p
             :audit    [(hrllm/trace request p)]})))

      ;; 3. PolicyGovernor — independent censor (separate system than the LLM).
      (g/add-node :govern
        (fn [{:keys [request context proposal]}]
          {:verdict (policy/check request context proposal db store/employee)}))

      ;; 4. Decide: auto-commit (clean), escalate to human (soft), or HOLD
      ;;    (hard violation — no human can override).
      (g/add-node :decide
        (fn [{:keys [request context proposal verdict]}]
          (cond
            (:hard? verdict)
            {:disposition :hold
             :audit [(policy/hold-fact request context verdict)]}

            (:escalate? verdict)
            {:disposition :escalate
             :audit [{:t :approval-requested
                      :op (:op request) :subject (:subject request)
                      :reason (cond (:high-stakes? verdict) :high-stakes
                                    :else :low-confidence)
                      :confidence (:confidence verdict)}]}

            :else
            {:disposition :commit
             :record {:effect (:effect proposal)
                      :value  (:value proposal)
                      :path   [(:subject request)]
                      :payload {:summary (:summary proposal)
                                :by (:actor-id context)}}})))

      ;; 5a. Approval handoff — paused by interrupt-before; ApprovalActor
      ;;     (human) resumes with :approval. Then route commit/hold.
      (g/add-node :request-approval
        (fn [{:keys [request context proposal approval verdict]}]
          (if (= :approved (:status approval))
            {:disposition :commit
             :record {:effect (:effect proposal)
                      :value  (:value proposal)
                      :path   [(:subject request)]
                      :payload {:summary (:summary proposal)
                                :approved-by (:by approval)}}
             :audit [{:t :approval-granted :op (:op request)
                      :subject (:subject request) :by (:by approval)}]}
            {:disposition :hold
             :audit [(merge (policy/hold-fact request context
                                              (assoc verdict :violations
                                                     [{:rule :approver-rejected}]))
                            {:t :approval-rejected})]})))

      ;; 5b. Commit — the ONLY node that writes the SSoT + audit ledger.
      (g/add-node :commit
        (fn [{:keys [request context proposal record]}]
          (store/commit-record! db record)
          (let [f (commit-fact request context proposal)]
            (store/append-ledger! db f)
            {:audit [f]})))

      ;; 5c. Hold — write the rejection to the ledger; no SSoT mutation.
      (g/add-node :hold
        (fn [{:keys [audit]}]
          ;; the hold-fact was produced upstream (:decide / :request-approval)
          ;; and is already on :audit; mirror it into the immutable ledger.
          (when-let [hf (last (filter #(#{:policy-hold :approval-rejected} (:t %)) audit))]
            (store/append-ledger! db (assoc hf :disposition :hold)))
          {}))

      (g/set-entry-point :intake)
      (g/add-edge :intake :advise)
      (g/add-edge :advise :govern)
      (g/add-edge :govern :decide)

      ;; Route on disposition.
      (g/add-conditional-edges :decide
        (fn [{:keys [disposition]}]
          (case disposition
            :commit   :commit
            :escalate :request-approval
            :hold)))

      ;; After human approval, route commit/hold.
      (g/add-conditional-edges :request-approval
        (fn [{:keys [disposition]}]
          (if (= :commit disposition) :commit :hold)))

      (g/set-finish-point :commit)
      (g/set-finish-point :hold)

      (g/compile-graph
       {:checkpointer     checkpointer
        :interrupt-before #{:request-approval}})))
