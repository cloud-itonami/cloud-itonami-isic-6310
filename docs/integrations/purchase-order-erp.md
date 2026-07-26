# Purchase-order ERP connection

The JVM approval drain resolves exactly one ERP environment. Secrets are read
at runtime and must not be committed.

```text
AI_DC_RUNTIME_ENV=development|staging|production
AI_DC_ERP_DEVELOPMENT_URL=https://...
AI_DC_ERP_DEVELOPMENT_TOKEN=...
AI_DC_ERP_STAGING_URL=https://...
AI_DC_ERP_STAGING_TOKEN=...
AI_DC_ERP_PRODUCTION_URL=https://...
AI_DC_ERP_PRODUCTION_TOKEN=...
```

Only the URL and token matching `AI_DC_RUNTIME_ENV` are loaded. Production
rejects non-HTTPS endpoints. The executor sends JSON by `POST`, Bearer auth,
and `Idempotency-Key: <itonami effect id>`. A successful response must contain
`id`, `purchase-order-id`, or `purchase_order_id`; otherwise execution fails
closed. Approval remains mandatory before the drain invokes the executor.

For an ERP with a different payload or authentication contract, inject
`:ai-datacenter-executors {:purchase-order-issue fn}` into
`cloud-itonami.runtime-handlers/handlers`; this explicit adapter takes
precedence over environment configuration.
