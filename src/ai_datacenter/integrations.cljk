(ns ai-datacenter.integrations)

(defn crm-lead-effect [{:keys [effect-id org repo lead]}]
  {:itonami.effect/id effect-id
   :itonami.effect/kind :crm/lead-ingest
   :itonami.effect/risk :read-only
   :itonami.effect/tenant {:org org :repo repo}
   :itonami.effect/payload-edn (pr-str {:lead lead})})

(defn mail-draft-effect
  [{:keys [effect-id org repo to subject text correlation-id]}]
  {:itonami.effect/id effect-id
   :itonami.effect/kind :mail/send
   :itonami.effect/risk :external-send
   :itonami.effect/tenant {:org org :repo repo}
   :itonami.effect/correlation-id correlation-id
   :itonami.effect/payload-edn
   (pr-str {:message {:mail/to [{:mail.address/email to}]
                      :mail/from {:mail.address/email "noreply@itonami.cloud"
                                  :mail.address/name "itonami.cloud"}
                      :mail/subject subject
                      :mail/parts [{:mail.part/type "text/plain"
                                    :mail.part/content text}]}})})

(defn billing-effect
  [{:keys [effect-id org repo invoice]}]
  {:itonami.effect/id effect-id
   :itonami.effect/kind :ai-datacenter/invoice.issue
   :itonami.effect/risk :financial
   :itonami.effect/tenant {:org org :repo repo}
   :itonami.effect/payload-edn (pr-str {:invoice invoice})})

(defn document-review-effect
  [{:keys [effect-id org repo contract-id document-digest reviewers]}]
  {:itonami.effect/id effect-id
   :itonami.effect/kind :ai-datacenter/contract.review
   :itonami.effect/risk :financial
   :itonami.effect/tenant {:org org :repo repo}
   :itonami.effect/payload-edn
   (pr-str {:contract-id contract-id :document-digest document-digest
            :reviewers reviewers})})

(defn purchase-order-effect
  [{:keys [effect-id org repo purchase-order]}]
  {:itonami.effect/id effect-id
   :itonami.effect/kind :ai-datacenter/purchase-order.issue
   :itonami.effect/risk :financial
   :itonami.effect/tenant {:org org :repo repo}
   :itonami.effect/payload-edn (pr-str {:purchase-order purchase-order})})

(defn effect-kind-valid? [effect]
  (and (string? (:itonami.effect/id effect))
       (keyword? (:itonami.effect/kind effect))
       (contains? #{:read-only :external-send :financial :destructive}
                  (:itonami.effect/risk effect))
       (seq (get-in effect [:itonami.effect/tenant :org]))
       (seq (get-in effect [:itonami.effect/tenant :repo]))))
