# International supply-chain readiness

Supported control packs: Japan, United States, mainland China, Hong Kong SAR
and the European Union. `EU` is not accepted as a complete deployment location:
a Member State must be selected because tax, permits, construction, energy and
NIS2 implementation depend on national law.

The executable gate is `ai-datacenter.international`. The public authority
source registry is `data/ai-datacenter/jurisdictions.edn`. Passing the gate does
not authorise market entry; it changes the result from `hold` to
`request-approval`.

## Required country-pack deliverables

1. legal entity, beneficial owners, contracting authority and local licences;
2. sanctions/restricted-party, export classification, end-user and end-use memo;
3. importer of record, customs value, tax/duties, title and risk transfer;
4. facility power, cooling, planning, electrical, fire, environment and worker safety approvals;
5. privacy roles, data map, localisation, cross-border mechanism and government-access analysis;
6. cybersecurity scope, critical-infrastructure/incident obligations and regulator contacts;
7. employment/contractor and 24x7 on-call arrangements;
8. currency, withholding, permanent-establishment, indirect-tax and transfer-pricing memo;
9. warranty territory, parts depot, response time, authentic-parts and firmware provenance;
10. recycling, hazardous material, media destruction and asset export/resale path.

All legal conclusions must identify jurisdiction, effective date, author and
source. A URL alone is discovery evidence, not legal approval.

## Regional emphasis

- **USA:** EAR/export classification and end-use, OFAC, and state/local privacy,
  environmental, utility and construction rules.
- **Mainland China:** PRC import/export plus PIPL/data localisation and transfer,
  cybersecurity/MLPS and ICP/telecom applicability. Do not reuse Hong Kong findings.
- **Hong Kong:** separate customs and PDPO assessment; confirm any mainland data flow independently.
- **EU:** choose Member State; GDPR roles/transfers, NIS2 national scope, energy
  reporting and CE/RoHS/WEEE evidence. Importer/manufacturer responsibilities must be explicit.
