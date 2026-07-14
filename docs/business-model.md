# Open Business Blueprint: cloud-itonami-6310

This repository publishes an OSS business model for operating a
talent-management SaaS replacement on itonami.cloud.

## Classification

- Repository name: `cloud-itonami-6310`
- Primary classification: ISIC Rev.5 6310
- Activity: computing infrastructure, data processing, hosting and related
  activities
- Served domain: HR and talent-management operations
- Original implementation name: `gftd-talent-actor`

The ISIC code describes the business activity of running a hosted, managed
software/data-processing service. The HR actor is the first productized service
inside that classification.

## Customer

Primary customers:

- companies with 50-500 employees that want to leave proprietary HR SaaS
- organizations that need personnel-data sovereignty and auditability
- schools, cooperatives, NPOs and local operators that cannot accept SaaS
  lock-in
- HR consultants and system integrators who want a repeatable OSS delivery
  package

## Problem

HR SaaS vendors hold employee data, workflow rules, evaluations, reports and
audit history inside a closed system. Customers pay continuously for access to
their own operating records and have limited ability to inspect policy logic or
prove why a decision happened.

## Offer

Operators provide an OSS replacement for talent-management SaaS:

- employee directory and organization graph
- evaluation and goal workflows
- survey and attrition-risk analysis
- governed CSV/report export
- role-based access control
- purpose limitation, fairness and minimal-disclosure gates
- immutable audit ledger
- migration and managed operations

The core promise: the HR-LLM can draft and analyze, but it cannot commit or
disclose records unless the independent PolicyGovernor allows it.

## Revenue

Operators can sell:

- self-host setup: one-time implementation fee
- managed hosting: monthly subscription per tenant
- support: monthly retainer with SLA
- migration: import from existing HR SaaS or spreadsheets
- policy customization: role, purpose and disclosure rules
- compliance package: audit export, retention, security review
- training: HR/admin/operator onboarding

Example pricing should be adapted by country and support burden:

| Package | Customer | Price shape (illustrative) |
|---|---|---|
| Self-host starter | small organization | setup ¥300k–800k + optional support |
| Managed standard | 50-200 employees | ¥60k–150k/月 per tenant |
| Managed regulated | sensitive HR process | ¥200k+/月 + audit package |
| Operator enablement | consultant/SI | training + certification |

The anchor: proprietary HR SaaS is typically priced per employee per month,
forever, with the data inside the vendor. A managed tenant here is priced per
tenant, the data stays in the customer's own store (in-mem/Datomic/
kotoba-server — the Store seam), and the policy logic is inspectable AGPL
code. That is the switching pitch.

## Funnel (demo → fork → certified operator)

1. **Demo** — the live GitHub Pages demo
   (<https://cloud-itonami.github.io/cloud-itonami-isic-6310/>) shows the
   Talent Board with protected attributes structurally absent, and the
   PolicyGovernor verdict table (fairness HOLD, minimal-disclosure HOLD,
   escalation) recomputed from the actual `.cljc` compliance code at page
   build — the sovereignty + governance pitch in one screen.
2. **Fork / self-host** — AGPL; run it for one organization.
3. **Managed operation** — an SI/consultant operates tenants; policy packs
   (roles/purposes/disclosure columns) are the customization surface.
4. **itonami.cloud certification** — listed operators get leads and may run
   managed HR tenants (same trust ladder as every cloud-itonami venture).

## Unit Economics

Track these numbers for every operator:

- setup hours per tenant
- monthly infrastructure cost
- LLM cost per operation
- support hours per tenant
- incident and audit hours
- gross margin after infrastructure and support
- churn and expansion revenue

Worked example (illustrative, one managed 100-employee tenant): actor +
store infrastructure ≈ ¥10k–20k/月 (writes are governed operations, reads
are cheap); LLM cost is bounded because only proposals call a model
(evaluation cycles + survey analyses, not every read); the real cost is HR
approval labor and support — budget ~10 h/月 until policy packs stabilize,
then ~3–5 h/月. At ¥100k/月 that is >70% gross margin at steady state; the
business scales with tenants, not headcount, and expansion revenue is
policy customization + the compliance package.

The business should only scale after setup work is repeatable and policy tests
catch customer-specific misconfiguration before production use.

## Open Participation

Anyone may:

- fork the repository
- run the demo
- deploy a self-hosted instance
- submit issues and patches
- publish compatible policy packs
- create a local operator business

itonami.cloud should require certification before listing an operator as a
trusted provider, routing customer leads, or allowing managed HR-data handling
under the platform brand.

## Operator Trust Levels

| Level | Capability |
|---|---|
| Contributor | patches, docs, issues, examples |
| Self-host operator | runs their own instance with no platform endorsement |
| Certified operator | listed on itonami.cloud after review |
| Managed operator | may receive leads and operate customer tenants |
| Core maintainer | can approve changes to policy, security and governance |

## Marketplace Metadata

Suggested itonami.cloud metadata:

```edn
{:itonami.blueprint/id "cloud-itonami-6310"
 :itonami.blueprint/name "Talent Actor"
 :itonami.blueprint/isic-rev5 "6310"
 :itonami.blueprint/domain :hr/talent-management
 :itonami.blueprint/license "AGPL-3.0-or-later"
 :itonami.blueprint/operator-model :certified-open-business
 :itonami.blueprint/repo "https://github.com/gftdcojp/cloud-itonami-6310"
 :itonami.blueprint/status :public-oss}
```

## Non-Negotiables

- Do not commit real employee data.
- Do not bypass the PolicyGovernor for production writes or disclosures.
- Do not market an uncertified deployment as an itonami.cloud certified
  operator.
- Do not run managed HR data without an incident-response path and audit-log
  export.
