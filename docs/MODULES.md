# Modules and Bounded Contexts

> **Implemented Phase 2 subset (2026-09-22):** `integrations` now owns the in-process simulation, adapter mappings, sync runs/items, lineage API and Systems/Sync UI. It consumes `workforce.api.WorkforceImportPort`; workforce never depends on integrations. The allowed neighbours are `shared`, `platform`, `access.api`, `audit.api`, `workforce.api`, enforced by ArchUnit. `messaging` and `operations` remain absent. Their relationships below describe future capabilities, not current dependencies. See the [Phase 2 plan](PHASE_2_IMPLEMENTATION_PLAN.md) and [review](PHASE_2_CODEX_REVIEW.md).

> **Reconciliation notice (2026-09-19, Daniel-approved refinements 1, 2 and 6):** section 2a below is the **accepted dependency direction** and supersedes the dependency matrix in section 2 wherever they differ. Outbox ownership moves from `integrations` to a technical `messaging` capability; `lifecycle` and `operations` are deferred until they own process data; Phase 1 builds `access`, `audit`, `workforce` plus the technical `platform` and `shared` packages. See [ADR-0018](adr/0018-module-transactions-and-persistence.md) (accepted in part) and [Review §5](CODEX_ARCHITECTURE_REVIEW.md#5-backend-feasibility). Sections 2 and 3 are retained as the original Phase 0 proposal.

Status: Phase 0 draft, **Proposed** ([ADR-0001](adr/0001-modular-monolith.md)) · Output of assignment step 4.

Diagram: [diagrams/03-module-map.md](diagrams/03-module-map.md). Rules that apply to every module: [ARCHITECTURE.md](ARCHITECTURE.md) section 2.

## 1. From the brief's 13 to 7 (+2 deferred)

| Brief module | Now | Why |
|---|---|---|
| People, Organizations, Employment | **`workforce`** | Change together, share transactions and invariants (an Assignment needs a Person, an Employment, an Org Unit). Three modules would mean three APIs and constant cross-calls. Person, Org Unit, and Legal Entity remain distinct *aggregates* inside. |
| Lifecycle, Workflow | **`lifecycle`** | Hire, change, and terminate are three known processes: explicit state machines with tasks, not a generic engine. |
| Compensation | *deferred* | Not needed to prove the thesis. Costa Rica statutory compensation concepts live in the registry until then. |
| TimeAndLeave | *deferred* | Same; where most statutory leave concepts will eventually land. |
| CountryPolicies | **`countrypolicy`** | Two parts: (a) Legal Concept Registry, reference data with no behavior; (b) policy packs, behavior behind a capability SPI, later. |
| Integrations | **`integrations`** | Adds: connector port and adapters, sync runs, mappings, outbox, delivery attempts, idempotency, provenance/lineage capture, simulator. |
| Identity | **`access`** + a target in `integrations` | The brief's "Identity" meant two different things: PalCheck's own authn/authz (`access`), and provisioning accounts in an external IdP (a *downstream system* behind the connector port). |
| Notifications | a downstream system in `integrations` | A consumer of events, not a domain. |
| Audit | **`audit`** | Cross-cutting, append-only, owns nothing but its own store. |
| EnterpriseSystems | **`operations`** | A read-mostly plane over `integrations` and `audit` plus a few small aggregates (incidents, job definitions, SLO definitions). Not a business domain. |

## 2a. Accepted dependency direction (2026-09-19; supersedes section 2 where they differ)

Accepted under Daniel's refinements 1 and 2 ([ADR-0018](adr/0018-module-transactions-and-persistence.md)). Package names are relative to `com.palcheck`. "Technical" packages contain no business rules and never depend on a domain module.

| Package | Kind | Phase | May depend on | Owns (schema / data) |
|---|---|---|---|---|
| `shared` | Shared kernel (tiny) | 1 | nothing | nothing (types only: `TenantId`, error/exception types, field violations) |
| `platform` | Technical infrastructure | 1 | `shared` | schema `platform`: `idempotency_record`; HTTP problem handling, request/correlation filters, logging support, clock |
| `access` | Domain module | 1 | `shared`, `platform`, `audit.api` | schema `access`: tenant, principal, principal role |
| `audit` | Domain module | 1 | `shared`, `platform` | schema `audit`: `audit_entry` (append-only) |
| `workforce` | Domain module | 1 | `shared`, `platform`, `access.api`, `audit.api` | schema `workforce`: legal entity, org unit, person, employment, assignment |
| `messaging` | **Technical** infrastructure | provisioning milestone | `shared`, `platform` | durable publication and delivery state. **Not owned by `integrations`.** |
| `integrations` | Domain module | Phase 2 inbound slice implemented | `shared`, `platform`, `access.api`, `audit.api`, `workforce.api`; `messaging.api` only when publication exists later | adapter mappings, sync state, lineage and Systems/Sync UI; destination operations remain future |
| `lifecycle` | Domain module | deferred | `workforce.api`, `access.api`, `audit.api`, `messaging.api` | only when approval/process state exists |
| `countrypolicy` | Domain module | deferred (no behavior in Phase 1) | `shared`, `platform`, `access.api`, `audit.api` | reviewed content projection, later assessments |
| `operations` | Read plane | deferred | `access.api`, `audit.api`, `integrations.api`, `messaging` metadata queries | authored catalog metadata, incidents |

Rules that follow (enforced by ArchUnit tests from Phase 1):

1. The graph is acyclic. `workforce` never depends on `integrations`, `lifecycle`, `countrypolicy`, `operations` or `messaging` internals; it publishes through the `messaging.api` publication abstraction when a first asynchronous effect exists.
2. `audit` never depends on `access` or `workforce`. Authorized audit reads are composed by the calling application facade (for example a `workforce` service checks permission and record existence, then calls `audit.api`).
3. A module exposes only its `api` package to other modules; `domain`, `application`, `infrastructure` and `web` are internal to the module. Modules never read each other's tables or repositories.
4. One application command owns one local database transaction. `audit` recording joins the caller's transaction (`Propagation.MANDATORY`); durable publication will do the same. No network I/O inside a business transaction.
5. **Consuming a typed event is a compile-time dependency.** `integrations` may depend on `workforce.api` event contracts because `workforce` does not depend on `integrations`.
6. `integrations` may consume published events; it must **not** own the infrastructure that domain modules need to publish them.

## 2. Dependency rules (original Phase 0 proposal; superseded in part by section 2a)

```
   operations ---------(read)---------> integrations ------> workforce
                                              ^                  ^
                                              |                  |
                                   (consumes published     lifecycle ---> countrypolicy --(read)--> workforce
                                    events; no code                 \
                                    dependency)                      +---------------------------> workforce
   lifecycle and workforce emit events; integrations reacts.

   access, audit: used by every module through small APIs; they depend on nothing but the shared kernel.
```

Allowed dependencies (row depends on column via its public API):

| | shared-kernel | access | audit | workforce | countrypolicy | lifecycle | integrations | operations |
|---|---|---|---|---|---|---|---|---|
| **access** | yes | | | | | | | |
| **audit** | yes | | | | | | | |
| **workforce** | yes | yes | yes | | | | | |
| **countrypolicy** | yes | yes | yes | read | | | | |
| **lifecycle** | yes | yes | yes | yes | yes | | | |
| **integrations** | yes | yes | yes | yes | | | | |
| **operations** | yes | yes | read | | | | read | |

Everything else is forbidden. Notes:

- `integrations` learns about hires and terminations by **consuming published events**, not by depending on `lifecycle`.
- `workforce` never depends on `integrations`, `lifecycle`, or `countrypolicy`. It emits events; they react.
- **Authorization without a cycle.** `access` supplies the authenticated principal, tenant, and permissions with *scope types*
  (self, direct reports, org unit, legal entity, tenant). Whether a target record is *in scope* depends on data owned by `workforce`,
  so the owning module supplies the scope check when it asks `access` to authorize. `access` never calls `workforce`.
  See [ADR-0014](adr/0014-authorization.md).

## 3. Module specifications

Ownership of data is conceptual (tables are not designed in Phase 0). "Published" events are versioned integration events; "internal"
events stay inside the monolith and are not contracts ([ADR-0006](adr/0006-event-driven-integration.md)).

### 3.1 `workforce`

- **Responsibility.** The system of record for who works where: Person, Employment, Assignment, Org Unit, Legal Entity, Location,
  worker type, status history, identifiers, local supplemental data. Enforces domain invariants and effective dating. Applies
  ownership policy on every write.
- **Owned data.** Person, Employment, Assignment, Org Unit tree, Legal Entity, Location, field ownership policy references, value provenance.
- **Depends on.** shared-kernel, `access`, `audit`.
- **Produces (internal).** `PersonRegistered`, `EmploymentCreated`, `EmploymentStarted`, `AssignmentChanged`, `EmploymentEnded`.
  Published projections of these (`JobChanged`, `EmploymentStarted`) are emitted through the outbox.
- **Consumes.** none.
- **Failure modes.** Overlapping or gapped effective ranges; writes to an externally owned field; scheduled effective transitions
  missed on restart. **Security.** Highest PII density; row-level tenant isolation; field-level protection for identifiers.
- **Extractability.** *Last.* It is the transactional heart. Stays internal.
- **Phase.** 1.

### 3.2 `lifecycle`

- **Responsibility.** Run the hire, job-change, and termination *processes* as explicit state machines with tasks and step status.
  Coordinates, but does not own, workforce state. Asks `countrypolicy` what local requirements apply to a hire. Emits the business
  facts `EmployeeHired` and `EmployeeTerminated`.
- **Owned data.** Process instances, tasks, step results, process history.
- **Depends on.** shared-kernel, `access`, `audit`, `workforce`, `countrypolicy`.
- **Produces (published).** `EmployeeHired`, `EmployeeTerminated`. **Consumes.** none (Phase 1).
- **Failure modes.** Process stuck in a step; partially completed hire (workforce written, event not). Mitigation: workforce write and
  outbox row in one transaction; processes are resumable and idempotent per step.
- **Extractability.** Low value in extracting; stays internal.
- **Phase.** 1 (hire only). Change and termination processes follow.

### 3.3 `countrypolicy`

- **Responsibility.** (a) **Legal Concept Registry**: concepts, versions, sources, mappings, behavior bindings, verification status.
  Reference data; **no behavior**. (b) **Policy packs** (later): country-specific behavior behind a capability-based SPI, producing
  annotations and derived facts, never mutating normalized data. Phase 1 has (a) only, read-only.
- **Owned data.** Concept, ConceptVersion, LegalSource, ConceptMapping, BehaviorBinding (all global, no tenant), plus per-tenant
  policy configuration and annotations *later*.
- **Depends on.** shared-kernel, `access`, `audit`, `workforce` (read).
- **Produces.** none in Phase 1. **Consumes.** `EmployeeHired` and similar *later*, for enrichment.
- **Failure modes.** Registry content out of sync with verified sources; a concept implemented without `VERIFIED` status. Mitigation:
  content lives in reviewed files; a binding to behavior requires verified status and a source version.
- **Extractability.** Possible (independent release cadence, content team), not planned.
- **Phase.** 1 (registry read-only, Costa Rica concepts at `DOCUMENTED`).

### 3.4 `integrations`

- **Responsibility.** Everything between PalCheck and other systems: connector port and adapters (HRIS inbound, downstream outbound),
  raw landing, versioned mappings, sync runs, reconciliation, transactional outbox and dispatcher, delivery attempts with retry/backoff/dead
  letter, idempotency keys, provenance and lineage capture, and the **simulator** (fault-injectable downstream systems). Provider models never leave
  its adapter packages.
- **Owned data.** Connector registry and configuration references, mapping versions, sync runs and per-record results, raw landing (redacted,
  retention-limited), outbox, delivery attempts, dead letters, idempotency records, lineage links, external identity links.
- **Depends on.** shared-kernel, `access`, `audit`, `workforce` (inbound writes and outbound payload reads).
- **Produces (published).** `ProvisioningRequested`, `ProvisioningCompleted`, `IntegrationFailed`, `ExternalWorkerUpdated`, `SyncRunCompleted`.
- **Consumes.** Published events from `lifecycle` and `workforce` via the outbox (`EmployeeHired`, `JobChanged`, `EmployeeTerminated`).
- **Failure modes.** See [INTEGRATION_ARCHITECTURE.md](INTEGRATION_ARCHITECTURE.md) and [ARCHITECTURE.md](ARCHITECTURE.md) section 9.
  Central risks: duplicate side effects on retry; sync writing an externally owned field; secrets leakage in payloads.
- **Extractability.** **First candidate.** I/O-bound, retry-heavy, different scaling and failure profile. Safe only if nothing else joins its tables.
- **Phase.** 1 (outbox, dispatcher, simulated Payroll/Identity/Equipment, delivery attempts, retry). Phase 2 adds the Workday-shaped connector,
  raw landing, mappings, sync runs, reconciliation.

### 3.5 `access`

- **Responsibility.** Tenants and their configuration; integration with the OIDC identity provider (token validation, principal mapping);
  roles as named bundles of **permissions with scope types**; the authorization decision API; access-review data.
- **Owned data.** Tenant, principal-to-tenant mapping, role definitions, role assignments, permission grants.
- **Depends on.** shared-kernel only. **Produces.** none published (changes go to `audit`). **Consumes.** none.
- **Failure modes.** Missing tenant context (must fail closed); permission drift; overly broad admin roles. **Security.** The boundary that
  protects every other module.
- **Extractability.** Unlikely to be extracted; may later be replaced by an external authorization service.
- **Phase.** 1 (dev-grade OIDC, tenant, a small permission set).

### 3.6 `audit`

- **Responsibility.** Append-oriented record of important changes: actor, timestamp, previous and new value, reason, workflow,
  correlation ID, source system; and, for sensitive fields, *read* access records. Designed so personal values can be pseudonymized or
  crypto-shredded without destroying the chain of custody.
- **Owned data.** Audit records, sensitive-read records.
- **Depends on.** shared-kernel. **Produces.** none. **Consumes.** an explicit `AuditRecorder` API called by other modules, plus internal
  change records. It is *not* fed by re-deriving state from events.
- **Failure modes.** Audit write fails after a business commit. Decision: audit is written **in the same transaction** as the change it
  describes (for changes owned by the calling module's database).
- **Extractability.** Extractable for compliance isolation later.
- **Phase.** 1.

### 3.7 `operations`

- **Responsibility.** The read-mostly plane for technical operators: systems catalog (derived from the connector registry, plus owner,
  description, category), health computation, integration topology, scheduled-job definitions and runs, lightweight incidents, SLO
  definitions and SLIs, event explorer, lineage views, retry/replay orchestration UI over `integrations`. See
  [ENTERPRISE_SYSTEMS.md](ENTERPRISE_SYSTEMS.md).
- **Owned data.** System metadata not derivable elsewhere, incidents and timelines, job definitions and run records, SLO definitions.
- **Depends on.** shared-kernel, `access`, `audit` (read), `integrations` (read).
- **Produces (internal).** `IncidentOpened`, `IncidentResolved`. **Consumes.** `IntegrationFailed`, `SyncRunCompleted`, delivery data.
- **Failure modes.** Catalog drifts from the connector registry (avoided by deriving it); health flapping; an operator's replay duplicating
  a side effect (prevented by idempotency in `integrations`).
- **Extractability.** Could become a separate operations app; not planned.
- **Phase.** 1 minimal (connector registry list, delivery attempts, basic health). The rest after the first vertical slice.

## 4. Deferred modules

- **`compensation`.** `Money` elements over time. Records and orchestrates; **never computes pay**. Depends on `workforce`, `countrypolicy`.
- **`timeandleave`.** Requests, approvals, balances. Depends on `workforce`, `countrypolicy`; the largest home of statutory Costa Rica concepts.

Both must pass the second-instance rule before introducing shared abstractions.

## 5. Events at a glance

| Event | Kind | Producer | Consumers (Phase 1) |
|---|---|---|---|
| `PersonRegistered`, `EmploymentCreated`, `AssignmentChanged`, `EmploymentEnded` | internal | workforce | audit |
| `EmploymentStarted`, `JobChanged` | published | workforce | integrations |
| `EmployeeHired`, `EmployeeTerminated` | published | lifecycle | integrations |
| `ProvisioningRequested`, `ProvisioningCompleted` | published | integrations | operations |
| `IntegrationFailed`, `SyncRunCompleted` | published | integrations | operations |
| `IncidentOpened`, `IncidentResolved` | internal | operations | (notifications, later) |

## 6. Open questions

- **MQ-01** Is `EmploymentStarted`/`JobChanged` published by `workforce` or derived and published by `lifecycle`? Recommendation: `workforce`
  for state-effective facts, `lifecycle` for process facts. Codex to challenge.
- **MQ-02** Should `countrypolicy` be split into `legalregistry` (data) and `policypacks` (behavior)? Recommendation: keep one module until packs exist.
- **MQ-03** Does `operations` deserve to be a module before it has its own data? It owns incidents, jobs, and SLO definitions, which is thin.
  Alternative: fold into `integrations` for Phase 1 and split when it grows.
