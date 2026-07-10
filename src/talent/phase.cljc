(ns talent.phase
  "Phase 0→3 staged rollout — the HR analog of robotaxi's ODD phases: start
  narrow (read-only), widen as trust grows. Where the PolicyGovernor answers
  'is this allowed?', the phase answers 'how much autonomy does the actor
  have *yet*?'. It can only ever make the actor MORE conservative than policy:
  it downgrades a policy-clean commit to approval or hold, never the reverse.

    Phase 0  read-only        — no writes at all. Directory/org/report reads
                                only (still policy-gated). Shadow/observe.
    Phase 1  assisted eval    — directory upsert + evaluation drafts allowed,
                                but every write needs human approval.
    Phase 2  + insight        — adds survey/analysis writes (still approval).
    Phase 3  supervised auto  — policy-clean, high-confidence writes may
                                auto-commit; the rest still escalate.

  `gate` runs AFTER `policy/check`, taking the policy disposition
  (:commit | :escalate | :hold) and returning the phase-adjusted disposition
  plus a reason when the phase changed it.")

(def read-ops  #{:report/export})
(def write-ops #{:employee/upsert :evaluation/draft :survey/analyze})

(def phases
  "phase → {:label .. :writes <ops allowed to write> :auto <ops allowed to
  auto-commit when policy-clean>}."
  {0 {:label "read-only"       :writes #{}                                          :auto #{}}
   1 {:label "assisted-eval"   :writes #{:employee/upsert :evaluation/draft}        :auto #{}}
   2 {:label "assisted-insight":writes #{:employee/upsert :evaluation/draft :survey/analyze} :auto #{}}
   3 {:label "supervised-auto" :writes write-ops                                    :auto write-ops}})

(def default-phase
  "The phase used when `context` carries no :phase at all (talent.operation:
  `(:phase context phase/default-phase)`), AND the fallback `gate` itself
  uses for an unrecognized phase NUMBER (`(get phases phase (get phases
  default-phase))`). This is directly reachable by any ordinary caller
  that simply omits :phase -- not just malformed/malicious input -- so it
  must be the MOST CONSERVATIVE phase, never the most permissive: 'start
  narrow, widen as trust grows' (this namespace's own docstring) has to
  hold for a MISSING phase too, not only an explicitly-set low one. This
  was 3 (supervised-auto, the single most permissive tier -- every write
  auto-commits) until a live check confirmed a caller who forgets :phase
  silently got maximum autonomy instead of the safe default. 1
  (assisted-eval) matches the sibling ports' own choice here
  (tsumugu.phase / shiropico.phase default-phase 1) -- writes are allowed
  but every one still needs human approval."
  1)

(defn gate
  "Adjust a policy disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}.

  - reads pass through unchanged (phase restricts autonomy, not reads).
  - a policy HOLD always stays HOLD (compliance wins).
  - a write op not yet enabled in this phase → HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible → ESCALATE (:phase-approval),
    even if policy was clean.
  - high-confidence proposals below the per-phase trust still escalate via
    policy; phase only *adds* caution."
  [phase {:keys [op]} policy-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold policy-disposition)        {:disposition :hold :reason nil}
      (contains? read-ops op)             {:disposition policy-disposition :reason nil}
      (not (contains? writes op))         {:disposition :hold :reason :phase-disabled}
      (and (= :commit policy-disposition)
           (not (contains? auto op)))     {:disposition :escalate :reason :phase-approval}
      :else                               {:disposition policy-disposition :reason nil})))

(defn verdict->disposition
  "Map a PolicyGovernor verdict to a base disposition before the phase gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
