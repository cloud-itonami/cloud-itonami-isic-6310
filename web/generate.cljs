;; Generates docs/index.html (the GitHub Pages demo UI) from EDN/Hiccup via
;; kotoba-lang/html + kotoba-lang/css -- markup/styling as data, not
;; hand-quoted HTML strings -- following kototama/web/generate.cljs's and
;; cloud-itonami-isic-6399/web's own precedent (nbb authoring, zero build
;; step for a visiting browser; in-browser interactivity is `search.cljs`
;; run by scittle, i.e. ClojureScript in the browser, not a hand-written
;; .js file).
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
;;     --classpath "../src:../../../kotoba-lang/html/src:../../../kotoba-lang/css/src:../../../kotoba-lang/langchain/src:../../../kotoba-lang/langgraph/src" \
;;     generate.cljs
(require '[clojure.string :as cstr]
         '[html.core :as html]
         '[css.core :as css]
         '[langgraph.graph :as g]
         '[talent.store :as store]
         '[talent.operation :as op]
         '[talent.report :as report]
         '["fs" :as fs])

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

(def results (mapv run-op! operations))
(def ledger (store/ledger db))

;; ReportActor output, computed from the POST-RUN store: the org chart
;; reflects op5's committed retention move, and the CSV renders exactly
;; the columns the minimal-disclosure gate allows for :headcount (the
;; same purpose op3's greedy export was HELD for).
(def org-chart (report/org-chart-text db "e-100"))
(def headcount-csv (report/render-csv db [:id :name :grade :dept]))

(def stylesheet
  (css/style-node
   {:rules
    {":root" {:--fg "#1b1f24" :--bg "#ffffff" :--muted "#57606a"
              :--card "#f6f8fa" :--line "#d0d7de" :--accent "#0b5cad"
              :--ok-bg "#dafbe1" :--ok-fg "#116329"
              :--hold-bg "#ffebe9" :--hold-fg "#a40e26"
              :--esc-bg "#fff8c5" :--esc-fg "#7d4e00"}
     "body" {:font-family "system-ui,-apple-system,'Hiragino Sans','Noto Sans JP',sans-serif"
             :margin "0 auto" :max-width 880 :padding "28px 20px 48px"
             :color "var(--fg)" :background "var(--bg)" :line-height 1.55}
     "header p.sub" {:color "var(--muted)" :margin-top 4}
     "h1"   {:font-size 24 :margin "0"}
     "h2"   {:font-size 17 :margin-top 40 :border-top "1px solid var(--line)"
             :padding-top 24}
     ".search" {:display :flex :gap 8 :margin-top 20}
     "input#q" {:flex 1 :font-size 16 :padding "10px 14px"
                :border "1.5px solid var(--line)" :border-radius 8
                :background "var(--bg)" :color "var(--fg)"}
     "select#dept" {:font-size 15 :padding "10px 12px"
                    :border "1.5px solid var(--line)" :border-radius 8
                    :background "var(--bg)" :color "var(--fg)"}
     "#board" {:display :grid :grid-template-columns "repeat(auto-fill,minmax(250px,1fr))"
               :gap 12 :margin-top 12}
     ".card" {:background "var(--card)" :border "1px solid var(--line)"
              :border-radius 10 :padding "14px 16px"}
     ".card h3" {:margin "0 0 2px" :font-size 16}
     ".card .meta" {:color "var(--muted)" :font-size 13.5}
     ".card ul" {:margin "8px 0 0" :padding-left 18 :font-size 13.5}
     ".badge" {:display :inline-block :font-size 12 :font-weight 600
               :border-radius 20 :padding "2px 10px" :margin-left 8
               :vertical-align "1px"}
     ".badge.ok" {:background "var(--ok-bg)" :color "var(--ok-fg)"}
     ".badge.hold" {:background "var(--hold-bg)" :color "var(--hold-fg)"}
     ".badge.esc" {:background "var(--esc-bg)" :color "var(--esc-fg)"}
     ".chip" {:display :inline-block :font-size 12 :color "var(--muted)"
              :border "1px solid var(--line)" :border-radius 20
              :padding "1px 9px" :margin-right 6}
     "#empty" {:color "var(--muted)" :margin-top 16}
     "table" {:border-collapse :collapse :width "100%" :margin-top 12
              :font-size 13.5}
     "th" {:text-align :left :color "var(--muted)" :font-weight 600
           :border-bottom "1.5px solid var(--line)" :padding "6px 8px"}
     "td" {:border-bottom "1px solid var(--line)" :padding "7px 8px"
           :vertical-align :top}
     "footer" {:margin-top 48 :padding-top 16 :border-top "1px solid var(--line)"
               :color "var(--muted)" :font-size 13.5}
     "a" {:color "var(--accent)"}
     "code" {:background "var(--card)" :padding "1px 5px" :border-radius 4
             :font-size "0.9em"}}
    :media
    {"(prefers-color-scheme: dark)"
     {":root" {:--fg "#e6edf3" :--bg "#0d1117" :--muted "#8d96a0"
               :--card "#161b22" :--line "#30363d" :--accent "#58a6ff"
               :--ok-bg "#12261e" :--ok-fg "#3fb950"
               :--hold-bg "#2d1215" :--hold-fg "#f85149"
               :--esc-bg "#2b2411" :--esc-fg "#d29922"}}}}))

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

(defn disposition-badge [d approved?]
  (cond
    (and (= d :commit) approved?) [:span [:span.badge.esc "escalate → 人間承認"] " " [:span.badge.ok "可決 → commit"]]
    (= d :commit)  [:span.badge.ok "auto-commit"]
    (= d :hold)    [:span.badge.hold "HOLD"]
    :else          [:span.badge.esc "escalate → 人間承認"]))

(def page
  [:html {:lang "ja"}
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    [:title "Talent Board — governed HR (cloud-itonami-isic-6310)"]
    [:meta {:name "description"
            :content "kaonavi 等 HR SaaS のオープンソース置き換えデモ。人材データはあなたの手元、HR-LLM の全操作は独立 PolicyGovernor（RBAC・目的制限・公正性・最小開示）が検閲。"}]
    stylesheet]
   [:body
    [:header
     [:h1 "Talent Board " [:span.badge.ok "governed"]]
     [:p.sub "人材データベース — HR SaaS (kaonavi 等) の OSS 置き換え。人材データを SaaS に人質に取られず、"
      "HR-LLM の全操作を独立 PolicyGovernor が検閲する。 "
      [:a {:href "https://github.com/cloud-itonami/cloud-itonami-isic-6310"} "cloud-itonami-isic-6310"]
      " のライブデモ(合成データ)。"]]

    [:div.search
     [:input {:id "q" :type "search" :placeholder "氏名・部署で検索…" :autocomplete "off"}]
     (into [:select {:id "dept"} [:option {:value ""} "全部署"]]
           (for [d (sort (distinct (map :dept employees)))]
             [:option {:value d} d]))]
    [:div {:id "board"}]
    [:p {:id "empty" :hidden true} "該当する社員はいません。"]
    [:p [:span.meta "カードに年齢・性別・国籍・健康情報が無いのは仕様です — 保護属性は "
         [:code "talent.policy/protected-attrs"]
         " として評価根拠・帳票開示の両方から HARD ガードされています。"]]

    [:h2 "PolicyGovernor — HR-LLM の operation がどう検閲されるか"]
    [:p "kaonavi 型 SaaS との違いはここです: HR-LLM(advisor) は提案しか返せず、コミット権は"
     "独立した "
     [:a {:href "https://github.com/cloud-itonami/cloud-itonami-isic-6310/blob/main/src/talent/policy.cljc"}
      "PolicyGovernor"]
     " が握ります(HARD violation は人間の承認でも覆せません)。下表はこのページの生成時に"
     "実際の OperationActor(StateGraph)を4回実行した結果です — HR-LLM 提案 → PolicyGovernor → "
     "phase gate → (必要なら)承認 interrupt → commit/hold。"]
    [:table
     [:thead [:tr [:th "operation"] [:th "判定"] [:th "根拠"]]]
     (into [:tbody]
           (for [{:keys [label disposition verdict approved?]} results]
             [:tr
              [:td label]
              [:td (disposition-badge disposition approved?)]
              [:td (if-let [vs (seq (:violations verdict))]
                     (into [:span] (for [v vs]
                                     [:span [:span.badge.hold (name (:rule v))] " " (:detail v) [:br]]))
                     (str "violation なし / confidence " (:confidence verdict)))]]))]

    [:h2 "監査台帳 — 上の4実行が実際に書いた追記専用レコード"]
    [:p "SaaS では得られない不変の証跡。以下はハードコードではなく、ページ生成時の実 actor 実行が "
     [:code "talent.store"] " の台帳に書いた事実そのものです。"]
    (into [:pre]
          [(cstr/join "\n" (map store/ledger-line ledger))])

    [:h2 "組織図 — ReportActor が実行後 Store から生成(kaonavi 組織図)"]
    [:p "manager リンクからの純関数レンダリング。op5 のリテンション配置転換が反映済みです。"]
    (into [:pre] [org-chart])

    [:h2 "帳票 — 最小開示ゲートを通った CSV の実物"]
    [:p "op3 の過剰開示(病歴・年齢・性別)は HOLD になりました。これは同じ :headcount 目的で"
     "最小開示ゲートが許す列だけを実際にレンダリングした帳票です — 保護属性の列は"
     "ポリシー上ここに現れることができません(" [:code "talent.policy/purpose-columns"] ")。"]
    (into [:pre] [headcount-csv])

    [:h2 "この人材ボードが保証すること"]
    [:ul
     [:li "保護属性(年齢・性別・国籍・信条・健康・婚姻・妊娠)は評価根拠にならない(" [:strong "公正性ゲート"] ")"]
     [:li "帳票は宣言した目的に許された列しか出ない(" [:strong "最小開示ゲート"] ")"]
     [:li "role × 操作 × 対象関係の RBAC を LLM が迂回できない"]
     [:li "等級変更・退職勧奨など高影響の操作は必ず人間の承認を経る"]
     [:li "すべての commit / hold / 承認が追記専用の監査台帳に残る"]]

    [:footer
     [:p "OSS (AGPL-3.0-or-later)。fork して自社の人材基盤として運営できます — "
      [:a {:href "https://github.com/cloud-itonami/cloud-itonami-isic-6310/blob/main/docs/business-model.md"} "business model"]
      " · "
      [:a {:href "https://github.com/cloud-itonami/cloud-itonami-isic-6310/blob/main/docs/operator-guide.md"} "operator guide"]
      " · 姉妹デモ: "
      [:a {:href "https://cloud-itonami.github.io/cloud-itonami-isic-6399/"} "Meta Job Search (isic-6399)"]
      "。このページは " [:code "web/generate.cljs"] " (nbb) が実 Store/PolicyGovernor を実行して生成し、検索は "
      [:code "search.cljs"] " (scittle = ブラウザ内 ClojureScript) が実行しています。"]]

    ;; employee board data for the in-browser search (search.cljs).
    ;; [:hiccup/raw ...] because script elements are raw text -- entities
    ;; are never decoded inside them (same as isic-6399's page).
    [:script {:type "application/json" :id "board-data"}
     [:hiccup/raw (js/JSON.stringify (clj->js (mapv employee->json-entry employees)))]]
    [:script {:src "https://cdn.jsdelivr.net/npm/scittle@0.6.22/dist/scittle.js"}]
    [:script {:type "application/x-scittle" :src "search.cljs"}]]])

(fs/mkdirSync "../docs" #js {:recursive true})
(fs/writeFileSync "../docs/index.html" (str "<!doctype html>\n" (html/render page) "\n"))
(fs/copyFileSync "search.cljs" "../docs/search.cljs")
(println (str "wrote docs/index.html (" (count employees) " employees; "
              (pr-str (mapv :disposition results)) ")"))
