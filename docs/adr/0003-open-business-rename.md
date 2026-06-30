# ADR-0003: Publish as cloud-itonami-6310 open business

## Status

Accepted.

## Context

The project started as `gftd-talent-actor`: an OSS actor design that replaces a
closed HR SaaS with a governed, auditable, self-owned implementation.

itonami.cloud needs this to be more than a code repository. It should be a
forkable business model that operators can deploy, sell, support and improve.
The repository name should follow the `cloud-itonami-{ISIC Rev.5 id}` pattern.

## Decision

Rename the public repository to `cloud-itonami-6310`.

Use ISIC Rev.5 6310 as the primary identifier because the business activity is
operating computing infrastructure, data processing, hosting and related
services for a SaaS replacement. The served domain remains HR and talent
management.

Publish the repository as an OSS open business with:

- AGPL-3.0-or-later code license
- business model documentation
- operator guide
- contribution, security and governance files
- certification expectations for itonami.cloud operators

## Consequences

- `gftd-talent-actor` remains a historical implementation name.
- itonami.cloud can list the project as a reusable business blueprint.
- anyone can fork and self-operate the code.
- certified operator status remains separate from the right to fork.
- production handling of HR data must require security and audit controls.
