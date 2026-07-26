;; Generates docs/index.html (the GitHub Pages demo UI) from EDN/Hiccup via
;; kotoba-lang/html + kotoba-lang/jp-go-digital-design-system -- markup/styling
;; as data, not hand-quoted HTML strings -- following kototama/web/generate.cljs's
;; and cloud-itonami-isic-6399/web's own precedent (nbb authoring, zero build
;; step for a visiting browser; in-browser interactivity is `search.cljs`
;; run by scittle, i.e. ClojureScript in the browser, not a hand-written
;; .js file).
;;
;; UI は デジタル庁デザインシステム(DADS)を kotoba-lang/jp-go-digital-design-system
;; 経由で使う(superproject ADR-2607261600)。この actor は労働・人事の法規制
;; (保護属性の取扱い・目的制限・最小開示)をソフトウェアとして実装しており、
;; 日本の公的サービスの視覚言語に揃える方が利用者の信頼判断に効く。
;; DADS は light mode 固定(上流に dark palette が無い)なので、移行前の
;; prefers-color-scheme による dark 対応は意図的に落としている。
;;
;; The people board, the operation dispositions AND the audit ledger on
;; the page are NOT hand-typed: this script requires the actor's own
;; namespaces (.cljc, on langchain.db/model/langgraph -- nbb-loadable
;; since kotoba-lang/langchain 9f4453d3 + 0f966d06) and runs the FULL
;; OperationActor StateGraph (HR-LLM sealed advisor -> PolicyGovernor ->
;; phase gate -> approval interrupt -> commit|hold) for the four
;; kaonavi-equivalent operations at build time -- the same runs
;; `talent.sim` walks on the JVM -- then renders the board from the
;; post-run Store and the append-only ledger those runs actually wrote.
;; The published page can never drift from the actual actor logic.
;;
;; Run (from this web/ directory, inside the monorepo checkout):
;;   ../../../../node_modules/.bin/nbb \
;;     --classpath "../src:../../../kotoba-lang/html/src:../../../kotoba-lang/jp-go-digital-design-system/src:../../../kotoba-lang/langchain/src:../../../kotoba-lang/langgraph/src" \
;;     generate.cljs
;;
;; dds.css の読み込みパスは環境変数 JP_GO_DDS_CSS で上書きできる
;; (CI / worktree など monorepo 以外のレイアウト用)。
(require '[clojure.string :as cstr]
         '[css.core :as css]
         '[jp-go-dds.core :as dds]
         '[jp-go-dds.page :as page]
         '[langgraph.graph :as g]
         '[talent.store :as store]
         '[talent.hrllm :as hrllm]
         '[talent.operation :as op]
         '[talent.report :as report]
         '["fs" :as fs])

(def dds-css-path
  (or (some-> js/process.env.JP_GO_DDS_CSS not-empty)
      "../../../kotoba-lang/jp-go-digital-design-system/resources/jp_go_dds/dds.css"))
(def dds-css (fs/readFileSync dds-css-path "utf8"))

(def db (store/seed-db))
(def actor (op/build db))

;; :phase 3 (supervised-auto) explicitly -- same as talent.sim; the
;; conservative default-phase 1 would phase-gate op1/op4 instead.
(def hrbp {:actor-id "e-900" :actor-role :hrbp :purpose :review :consent? true :phase 3})

;; The four kaonavi-equivalent demo operations (same set as talent.sim).
(def operations
  [{:tid "op1" :label "op1 従業員DB upsert（HRBP が部署を更新・正当）"
    :request {:op :employee/upsert :subject "e-002"
              :patch {:id "e-002" :dept "営業推進"}}
    :context hrbp}
   {:tid "op2" :label "op2 評価ドラフトが性別・婚姻を判断根拠に引用"
    :request {:op :evaluation/draft :subject "e-001" :bias? true}
    :context hrbp}
   {:tid "op3" :label "op3 帳票 export（目的=headcount なのに病歴・年齢・性別の列を要求）"
    :request {:op :report/export :subject "*" :greedy? true}
    :context (assoc hrbp :purpose :headcount)}
   {:tid "op4" :label "op4 サーベイ分析（e-002 離職リスク high・重大かつ低確信）"
    :request {:op :survey/analyze :subject "e-002"}
    :context hrbp}
   {:tid "op5" :label "op5 配置転換 — op4 の高リスク所見を受けたリテンション施策（e-002、根拠はサーベイ業務シグナルのみ）"
    :request {:op :assignment/propose :subject "e-002" :to-dept "カスタマーサクセス" :retention? true}
    :context hrbp}
   {:tid "op5b" :label "op5b 配置転換が年齢・通院を判断根拠に引用"
    :request {:op :assignment/propose :subject "e-001" :to-dept "倉庫管理" :bias? true}
    :context hrbp}])

(defn run-op!
  "One supervised actor run, mirroring talent.sim/run-op!: if the graph
  interrupts for human approval, the HRBP approves and the run resumes."
  [{:keys [tid request context] :as o}]
  (let [r (g/run* actor {:request request :context context} {:thread-id tid})
        interrupted? (= :interrupted (:status r))
        r (if interrupted?
            (g/run* actor {:approval {:status :approved :by "e-100"}}
                    {:thread-id tid :resume? true})
            r)]
    (assoc o
           :approved? interrupted?
           :disposition (get-in r [:state :disposition])
           :verdict (get-in r [:state :verdict]))))

(def base-results (mapv run-op! operations))

;; op6 — check 7 (rationale-suspect) demonstrated with a deliberately
;; biased advisor: CLEAN structured cites, protected keyword in the
;; free-text rationale — the live-model failure mode the check was added
;; for (talent.policy check 7). Escalates; the human REJECTS.
(def suspect-advisor
  (let [mock (hrllm/mock-advisor)]
    (reify hrllm/Advisor
      (-advise [_ st req]
        (if (= :evaluation/draft (:op req))
          {:summary "評価ドラフト（次期は昇給見送りが妥当）"
           :rationale "女性で時短勤務のため成長期待は限定的と判断。"
           :cites [:goals]
           :effect :set-goal-eval
           :stake nil
           :confidence 0.9}
          (hrllm/-advise mock st req))))))

(def suspect-run
  (let [actor2 (op/build db {:advisor suspect-advisor})
        r (g/run* actor2 {:request {:op :evaluation/draft :subject "e-001"}
                          :context hrbp} {:thread-id "op6"})
        r2 (when (= :interrupted (:status r))
             (g/run* actor2 {:approval {:status :rejected :by "e-100"}}
                     {:thread-id "op6" :resume? true}))]
    {:tid "op6"
     :label "op6 check 7: cites はクリーン、rationale に「女性で…」（実LLMの失敗様式） — 人間が却下"
     :rejected? (some? r2)
     :disposition (get-in (or r2 r) [:state :disposition])
     :verdict (get-in r [:state :verdict])}))

(def results (conj base-results suspect-run))
(def ledger (store/ledger db))

;; ReportActor output, computed from the POST-RUN store: the org chart
;; reflects op5's committed retention move, and the CSV renders exactly
;; the columns the minimal-disclosure gate allows for :headcount (the
;; same purpose op3's greedy export was HELD for).
(def org-chart (report/org-chart-text db "e-100"))
(def headcount-csv (report/render-csv db [:id :name :grade :dept]))

;; ページ固有の微調整のみ。色は DADS token 参照で raw hex は書かない
;; (kotoba-uiux 規約)。レイアウトの土台は dds-ext-*(jp-go-dds.core/ext-css)。
;; select は上流 DADS の vendored subset に無い(dds.css に .dads-select が
;; 無い)ので、.dads-input-text__input と寸法・境界・focus を揃える。
(def app-rules
  [[".tb-header" {:padding-block "2.5rem 0"}]
   [".tb-header .dads-heading" {:margin "0 0 .5rem"}]
   [".tb-lead" {:color "var(--color-neutral-solid-gray-700)" :line-height 1.7
                :margin ".75rem 0 0"}]
   [".tb-pitch" {:margin-block "2rem"}]
   [".tb-pitch .dads-heading" {:margin "0 0 .75rem"}]
   [".tb-pitch p" {:margin "0 0 .75rem" :line-height 1.8}]
   [".tb-pitch ul" {:margin ".75rem 0 0" :padding-left "1.25rem" :line-height 1.9}]
   [".tb-ctarow" {:display "flex" :gap ".75rem" :flex-wrap "wrap" :margin-top "1.25rem"}]
   [".tb-fine" {:color "var(--color-neutral-solid-gray-600)" :font-size ".8125rem"
                :line-height 1.8 :margin-top "1rem"}]
   [".tb-search" {:display "flex" :gap ".75rem" :flex-wrap "wrap"
                  :align-items "flex-end" :margin-bottom "1.5rem"}]
   [".tb-search .dads-form-control-label" {:flex 1 :min-width "14rem"}]
   [".dads-input-text__input" {:width "100%"}]
   ;; 社員カードは search.cljs が実行時に注入する(dds-ext-card + tb-card)
   ["#board" {:display "grid"
              :grid-template-columns "repeat(auto-fill,minmax(16rem,1fr))"
              :gap "1rem" :margin-top "1rem"}]
   ["#board>*" {:min-width 0}]
   [".tb-card h3" {:margin "0 0 .35rem" :font-size "1rem"}]
   [".tb-card .meta" {:color "var(--color-neutral-solid-gray-600)"
                      :font-size ".8125rem" :line-height 1.7}]
   [".tb-card ul" {:margin ".5rem 0 0" :padding-left "1.125rem"
                   :font-size ".8125rem" :line-height 1.7}]
   [".tb-empty" {:color "var(--color-neutral-solid-gray-600)" :margin-top "1rem"}]
   [".tb-note" {:color "var(--color-neutral-solid-gray-600)" :font-size ".875rem"
                :line-height 1.8 :margin-top "1rem"}]
   [".tb-verdict-basis" {:display "block" :margin-block ".15rem"}]
   ;; チップのラベルを途中で折り返さない(「人間承認」が「人間承/認」に割れる)。
   ;; .dads-table 側が overflow-x:auto なので、広がっても表の中でスクロールする。
   [".dads-table .dads-chip-label" {:white-space "nowrap"}]
   ;; 台帳 / 組織図 / CSV は等幅。横に長いので自身の中でだけ横スクロールさせる
   ["pre" {:font-family "var(--font-family-mono)" :font-size ".8125rem"
           :line-height 1.7 :background "var(--color-neutral-solid-gray-50)"
           :border "1px solid var(--color-neutral-solid-gray-200)"
           :border-radius 8 :padding "1rem" :overflow-x "auto" :margin-top "1rem"}]
   [".tb-guarantees" {:line-height 1.9 :padding-left "1.25rem" :margin 0}]
   [".tb-footer" {:border-top "1px solid var(--color-neutral-solid-gray-200)"
                  :margin-top "3rem" :padding-block "1.5rem 3rem"
                  :color "var(--color-neutral-solid-gray-600)"
                  :font-size ".875rem" :line-height 1.8}]
   [".tb-footer p" {:margin "0 0 .75rem"}]
   [".tb-footer .cta" {:font-size ".9375rem" :font-weight 700
                       :color "var(--color-neutral-solid-gray-900)"}]
   ["code" {:font-family "var(--font-family-mono)"
            :background "var(--color-neutral-solid-gray-50)"
            :border "1px solid var(--color-neutral-solid-gray-200)"
            :border-radius 4 :padding "1px 5px" :font-size ".9em"}]])

(def app-css (css/css {:rules app-rules}))

;; Read AFTER the actor runs -- the board reflects the post-run Store
;; (op1's committed dept change included).
(def employees (store/all-employees db))

(defn employee->json-entry [e]
  (let [goals (store/goals-of db (:id e))
        survey (store/survey-of db (:id e))
        mgr (some->> (:manager e) (store/employee db) :name)]
    {:id (:id e) :name (:name e)
     :grade (name (:grade e)) :dept (:dept e)
     :manager (or mgr "—")
     :goals (mapv #(str (:title %) " — " (:actual %) "/" (:target %)
                        (when (>= (:actual %) (:target %)) " ✓")) goals)
     :engagement (if survey
                   (str "engagement " (:engagement survey) " / eNPS " (:enps survey))
                   "サーベイ未回答")}))

;; 判定バッジは DADS chip-label(filled-1)。ラベル文字列は verify_search.cljs が
;; 実際に assert しているので変えない(例 "却下 → HOLD" / "rationale-suspect")。
(defn- chip [label color] (dds/chip-label label {:color color :style "filled-1"}))

(defn disposition-badge [{:keys [disposition approved? rejected?]}]
  (cond
    rejected?
    [:span (chip "escalate → 人間レビュー" "yellow") " " (chip "却下 → HOLD" "red")]
    (and (= disposition :commit) approved?)
    [:span (chip "escalate → 人間承認" "yellow") " " (chip "可決 → commit" "green")]
    (= disposition :commit) (chip "auto-commit" "green")
    (= disposition :hold)   (chip "HOLD" "red")
    :else                   (chip "escalate → 人間承認" "yellow")))

(defn- verdict-basis [{:keys [verdict]}]
  (cond
    (seq (:violations verdict))
    (into [:span] (for [v (:violations verdict)]
                    [:span {:class "tb-verdict-basis"}
                     (chip (name (:rule v)) "red") " " (:detail v)]))

    (:rationale-suspect? verdict)
    [:span (chip "rationale-suspect" "yellow")
     " 保護属性語を rationale に検出(SOFT — 抑圧でなく人間レビューに回し、人間が却下した)"]

    :else
    (str "violation なし / confidence " (:confidence verdict))))

(def body
  (dds/container
   [:header {:class "tb-header"}
    (dds/heading 1 [:span "Talent Board " (chip "governed" "green")])
    [:p {:class "tb-lead"}
     "人材データベース — HR SaaS (kaonavi 等) の OSS 置き換え。人材データを SaaS に人質に取られず、"
     "HR-LLM の全操作を独立 PolicyGovernor が検閲する。 "
     [:a {:href "https://github.com/cloud-itonami/cloud-itonami-isic-6310"} "cloud-itonami-isic-6310"]
     " のライブデモ(合成データ)。"]]

   [:div {:class "tb-pitch"}
    (dds/card
     (dds/heading 2 "人材データ、いつまでベンダーに預けますか?" {:size "24"})
     [:p "多くの HR SaaS は" [:strong "従業員1人あたり月額課金・永続契約"]
      "で、データはベンダーのシステムの中にあります。このボードは"
      [:strong " テナント単位の定額 ¥80,000/月"] "。データはあなた自身のストア"
      "(in-memory / Datomic / kotoba-server から選択可)に残り、ポリシーロジックは"
      "AGPL で検査可能なコードそのものです — 乗り換えのたびにベンダーロックインを"
      "心配する必要がありません。"]
     [:ul
      [:li "従業員規模が増えても課金は変わらない(テナント単位、シート単位ではない)"]
      [:li "HR-LLM の RBAC・目的制限・公正性・最小開示は独立 PolicyGovernor が検閲 — 人間の承認でも覆せない"]
      [:li "自前運用(セルフホスト)も可能 — ソースは AGPL 公開、ベンダー都合の仕様変更に振り回されない"]]
     [:div {:class "tb-ctarow"}
      (dds/button "🡒 Managed Talent Board を購読(¥80,000/月)"
                  {:type :solid-fill :size "lg"
                   :href "https://buy.stripe.com/4gM00i1K3f4c4ikfQHbMQ0c"})
      (dds/button "自前運用(セルフホスト)に興味がある"
                  {:type :outline :size "lg"
                   :href "https://github.com/cloud-itonami/cloud-itonami-isic-6310/issues/new?template=operator-interest.yml"})]
     [:p {:class "tb-fine"}
      "この価格帯は姉妹 flagship(cloud-itonami-isic-6399/7810)の実競合調査(2026-07-16)と"
      "同じ ¥50k–150k/月 レンジ内 — 6310 単独の競合調査はまだ実施していません(正直な現状、"
      [:a {:href "https://github.com/cloud-itonami/cloud-itonami-isic-6310/blob/main/docs/business-model.md"} "business model"]
      " 参照)。下の技術デモは合成データによる実 actor 実行結果、この価格帯とは独立して生成されています。"])]

   (dds/section
    {:title "人材ボード"}
    [:div {:class "tb-search"}
     (dds/form-field
      {:label "検索" :for "q"}
      (dds/input-text {:id "q" :type "search" :autocomplete "off"
                       :placeholder "氏名・部署で検索…"}))
     (dds/form-field
      {:label "部署" :for "dept"}
      (dds/select {:id "dept"}
                  (into [["" "全部署"]]
                        (for [d (sort (distinct (map :dept employees)))] [d d]))))]
    [:div {:id "board"}]
    [:p {:id "empty" :class "tb-empty" :hidden true} "該当する社員はいません。"]
    [:p {:class "tb-note"}
     "カードに年齢・性別・国籍・健康情報が無いのは仕様です — 保護属性は "
     [:code "talent.policy/protected-attrs"]
     " として評価根拠・帳票開示の両方から HARD ガードされています。"])

   (dds/section
    {:title "PolicyGovernor — HR-LLM の operation がどう検閲されるか"}
    [:p {:class "tb-lead"}
     "kaonavi 型 SaaS との違いはここです: HR-LLM(advisor) は提案しか返せず、コミット権は"
     "独立した "
     [:a {:href "https://github.com/cloud-itonami/cloud-itonami-isic-6310/blob/main/src/talent/policy.cljc"}
      "PolicyGovernor"]
     " が握ります(HARD violation は人間の承認でも覆せません)。下表はこのページの生成時に"
     "実際の OperationActor(StateGraph)を4回実行した結果です — HR-LLM 提案 → PolicyGovernor → "
     "phase gate → (必要なら)承認 interrupt → commit/hold。"]
    (dds/table
     {:headers ["operation" "判定" "根拠"]
      :rows (for [{:keys [label] :as row} results]
              [label (disposition-badge row) (verdict-basis row)])}))

   (dds/section
    {:title "監査台帳 — 上の4実行が実際に書いた追記専用レコード"}
    [:p {:class "tb-lead"}
     "SaaS では得られない不変の証跡。以下はハードコードではなく、ページ生成時の実 actor 実行が "
     [:code "talent.store"] " の台帳に書いた事実そのものです。"]
    [:pre (cstr/join "\n" (map store/ledger-line ledger))])

   (dds/section
    {:title "組織図 — ReportActor が実行後 Store から生成(kaonavi 組織図)"}
    [:p {:class "tb-lead"}
     "manager リンクからの純関数レンダリング。op5 のリテンション配置転換が反映済みです。"]
    [:pre org-chart])

   (dds/section
    {:title "帳票 — 最小開示ゲートを通った CSV の実物"}
    [:p {:class "tb-lead"}
     "op3 の過剰開示(病歴・年齢・性別)は HOLD になりました。これは同じ :headcount 目的で"
     "最小開示ゲートが許す列だけを実際にレンダリングした帳票です — 保護属性の列は"
     "ポリシー上ここに現れることができません(" [:code "talent.policy/purpose-columns"] ")。"]
    [:pre headcount-csv])

   (dds/section
    {:title "この人材ボードが保証すること"}
    [:ul {:class "tb-guarantees"}
     [:li "保護属性(年齢・性別・国籍・信条・健康・婚姻・妊娠)は評価根拠にならない(" [:strong "公正性ゲート"] ")"]
     [:li "帳票は宣言した目的に許された列しか出ない(" [:strong "最小開示ゲート"] ")"]
     [:li "role × 操作 × 対象関係の RBAC を LLM が迂回できない"]
     [:li "等級変更・退職勧奨など高影響の操作は必ず人間の承認を経る"]
     [:li "すべての commit / hold / 承認が追記専用の監査台帳に残る"]])

   [:footer {:class "tb-footer"}
    [:p {:class "cta"}
     [:a {:href "https://github.com/cloud-itonami/cloud-itonami-isic-6310/issues/new?template=operator-interest.yml"}
      "🡒 自社・自団体でこの人材基盤を運営したい方はこちら(operator-interest)"]]
    [:p "OSS (AGPL-3.0-or-later)。fork して自社の人材基盤として運営できます — "
     [:a {:href "https://github.com/cloud-itonami/cloud-itonami-isic-6310/blob/main/docs/business-model.md"} "business model"]
     " · "
     [:a {:href "https://github.com/cloud-itonami/cloud-itonami-isic-6310/blob/main/docs/operator-guide.md"} "operator guide"]
     " · 姉妹デモ: "
     [:a {:href "https://cloud-itonami.github.io/cloud-itonami-isic-6399/"} "Meta Job Search (isic-6399)"]
     "。このページは " [:code "web/generate.cljs"] " (nbb) が実 Store/PolicyGovernor を実行して生成し、検索は "
     [:code "search.cljs"] " (scittle = ブラウザ内 ClojureScript) が実行しています。"]]))

;; employee board data for the in-browser search (search.cljs).
;; script は html.core の raw-text tag なので子は素の文字列で渡す。
(def scripts
  [[:script {:type "application/json" :id "board-data"}
    (js/JSON.stringify (clj->js (mapv employee->json-entry employees)))]
   [:script {:src "https://cdn.jsdelivr.net/npm/scittle@0.6.22/dist/scittle.js"}]
   ;; search.cljs は hiccup を html.core で文字列化する(生 HTML を書かない)ので、
   ;; そのライブラリもブラウザへ同梱する。読み込み順は依存順。
   [:script {:type "application/x-scittle" :src "html_core.cljs"}]
   [:script {:type "application/x-scittle" :src "search.cljs"}]])

(fs/mkdirSync "../docs" #js {:recursive true})
(fs/writeFileSync
 "../docs/index.html"
 (str (page/->page
       {:title "kaonavi代替 — 人材データを人質に取らない、月額固定¥80,000 | Talent Board (cloud-itonami-isic-6310)"
        :description "kaonavi等HR SaaSのオープンソース置き換え。1人あたり課金でずっと払い続け、データはベンダーの中 — このボードはテナント単位の定額¥80,000/月、データはあなたのストアに残る。HR-LLMの全操作を独立PolicyGovernorが検閲。"
        :lang "ja"
        :css dds-css
        :app-css app-css}
       body
       scripts)
      "\n"))
(fs/copyFileSync "search.cljs" "../docs/search.cljs")
;; ブラウザ側 .cljs はコピーするだけ(ビルド無し)。
(def html-root
  (or (some-> js/process.env.KOTOBA_HTML_ROOT not-empty) "../../../kotoba-lang/html"))
(fs/copyFileSync (str html-root "/src/html/core.cljc") "../docs/html_core.cljs")
(println (str "wrote docs/index.html (" (count employees) " employees; "
              (pr-str (mapv :disposition results)) ")"))
