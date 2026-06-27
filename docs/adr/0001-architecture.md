# ADR-0001: gftd-talent-actor — HR-LLM を封じ込めた知能ノードとするタレントマネジメント・アクター設計

- Status: Accepted (2026-06-27)
- 関連: robotaxi-actor ADR-0001 (研究モデルを信頼境界に封じ込める actor 設計), langgraph-clj ADR-0001 (Pregel superstep + interrupt + Datomic checkpoint), gftd-keiei-sim (kotoba-datomic SSoT + 意思決定台帳 + LLM社員エージェント), kawasakijun ADR-0010 (Life Graph — EDN事実層 + Datalogビュー)
- 文脈: 商用タレントマネジメント SaaS（kaonavi 等）への支払いに依存せず、同等以上を OSS の actor として自前運用する。

## 課題

タレントマネジメント（従業員DB・組織図 / 評価・目標 MBO・OKR / サーベイ・分析 /
承認ワークフロー・帳票）を SaaS に課金して借りるのではなく、**自前の OSS actor**
として持ちたい。狙いは費用削減だけではない:

1. **データ主権** — 人事データという最も機微な資産を、解約・値上げ・サービス終了・
   支払い遅延での停止で人質に取られない（`orgs/personal/.../2503-kaonavi-cursor`
   の係争はまさにこの依存リスクが顕在化した事例）。SSoT は自分の Datomic に置く。
2. **監査可能性** — 「誰が・誰の・どの項目を・どの根拠で変更/開示したか」を
   不変の台帳（append-only ledger）に残し、Datalog で問える。
3. **コンプライアンスをコードで固定** — 権限分離・利用目的拘束・評価の公正性・
   個人情報の最小開示を、規程ドキュメントではなく実行可能なゲートとして強制する。

一方、評価ドラフト生成・サーベイ分析・離職予兆スコアリング・承認経路の提案には
LLM が有効だが、**LLM に人事レコードを直接書かせる/開示させるのは危険**である
（保護属性を根拠にした評価、過剰な個人情報開示、権限を越えた更新、幻覚）。

したがって設計課題は「LLM で人事を回す」ことではなく、**「LLM を信頼境界の内側に
封じ込め、人事に必要な権限・公正性・監査・人間承認の層をどう被せるか」**である。
これは robotaxi-actor が AR1（安全機構の無い研究モデル）を SafetyGovernor で
封じ込めた構図の、そのままの写像である。

## 決定

### 1. HR-LLM は最下層の1ノードに封じ込め、直接書き込み/開示させない

OperationActor 内で HR-LLM は *proposal*（評価ドラフト・分析・スコア・承認経路の提案
＋根拠トレース）のみを返す**助言者**として扱う。出力は必ず独立した
`PolicyGovernor` を通してから台帳に commit する。**単一の不変条件**:

> **HR-LLM は、PolicyGovernor が拒否する人事レコードの書き込み・開示を決して行わない。**

これが「LLM は人事の最終権限を持てない」という制約を埋め、生成モデルに人事業務を
任せる唯一の根拠。robotaxi の「AR1 は SafetyGovernor が拒否する軌道を作動させない」
と同型。

### 2. OperationActor = langgraph-clj StateGraph、1 run = 1 HR操作

```
intake → advise(HR-LLM) → govern(PolicyGovernor) → decide ─┬─ ok・確信・低リスク ──────▶ commit → END
                                                           ├─ 重大操作 / 低確信 ─▶ request-approval
                                                           │                       [interrupt-before]
                                                           │                       HRBP/上長がレビュー
                                                           │                       resume ─▶ commit | hold
                                                           └─ 規程違反(権限/公正/開示) ─▶ hold → END
```

- 連続処理ループを持たず「1操作=1 run」とし、各操作を監査可能・checkpoint 可能にする
  （robotaxi の「1 tick=1 run」と同型）。
- `:context` チャネルに **RBAC/目的/同意のコンテキストを外部注入**（誰が・誰に・
  何の目的で操作するか）。robotaxi の `:route` 外部注入の写像。
- `interrupt-before #{:request-approval}` を**実際の承認ワークフロー**に転用
  （langgraph-clj の human-in-the-loop をそのまま「上長/HRBP 承認」に適用）。
  kaonavi の承認フローを、SaaS のワークフロー機能ではなく actor の割り込みで実現。
- `:audit` チャネルに提案根拠・判定・承認・差し戻しを蓄積 → 監査台帳・規程適合の
  証跡が同一ファクトログから落ちてくる（gftd-keiei-sim の意思決定台帳と同型）。

### 3. PolicyGovernor は HR-LLM と別系統

LLM とは別経路（規程ルール + 権限表 + 保護属性表）で構築し、LLM 提案を**拒否して
「hold（書き込まない）」に落とせる**ことを保証する。これが robotaxi の MRC（安全停止）
に相当する HR 版フォールバック。

