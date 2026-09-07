(ns talent.facts
  "Production seed adapter — hydrate the SSoT from the real org facts in
  `m365-archive/facts/people.edn` (line-delimited `:person/*` EDN, the same
  source gftd-keiei-sim seeds from) instead of the built-in demo employees.

  Graceful fallback is the whole point: `m365-archive` is a DataLad/git-annex
  dataset that is OFF by default (the working copy is just a pointer until
  `west annex-get` materializes it with B2 creds). So when the file is
  absent, empty, or still an annex pointer, this returns nil and the caller
  keeps the deterministic demo seed — the demo and the policy tests never
  depend on credentialed data.

  The filesystem boundary is capability-injected: `read-people` takes a
  `read-string!` fn (path -> string or nil) instead of importing java.io;
  hosts supply their own adapter (default uses clojure.core/slurp)."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [talent.store :as store]))

(def facts-dir "../m365-archive/facts")
(def default-people-path  (str facts-dir "/people.edn"))
(def default-goals-path   (str facts-dir "/goals.edn"))
(def default-surveys-path (str facts-dir "/surveys.edn"))

(defn annex-pointer?
  "True when `content` is an unmaterialized git-annex pointer rather than
  real EDN (datalad dataset not fetched)."
  [content]
  (boolean (re-find #"^/annex/objects/" (str/trim (or content "")))))

(def default-read-string!
  "Default host adapter: clojure.core/slurp (no clojure.java.io / java.*
  interop at the require level). Hosts without a filesystem pass their own fn
  or call the 2-arity."
  (fn [path] (try (slurp path) (catch Exception _ nil))))

(defn read-people
  "Read line-delimited `:person/*` EDN. `read-string!` is the injected
  capability fn (path -> string or nil); defaults to `default-read-string!`.
  Returns [] when the file is missing, an annex pointer, or unparseable —
  never throws."
  ([path] (read-people path default-read-string!))
  ([path read-string!]
   (let [content (read-string! path)]
     (if (and content (not (annex-pointer? content)))
       (->> (str/split-lines content)
            (map str/trim)
            (filter #(str/starts-with? % "{"))
            (keep #(try (edn/read-string %) (catch Exception _ nil))))
       []))))

(defn- person-id [p]
  (or (:person/id p) (:person/mail p) (some-> (:db/id p) str)))

(defn ->employee
  "Map one m365 `:person/*` fact to the actor's employee schema. Facts carry
  no HR grade, so default to :G1; protected attributes are intentionally NOT
  imported from M365 (the fairness gate has nothing to leak unless HR adds it
  deliberately)."
  [p]
  (let [id (person-id p)]
    (when id
      {:id        id
       :name      (or (:person/name p) id)
       :grade     (or (:person/grade p) :G1)
       :dept      (or (:person/dept p) (:person/department p) "—")
       :manager   (:person/manager p)
       :protected (or (:person/protected p) {})})))

(defn people->employees
  "Internal persons only → {id employee}. Externals (vendors/customers in the
  same facts file) are excluded from the employee directory."
  [people]
  (->> people
       (filter #(= :internal (:person/role %)))
       (keep ->employee)
       (map (juxt :id identity))
       (into {})))

(defn load-employees
  "Employees map hydrated from facts, or nil when facts are unavailable
  (caller falls back to the demo seed). `path` defaults to the m365 dataset."
  ([] (load-employees default-people-path))
  ([path]
   (let [emps (people->employees (read-people path))]
     (when (seq emps) emps))))

;; ───────────────────────── goals (MBO/OKR) ─────────────────────────

(defn ->goal [g]
  {:id (:goal/id g) :title (:goal/title g) :target (:goal/target g)
   :actual (:goal/actual g) :period (:goal/period g)})

(defn load-goals
  "Goals grouped by employee id ({id [goal ...]}), or nil when unavailable.
  Facts shape: line-delimited `{:goal/person <emp-id> :goal/id .. :goal/title
  .. :goal/target .. :goal/actual .. :goal/period ..}`."
  ([] (load-goals default-goals-path))
  ([path]
   (let [by-emp (->> (read-people path)              ; same line-delimited reader
                     (filter :goal/person)
                     (group-by :goal/person))
         m (into {} (for [[eid gs] by-emp] [eid (mapv ->goal gs)]))]
     (when (seq m) m))))

;; ───────────────────────── engagement surveys ─────────────────────────

(defn ->survey [s]
  {:engagement (:survey/engagement s) :enps (:survey/enps s) :free (:survey/free s)})

(defn load-surveys
  "Surveys keyed by employee id ({id survey}), or nil when unavailable.
  Facts shape: `{:survey/person <emp-id> :survey/engagement .. :survey/enps ..
  :survey/free ..}` (latest wins on duplicate)."
  ([] (load-surveys default-surveys-path))
  ([path]
   (let [m (into {} (for [s (filter :survey/person (read-people path))]
                      [(:survey/person s) (->survey s)]))]
     (when (seq m) m))))

(defn hydrate!
  "Replace/seed a Store's directory + goals + surveys from m365-archive facts,
  each falling back to whatever the store already holds when its facts file is
  absent or an annex pointer. Returns the store. The production seam: build a
  demo or Datomic store, then `(facts/hydrate! store)`."
  ([st] (hydrate! st {}))
  ([st {:keys [people-path goals-path surveys-path]
        :or   {people-path default-people-path
               goals-path default-goals-path
               surveys-path default-surveys-path}}]
   (-> st
       (store/with-employees (load-employees people-path))
       (store/with-goals     (load-goals goals-path))
       (store/with-surveys   (load-surveys surveys-path)))))
