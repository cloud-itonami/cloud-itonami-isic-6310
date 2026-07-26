# AI data-center implementation checklist

- [ ] Customer, operator, asset owner, facility, maintainer and compute reseller identified
- [ ] Workload/capacity/security requirements approved
- [ ] Vendor source records refreshed and identities verified
- [ ] RFQ approved and issued; responses retained unchanged
- [ ] Quotes normalised; exceptions, related parties and conflicts disclosed
- [ ] Tax/accounting memo obtained from accountable professional
- [ ] Legal review of contract pack completed
- [ ] Facility power/cooling/network/fire acceptance complete
- [ ] PO, serial, title, warranty and insurance registered
- [ ] Commissioning and acceptance evidence signed
- [ ] SLA, escalation contacts, monitoring and on-call activated
- [ ] Tenant isolation, access, logging, keys, vulnerability and incident controls tested
- [ ] Meter-to-invoice reconciliation tested
- [ ] BCP/DR and incident tabletop completed
- [ ] Decommission, sanitisation and chain-of-custody path tested

Production status must remain `:hold` while any applicable item is incomplete.

The executable procurement gate is `ai-datacenter.supply-chain`. It permits
only one-stage transitions, requires digest-addressed artifacts for every gate,
and treats replacement of an artifact under the same type as a conflict.
