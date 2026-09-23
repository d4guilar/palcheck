# ADR-0024: Provider-Neutral HRIS Adapter Boundary (Phase 2)

**Status:** Proposed (implemented provisionally by Phase 2 under Daniel's direction; explicit confirmation requested)
**Date:** 2026-09-22

## Context

Daniel opened Phase 2 (external HRIS translation foundation) and asked for one complete vertical slice proving PalCheck can sit beside an external HRIS without coupling its core domain to that provider: a mock Workday-shaped adapter, a normalized contract, and a write path into the Phase 1 workforce model that a second provider could reuse without touching `workforce`. [ADR-0004](0004-hris-adapter-boundary.md) and [INTEGRATION_ARCHITECTURE.md](../INTEGRATION_ARCHITECTURE.md) already designed this boundary in Phase 0, for a later phase that assumed raw landing, policy enrichment and an outbox would exist alongside it. None of that exists yet, and Daniel's Phase 2 message explicitly excludes the outbox/broker/event half of that design. This ADR fixes the boundary as actually built: which module exists, what it may depend on, and how a provider's own vocabulary is kept out of `workforce`.

## Decision

1. **New module, `integrations`**, with the same internal shape as `workforce`: `domain`, `application`, `infrastructure`, `web`. Package `com.palcheck.integrations`. No `integrations.api` package is created yet, for the same reason `workforce.api` did not exist before this ADR: nothing outside the module consumes it (matching [ARCHITECTURE.md](../ARCHITECTURE.md) section 11's own rule — a package appears when something needs it, not in advance). It appears the day a later `operations` read plane, or a second consumer, needs one.
2. **`integrations` depends on `shared`, `platform`, `access.api`, `audit.api`, `workforce.api`** — the direction [MODULES.md](../MODULES.md) section 2a already accepted, minus `messaging.api` (not created; nothing is published in Phase 2). `integrations` never depends on `workforce.domain`, `.application`, `.infrastructure` or `.web`, and never reads a `workforce` table.
3. **`workforce` gains a new public package, `workforce.api`**, exposing exactly one write port, `WorkforceImportPort`, that `integrations` calls to import or update a worker. `workforce` still never depends on `integrations` (extends the existing `accessNeverDependsOnWorkforce`-style ArchUnit rule to `workforce` and `integrations`).
4. **The mock provider's own DTOs are package-private inside `integrations.application.provider`.** A dedicated `WorkdayMapper` (also package-private) is the only code that reads them, and it produces `NormalizedWorker` (public within `integrations.domain`) — the only shape anything else in `integrations` or a test sees. Provider DTO types and company/department/vocabulary mappings stay in the adapter. Opaque source-field labels may cross in `NormalizedWorker.sourceFields` and lineage responses for display; they never become workforce business vocabulary. `workforce.api` receives normalized values and a generic source display name for audit.
5. **`NormalizedWorker` is deliberately small** — `externalRef` (system, externalWorkerId), `person`, `employment`, `assignment`, `manager` (an external reference, resolved in a second pass — [ADR-0025](0025-ownership-lineage-and-sync-model-phase2.md)), opaque `sourceFields` metadata, and a `mappingReport` of anything the mapper could not map. It is an **integration/application boundary type**, not a second permanent domain model; nothing persists it as a whole, and it does not grow governed-field policy, effective dating or any workforce invariant of its own. `workforce.api`'s `WorkforceImportPort` has **its own, separate request shape**, built by `integrations.application` from a `NormalizedWorker` — the two contracts stay decoupled on purpose, at the cost of one small translation step.
6. **Adding a second provider (SAP SuccessFactors, Oracle HCM) should add an adapter package and a mapper and change nothing in `workforce` or the normalized contract**, exactly as [INTEGRATION_ARCHITECTURE.md](../INTEGRATION_ARCHITECTURE.md) section 9 describes. Neither is implemented or stubbed in Phase 2; only the boundary is proven future-compatible, with one simulated provider through it; a second provider has not yet been tested.
7. **Simulated is labelled everywhere it appears**: the system row's `mode` is `SIMULATION`, its display name is "Workday Simulation," and no UI, API response, log line or document implies Workday partnership, certification or API parity.

## Alternatives Considered

| Alternative | Assessment |
|---|---|
| Skip the port; let `integrations` call `workforce.application` services directly | Fails the accepted module rule (only `api` packages cross a module boundary) and would let ownership enforcement be bypassed from outside `workforce`, which is exactly the failure `DATA_OWNERSHIP_AND_LINEAGE.md` names first |
| Collapse adapter and mapper into one class since the provider is mocked, not a real HTTP boundary | Rejected: keeping them separate is what makes "add a second provider without touching `workforce`" actually demonstrated, not asserted; the cost is one extra small class |
| Reuse `NormalizedWorker` as `WorkforceImportPort`'s request type directly | Simpler, but couples `workforce`'s write contract to `integrations`' normalization choices; a future provider change could then force a `workforce.api` change. Rejected for a small extra translation step |
| Stand up empty `SAPSuccessFactorsConnector`/`OracleHCMConnector` classes now, to "show the shape" | Explicitly rejected by Daniel's brief; an empty adapter is dead code with no test behind it |
| Build a real HTTP mock server process for the provider | Deferred (Phase 0's IQ-03 recommendation): in-process behind the port is enough to prove the boundary; a real process is worth it only once network failure modes need exercising, which Phase 2 does not need for its DoD |

## Consequences

**Positive:** the boundary Daniel asked to prove is real, not asserted — the boundary is structured so a second provider can reuse `workforce.api`; `workforce`'s existing ownership/audit/concurrency guarantees apply to synced writes automatically, because they go through the same domain services a human edit does; ArchUnit makes the boundary a test-time fact, not a convention.

**Negative:** one more module, one more small translation layer (`NormalizedWorker` -> `WorkforceImportPort` request), and a public `workforce.api` package that Phase 1 did not need. The mock provider being in-process means Phase 2's tests do not exercise real network failure modes (timeout, partial response, TLS) — those remain a real mock HTTP process's job, not this ADR's.

**Operational burden:** none beyond what Phase 1 already has; no new port, secret, or process.

## Reconsider When

A second provider is actually built (validate the boundary against a real second shape — the "second-instance rule" from [INTEGRATION_ARCHITECTURE.md](../INTEGRATION_ARCHITECTURE.md) section 9); the mock provider needs to become a real out-of-process server to exercise network failure modes; `workforce.api` needs a second port (a read port, for example) and the single-port design should be revisited.

## Interview line

"Integrations talks to a provider-neutral port on the workforce module, never to its internals, so a mock Workday adapter and a future SAP adapter would both write through the exact same door, and workforce's ownership and audit rules apply automatically either way."

## Related Decisions

[ADR-0004](0004-hris-adapter-boundary.md) · [ADR-0018](0018-module-transactions-and-persistence.md) · [ADR-0022](0022-phase1-technical-baseline.md) · [ADR-0025](0025-ownership-lineage-and-sync-model-phase2.md) · [Phase 2 plan](../PHASE_2_IMPLEMENTATION_PLAN.md)

## Codex review (2026-09-22)

Provider-specific company/department code maps were moved out of generic orchestration into WorkdayMapper. Workforce no longer hardcodes a provider display name. ArchUnit and the full PostgreSQL suite pass. The in-process scenario selector is dev/test-only; it demonstrates a changed source through the real UI without adding an HTTP simulator. Q3 recommendation: **ACCEPT** the integrations-owned Systems/Sync surface; no operations module is needed. Status remains Proposed pending Daniel’s acceptance. See [PHASE_2_CODEX_REVIEW.md](../PHASE_2_CODEX_REVIEW.md).
