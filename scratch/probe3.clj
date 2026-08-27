(ns probe3
  (:require [clojure.pprint :as pp]
            [clojure.edn :as edn]
            [langgraph.graph :as g]
            [talent.store :as tstore]
            [talent.operation :as top]
            [ai-datacenter.actor :as ada]
            [ai-datacenter.store :as adstore]))

(def digest (str "sha256:" (apply str (repeat 64 "a"))))
(def d2 (str "sha256:" (apply str (repeat 64 "b"))))

(defn -main [& _]
  ;; talent: approved assignment + approved evaluation
  (let [db (tstore/seed-db) actor (top/build db)
        hrbp {:actor-id "e-100" :actor-role :hrbp :purpose :review :consent? true :phase 3}]
    (g/run* actor {:request {:op :assignment/propose :subject "e-002" :to-dept "カスタマーサクセス" :retention? true} :context hrbp} {:thread-id "X"})
    (let [r (g/run* actor {:approval {:status :approved :by "e-001"}} {:thread-id "X" :resume? true})]
      (println "assignment record" (pr-str (get-in r [:state :record])))
      (println "store assignment-of e-002 =>" (pr-str (tstore/assignment-of db "e-002"))))
    (g/run* actor {:request {:op :evaluation/draft :subject "e-001"} :context (assoc hrbp :phase 1)} {:thread-id "Y"})
    (let [r (g/run* actor {:approval {:status :approved :by "e-002"}} {:thread-id "Y" :resume? true})]
      (println "eval record" (pr-str (get-in r [:state :record])))
      (println "store evaluation-of e-001 =>" (pr-str (tstore/evaluation-of db "e-001")))))
  ;; ai-datacenter
  (let [st (adstore/mem-store)
        vendors (edn/read-string (slurp "data/ai-datacenter/vendors.edn"))
        v (first (filter #(= "gdep-advance" (:vendor/id %)) vendors))
        actor (ada/build st)
        ctx {:now "2026-08-15T00:00:00Z"}]
    (println "\n--- ai-datacenter ---")
    ;; 1 operation-not-allowed
    (let [r (ada/run! actor {:operation :vendor/delete :proposal/digest digest} ctx "a1")]
      (println "a1" (:status r) (pr-str (get-in r [:state :verdict]))))
    ;; 2 invalid-entity
    (let [r (ada/run! actor {:operation :vendor/register :entity-type :vendor
                             :entity {:vendor/id "gdep-advance"} :entity-id "gdep-advance"
                             :proposal/digest digest} ctx "a2")]
      (println "a2" (:status r) (pr-str (get-in r [:state :verdict]))))
    ;; 3 missing-evidence
    (let [r (ada/run! actor {:operation :contract/sign :subject-id "eng-1" :evidence []
                             :proposal/digest digest} ctx "a3")]
      (println "a3" (:status r) (pr-str (get-in r [:state :verdict]))))
    ;; 4 vendor-not-qualified (real seed vendor)
    (let [r (ada/run! actor {:operation :rfq/send :vendor v :subject-id "rfq-1" :proposal/digest digest} ctx "a4")]
      (println "a4" (:status r) (pr-str (get-in r [:state :verdict]))))
    ;; 5 approval-required then approve
    (let [r (ada/run! actor {:operation :vendor/register :entity-type :vendor
                             :entity {:vendor/id "gdep-advance" :vendor/legal-name "GDEP Advance, Inc." :vendor/role #{:solution-integrator}}
                             :entity-id "gdep-advance" :proposal/digest digest} ctx "a5")]
      (println "a5" (:status r) (pr-str (get-in r [:state :verdict]))))
    ;; 6 quote/select needs approval
    (let [r (ada/run! actor {:operation :quote/select :entity-id "q-1" :proposal/digest d2} ctx "a6")]
      (println "a6" (:status r) (pr-str (get-in r [:state :verdict])))
      (let [r2 (ada/resume! actor {:approval/id "ap-1" :approval/proposal-digest d2 :approval/status :approved
                                   :approval/by "e-100" :approval/role :procurement-manager
                                   :approval/at "2026-08-15T01:00:00Z"} "a6")]
        (println "a6-resume" (:status r2) (pr-str (get-in r2 [:state :verdict])) "rec" (pr-str (get-in r2 [:state :record])))))
    ;; 7 market entry holds
    (let [r (ada/run! actor {:operation :engagement/create :market-entry {:jurisdiction :BRA} :proposal/digest digest} ctx "a7")]
      (println "a7" (pr-str (get-in r [:state :verdict]))))
    (let [r (ada/run! actor {:operation :engagement/create :market-entry {:jurisdiction :EU} :proposal/digest digest} ctx "a8")]
      (println "a8" (pr-str (get-in r [:state :verdict]))))
    (let [r (ada/run! actor {:operation :engagement/create :market-entry {:jurisdiction :JPN :evidence #{}} :proposal/digest digest} ctx "a9")]
      (println "a9 reason" (:reason (get-in r [:state :verdict])) "missing-count" (count (:missing (get-in r [:state :verdict])))))
    (let [r (ada/run! actor {:operation :shipment/register :route {:origin :USA :destination :JPN :packs {}} :proposal/digest digest} ctx "a10")]
      (println "a10" (pr-str (get-in r [:state :verdict]))))
    (println "\nevents:") (pp/pprint (adstore/events st))
    (println "effects:" (pr-str (adstore/effects st)))))
