# cloud-itonami integration effects

`ai-datacenter.integrations` emits the common envelope:

```clojure
{:itonami.effect/id "stable-idempotency-key"
 :itonami.effect/kind :mail/send
 :itonami.effect/risk :external-send
 :itonami.effect/tenant {:org "owner" :repo "business"}}
```

Mappings:

| Business event | Effect kind | Existing owner |
|---|---|---|
| lead/form/inbound inquiry | `:crm/lead-ingest` | CRM/kotobase projection |
| vendor RFQ/customer notice | `:mail/send` | mail approval + Resend handler |
| invoice/payment action | `:ai-datacenter/invoice.issue` | billing adapter |
| contract review/signature request | `:ai-datacenter/contract.review` | approval/document adapter |

`:mail/send` has an existing cloud-itonami execution path. Direct
`:crm/lead-ingest`, `:ai-datacenter/invoice.issue` and
`:ai-datacenter/contract.review`
effects are integration contracts introduced by this vertical and require
explicit handlers in the selected tenant deployment. The default runner must
fail them as `missing-handler`; it must never treat a queued effect as delivered.

Handlers return provider IDs and delivery state keyed by effect ID. Retry must
reuse that ID. The vertical records delivery receipts as new events; it never
rewrites the original proposal or approval. Unknown effects remain queued and
surface as an operator action rather than silently succeeding.
