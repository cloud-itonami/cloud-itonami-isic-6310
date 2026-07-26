;; In-browser search over the employee board -- ClojureScript run by
;; scittle (no build step, no hand-written .js), same pattern as
;; cloud-itonami-isic-6399/web. Data is the JSON the generator embedded
;; in #board-data, read from the REAL talent.store seed; protected
;; attributes are structurally absent from it.
(ns talent.board)
;;
;; カードは **hiccup で組み html.core が文字列化する** — 生 HTML の文字列連結は
;; しない(生成側 generate.cljs と同じ規約)。html.core が属性値もテキストも
;; エスケープするので自前の esc は不要になった。html_core.cljs は
;; generate.cljs がページと一緒に同梱する。
;;
;; html.core は ns の :require ではなく **完全修飾**で呼ぶ — headless ハーネス
;; (verify_search.cljs)がこのファイルを load-string で評価するが、nbb の
;; load-string は ns の :require を解決できないため(実測 "Doesn't support name:")。
;;
;; 子の並びは必ず [:<> ...] フラグメントで包む — 素のベクタだとヒット0件のとき
;; `[]` になり html.core がタグ付きノードと解釈して落ちる。


(def employees
  (js->clj (js/JSON.parse (.-textContent (js/document.getElementById "board-data")))
           :keywordize-keys true))


;; dds-ext-card は jp-go-dds の layout 拡張(生成側の静的カードと同じ見た目)、
;; tb-card は本ページ固有の中身の字送り。どちらも generate.cljs 側で定義済み。
(defn- card [e]
  [:div {:class "dds-ext-card tb-card"}
   [:h3 (:name e)]
   [:div {:class "meta"} (:grade e) " · " (:dept e) " · 上長: " (:manager e)]
   [:div {:class "meta"} (:engagement e)]
   (when (seq (:goals e))
     (into [:ul] (map (fn [g] [:li g]) (:goals e))))])

(defn- matches? [e q dept]
  (and (or (= dept "") (= dept (:dept e)))
       (or (= q "")
           (.includes (.toLowerCase (str (:name e) " " (:dept e) " " (:grade e))) q))))

(defn- render! []
  (let [q (.toLowerCase (.-value (js/document.getElementById "q")))
        dept (.-value (js/document.getElementById "dept"))
        hits (filter #(matches? % q dept) employees)]
    (set! (.-innerHTML (js/document.getElementById "board"))
          (html.core/->html (into [:<>] (map card hits))))
    (set! (.-hidden (js/document.getElementById "empty")) (boolean (seq hits)))))

(.addEventListener (js/document.getElementById "q") "input" render!)
(.addEventListener (js/document.getElementById "dept") "change" render!)
(render!)
