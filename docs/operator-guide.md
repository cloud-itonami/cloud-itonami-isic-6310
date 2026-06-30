# Operator Guide

This guide is for people who want to start an open business from
`cloud-itonami-6310`.

## 1. Fork and Run

```bash
git clone https://github.com/gftdcojp/cloud-itonami-6310
cd cloud-itonami-6310
clojure -M:dev:test
clojure -M:dev:run
```

The default demo uses synthetic data. Production employee records must stay
outside the repository and be injected through a store adapter.

## 2. Choose an Operating Mode

| Mode | Use when |
|---|---|
| Demo | validating the actor and policy contract |
| Self-host | one organization owns infrastructure and data |
| Managed tenant | an operator hosts for a customer |
| Certified operator | itonami.cloud has reviewed security and process controls |

## 3. Production Checklist

- replace demo data with a customer-owned store
- configure Datomic Local, kotoba-server or an equivalent durable SSoT
- configure the LLM adapter through environment variables or secret manager
- define customer RBAC, purpose and disclosure rules
- run `clojure -M:dev:test`
- run `clojure -M:lint`
- verify audit-ledger export
- document backup and restore
- document incident response
- get written approval for HR-data handling

## 4. Sales Motion

Start with a narrow offer:

1. migrate a small HR directory
2. prove governed report export
3. run one evaluation or survey workflow in assisted mode
4. export the audit ledger for review
5. convert to managed support

Avoid selling broad HR transformation before the customer has accepted the
policy and audit model.

## 5. Certification Requirements

itonami.cloud certification should require:

- passing tests and lint on the published version
- written data-flow diagram
- backup/restore evidence
- incident contact and response window
- proof that production writes go through PolicyGovernor
- proof that real employee data is not stored in Git
- customer-facing support terms

## 6. Operator Responsibilities

Operators are responsible for:

- customer consent and lawful basis
- local employment and privacy-law review
- secure infrastructure
- tenant isolation
- human approval workflow design
- data-retention policy
- security updates

The OSS project provides software and an operating blueprint. It does not make
an operator compliant by itself.
