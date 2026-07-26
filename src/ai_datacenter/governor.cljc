(ns ai-datacenter.governor
  (:require [ai-datacenter.evidence :as evidence]
            [ai-datacenter.schema :as schema]
            [ai-datacenter.procurement :as procurement]
            [ai-datacenter.international :as international]))

(def allowed-operations
  #{:engagement/create :engagement/transition :vendor/register
    :rfq/create :rfq/send :quote/register :quote/select
    :contract/register :contract/sign :purchase-order/issue
    :shipment/register :asset/register :asset/accept
    :compute/register-reading :invoice/create :invoice/issue
    :payment/register :settlement/approve :settlement/pay
    :integration/enqueue})

(def financial-operations
  #{:quote/select :contract/sign :purchase-order/issue
    :invoice/issue :settlement/approve :settlement/pay})

(def external-operations
  #{:rfq/send :contract/sign :purchase-order/issue
    :invoice/issue :settlement/pay :integration/enqueue})

(def required-evidence-by-op
  {:contract/sign #{:legal-review}
   :purchase-order/issue #{:budget-approval :vendor-due-diligence
                           :export-import-review}
   :asset/accept #{:electrical-compliance :facility-acceptance
                   :commissioning-result}
   :invoice/issue #{:meter-reconciliation}
   :settlement/pay #{:invoice-reconciliation :payment-authorisation}})

(def approval-roles
  {:quote/select #{:procurement-manager :director}
   :contract/sign #{:authorised-signatory}
   :purchase-order/issue #{:procurement-manager :director}
   :invoice/issue #{:billing-manager :director}
   :settlement/approve #{:finance-manager :director}
   :settlement/pay #{:payment-approver :director}
   :rfq/send #{:procurement-manager}
   :integration/enqueue #{:operator :procurement-manager :billing-manager}})

(defn check
  [{:keys [operation entity-type entity subject-id proposal-digest approval
           evidence now vendor market-entry route]}]
  (let [shape (when entity-type (schema/validate-entity entity-type entity))
        required-evidence (get required-evidence-by-op operation #{})
        missing-evidence (evidence/missing-types evidence required-evidence subject-id now)
        roles (get approval-roles operation)
        approval-ok? (or (nil? roles)
                         (evidence/valid-approval? approval proposal-digest roles))
        vendor-result (when (= :rfq/send operation)
                        (procurement/govern-vendor-action :send-rfq vendor))
        market-result (when market-entry
                        (international/assess-market-entry market-entry))
        route-result (when route (international/assess-route route))]
    (cond
      (not (contains? allowed-operations operation))
      {:disposition :hold :reason :operation-not-allowed}
      (and shape (not (:valid? shape)))
      {:disposition :hold :reason :invalid-entity :missing (:missing shape)}
      (seq missing-evidence)
      {:disposition :hold :reason :missing-evidence :missing missing-evidence}
      (= :hold (:disposition vendor-result)) vendor-result
      (= :hold (:disposition market-result)) market-result
      (= :hold (:disposition route-result)) route-result
      (not approval-ok?)
      {:disposition :request-approval :reason :approval-required
       :roles roles :proposal-digest proposal-digest}
      :else
      {:disposition :commit :risk (cond
                                    (contains? financial-operations operation) :financial
                                    (contains? external-operations operation) :external-send
                                    :else :read-only)})))
