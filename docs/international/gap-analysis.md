# Supply-chain implementation gap analysis

## Implemented

- business lifecycle and evidence-gated commissioning;
- public-source supplier discovery records;
- RFQ drafting, approval-gated inquiry and like-for-like quote comparison;
- contract negotiation pack, acceptance form and operating runbooks;
- jurisdiction evidence gate for JPN/USA/CHN/HKG/EU;
- cross-border origin and destination approval gate.

## Still incomplete for a real transaction

| Gap | Why code cannot complete it | Closure evidence |
|---|---|---|
| Live price, stock and allocation | vendor-specific and time-sensitive | signed quote/API response with validity |
| Exact GPU export classification | SKU, origin, destination, end-user/use dependent | classification and trade-counsel approval |
| Supplier due diligence | public product pages do not prove authority/solvency | registry extract, bank/authority check, beneficial-owner review |
| Facility engineering | no site, load or utility reservation supplied | stamped design, utility/cooling capacity and AHJ approvals |
| Financing and tax outcome | entity/transaction facts absent | lender terms and signed adviser memo |
| Customer demand/offtake | no executed compute customer commitment | signed capacity/offtake agreement |
| Data-transfer legality | workload and data map absent | approved data map, transfer mechanism and security assessment |
| Insurance | asset, cyber, interruption and liability terms absent | binder/certificate and exclusions review |
| Logistics/chain of custody | route and Incoterms not selected | forwarder plan, cargo cover, serial custody records |
| Local operations | no named 24x7 staff/provider | roster, training, access approval and escalation test |
| BCP/DR | second site/capacity not contracted | tested recovery plan and evidence |
| Binding contracts | templates are not negotiated | counterparty redlines, counsel approval and signatures |

The present implementation can organise and gate these facts. It cannot make
them true. Production and purchase remain held until the applicable rows close.
