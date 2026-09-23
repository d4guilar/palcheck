# Enterprise Systems (the Operations Console)

> **Phase 2 current product (2026-09-22):** Enterprise Systems now opens a quiet systems list, Workday Simulation detail with authorized Run sync and paginated history, and a run detail with actual counts and inspectable failures. Employment/Assignment source disclosure links to its run. These surfaces live in `integrations`, not an operations module. Simulation mode and last synchronization are shown; no live health measurement is claimed. The broader operations console below remains a Phase 0 proposal. See the [Phase 2 plan](PHASE_2_IMPLEMENTATION_PLAN.md) and [review](PHASE_2_CODEX_REVIEW.md).

> **Reconciliation notice (2026-09-19):** Phase 1 contains **no** operations console. [Review §14](CODEX_ARCHITECTURE_REVIEW.md#14-enterprise-systems-feature-prioritization) classification stands as the plan: audit activity is the only MVP capability (Phase 1); catalog, health, jobs, event explorer, retry and lineage belong to the provisioning and overlay milestones; topology, incidents, SLOs and replay are future. Baseline text is retained.

Status: Phase 0 draft, **Proposed** ([ADR-0016](adr/0016-operations-model.md)) · Output of assignment step 9. Proposed future owning module: `operations` ([MODULES.md](MODULES.md) 3.7).
Diagram: [08 operations topology](diagrams/08-ops-topology.md). Related: [INTEGRATION_ARCHITECTURE.md](INTEGRATION_ARCHITECTURE.md), [DATA_OWNERSHIP_AND_LINEAGE.md](DATA_OWNERSHIP_AND_LINEAGE.md).

## 1. What it is

A console where technical operators see how PalCheck's connected systems behave and can act when something goes wrong. It demonstrates how an internal technology organization
runs interconnected systems: catalog, health, topology, jobs, incidents, events, lineage, replay, and reliability targets.

It is **not** a clone of any company's internal tooling and uses no proprietary terminology. It is not a replacement for a metrics/tracing stack or for an incident-management
product; it is the *domain-aware* view that generic tools do not give (this event, for this employee, to this system, and why).

## 2. Design principles

1. **Derived from real flows.** Every number and status is computed from delivery attempts, sync runs, jobs, and probes that actually happened. If a tile cannot be backed by data,
   it is not built. No decorative metrics.
2. **One source of truth per fact.** The systems catalog is derived from the connector registry, not maintained in parallel (avoids drift; [PHASE0_CRITIQUE.md](PHASE0_CRITIQUE.md) B-04).
3. **Unknown is a real state.** Absence of data shows as *Unknown*, never *Healthy*.
4. **Explain, don't just display.** Health and incidents show *why* ("3 of the last 10 attempts failed"), with links to the underlying events.
5. **Safe actions.** Retry, replay, and reprocess are separate, permissioned, idempotency-aware, and audited ([INTEGRATION_ARCHITECTURE.md](INTEGRATION_ARCHITECTURE.md) 6.4).
6. **Simulated is labelled.** Simulated systems and injected faults are unmistakably marked.
7. **Not ITIL.** Just enough process to show operational thinking.
8. **Accessible.** Every visual (including topology) has a text/table equivalent ([UI_SYSTEM.md](UI_SYSTEM.md)).

## 3. Capabilities

### 3.1 Systems catalog

Track every system PalCheck interacts with. Fields:

| Field | Source |
|---|---|
| `name`, `type`/`category` (HRIS, Payroll, Identity, IT Operations, Benefits, Notifications), `authenticationType`, `capabilities`, `lastSuccessfulInteraction`, `environment`, `simulated` | derived from the connector registry and delivery/sync records |
| `owner`, `description`, `dependencies` | authored in `operations` |
| `health` | computed (3.2) |

Phase 1 catalog: PalCheck itself, the identity provider (authentication), and three simulated downstream systems (Payroll, Identity Provider, Equipment). Phase 2 adds the reference HRIS.
The brief's examples (a payroll system in a degraded state, an HRIS synced minutes ago) are illustrations of what the catalog shows, not fixed content.

### 3.2 Health

States: **Healthy · Degraded · Offline · Unknown**.

Computed from signals over a recent window: delivery success ratio, latency relative to a target, dead-letter count and age, most recent health-probe result, and staleness of the last
successful interaction. Rules:

- Thresholds and windows are **configuration** chosen and recorded in Phase 1 (illustratively: "degraded when a meaningful share of recent attempts fail"); this document asserts no numbers.
- **Unknown** when there is too little data to judge (new system, no traffic, probe not yet run). Never default to Healthy.
- **Hysteresis** so a system does not flap between states on a single failure.
- Every state carries **reasons** (which signals crossed which thresholds), shown in the UI and API.

Integration health metrics exposed per system: success rate, failure rate, latency (median and tail), throughput, retry count, dead-letter count, last successful interaction/sync, current health.

### 3.3 Integration topology

A graph *derived* from the catalog (nodes), declared dependencies, and observed flows (edges labelled with event types and direction), colored/badged by health. Shape (from the brief):

```
Workday-shaped reference HRIS (Phase 2)
        |
     PalCheck
        |
        +--> Payroll (simulated)
        +--> Identity Provider (simulated)
        +--> Equipment (simulated)
        +--> Benefits (later)
```

Accessibility: the same information is available as a sortable table (system, direction, event types, health, last interaction). The visual is an enhancement, not the only route. Diagram: [08](diagrams/08-ops-topology.md).

### 3.4 Scheduled jobs

Model recurring background work as `JobDefinition` (name, schedule, owner module, description) and `JobRun` (started, finished, duration, records processed, created, updated, failed, status, error summary).
Display: schedule, last execution, next execution, duration, counts, status.

| Job | Purpose | Phase |
|---|---|---|
| Effective-date transitions | Make future-dated changes (e.g., `EmploymentStarted`) effective and emit events | 1 |
| Outbox retention and cleanup | Enforce retention on delivered outbox and delivery-attempt records | 1 |
| Worker synchronization | Delta/full pull from the reference HRIS | 2 |
| Organization synchronization | Pull org structure | 2 |
| Audit archival | Move aged audit records per retention policy | later |
| Policy evaluation | Re-evaluate assessments after registry change | later, needs verified concepts |

The outbox dispatcher is continuous, not a scheduled job, but appears in the catalog as a worker with its own health (outbox lag).
Rules: single-node execution enforced by a lock so a second instance does not double-run a job; runs are idempotent; a missed run is detected and shown rather than silently skipped.

### 3.5 Incidents (lightweight)

Model, from the brief: identifier (`INC-nnnnn`), title, **severity** (SEV-1 to SEV-4), **status** (Investigating · Identified · Monitoring · Resolved), affected system(s), **impact** (plain language), **timeline**
(typed, timestamped entries: threshold exceeded, incident created, acknowledged, root cause identified, service restored), linked evidence (events, delivery attempts, health snapshots), and a short post-incident note.

Behavior: `operations` may **suggest** an incident when health stays Degraded/Offline beyond a configured duration; a human opens and acknowledges it. There is no problem/change management, no SLAs,
no on-call scheduling. Incident lifecycle events are internal (`IncidentOpened`, `IncidentResolved`); notification delivery is a later downstream integration.

### 3.6 Reliability targets (SLOs)

Concepts: an **SLI** is a measured ratio or latency; an **SLO** is a target for an SLI over a window; the **error budget** is the allowed shortfall. Health checks feed availability SLIs.

Rules (to keep this from becoming decoration; [PHASE0_CRITIQUE.md](PHASE0_CRITIQUE.md) O-07):

1. An SLO is published **only for an SLI that is actually measured**.
2. An SLO must be able to change a decision (e.g., suggest an incident, block a mapping rollout while budget is exhausted).
3. Targets are stated with their measurement method and window.

Illustration from the brief: for worker synchronization, an availability target and a latency target ("most requests under a stated bound") compared against current measured values. The specific targets are set
when the sync exists (Phase 2), from measurement, not from aspiration. Phase 1 publishes none.

### 3.7 Event explorer

For technical users to inspect: event ID, type, schema version, correlation and causation IDs, producer, consumers, status, attempts, timestamps, payload **metadata** (which fields are present, sizes), and a
redacted payload. Search by correlation ID, event ID, type, aggregate, status, and time window. Detail view shows the delivery timeline per consumer.

Controls: unredacted payload requires a distinct, audited permission (`VIEW_EVENT_PAYLOAD`); tenant scoping is absolute; bulk export is off by default. *Do not expose sensitive fields casually* (brief).

### 3.8 Data lineage view

Field lineage and flow lineage as defined in [DATA_OWNERSHIP_AND_LINEAGE.md](DATA_OWNERSHIP_AND_LINEAGE.md) section 4: source, transformation, destination, last sync, downstream consumers, correlation ID. Metadata by default; values only under
normal authorization and audit.

### 3.9 Retry and replay

| Action | Where | Preconditions | Safety |
|---|---|---|---|
| **Retry** a failed delivery | Delivery attempt detail | Destination idempotency mode allows it (`KEY` or resolved `LOOKUP`); state is `FAILED`/`DEAD_LETTERED` | Same idempotency key; result recorded; audited; `RETRY_INTEGRATION` |
| **Replay** a historical event | Event detail | Elevated permission; dry-run shows affected consumers | Flagged `replay=true`; consumers idempotent; audited |
| **Reprocess** with a corrected mapping | Sync run detail | Elevated permission; new mapping version | Ownership rules still apply; preview of changes; audited |

Example from the brief: integration attempt `INT-192851` to the identity provider failed with HTTP 503 after three attempts; the operator sees the error class (transient), the idempotency key, and a **Retry** action. For a
`NONE`-mode destination whose last outcome was *unknown*, the action becomes **Confirm with system owner**, not a blind retry ([INTEGRATION_ARCHITECTURE.md](INTEGRATION_ARCHITECTURE.md) 6.3).

### 3.10 Simulator control panel

Choose a scenario (which system fails, how, when), a deterministic seed, and reset state. Administrator-only, enabled only in demo/dev profiles, and every simulated system shows a *Simulated* label ([INTEGRATION_ARCHITECTURE.md](INTEGRATION_ARCHITECTURE.md) section 7).

## 4. What ships when

| Capability | Phase 1 | Phase 2 | Later |
|---|---|---|---|
| Catalog | Minimal list from connector registry | + reference HRIS | Owner/description editing, categories |
| Health | Basic computed state with reasons | + sync health | Probe scheduling, hysteresis tuning |
| Delivery attempts, retry | Yes (the core resilience demo) | | |
| Topology | Table first; simple diagram | + HRIS edge | Richer views |
| Jobs | Effective-date transitions, retention cleanup (visible list) | + sync jobs | Full history and manual trigger |
| Incidents | | Manual incidents | Suggested incidents from health |
| SLOs | | First SLO for worker sync | Error-budget-driven decisions |
| Event explorer | Basic search and metadata | + payload redaction controls | Replay |
| Lineage | | Field lineage for synced fields | Flow lineage views |
| Replay / reprocess | | | Yes |

Rationale: build the *smallest operations surface that makes resilience visible*, after the first vertical slice, and grow it from real data ([ADR-0016](adr/0016-operations-model.md)).

## 5. Data and performance

`operations` reads from `integrations` and `audit` through read APIs and may keep its own read models. Pagination and time-window limits are mandatory on every list. Retention of attempt and event history is bounded and configurable;
long-term aggregates (success ratios, latency histograms) are stored separately from raw attempts so charts do not need raw rows.

## 6. Security

Everything here is **tenant-scoped** and permission-gated: `VIEW_INTEGRATIONS`, `RETRY_INTEGRATION`, `MANAGE_SYSTEMS`, and proposed `VIEW_EVENT_PAYLOAD`, `REPLAY_EVENTS`, `RUN_SIMULATOR_SCENARIOS`, `VIEW_LINEAGE`
([SECURITY.md](SECURITY.md)). Every action is audited with actor, target, reason, and correlation ID. Payloads are redacted by default. Cross-tenant views are not part of the product.

## 7. Failure modes

| Failure | Effect | Response |
|---|---|---|
| Health computed on stale data | Misleading status | Staleness makes health `Unknown`, shown as such |
| Operator retries an ambiguous outcome | Duplicate side effect | Idempotency mode gates the action; halt-and-confirm for `NONE` |
| Console slow at volume | Unusable during an incident | Read models, pagination, window limits, indexes designed with the queries |
| Console itself unavailable | Blind during an incident | Console is read-mostly; core flow does not depend on it; logs and metrics remain the fallback |
| Catalog drifts from reality | Mistrust | Derived from connector registry; authored fields limited to what cannot be derived |

## 8. Testing

Health computation with table-driven signal sets (including Unknown and hysteresis); job locking under two concurrent instances; retry/replay permission and idempotency-mode gates; redaction (canary values never appear); tenant isolation on every list and detail; accessibility of the topology's table equivalent.

## 9. Alternatives considered

| Alternative | Why not |
|---|---|
| Rely only on Grafana/Prometheus dashboards | Excellent for infrastructure, blind to domain meaning (which employee, which event, why). Grafana can *complement* this later. |
| Adopt an incident-management or service-catalog product | Product scope, cost, and clone risk; the goal is to demonstrate operational thinking, not to integrate a vendor |
| Build all of it first | It would outweigh the core product and be faked without real flows |
| A generic "admin" screen with raw tables | Answers none of "what happened, why, did it recover?" |

## 10. Open questions

- **EQ-01** Health thresholds and windows: choose after the first real delivery data (Phase 1), not before.
- **EQ-02** Whether incidents warrant their own module later; today they live in `operations`.
- **EQ-03** Whether topology needs a graph library or can stay a constrained layout. Recommendation: table plus a small fixed-layout diagram first.
- **EQ-04** How much history the explorer retains by default (S-01 in the critique).
