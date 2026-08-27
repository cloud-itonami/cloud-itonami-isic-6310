(ns probe2
  (:require [langgraph.graph :as g]
            [talent.store :as store]
            [talent.operation :as op]
            [clojure.pprint :as pp]))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)
        hrbp {:actor-id "e-100" :actor-role :hrbp :purpose :review :consent? true :phase 3}]
    ;; A. upsert at phase 1 -> phase-approval escalate -> approve -> commit
    (let [r (g/run* actor {:request {:op :employee/upsert :subject "e-002" :patch {:id "e-002" :dept "カスタマーサクセス"}}
                           :context (assoc hrbp :phase 1)} {:thread-id "A"})]
      (println "A status" (:status r) "audit-last" (pr-str (last (get-in r [:state :audit]))))
      (let [r2 (g/run* actor {:approval {:status :approved :by "e-001"}} {:thread-id "A" :resume? true})]
        (println "A2 record" (pr-str (get-in r2 [:state :record])))
        (println "A2 store emp e-002 =>" (pr-str (store/employee db "e-002")))))
    ;; B. evaluation/draft clean e-002 -> ?
    (let [r (g/run* actor {:request {:op :evaluation/draft :subject "e-002"} :context hrbp} {:thread-id "B"})]
      (println "\nB status" (:status r) "disp" (get-in r [:state :disposition])
               "reason" (pr-str (last (get-in r [:state :audit])))
               "conf" (get-in r [:state :verdict :confidence]))
      (when (= :interrupted (:status r))
        (let [r2 (g/run* actor {:approval {:status :approved :by "e-001"}} {:thread-id "B" :resume? true})]
          (println "B2 record" (pr-str (get-in r2 [:state :record])))
          (println "B2 store eval e-002 =>" (pr-str (store/evaluation-of db "e-002"))))))
    ;; C. survey/analyze on e-100 (NO survey row) -> low-confidence?
    (let [r (g/run* actor {:request {:op :survey/analyze :subject "e-100"} :context hrbp} {:thread-id "C"})]
      (println "\nC status" (:status r) "audit-last" (pr-str (last (get-in r [:state :audit])))))
    ;; D. rejection path
    (let [r (g/run* actor {:request {:op :assignment/propose :subject "e-001" :to-dept "倉庫管理"} :context hrbp} {:thread-id "D"})]
      (println "\nD status" (:status r))
      (let [r2 (g/run* actor {:approval {:status :rejected :by "e-001"}} {:thread-id "D" :resume? true})]
        (println "D2 disp" (get-in r2 [:state :disposition]) "audit-last" (pr-str (last (get-in r2 [:state :audit]))))))
    ;; E. rbac (employee role)
    (let [r (g/run* actor {:request {:op :evaluation/draft :subject "e-001"}
                           :context {:actor-id "e-002" :actor-role :employee :purpose :review :consent? true :phase 3}} {:thread-id "E"})]
      (println "\nE verdict" (pr-str (get-in r [:state :verdict :violations]))))
    ;; F. rbac-subject (manager on self)
    (let [r (g/run* actor {:request {:op :evaluation/draft :subject "e-100"}
                           :context {:actor-id "e-100" :actor-role :manager :purpose :review :consent? true :phase 3}} {:thread-id "F"})]
      (println "F verdict" (pr-str (get-in r [:state :verdict :violations]))))
    ;; G. purpose nil
    (let [r (g/run* actor {:request {:op :employee/upsert :subject "e-001" :patch {:id "e-001" :dept "営業"}}
                           :context {:actor-id "e-100" :actor-role :hrbp :purpose nil :consent? true :phase 3}} {:thread-id "G"})]
      (println "G verdict" (pr-str (get-in r [:state :verdict :violations]))))
    ;; H. consent false
    (let [r (g/run* actor {:request {:op :evaluation/draft :subject "e-001"}
                           :context {:actor-id "e-100" :actor-role :hrbp :purpose :review :consent? false :phase 3}} {:thread-id "H"})]
      (println "H verdict" (pr-str (get-in r [:state :verdict :violations]))))
    ;; I. phase 0 disabled
    (let [r (g/run* actor {:request {:op :employee/upsert :subject "e-002" :patch {:id "e-002" :dept "営業"}}
                           :context (assoc hrbp :phase 0)} {:thread-id "I"})]
      (println "I audit-last" (pr-str (last (get-in r [:state :audit])))))
    (println "\n== full ledger ==")
    (pp/pprint (store/ledger db))
    (println "\n== assignments ==" (pr-str (store/assignment-of db "e-001")))))
