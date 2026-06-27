(ns talent.sim
  "Demo runner: push the four kaonavi-equivalent domains through one
  OperationActor and watch the PolicyGovernor + approval workflow earn the
  HR-LLM the right to commit.

    op1  従業員DB upsert（HRBP・正当）                 → commit
    op2  評価ドラフトが性別を判断根拠に引用            → 公正性 REJECT → hold
    op3  帳票 export が病歴/年齢を過剰列に含む          → 最小開示 REJECT → hold
    op4  サーベイ分析が離職リスク high（重大・低確信）  → 人間承認へ escalate
                                                       → HRBP approve → commit

  Run: clojure -M:dev:run"
  (:require [langgraph.graph :as g]
            [talent.store :as store]
            [talent.operation :as op]
            [talent.report :as report]))

(defn- line [& xs] (println (apply str xs)))

(defn- run-op!
  "Run one HR operation on its own thread-id. If it interrupts for human
  approval, the ApprovalActor 'approves' and we resume — mirroring a real
  approval workflow."
  [actor thread-id request context approve?]
  (let [res (g/run* actor {:request request :context context} {:thread-id thread-id})]
    (if (= :interrupted (:status res))
      (do (line "   ⏸  承認ワークフロー — ApprovalActor がレビュー中 (reason: "
                (-> res :state :audit last :reason) ")")
          (let [res2 (g/run* actor
                             {:approval {:status (if approve? :approved :rejected)
                                         :by "e-100"}}
                             {:thread-id thread-id :resume? true})]
            (line "   ▶  承認" (if approve? "可決" "却下") " → disposition = "
                  (get-in res2 [:state :disposition]))
            res2))
      (do (line "   → disposition = " (get-in res [:state :disposition])
                "  (confidence " (get-in res [:state :verdict :confidence]) ")")
          res))))

(defn -main [& _]
  (let [db    (store/seed-db)
        actor (op/build db)
        hrbp  {:actor-id "e-900" :actor-role :hrbp :purpose :review :consent? true}]

    (line "── 従業員DB / 組織図 (DirectoryActor) ──")
    (line (report/org-chart-text db "e-100"))

    (line "\n── OperationActor (HR-LLM sealed; PolicyGovernor active) ──")

    (line "\nop1  従業員DB upsert（HRBP が等級と部署を更新・正当）")
    (run-op! actor "op1"
             {:op :employee/upsert :subject "e-002"
              :patch {:id "e-002" :dept "営業推進"}}
             hrbp true)

    (line "\nop2  評価ドラフト — HR-LLM が「女性なので」と性別を根拠に引用")
    (run-op! actor "op2"
             {:op :evaluation/draft :subject "e-001" :bias? true}
             hrbp true)

    (line "\nop3  帳票 export（目的=headcount なのに病歴・年齢を過剰列に要求）")
    (run-op! actor "op3"
             {:op :report/export :subject "*" :greedy? true}
             (assoc hrbp :purpose :headcount) true)

    (line "\nop4  サーベイ分析（e-002 離職リスク high・重大かつ低確信 → 人間承認）")
    (run-op! actor "op4"
             {:op :survey/analyze :subject "e-002"}
             hrbp true)

    (line "\n── 帳票（最小開示で許可された列のみ・headcount 目的）──")
    (line (report/render-csv db [:id :name :grade :dept]))

    (line "\n── 監査台帳 (append-only; SaaS では得られない不変の証跡) ──")
    (doseq [f (store/ledger db)]
      (line "  " (store/ledger-line f)))
    (line "\ndone.")))
