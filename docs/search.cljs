;; In-browser search over the employee board -- ClojureScript run by
;; scittle (no build step, no hand-written .js), same pattern as
;; cloud-itonami-isic-6399/web. Data is the JSON the generator embedded
;; in #board-data, read from the REAL talent.store seed; protected
;; attributes are structurally absent from it.
(ns talent.board)

(def employees
  (js->clj (js/JSON.parse (.-textContent (js/document.getElementById "board-data")))
           :keywordize-keys true))

(defn- esc [s]
  (-> (str s)
      (.replaceAll "&" "&amp;")
      (.replaceAll "<" "&lt;")
      (.replaceAll ">" "&gt;")))

(defn- card-html [e]
  (str "<div class=\"card\">"
       "<h3>" (esc (:name e)) "</h3>"
       "<div class=\"meta\">" (esc (:grade e)) " · " (esc (:dept e))
       " · 上長: " (esc (:manager e)) "</div>"
       "<div class=\"meta\">" (esc (:engagement e)) "</div>"
       (when (seq (:goals e))
         (str "<ul>" (apply str (map #(str "<li>" (esc %) "</li>") (:goals e))) "</ul>"))
       "</div>"))

(defn- matches? [e q]
  (or (= q "")
      (.includes (.toLowerCase (str (:name e) " " (:dept e) " " (:grade e))) q)))

(defn- render! []
  (let [q (.toLowerCase (.-value (js/document.getElementById "q")))
        hits (filter #(matches? % q) employees)]
    (set! (.-innerHTML (js/document.getElementById "board"))
          (apply str (map card-html hits)))
    (set! (.-hidden (js/document.getElementById "empty")) (boolean (seq hits)))))

(.addEventListener (js/document.getElementById "q") "input" render!)
(render!)
