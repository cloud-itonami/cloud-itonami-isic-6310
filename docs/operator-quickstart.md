# Operator Quickstart — your own governed HR base, fork to production

The shortest path from forking this repo to running a governed
talent-management instance for one organization. This is the concrete
version of `docs/business-model.md`'s funnel step 2 (fork / self-host).

**Unlike the job-search sibling (isic-6399), nothing here goes on public
Pages with real data.** The live demo is synthetic; your instance runs
privately. HR records are personal data — the demo's publishing
pipeline is for the synthetic showcase only.

## 1. Fork and prove the actor green

```bash
git clone https://github.com/<you>/cloud-itonami-isic-6310 && cd cloud-itonami-isic-6310
clojure -M:dev:test    # 27 tests — policy contract, store parity, phases
clojure -M:dev:run     # the four kaonavi-equivalent ops end-to-end
```

(`deps.edn` resolves `kotoba-lang/langgraph`/`langchain` as sibling
checkouts via `:local/root`; standalone forks clone those two next to
the repo or override with git coordinates.)

## 2. Seed your organization

Two options:

- **Directly**: build a store from your own data with
  `talent.store/->MemStore` / `datomic-store` — the record shapes are in
  `talent.store/demo-data` (employees + goals + surveys; keep
  `:protected` attributes accurate — they are what the fairness and
  disclosure gates defend).
- **From facts**: `talent.facts` hydrates employees/goals/surveys from
  an m365-archive-style facts export (see its docstring and the README's
  本番データへの接続 section).

## 3. Customize the policy pack

`talent.policy` is data-first — adapt these tables to your organization
and re-run the policy contract tests:

- `permissions` — role × operation RBAC
- `purpose-columns` — which report purpose may disclose which columns
- `protected-attrs` — the attributes that must never ground an
  evaluation or leak into a report
- `confidence-floor` / `high-stakes` — what always goes to a human

Every change must keep `clojure -M:dev:test` green — the tests ARE the
policy contract your operators and works council can read.

## 4. Wire the approval workflow and phase rollout

- Operations that escalate pause on langgraph's `interrupt-before` and
  resume with `{:approval {:status :approved :by <who>}}` — bind that to
  your actual approval inbox (email/Slack/console).
- Start at `:phase 1` (assisted — every write needs approval; this is
  the default when you pass no phase), widen to `:phase 3`
  (supervised-auto for policy-clean, high-confidence writes) as trust
  grows. The phase can only ever ADD caution over the PolicyGovernor.

## 5. Production posture

- Swap `MemStore` for `DatomicStore` (same contract, proven by
  `store_contract_test`) pointed at your Datomic Local / kotoba-server.
- Swap `mock-advisor` for `llm-advisor` (a `langchain.model/ChatModel`)
  when you want real drafting — the governor treats both identically,
  and a malformed LLM response degrades to a safe noop.
- Export the audit ledger on a schedule; it is your evidence trail for
  every commit, hold and approval.
- Keep real HR data out of git (see SECURITY.md).

## 6. Where this goes next

Pricing shapes, unit economics and the certification ladder
(itonami.cloud listing, managed tenants) are in
`docs/business-model.md`.
