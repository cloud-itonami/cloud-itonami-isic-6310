(ns ai-datacenter.supply-chain-test
  (:require [clojure.test :refer [deftest is testing]]
            [ai-datacenter.schema :as schema]
            [ai-datacenter.supply-chain :as supply-chain]))

(defn new-case []
  (:case (supply-chain/open-case
          {:case-id "pc-1" :engagement-id "eng-1" :market :JPN
           :buyer-id "buyer-1"})))

(deftest procurement-case-cannot-skip-or-advance-without-evidence
  (testing "stage skipping is held"
    (is (= :invalid-stage-transition
           (:reason (supply-chain/advance (new-case) :rfq-approved
                                          {:at "2026-07-20" :by "u-1"})))))
  (testing "missing artifacts are reported"
    (is (= [:requirements]
           (:missing (supply-chain/advance (new-case) :requirements-approved
                                           {:at "2026-07-20" :by "u-1"}))))))

(deftest artifacts-are-immutable-by-type
  (let [c1 (:case (supply-chain/attach-artifact
                   (new-case) :requirements {:digest "sha256:a"}))]
    (is (= :ok (:status (supply-chain/attach-artifact
                         c1 :requirements {:digest "sha256:a"}))))
    (is (= :artifact-conflict
           (:reason (supply-chain/attach-artifact
                     c1 :requirements {:digest "sha256:b"}))))))

(deftest complete-case-reaches-ordered-only-in-sequence
  (let [artifact-types (->> supply-chain/required-artifacts vals
                            (apply concat) set)
        with-artifacts (reduce
                        (fn [c artifact-type]
                          (:case (supply-chain/attach-artifact
                                  c artifact-type
                                  {:digest (str "sha256:" (name artifact-type))})))
                        (new-case) artifact-types)
        completed (reduce
                   (fn [c stage]
                     (:case (supply-chain/advance
                             c stage {:at "2026-07-20" :by "operator-1"})))
                   with-artifacts supply-chain/stages)]
    (is (supply-chain/production-ready? completed))
    (is (= 8 (count (:procurement-case/history completed))))))

(deftest purchase-order-has-a-real-schema
  (is (:valid? (schema/validate-entity
                :purchase-order
                {:purchase-order/id "po-1" :engagement/id "eng-1"
                 :rfq/id "rfq-1" :quote/id "q-1" :vendor/id "v-1"
                 :purchase-order/currency "JPY"
                 :purchase-order/total-minor-units 100000
                 :purchase-order/status :approved}))))
