# wasm/ — kotoba-wasm deployment of the achievement-band recompute

`achievement_band.kotoba` is a port of `talent.hrllm`'s MBO/OKR
achievement-rate formula and its threshold banding (see
`draft-evaluation`'s `pct`/`rate`/`band` in `src/talent/hrllm.cljc`) into
the minimal `.kotoba` language subset, compiled to a real WASM module via
`kotoba wasm emit`, and hosted via `kototama.tender`
(`test/wasm/achievement_band_test.clj`).

This follows the same `kotoba wasm emit` → `kototama.tender` pipeline
`cloud-itonami-isic-6492`'s `wasm/affordability.kotoba` and
`cloud-itonami-isic-6630`'s `wasm/fee_accrual.kotoba` established
(ADR-2607062330 addendum 5) — `achievement_band.kotoba` is the closest
analog to `affordability.kotoba`: a formula recompute + threshold band
over integer inputs, zero host imports.

## Why this repo's port differs from the others: no `registry.cljc`

Every prior cloud-itonami wasm port (`credit.registry`/`fundmgmt.registry`/
`underwriting.governor`) ported a function that already lived in a
dedicated `registry.cljc`/`governor.cljc` — a trusted layer independent of
the LLM advisor. **This repo (`cloud-itonami-isic-6310`) has no
`registry.cljc`.** Its only decision-making module is
`src/talent/policy.cljc` (the PolicyGovernor), and every check in it —
`rbac-violations`, `purpose-violations`, `fairness-violations`,
`disclosure-violations`, `rationale-suspect?`, `high-stakes` — is a
categorical/set-membership/RBAC check, not an arithmetic one (the one
numeric exception, the `confidence-floor` comparison, is a single `<`
against a fixed constant with no independent formula behind it — no
different in kind from a plain threshold literal).

The one genuine numeric **formula** in this repository (a ratio, scaled
to fixed-point, then banded across two thresholds) instead lives in
`talent.hrllm` — the file this repo's own docstring calls "the *contained
intelligence node*", i.e. the untrusted, LLM-standing-in advisor, not a
governor. `draft-evaluation` computes each goal's completion ratio
(`actual`/`target`), averages those ratios across an employee's goals,
and bands the result into 達成 (>=100%) / 概ね達成 (>=70%) / 未達
(otherwise) — this drives both the evaluation draft's own displayed
"達成率 N%" and, when the rate is low (`< 0.5`), the `:grade-change`
stake that `talent.policy/high-stakes` escalates to a human. It is real
HR business logic (a genuine MBO/OKR ratio+threshold recompute — the
"candidate/employee-scoring formula" shape), just not one currently
cross-checked by an independent governor the way
`fundmgmt.governor`'s `:accrual-mismatch` independently recomputes
`fundmgmt.registry/fee-accrued` against an upstream claim. Porting it to
`.kotoba` proves the formula itself runs as a capability-sandboxed WASM
guest; wiring a governor-side cross-check against it (mirroring
`:accrual-mismatch`) is a natural follow-up this PR does not attempt (see
Follow-ups below).

## Why the source differs from `talent.hrllm`

The `.kotoba` compiler's actual WASM code-generator supports only a
small, empirically-verified subset: the special forms `do`/`let`/`if`
plus `+ - * quot / rem mod = < > <= >= zero? not inc dec` (confirmed by
reading `compile-wasm-expr` in `kotoba-lang/kotoba/src/kotoba/runtime.clj`
— no `pos?`/`neg?`/`and`/`or`/`when`/`cond`, unlike the broader
tree-walking interpreter, same finding `cloud-itonami-isic-6492`/`-6630`
document). The port therefore:

- Uses plain positional `i32` args instead of `{:keys [...]}` map
  destructuring over a vector of goal maps (no maps, no vectors, no
  variable-length collections in the wasm-compilable subset).
- Replaces the `cond` band lookup with nested `if`.
- **Changes the aggregation from a mean-of-per-goal-ratios to a
  ratio-of-sums.** The original `rate` is
  `(/ (reduce + (map pct goals)) (count goals))` — averaging each goal's
  own `actual/target` ratio. `.kotoba` has no recursion/loop construct to
  walk a variable-length goal list from a 0-arity `main`, so this module
  instead takes the goal list's *already-aggregated* `sum-actual` and
  `sum-target` (the host's job — a plain `reduce +`, same "the guest only
  ever sees facts a governor already validated/computed" posture
  `underwriting_decision.kotoba` documents) and recomputes
  `sum-actual / sum-target` as the rate. For goals that share a common
  unit (the demo data's goals are all counts — "新規受注 N 件" etc.), this
  is a reasonable equivalent aggregate metric and gives an *identical*
  band to the original for every case in `talent.store/demo-data`
  (verified in `test/wasm/achievement_band_test.clj`'s
  `achievement-band-wasm-demo-org-parity`) and for any employee with a
  single goal (where ratio-of-sums and mean-of-ratios are the same
  number). It diverges from the original's exact percentage when goals
  have very different target magnitudes — documented here rather than
  silently changed.
- Represents the rate as integer **per-mille** (`rate-permille`, e.g.
  `1150` = 115.0%) via one `quot` (`(sum-actual * 1000) / sum-target`)
  instead of the double division `(/ (double actual) target)` — avoids
  floating point entirely, the same fixed-point convention
  `fee_accrual.kotoba` uses for its rate/years inputs. The `>= 1.0`/
  `>= 0.7` thresholds become `>= 1000`/`>= 700` on the per-mille scale.
- Returns a 3-way band code (`0`/`1`/`2`) instead of a string
  ("未達"/"概ね達成"/"達成") — `.kotoba` has no string-construction
  primitive beyond compile-time literals (see
  `underwriting_decision.kotoba`'s README for the same finding), so the
  band is an integer the host maps back to a label.
- Drops the `(if (seq gs) ... 0.0)` empty-goals guard's need for a
  sequence check entirely: `sum-target <= 0` (the aggregate of zero
  goals, or a malformed all-zero-target goal set) already falls through
  the `(> sum-target 0)` guard to the same `0` (未達) result the original
  gives for an employee with no goals — verified in
  `achievement-band-wasm-no-goals`.

## ABI — parameterized invocation

`kotoba wasm emit` rejects any `main` with parameters (`:main-arity` —
the compiler only ever exports a 0-arity `main`, see `compile-wasm-expr`
in `kotoba-lang/kotoba/src/kotoba/runtime.clj`), so real inputs are
passed through the guest's exported linear memory instead — the same
convention `cloud-itonami-isic-6492`'s `affordability.kotoba` and
`cloud-itonami-isic-6630`'s `fee_accrual.kotoba` use. A host writes two
little-endian i32 values before calling `main()`:

| offset | field        | unit                                              |
|--------|--------------|----------------------------------------------------|
| 0      | `sum-actual` | sum of an employee's `goals-of` `:actual` values   |
| 4      | `sum-target` | sum of the same goals' `:target` values            |

`main()` returns the achievement band as an i32 code: `2` (達成, rate
>= 100%), `1` (概ね達成, 70% <= rate < 100%), or `0` (未達, rate < 70%,
including the no-goals `sum-target = 0` case). Both offsets are well
below `heap-base` (2048), so they never collide with anything the
compiler itself places in memory.

## Rebuilding

```sh
cd ../../kotoba-lang/kotoba   # sibling checkout, west-managed
bin/kotoba-clj wasm emit ../../cloud-itonami/cloud-itonami-isic-6310/wasm/achievement_band.kotoba \
  --package-lock kotoba.lock.edn \
  --output ../../cloud-itonami/cloud-itonami-isic-6310/wasm/achievement_band.wasm --json
```

## Verified locally (this PR)

- `kotoba wasm emit` compiles the module cleanly on the first attempt:
  136 bytes, 0 data segments, 0 host imports, `main` exported with `i32`
  result, 2 functions (`achievement-band`, `main`).
- `test/wasm/achievement_band_test.clj` loads and runs the compiled
  `.wasm` through a real `kototama.tender/instantiate`/`call-main`
  (Chicory `Instance`, not a mock) across 6 `deftest`s / 8 assertions:
  full attainment (達成), partial attainment (概ね達成), below-floor
  (未達), the no-goals zero-target edge case, the exact 70%/100%
  boundary values, and parity against both employees in
  `talent.store/demo-data` (e-001's two goals, e-002's single goal).
  `clojure -M:dev:test` — 41 tests, 135 assertions, 0 failures, 0 errors
  (35 tests / 127 assertions pre-existing + this module's 6/8).
  `clojure -M:lint` — 0 errors, 0 warnings.

Fleet deployment: not attempted in this pass — see
`cloud-itonami-isic-6492`/`cloud-itonami-isic-6511` for the established
pattern.

## Follow-ups

- This module is a standalone formula recompute; it is not wired into
  `talent.policy/check` as an independent cross-check the way
  `fundmgmt.governor`'s `:accrual-mismatch` recomputes
  `fundmgmt.registry/fee-accrued` against an upstream claim. A natural
  next step would give the WASM module a third input (a claimed band or
  claimed per-mille rate from the advisor's proposal) and return a
  match/mismatch bit, mirroring `fee_accrual.kotoba`'s/
  `displayed_compensation.kotoba`'s comparison-style ABI — not attempted
  here to avoid inventing a "claimed value" extraction step
  (parsing a percentage back out of the advisor's free-text summary)
  that does not exist anywhere in the current Clojure source.
- The ratio-of-sums vs. mean-of-ratios divergence noted above is
  unresolved for goal sets with very different target magnitudes; not a
  concern for this repo's demo data, but worth flagging before treating
  this module as a drop-in behavioral replacement for
  `talent.hrllm/draft-evaluation`'s own rate computation.