| 責務 | 機構 |
|---|---|
| 権限分離 (RBAC) | actor の role が「その操作 × その対象」に権限を持つか。無ければ拒否 |
| 利用目的拘束 | 目的が宣言され、対象者の同意/法的根拠があるか。無ければ拒否 |
| 評価の公正性 | 評価/判断ドラフトが**保護属性**（年齢・性別・国籍・信条・病歴・婚姻・妊娠）を根拠に引いていないか。引いていれば拒否 |
| 個人情報の最小開示 | 帳票/開示が目的に対し過剰な項目を含まないか。過剰なら拒否 |
| 確信度フロア | LLM 信頼度が閾値未満 → 人間承認へ escalate |
| 重大操作ゲート | 等級変更・解雇・減給など high-stakes → 必ず人間承認 |

**規程違反（権限/公正/開示）は人間承認では上書きできず hold に固定**する。
人間が上書きできるのは「低確信」「重大操作」など*正当だが要確認*のケースのみ。
これにより「承認ボタンで違反を押し通す」運用を構造的に塞ぐ。

### 4. SSoT と台帳はすべて自前（SaaS 非依存の核）

- 状態の SSoT は **Datomic**（dev は in-mem の EDN 事実層）。従業員・組織・目標・
  サーベイは entity 事実として持つ。gftd-keiei-sim の kotoba-datomic SSoT と同じ。
- すべての commit / reject は **append-only の監査台帳**に積む。SaaS では得られない
  「不変・問い合わせ可能な人事監査ログ」を自前資産にする。
- 種データは `m365-archive/facts`（people 等）から seed 可能（gftd-keiei-sim と共有）。

### 5. アクター・トポロジ（監督ツリー）

```
TalentSystem (root supervisor)
├── DirectoryActor ……… 従業員DB・組織図（EmployeeUpsert / OrgGraph）
├── PerformanceActor …… 評価・目標 MBO/OKR（EvaluationDraft / GoalProgress）
├── InsightActor ……… サーベイ・分析（SurveyAnalyze / AttritionRisk）
├── OperationActor[op] … ★ 1操作=1 actor run; HR-LLM 封じ込め + PolicyGovernor ★
│     ├── HR-LLM (sealed)         proposal only
│     ├── PolicyGovernor          INDEPENDENT 規程ゲート（別系統）
│     ├── Committer               台帳/SSoT への書き込み
│     └── Ledger/Recorder         監査台帳（不変）
├── ApprovalActor ……… 上長/HRBP 承認（interrupt を受ける human-in-the-loop）
└── ReportActor ……… 帳票/CSV 出力（PolicyGovernor を通した governed read）
```

## 4 ドメインを 1 つの操作モデルに統一

kaonavi 相当の 4 領域は別アプリではなく、**1 種類の OperationActor が処理する
operation の種別**として表す（DRY・監査台帳が一本化される）:

| operation | ドメイン | HR-LLM の役割 | 主なゲート |
|---|---|---|---|
| `:employee/upsert` | 従業員DB・組織図 | patch 正規化 | RBAC・目的 |
| `:evaluation/draft` | 評価・目標 MBO/OKR | 目標実績から評価文ドラフト | 公正性・確信度・重大操作 |
| `:survey/analyze` | サーベイ・分析 | エンゲージメント要約・離職予兆 | 目的・確信度・最小開示 |
| `:report/export` | ワークフロー・帳票 | 列セット提案 | 最小開示・RBAC |

## 帰結

- **得るもの**: SaaS 月額の消滅、人事データ主権、不変の監査台帳、規程の実行可能化、
  LLM 支援（評価/分析）と人間最終承認の両立。
- **負うもの**: 自前運用（Datomic 運用・規程表/権限表の保守・LLM 推論基盤）。
  SaaS の「運用を肩代わり」価値は手放すので、運用を内製できる組織が前提。
- **段階導入**: Phase 0 read-only（DB/組織図/帳票）→ Phase 1 評価ドラフト（人間承認必須）
  → Phase 2 サーベイ分析 → Phase 3 確信度の高い定型操作のみ自動 commit。robotaxi の
  ODD 段階拡大と同型で、介入率が下がった範囲から自動化を広げる。

## 代替案と不採用理由

- **kaonavi 等 SaaS 継続**: 運用は楽だが、データ主権・監査の自由・解約/値上げ/停止
  リスク・規程のコード化が得られない。本 ADR の動機（依存リスクの顕在化）に反する。
- **Next.js + Supabase の素の CRUD アプリ**（`_intake/ai-gftd-oshiete` 系スタック）:
  作れるが「LLM を封じ込める信頼境界」「監査台帳」「規程ゲート」という actor の核が
  構造として現れず、結局 SaaS の機能を薄く再実装するだけになる。actor 設計を採る。
- **LLM に書き込み権限を直接付与（エージェント自律）**: 速いが、保護属性に基づく
  評価・過剰開示・権限越えを構造的に防げない。単一不変条件（決定1）に反する。
