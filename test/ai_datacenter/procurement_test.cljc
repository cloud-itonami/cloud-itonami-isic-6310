(ns ai-datacenter.procurement-test
  (:require [clojure.test :refer [deftest is]]
            [ai-datacenter.procurement :as procurement]))

(deftest rfq-is-a-draft-and-send-is-gated
  (let [rfq (procurement/create-rfq
             {:rfq/id "rfq-1" :buyer/legal-name "Buyer KK"
              :delivery/location "Tokyo" :workload/profile :inference
              :capacity/gpu-count 8 :power/max-kw 15 :response/due-on "2026-08-10"})]
    (is (= :draft (:status rfq)))
    (is (:approval-required? rfq))
    (is (= :request-approval
           (:disposition (procurement/govern-action :send-rfq))))))

(deftest inquiry-targets-official-channel-but-remains-unsent
  (let [vendor {:vendor/id "vendor-1" :vendor/legal-name "Vendor"
                :vendor/role #{:systems-vendor}
                :vendor/source-url "https://vendor.example/products"
                :vendor/contact-url "https://vendor.example/contact"
                :vendor/verified-on "2026-07-20"
                :vendor/source-status :verified-public-page}
        rfq (procurement/create-rfq
             {:rfq/id "rfq-1" :buyer/legal-name "Buyer KK"
              :delivery/location "Tokyo" :workload/profile :inference
              :capacity/gpu-count 8 :power/max-kw 15 :response/due-on "2026-08-10"})
        inquiry (procurement/create-inquiry vendor rfq)]
    (is (= :draft (:status inquiry)))
    (is (:approval-required? inquiry))
    (is (= "https://vendor.example/contact" (:contact-url inquiry)))))

(deftest public-discovery-record-cannot-be-sent-until-qualified
  (let [vendor {:vendor/id "vendor-1"}]
    (is (= :vendor-not-qualified
           (:reason (procurement/govern-vendor-action :send-rfq vendor))))
    (is (= #{:contracting-entity :sales-authority :payment-destination
             :warranty-territory}
           (set (:missing (procurement/govern-vendor-action :send-rfq vendor)))))))

(deftest discovery-filter-does-not-imply-qualification
  (let [vendors [{:vendor/id "a" :vendor/regions #{"EU"}
                  :vendor/role #{:server-manufacturer}}
                 {:vendor/id "b" :vendor/regions #{"JPN"}
                  :vendor/role #{:server-manufacturer}}]]
    (is (= ["a"] (mapv :vendor/id
                         (procurement/vendors-for
                          vendors {:region "EU" :role :server-manufacturer}))))))

(deftest quote-comparison-rejects-incomplete-data
  (is (= :incomplete-quotes
         (:reason (procurement/rank-quotes [{:quote/vendor-id "v1"}])))))

(deftest quote-comparison-calculates-like-for-like-total
  (let [q {:quote/vendor-id "v1" :quote/currency "JPY"
           :quote/total-ex-tax 115 :quote/valid-until "2026-08-31"
           :quote/lead-time-days 60 :quote/warranty-months 36
           :quote/power-peak-kw 10 :quote/spec-conforms? true
           :quote/hardware 100 :quote/freight 5 :quote/installation 5
           :quote/support 5 :quote/other 0}]
    (is (= 115 (-> (procurement/rank-quotes [q]) :quotes first
                   :quote/calculated-total)))))
