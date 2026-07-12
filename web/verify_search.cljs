;; Headless verification harness for docs/search.cljs -- stubs the DOM
;; surface search.cljs touches, feeds it the REAL JSON block extracted
;; from the generated docs/index.html, loads search.cljs, and asserts
;; the rendered board + a simulated search interaction + the page's
;; policy-verdict content. nbb script (Node-harnesses-in-nbb rule),
;; same pattern as cloud-itonami-isic-6399/web/verify_search.cljs.
;;
;; Run (from this web/ directory):
;;   ../../../../node_modules/.bin/nbb verify_search.cljs
(require '["fs" :as fs])

(def html (fs/readFileSync "../docs/index.html" "utf8"))

(def json-block
  (let [m (re-find #"<script type=\"application/json\" id=\"board-data\">(\[.*?\])</script>" html)]
    (or (second m) (throw (js/Error. "board-data JSON block not found in docs/index.html")))))

;; ---- DOM stub --------------------------------------------------------------

(def listeners (atom {}))

(defn- el [id init]
  (let [o (js-obj)]
    (doseq [[k v] init] (aset o k v))
    (aset o "addEventListener"
          (fn [ev f] (swap! listeners assoc [id ev] f)))
    o))

(def elements
  {"board-data" (el "board-data" {"textContent" json-block})
   "q"          (el "q" {"value" ""})
   "dept"       (el "dept" {"value" ""})
   "board"      (el "board" {"innerHTML" ""})
   "empty"      (el "empty" {"hidden" true})})

(aset js/globalThis "document"
      (js-obj "getElementById" (fn [id] (get elements id))))

;; ---- load the real client code --------------------------------------------

(load-string (fs/readFileSync "../docs/search.cljs" "utf8"))

(defn- board-html [] (aget (get elements "board") "innerHTML"))
(defn- assert! [ok? msg]
  (if ok?
    (println "ok  " msg)
    (do (println "FAIL" msg) (js/process.exit 1))))

;; initial render: all three employees on the board
(assert! (.includes (board-html) "佐藤 花子") "initial render shows e-001")
(assert! (.includes (board-html) "鈴木 太郎") "initial render shows e-002")
(assert! (.includes (board-html) "田中 部長") "initial render shows e-100")
(assert! (.includes (board-html) "新規受注 12 件 — 14/12 ✓") "goal progress rendered from the real store")

;; protected attributes are structurally absent from the search data
;; (no "age" needle: "manager"/"engagement" legitimately contain it)
(doseq [needle ["gender" "nationality" "health" "通院中" "protected"]]
  (assert! (not (.includes json-block needle)) (str "protected surface '" needle "' absent from board data")))

;; simulate a search that matches only 佐藤
(aset (get elements "q") "value" "佐藤")
((get @listeners ["q" "input"]))
(assert! (.includes (board-html) "佐藤 花子") "query keeps 佐藤")
(assert! (not (.includes (board-html) "鈴木 太郎")) "query filters 鈴木 out")

;; dept facet: カスタマーサクセス keeps only e-002 (the retention move
;; committed by op5, following op4's high-risk survey insight)
(aset (get elements "q") "value" "")
(aset (get elements "dept") "value" "カスタマーサクセス")
((get @listeners ["dept" "change"]))
(assert! (.includes (board-html) "鈴木 太郎") "dept facet keeps e-002 in his post-retention-move dept")
(assert! (not (.includes (board-html) "佐藤 花子")) "dept facet filters other depts out")
(aset (get elements "dept") "value" "")

;; no-hit query -> empty notice
(aset (get elements "q") "value" "zzz")
((get @listeners ["q" "input"]))
(assert! (= "" (board-html)) "no-hit query renders no cards")
(assert! (false? (boolean (aget (get elements "empty") "hidden"))) "no-hit query reveals the empty notice")

;; the page carries the REAL actor-run verdicts computed at build time
(assert! (.includes html "fairness") "fairness hold present in verdict table")
(assert! (.includes html "minimal-disclosure") "minimal-disclosure hold present in verdict table")
(assert! (.includes html "escalate") "escalation present in verdict table")

;; the audit ledger section is the REAL append-only record of the build-time runs
(assert! (.includes html "op=:employee/upsert") "ledger has op1 upsert fact")
(assert! (.includes html "basis=[:fairness]") "ledger has op2 fairness hold fact")
(assert! (.includes html "basis=[:minimal-disclosure]") "ledger has op3 disclosure hold fact")
(assert! (.includes html "op=:survey/analyze") "ledger has op4 survey fact")
(assert! (.includes html "op=:assignment/propose") "ledger has op5 assignment facts")
(assert! (.includes json-block "カスタマーサクセス") "board reflects op5's committed retention move (e-002)")
(assert! (.includes html "リテンション施策") "op5 row names the retention basis")
(assert! (.includes json-block "カスタマーサクセス") "board reflects op5's approved 配置転換")
(assert! (.includes html "配置転換") "assignment rows present in verdict table")

;; ReportActor sections computed from the post-run store
(assert! (.includes html "└ 田中 部長") "org chart rendered from manager links")
(assert! (.includes html "カスタマーサクセス)") "org chart reflects the retention move")
(assert! (.includes html "id,name,grade,dept") "governed CSV renders the headcount-allowed columns")
(let [csv-i (.indexOf html "id,name,grade,dept")
      csv-block (.substring html csv-i (+ csv-i 400))]
  (doseq [col ["health" "gender" "age"]]
    (assert! (not (.includes csv-block col)) (str "governed CSV has no '" col "' column"))))

(println "verify_search: all assertions passed")
