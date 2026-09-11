(ns ai-datacenter.international-test
  (:require [clojure.test :refer [deftest is]]
            [ai-datacenter.international :as intl]))

(deftest eu-is-not-treated-as-a-country
  (is (= :eu-member-state-required
         (:reason (intl/assess-market-entry {:jurisdiction :EU :evidence #{}})))))

(deftest missing-controls-hold-market-entry
  (let [r (intl/assess-market-entry {:jurisdiction :USA :evidence #{}})]
    (is (= :hold (:disposition r)))
    (is (some #{:usa-ear-review} (:missing r)))
    (is (some #{:export-classification} (:missing r)))))

(deftest complete-pack-still-needs-human-market-approval
  (let [evidence (intl/required-evidence :HKG)]
    (is (= :request-approval
           (:disposition (intl/assess-market-entry
                          {:jurisdiction :HKG :evidence evidence}))))))

(deftest cross-border-route-needs-both-packs
  (is (= [:CHN]
         (:missing (intl/assess-route
                    {:origin :CHN :destination :HKG
                     :packs {:CHN {:status :draft} :HKG {:status :approved}}})))))
