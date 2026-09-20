# ADR-0016: Enterprise Operations Model

**Status:** Proposed
**Date:** 2026-09-19

## Context

The brief wants an "Enterprise Systems" area: systems catalog, health, topology, scheduled jobs, incidents, event explorer, lineage, replay, and SLOs, to demonstrate how an internal technology organization operates interconnected systems, without cloning any company's tooling or
using proprietary terminology. It is roughly as large as the core product, and its value depends on being real: numbers backed by data, actions that are safe.

## Decision

1. **A module `operations`**: a read-mostly plane over `integrations` and `audit`, plus small owned aggregates (incident records, job definitions/runs, SLO definitions, catalog metadata not derivable elsewhere). It is presented in the product as the **Operations Console**.
2. **Derived from real flows.** No tile without underlying data. The **catalog is derived from the connector registry**; only owner, description, and category are authored.
3. **Health states** Healthy / Degraded / Offline / **Unknown**; Unknown when data is insufficient, never defaulting to Healthy; hysteresis; every state carries reasons; thresholds are configuration set from real data in Phase 1.
4. **SLOs only for measured SLIs**, and only if they can change a decision; none published in Phase 1.
5. **Incidents are lightweight**: severity, status, affected systems, impact, timeline, evidence links, a short post-incident note; systems may *suggest* incidents; humans open and acknowledge. No ITIL processes, SLAs, or on-call scheduling.
6. **Safe actions**: retry, replay, and reprocess are separate, permissioned, idempotency-aware, and audited ([INTEGRATION_ARCHITECTURE.md](../INTEGRATION_ARCHITECTURE.md) 6.4). Non-idempotent destinations halt for human confirmation.
7. **Event explorer and lineage are metadata-first**; unredacted payloads need a distinct, audited permission.
8. **The simulator is labelled** and its fault injection is admin-only and demo/dev-only.
9. **Every visual has a table equivalent.**
10. **Phased delivery** ([ENTERPRISE_SYSTEMS.md](../ENTERPRISE_SYSTEMS.md) section 4): Phase 1 ships only connector registry, delivery attempts with retry, and basic health; the rest follows the first vertical slice and grows from real data.
11. **No proprietary terminology** and no imitation of any specific internal tool.

## Alternatives Considered

| Alternative | Why not |
|---|---|
| **Only Prometheus/Grafana dashboards** | Excellent for infrastructure, blind to domain meaning (which employee, which event); can complement later |
| **Adopt an incident or service-catalog product** | Adds cost, lock-in, and clone risk; the goal is demonstrating operational thinking |
| **Build everything up front** | Would outweigh the core product and would be faked without real flows |
| **Fold operations into `integrations`** | Simple for Phase 1 and possibly right; kept separate because operations reads across `integrations` and `audit` and owns different data (open question MQ-03 in [MODULES.md](../MODULES.md)) |
| **A generic admin UI over raw tables** | Does not answer "what happened, why, did it recover?" |

## Consequences

**Positive**

- A credible, honest demonstration of operations: real numbers, safe actions, visible resilience.
- Small first version makes the resilience story visible without large scope.
- Derivation removes drift and duplicated data entry.

**Negative**

- Considerable long-term scope; risk of gold-plating.
- Read models and retention need design as volume grows.
- Health thresholds cannot be finalized until real data exists.
- A separate module for thin ownership may be premature (MQ-03).

## Reconsider When

- `operations` owns too little to justify a module (merge into `integrations`).
- Real operators need a mature tool (link out to Grafana or an incident product rather than growing this).
- SLOs never influence a decision (demote them to documentation).

## Interview line

"The operations console is derived from the actual delivery, sync, and job records, so every number is real and 'Unknown' is a legitimate state; and retry, replay, and reprocess are three different, permissioned, idempotency-aware operations."

## Related Decisions

[ADR-0001](0001-modular-monolith.md) · [ADR-0006](0006-event-driven-integration.md) · [ADR-0015](0015-data-lineage.md) · [ENTERPRISE_SYSTEMS.md](../ENTERPRISE_SYSTEMS.md)
