# ADR-0003: AI data-center business lane

## Decision

Add an AI data-center lane to the existing ISIC 6310 vertical. It covers:

`lead → qualification → tax review → contract → GPU procurement → installation
→ commissioning → compute service → period settlement → retirement`.

The canonical implementation is `ai-datacenter.business`. It is a pure state
machine so the same rules can run in the operator console, edge worker and
audit replay.

Tax eligibility, electrical compliance and facility acceptance are external
evidence. The actor records their issuer and result; it never represents its
own output as tax, accounting or engineering certification. Contract signing,
GPU procurement, commissioning and retirement require human approval.

Revenue is measured per settlement period. No guaranteed return, future
utilisation or tax saving is encoded as fact.

## Boundary

This lane does not replace the existing talent-hosting product. Both are
products operated under ISIC 6310. The occupational execution and physical
safety controls live in the ISCO 1330 vertical.
