# AI data-center tenant API contract

All paths are scoped by `/api/{org}/{repo}/ai-datacenter`. Authentication,
membership, idempotency and approval identity are supplied by cloud-itonami.

| Method/path | Operation | Result |
|---|---|---|
| `POST /engagements` | `:engagement/create` | engagement |
| `POST /engagements/{id}/transitions` | `:engagement/transition` | state or hold |
| `GET /vendors?region=&role=` | discovery | candidates, never approval status |
| `POST /vendors/{id}/qualifications` | `:vendor/register` | evidence record |
| `POST /rfqs` | `:rfq/create` | draft |
| `POST /rfqs/{id}/send` | `:rfq/send` | approval interrupt/effect ID |
| `POST /quotes` | `:quote/register` | immutable quote version |
| `POST /quotes/{id}/select` | `:quote/select` | approval interrupt |
| `POST /contracts` | `:contract/register` | draft/version/digest |
| `POST /contracts/{id}/sign` | `:contract/sign` | approval interrupt/e-sign effect |
| `POST /purchase-orders` | `:purchase-order/issue` | approval interrupt/effect |
| `POST /shipments` | `:shipment/register` | shipment |
| `POST /assets` | `:asset/register` | asset/serial |
| `POST /assets/{id}/accept` | `:asset/accept` | evidence-gated acceptance |
| `POST /meter-readings` | `:compute/register-reading` | deduplicated reading |
| `POST /invoices` | `:invoice/create` | draft calculation |
| `POST /invoices/{id}/issue` | `:invoice/issue` | approval/billing effect |
| `POST /payments` | `:payment/register` | payment evidence |
| `POST /settlements` | `:settlement/approve` | approval interrupt |
| `POST /settlements/{id}/pay` | `:settlement/pay` | payment effect |
| `GET /events` | audit | append-only events |
| `GET /effects` | outbox | queued/delivered/failed effects |

Mutating requests require `Idempotency-Key`. Responses expose
`proposalDigest`, `threadId`, `disposition`, `missingEvidence`, `approvalRoles`
and entity/event versions. `409` means version/idempotency conflict, `422`
means shape/evidence hold and `202` means approval interrupt or queued effect.

The API must not accept client-computed totals, approval status or evidence
validity as authoritative. It recomputes totals and resolves signed evidence
and approval records from the tenant store.
