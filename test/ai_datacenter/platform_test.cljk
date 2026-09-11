(ns ai-datacenter.platform-test
  (:require [clojure.test :refer [deftest is]]
            [ai-datacenter.actor :as actor]
            [ai-datacenter.commerce :as commerce]
            [ai-datacenter.compute :as compute]
            [ai-datacenter.evidence :as evidence]
            [ai-datacenter.governor :as governor]
            [ai-datacenter.integrations :as integrations]
            [ai-datacenter.schema :as schema]
            [ai-datacenter.store :as store]))

(def digest (str "sha256:" (apply str (repeat 64 "a"))))

(def legal-review
  {:evidence/id "ev-1" :evidence/type :legal-review
   :evidence/subject "contract-1" :evidence/issuer "counsel-1"
   :evidence/issued-at "2026-07-20" :evidence/digest digest})

(deftest evidence-is-subject-bound-and-expiry-aware
  (is (evidence/covers? legal-review :legal-review "contract-1" "2026-07-21"))
  (is (not (evidence/covers? legal-review :legal-review "contract-2" "2026-07-21")))
  (is (not (evidence/valid? (assoc legal-review :evidence/expires-at "2026-07-01")
                            "2026-07-21"))))

(deftest approval-is-bound-to-proposal-and-role
  (let [approval {:approval/id "ap-1" :approval/proposal-digest digest
                  :approval/status :approved :approval/by "director-1"
                  :approval/role :authorised-signatory :approval/at "2026-07-21"}]
    (is (evidence/valid-approval? approval digest #{:authorised-signatory}))
    (is (not (evidence/valid-approval? approval digest #{:operator})))))

(deftest contract-sign-requires-both-evidence-and-bound-approval
  (let [contract {:contract/id "contract-1" :engagement/id "eng-1"
                  :contract/type :master-services :contract/parties ["a" "b"]
                  :contract/status :draft}
        base {:operation :contract/sign :entity-type :contract :entity contract
              :subject-id "contract-1" :proposal-digest digest
              :now "2026-07-21" :evidence [legal-review]}
        pending (governor/check base)
        approved (governor/check
                  (assoc base :approval
                         {:approval/id "ap-1" :approval/proposal-digest digest
                          :approval/status :approved :approval/by "signatory-1"
                          :approval/role :authorised-signatory
                          :approval/at "2026-07-21"}))]
    (is (= :request-approval (:disposition pending)))
    (is (= :commit (:disposition approved)))))

(deftest meter-to-invoice-to-settlement
  (let [reading {:reading/id "r-1" :asset/id "gpu-1" :tenant/id "tenant-1"
                 :reading/from "2026-07-01T00:00:00Z"
                 :reading/to "2026-07-01T01:00:00Z"
                 :reading/gpu-milliseconds 3600000 :reading/source-id "scheduler-1"
                 :reading/source-digest digest}
        lines (commerce/usage-lines [reading]
                                    {:currency "USD" :rate-minor-units 200
                                     :rate-per-gpu-milliseconds 3600000})
        invoice (commerce/create-invoice {:invoice-id "inv-1"
                                          :tenant-id "tenant-1" :lines lines})
        settlement (commerce/settlement
                    {:settlement-id "set-1" :invoice invoice
                     :collected-minor-units 200 :electricity-minor-units 40
                     :facility-minor-units 20 :maintenance-minor-units 10
                     :sales-fee-minor-units 10 :owner-share-ppm 800000})]
    (is (compute/valid-reading? reading))
    (is (= 200 (:invoice/subtotal-minor-units invoice)))
    (is (= 120 (:settlement/distributable-minor-units settlement)))
    (is (= 96 (:settlement/owner-amount-minor-units settlement)))))

(deftest effects-match-cloud-itonami-contract
  (doseq [effect [(integrations/crm-lead-effect
                   {:effect-id "e1" :org "buyer" :repo "dc" :lead {:id "l1"}})
                  (integrations/mail-draft-effect
                   {:effect-id "e2" :org "buyer" :repo "dc"
                    :to "sales@example.com" :subject "RFQ" :text "body"})
                  (integrations/billing-effect
                   {:effect-id "e3" :org "buyer" :repo "dc"
                    :invoice {:invoice/id "i1"}})
                  (integrations/purchase-order-effect
                   {:effect-id "e4" :org "buyer" :repo "dc"
                    :purchase-order {:purchase-order/id "po-1"}})]]
    (is (integrations/effect-kind-valid? effect))))

(deftest actor-commits-record-and-audit
  (doseq [st [(store/mem-store) (store/datomic-store)]]
    (let [graph (actor/build st)
          engagement {:engagement/id "eng-1" :customer/id "customer-1"
                      :jurisdiction :JPN :stage :lead}
          proposal {:operation :engagement/create :entity-type :engagement
                    :entity-id "eng-1" :entity engagement
                    :subject-id "eng-1" :proposal/digest digest}
          result (actor/run! graph proposal {:now "2026-07-21"}
                             (str "thread-" (type st)))]
      (is (= :done (:status result)))
      (is (= engagement (store/entity st :engagement "eng-1")))
      (is (= [engagement] (store/entities st :engagement)))
      (is (= 1 (count (store/events st)))))))

(deftest store-backends-preserve-effect-outbox
  (doseq [st [(store/mem-store) (store/datomic-store)]]
    (store/enqueue-effect! st {:itonami.effect/id "e1"})
    (is (= [{:itonami.effect/id "e1"}] (store/effects st)))))

(deftest actor-interrupts-and-resumes-with-bound-approval
  (let [st (store/mem-store)
        graph (actor/build st)
        contract {:contract/id "contract-1" :engagement/id "eng-1"
                  :contract/type :master-services :contract/parties ["a" "b"]
                  :contract/status :signed}
        proposal {:operation :contract/sign :entity-type :contract
                  :entity-id "contract-1" :entity contract
                  :subject-id "contract-1" :proposal/digest digest
                  :evidence [legal-review]}
        interrupted (actor/run! graph proposal {:now "2026-07-21"} "thread-approval")
        approval {:approval/id "ap-1" :approval/proposal-digest digest
                  :approval/status :approved :approval/by "signatory-1"
                  :approval/role :authorised-signatory :approval/at "2026-07-21"}
        done (actor/resume! graph approval "thread-approval")]
    (is (= :interrupted (:status interrupted)))
    (is (= :done (:status done)))
    (is (= contract (store/entity st :contract "contract-1")))))

(deftest schema-rejects-incomplete-entities
  (is (= #{:customer/id :jurisdiction :stage}
         (set (:missing (schema/validate-entity
                        :engagement {:engagement/id "e"}))))))
