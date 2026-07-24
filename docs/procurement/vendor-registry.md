# Supplier discovery registry

`data/ai-datacenter/vendors.edn` contains public-source discovery records.
Current coverage includes GPU/platform manufacturers, server manufacturers,
HPC/GPU systems vendors, technology distributors, integrators and an AI data-
center business operator across Japan, the United States, mainland China,
Hong Kong and EU markets. Taiwan-headquartered manufacturers are included
because they are material upstream server suppliers to those markets.

`vendors-for` filters candidates by region and role. This is discovery, not an
approved-vendor list. Before an RFQ can be sent, independently confirm:

- exact contracting legal entity and company-registry record;
- representative/sales authority for the SKU and destination;
- bank/payment destination using an out-of-band check;
- manufacturer authorisation and warranty/service territory;
- beneficial ownership, sanctions/export controls and conflicts;
- current product configuration, price, allocation, lead time and support.

The code returns `:vendor-not-qualified` until all four identity/authority/
payment/warranty facts are marked verified. Public pages never satisfy these
transaction-specific controls by themselves.
