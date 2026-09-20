# 03. Modules and Bounded Contexts

> **Reconciliation notice (2026-09-19):** the diagram below is the **original Phase 0 proposal**. It is superseded in part by the accepted dependency direction in [MODULES.md](../MODULES.md) section 2a and the diagram at the end of this file: durable publication is technical `messaging` infrastructure (not owned by `integrations`); `lifecycle` and `operations` are deferred; Phase 1 builds `access`, `audit` and `workforce` only.


Allowed dependencies between modules. Solid arrow = calls the target's public API. Dashed arrow = reacts to published events (no code dependency). The graph is acyclic.

```mermaid
flowchart TB
  kernel["Shared kernel (tiny, used by all):<br/>ids, Money, Country, EffectiveRange,<br/>CorrelationContext, TenantId"]

  access["access<br/>tenants, authn/z, permission x scope"]
  audit["audit<br/>append-only change and read records"]

  workforce["workforce<br/>Person, Employment, Assignment,<br/>Org Unit, Legal Entity<br/>ownership + provenance"]
  countrypolicy["countrypolicy<br/>Legal Concept Registry<br/>policy packs (later)"]
  lifecycle["lifecycle<br/>hire, change, terminate<br/>state machines"]
  integrations["integrations<br/>connectors, outbox, delivery,<br/>sync, simulator, lineage capture"]
  operations["operations<br/>catalog, health, jobs, incidents,<br/>SLOs, event explorer"]

  lifecycle --> workforce
  lifecycle --> countrypolicy
  countrypolicy -->|"read"| workforce
  integrations --> workforce
  operations -->|"read"| integrations
  operations -->|"read"| audit

  workforce -.->|"published events"| integrations
  lifecycle -.->|"published events"| integrations

  workforce --> access
  workforce --> audit
  lifecycle --> access
  lifecycle --> audit
  countrypolicy --> access
  integrations --> access
  integrations --> audit
  operations --> access

  later["Deferred: compensation, timeandleave"]:::deferredStyle
  later -.-> workforce
  later -.-> countrypolicy

  classDef deferredStyle fill:#f5f5f5,stroke:#616161,stroke-dasharray: 4 3,color:#1b1b1b
```

**How to read this.** `workforce` never depends on `integrations`, `lifecycle`, or `countrypolicy`; it emits events and they react. `access` and `audit` depend on nothing but the shared kernel. Modules reference each other by ID only (no cross-module foreign
keys or joins), which is what keeps extraction possible. `integrations` is the first extraction candidate. Full specification: [MODULES.md](../MODULES.md).

Authorization without a cycle: `access` evaluates grants; the owning module supplies the "is this record in scope?" check ([ADR-0014](../adr/0014-authorization.md)).

## Accepted dependency direction (2026-09-19)

Phase 1 packages are solid; later packages are dashed. `messaging` is technical infrastructure and depends on no domain module. `integrations` consumes published events and does not own the infrastructure that domain modules publish through.

```mermaid
flowchart TB
  shared["shared (kernel): TenantId, error types"]
  platform["platform (technical): problem handling, request context,<br/>logging, idempotency store, clock"]

  access["access: tenants, principals, permissions"]
  audit["audit: append-only change history"]
  workforce["workforce: Legal Entity, Org Unit,<br/>Person, Employment, Assignment"]

  messaging["messaging (technical, later):<br/>durable publication and delivery state"]
  integrations["integrations (later):<br/>consumes published events"]
  lifecycle["lifecycle (deferred)"]
  countrypolicy["countrypolicy (deferred runtime)"]
  operations["operations (deferred read plane)"]

  platform --> shared
  access --> platform
  audit --> platform
  workforce --> platform
  access --> audit
  workforce --> access
  workforce --> audit

  workforce -.->|"publishes through abstraction"| messaging
  messaging -.-> platform
  integrations -.->|"consumes"| messaging
  integrations -.-> workforce
  lifecycle -.-> workforce
  countrypolicy -.-> access
  operations -.-> integrations

  classDef later fill:#f5f5f5,stroke:#616161,stroke-dasharray: 4 3,color:#1b1b1b
  class messaging,integrations,lifecycle,countrypolicy,operations later
```

`audit` never depends on `access` or `workforce`. Solid arrows are Phase 1 code dependencies.
