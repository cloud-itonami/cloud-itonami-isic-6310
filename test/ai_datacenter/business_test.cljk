(ns ai-datacenter.business-test
  (:require [clojure.test :refer [deftest is]]
            [ai-datacenter.business :as business]))

(deftest evidence-and-approval-gates
  (let [base (assoc (business/new-engagement {:id "dc-1" :customer-id "c-1"})
                    :stage :tax-review)]
    (is (= :missing-evidence (:reason (business/propose base :sign-contract []))))
    (is (= :request-approval
           (:disposition (business/propose base :sign-contract
                                           [{:type :tax-eligibility :issuer "tax-adviser"}]))))))

(deftest commissioning-requires-independent-evidence
  (let [base (assoc (business/new-engagement {:id "dc-1" :customer-id "c-1"})
                    :stage :installed)]
    (is (= [:facility-acceptance]
           (:missing (business/propose base :commission
                                       [{:type :electrical-compliance}]))))))

(deftest economics-never-assumes-return
  (is (= {:revenue-yen 120 :cost-yen 100 :operating-margin-yen 20}
         (business/period-economics {:compute-revenue-yen 120
                                     :electricity-yen 60 :facility-yen 25
                                     :maintenance-yen 10 :fees-yen 5}))))
