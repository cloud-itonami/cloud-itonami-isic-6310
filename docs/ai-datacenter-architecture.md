# AI data-center operating architecture

## System of record

The vertical owns engagements, vendors, RFQs, quotes, contracts, purchase
orders, shipments, assets, meter readings, invoices, payments, settlements,
evidence, approvals and an append-only event ledger. `ai-datacenter.store/Store`
is the backend seam; `MemStore` is the deterministic reference implementation
and `DatomicStore` persists the same contract through `langchain.db` so its
connection can be backed by the tenant's Datomic-compatible/kotoba store.
Production must additionally make entity + event/outbox persistence atomic.

## Governed operation

```text
API command
  -> canonical proposal + sha256 digest
  -> DataCenterGovernor (shape, evidence, vendor, jurisdiction, role)
  -> commit | HOLD | StateGraph interrupt
  -> signed human approval bound to digest
  -> commit entity/event OR enqueue cloud-itonami effect
```

`ai-datacenter.actor` is the only write route. One operation is one checkpointed
StateGraph run. Approval resumes the same thread and cannot approve a different
proposal digest. Evidence is bound to type and subject and carries issuer,
issue time, optional expiry, status and content digest.

## Responsibility boundary

| Capability | Owner |
|---|---|
| Engagement and lifecycle | this vertical |
| Vendor/RFQ/quote/PO/shipment/asset records | this vertical |
| GPU usage evidence, invoice calculation, owner settlement | this vertical |
| Lead capture and CRM projection | cloud-itonami CRM lane |
| Inbound/outbound email and delivery receipts | cloud-itonami mail lane |
| Checkout, invoice delivery, payment-provider calls | cloud-itonami billing lane |
| Document signature/review workflow | cloud-itonami approval/document lane |
| Actual scheduler and telemetry | injected compute provider adapter |
| Customs, legal, tax, facility certification | accountable external professionals |

External capabilities receive `:itonami.effect/*` proposals from
`ai-datacenter.integrations`. They are never called from pure domain code.

## Transaction invariants

- no unknown operation can commit;
- external/financial operations require a role-bound approval;
- approval references the canonical proposal SHA-256 digest;
- evidence must cover the exact contract, PO, asset, invoice or settlement;
- public vendor discovery is not vendor qualification;
- origin and destination jurisdiction packs must both be approved;
- duplicate meter source/digest records are rejected by ingestion adapters;
- invoice lines use integer minor units and one currency;
- owner distribution is based on cash collected, never invoiced forecasts;
- external calls are idempotent by `:itonami.effect/id` and live in an outbox;
- tax eligibility, utilisation and return are never self-certified or guaranteed.

## Production adapters still requiring deployment configuration

The implementations are injected seams, because credentials and providers are
tenant-specific: cloud-itonami runtime handler, scheduler
meter feed, object storage for signed documents, WebAuthn identity, payment
provider, email provider and e-signature provider. A deployment is not live
until its adapter contract tests and end-to-end sandbox test pass. The included
MemStore/DatomicStore parity tests do not prove a remote production connection.
