# Phase 2 Implementation Plan: External HRIS Translation Foundation

Status: **Phase 2 implemented, independently reviewed; awaiting Daniel’s acceptance** (opened by Daniel Aguilar, 2026-09-22) · Owner of decisions: Daniel · Authors: Claude Code (backend plan), Codex (review and completion) · Last updated: 2026-09-22
Architecture baseline: [ARCHITECTURE.md](ARCHITECTURE.md), [MODULES.md](MODULES.md) section 2a, [INTEGRATION_ARCHITECTURE.md](INTEGRATION_ARCHITECTURE.md), [DATA_OWNERSHIP_AND_LINEAGE.md](DATA_OWNERSHIP_AND_LINEAGE.md), [ADR-0024](adr/0024-hris-adapter-boundary-phase2.md), [ADR-0025](adr/0025-ownership-lineage-and-sync-model-phase2.md).

This plan is the contract for Phase 2, exactly as [PHASE_1_IMPLEMENTATION_PLAN.md](PHASE_1_IMPLEMENTATION_PLAN.md) was for Phase 1. Anything not in it is out of scope.

## 1. Objective and definition of done

**Objective.** Prove one architectural idea, narrowly and completely: PalCheck can sit beside an external HRIS, pull synthetic worker data through a provider-neutral adapter, normalize it into a small integration contract, write it into the Phase 1 workforce model through a single writer that enforces who owns each value, record source metadata for the six supported lineage fields, and make failures and partial success visible, with repeatable workforce upserts — without inventing distributed-systems machinery Phase 2 does not need.

**An authorized user can:** see the one configured external system (a labelled simulation); run or inspect a synchronization; find a newly imported synthetic worker in the People directory; see which of their fields came from that system and cannot be edited here; open the source of a field (system, source field, when, which sync); see sync history with created/updated/unchanged/failed counts; open a failed sync item and see why; start another full pull after reviewing the outcome.

**Definition of done:**

| # | Criterion | Evidence | Status |
|---|---|---|---|
| 1 | Provider-neutral HRIS boundary exists | `integrations` depends only on `shared, platform, access.api, audit.api, workforce.api` (ArchUnit rule `integrationsDependsOnlyOnItsAllowedNeighbours`); `workforce.api.WorkforceImportPort` is the only write path (`workforceNeverDependsOnIntegrations`, `nothingOutsideIntegrationsUsesItsInternals`); provider DTOs never leave `integrations.application.provider` (`providerVocabularyNeverLeavesTheAdapterPackage`). 8 new ArchUnit rules, all passing. | done |
| 2 | Workday simulation exists and is clearly labelled | `MockWorkdayProvider`/`WorkdayAdapter` produce a system row with `mode=SIMULATION`, `displayName="Workday Simulation"`; audit and logs name it that way; ADR-0024 decision 7. Systems list/detail, run detail and source disclosure label the simulation. | done |
| 3 | Synthetic external worker data can be imported | `SyncIT.aSyntheticWorkerIsFindableInThePeopleDirectoryAfterImport`: a synthetic worker created by a sync is found through the real `GET /api/people` search. | done |
| 4 | Repeated imports are idempotent | `SyncIT.repeatedUnchangedImportCreatesNothingNewAndWritesNoAudit`: two identical syncs produce exactly one Person/Employment (partial unique index on external identity). | done |
| 5 | Unchanged records remain unchanged | Same test: second sync's `unchangedCount=4`, `employment.updatedAt` unchanged, audit entry count unchanged. | done |
| 6 | Changed external records update the right fields | `SyncIT.aChangedSourceValueUpdatesOnlyThatFieldAndIsAudited`: a changed title updates only the assignment; status/type stay untouched. | done |
| 7 | Source-of-truth ownership is enforced | `SyncIT.theImportedEmploymentAndAssignmentAreMarkedAsExternallyOwned`; `workforce.application.ImportWorkerService` is the sole writer of owned records, reached only through `WorkforceImportPort`. | done |
| 8 | Externally owned fields cannot be silently edited locally | `SyncIT.aHumanCannotEditAnExternallyOwnedEmploymentOrAssignment`: `PATCH` on an owned Employment/Assignment returns `409 EXTERNALLY_OWNED` naming the owning system. | done |
| 9 | Sync runs and sync items are persisted | `integrations.sync_run`/`sync_item` tables (V7 migration); `SyncIT.runningSyncOverHttpEndToEndWorks` reads them back through the real API. | done |
| 10 | Partial sync failures are represented correctly | `SyncIT.firstSyncCreatesFourWorkersAndFailsTwoWithoutLosingEitherOutcome` and `...unresolvedManagerReference...`: failures interspersed with successes in one run; counts add up (`recordsSeen = created+updated+unchanged+failed` for pass 1; pass-2 manager failures are additional, documented in ADR-0025). | done |
| 11 | Basic reconciliation cases are visible | `SyncIT.aMissingLegalEntityMappingFailsCleanlyAndCreatesNothing`, `...anUnresolvedManagerReferenceFailsOnlyTheLink...`: specific reasons, no crash, no partial write. | done |
| 12 | Data lineage is inspectable | `SyncIT.fieldLineageNamesTheSourceSystemAndField`: lineage for `assignment.title` (and employment status/type, person names/email) resolves system, field and mapping version. | done |
| 13 | Audit distinguishes external synchronization from human edits | `SyncIT.aChangedSourceValueUpdatesOnlyThatFieldAndIsAudited` asserts `actor.type=SYSTEM`, `actor.displayName="Workday Simulation"`. | done |
| 14 | Tenant isolation covers integrations and lineage | `SyncIT.oneTenantCannotSeeAnotherTenantsSystemsSyncsOrLineage`: cross-tenant run id is `404`; cross-tenant lineage is empty, not an error. | done |
| 15 | Permissions cover integration actions | `SyncIT.onlyRunSyncCanTriggerASyncAndOnlyViewSystemsCanListThem`: `HR_VIEWER` gets `403` on both; `HR_ADMIN` succeeds. | done |
| 16 | UI remains accessible and Apple-inspired | Native lists/disclosures; keyboard flow; axe scans in light/dark and 320px layouts; no new UI dependency. | done |
| 17 | Tests prove critical behavior | 192 backend tests pass (72 unit/architecture; 120 integration including 20 `SyncIT` tests using real PostgreSQL), 114 frontend tests and 20 real-stack Chrome E2E tests; typecheck, lint and build pass. | done |
| 18 | No Kafka, microservices or legal execution introduced | Grep for legal terms and country-specific branching in the new code found none; no `messaging`/outbox/broker dependency added; `mvn verify` green with no new dependency in `pom.xml`. | done |
| 19 | Documentation matches implementation | Plan/ADRs, architecture, modules, domain, integration/lineage, systems, roadmap, README, security, UI and local-development docs reconciled; full evidence in PHASE_2_CODEX_REVIEW.md. | done |
| 20 | Repository states the adapter is simulated/reference-only | README, UI and ADR-0024 explicitly label the in-process synthetic simulation; no live connection or certification claim. | done |

Rows are updated to `done` as each step lands, exactly as the Phase 1 plan was.

## 2. Scope

**In:** one new module, `integrations` (adapter boundary, mock Workday provider, normalized contract, sync run/item, field lineage); one new public port on `workforce`, `workforce.api`, through which `integrations` writes; ownership marking on `Employment` and `Assignment` (not `Person` — see section 4.3); a small `field_lineage` read model; a minimal systems/sync surface in the UI; permissions `VIEW_SYSTEMS`, `VIEW_SYNCS`, `RUN_SYNC`, `VIEW_LINEAGE`.

**Out (Daniel's explicit Phase 2 non-goals, unchanged from his message):** real Workday production integration or API certification; Kafka, Redpanda, any broker; transactional outbox or dispatcher; SAP SuccessFactors or Oracle HCM connectors (not even empty adapters); payroll; provisioning/equipment workflows; CCSS, aguinaldo, vacation, cesantía, preaviso or any executable Costa Rican legal rule; incident management, SLO dashboards, Prometheus, Grafana, distributed tracing; microservices; a mobile app; anything real (credentials, employer data, tenant data, law). Do not implement Phase 3.

**Deliberately not built although the architecture allows it** (so it is not mistaken for an omission): the `messaging`/outbox infrastructure and any published event ([ADR-0018](adr/0018-module-transactions-and-persistence.md) decision 3 — still not created; nothing in Phase 2 needs it, because `integrations` calls `workforce.api` synchronously inside its own import step, exactly as [INTEGRATION_ARCHITECTURE.md](INTEGRATION_ARCHITECTURE.md) section 3.5 describes); raw payload landing (section 3.2 of that document — Phase 2 records failure reasons and field-level metadata, never the full provider payload, so there is nothing to replay from and nothing extra to protect); a `PROPOSE_ONLY` ownership mode; effective-dated ownership policy; a full `operations` module (the systems/sync surface lives inside `integrations.web` for Phase 2 — see sections 9 and 14); a declarative/no-code mapping engine (small code-defined mappings suffice for one simulation); per-field ownership policy configuration (ownership in Phase 2 is a fact recorded at import time, not a tenant-configurable policy — see section 4.3); person-level ownership (see section 4.3); write-back to the external system; webhooks; delta sync (every Phase 2 sync run is a full pull of the mock provider's small fixed worker set).

## 3. Reconciling this Phase 2 with the Phase 0 integration design

[INTEGRATION_ARCHITECTURE.md](INTEGRATION_ARCHITECTURE.md) sections 3 and 8 already describe an inbound pipeline (adapter, raw landing, mapper, reconciliation, workforce write, policy enrichment, orchestration) and a "reference HRIS" concept, both originally scoped to a later phase than the Phase 1 that was actually built. Phase 1 shipped none of it. This Phase 2 implements a **narrowed** version of that pipeline: adapter, mapper, reconciliation and workforce write, exactly as designed; **not** raw landing (see section 2), **not** policy enrichment (no country policy exists), and **not** orchestration/outbox (no downstream event exists to trigger — that remains the deferred outbound half of that document, still not built). Where this plan differs from that baseline, this section is the reconciliation notice; the baseline document is updated with a pointer here rather than rewritten.

## 4. Architecture

### 4.1 Module and dependency direction

Extends the accepted direction ([MODULES.md](MODULES.md) section 2a) exactly as already planned there, with `messaging` omitted because nothing publishes yet:

```
shared  <-  platform  <-  { access, audit, workforce, integrations }
access -> audit.api                    integrations -> access.api, audit.api, workforce.api
workforce -> access.api, audit.api     integrations -> platform, shared
```

- `workforce` gains a new public package, `com.palcheck.workforce.api`, exposing one write port (`WorkforceImportPort`) that `integrations` calls. `workforce.domain`, `.application`, `.infrastructure` and `.web` stay internal, as before.
- `integrations` is a new domain module with the same internal shape as `workforce`: `domain`, `application`, `infrastructure`, `web`. No `integrations.api` package exists yet (nothing outside the module needs one, exactly as `workforce.api` did not exist before this phase needed it).
- `integrations` never touches a `workforce` table, and `workforce` never depends on `integrations` — extended ArchUnit rules enforce both directions.
- The Workday-shaped provider DTOs are package-private inside `integrations.application.provider`; they never appear in `integrations.domain`, `integrations.web`, `workforce`, or the frontend (INTEGRATION_ARCHITECTURE.md principle 2).

### 4.2 Pipeline

```
MockWorkdayProvider (deterministic synthetic workers, controlled failure cases)
        |
        v
WorkdayMapper  (package-private provider DTO -> NormalizedWorker; records what it could not map)
        |
        v
NormalizedWorker (integrations.domain; small, integration-owned, not a second workforce model)
        |
        v
SyncRunService  (orchestrates separate worker transactions; two passes — see 4.4)
        |
        v
WorkforceImportPort (workforce.api; the only door into workforce data)
        |
        v
workforce.application  (validates, resolves the legal entity by name, creates Person once and upserts Employment/Assignment,
                         stamps ownership, writes one audit entry per changed entity — all in one transaction)
        |
        +--> integrations records the SyncItem outcome and field_lineage rows (subsequent independent writes; see 4.4)
```

### 4.3 Ownership and lineage (the least complex model that is still real)

Full field-level governed-field ownership with a configurable per-tenant policy is designed in [DATA_OWNERSHIP_AND_LINEAGE.md](DATA_OWNERSHIP_AND_LINEAGE.md) and is **not built now** (open question LQ-02 there is still open). Phase 2 implements a smaller, honest subset, chosen for the least complexity that still enforces the real rule Daniel named first ("externally owned fields cannot be silently overwritten locally"):

1. **Ownership is recorded on the Employment and the Assignment**, not per field and not on the Person. A synced Employment carries `ownedBySystem` (a stable system code, e.g. `workday-simulation`), `externalWorkerId`, and `lastSyncedAt`; a synced Assignment carries `ownedBySystem` and `lastSyncedAt`. `Person` identity fields (name, work email, locale) are initialized only on creation and preserved by every re-sync; they stay PalCheck-editable even for an externally sourced worker — a real system would very likely also govern identity fields; Phase 2 draws the line at Employment/Assignment because that is where Daniel's own example (`Assignment.title`) lives, and extending it to Person is mechanical, deferred work, not a design gap (recorded as a known limitation, section 17).
2. **Enforcement**: `EditPersonService` is unaffected. `EditEmploymentService.updateEmployment`/`updateAssignment` reject a human (`USER`) edit of a record whose `ownedBySystem` is set, with a new error code `EXTERNALLY_OWNED` (HTTP 409, distinct from optimistic-concurrency `CONFLICT` so the frontend can show a specific message instead of the stale-version banner). A sync's own write goes through a separate path (`WorkforceImportPort`) that is allowed to write an owned record.
3. **Field lineage** ("where did this value come from") is a separate concern from ownership *enforcement*, and is **read-only metadata owned by `integrations`**: one `field_lineage` row per supported (tenant, entity type, entity id, field path) naming the source system, the source field, the mapping version, the sync run, and when it was imported. It answers the UI question without being consulted for write authorization. `integrations` links to workforce entities by opaque UUID only; it does not join into `workforce`'s tables.
4. **Identity linking** (never duplicate a worker) reuses the ownership columns: a **partial unique index** on `workforce.employment (tenant_id, owned_by_system, external_worker_id)` where `owned_by_system IS NOT NULL` means "find the existing employment for this external worker, or there is none" is a single lookup, not a separate link table. A worker maps to exactly one Employment (and its Person) in Phase 2; a person having two concurrent externally sourced employments is out of scope.
5. **Legal entity mapping** is code-defined inside `integrations.application.provider.WorkdayMapper` as a small `Map<externalCompanyCode, legalEntityName>` (Daniel's brief: "Mappings may be code-defined in Phase 2 if that is cleaner. Document why" — a second provider or a real customer is what would justify a mapping UI; one mock provider does not). `WorkforceImportPort` resolves the **name** to an internal legal entity (no new column on `workforce.legal_entity`); an unresolvable name fails that one sync item with `MISSING_REQUIRED_FIELD`-class reason, exactly as Daniel's example describes, and creates nothing.

### 4.4 Sync model

- **Sync Run**: `id, tenantId, systemId, startedAt, completedAt, status (RUNNING|SUCCEEDED|SUCCEEDED_WITH_FAILURES|FAILED), recordsSeen, createdCount, updatedCount, unchangedCount, failedCount`.
- **Sync Item**: `id, syncRunId, externalWorkerId, status (SUCCEEDED|FAILED), action (CREATED|UPDATED|UNCHANGED|null on failure), failureReason (an enum: `UNKNOWN_LEGAL_ENTITY`, `MISSING_REQUIRED_FIELD`, `INVALID_MANAGER_REFERENCE`, `DUPLICATE_EXTERNAL_RECORD`, `TEMPORARY_PROVIDER_FAILURE`, `IMPORT_FAILED`), failureDetail, targetPersonId, targetEmploymentId, occurredAt`. `PENDING`/`SKIPPED` are not needed in Phase 2 (every item resolves to succeeded or failed within its own run; nothing waits).
- **Transaction boundaries**: one worker = one call into `workforce.api`, in `workforce`'s own transaction. `integrations` writes the `sync_item` row for that worker in its own separate transaction immediately after, whatever the outcome. A run is never one giant transaction; a failed worker never rolls back an already-succeeded one.
- **Two passes**, per Daniel's explicit instruction: pass 1 initializes Person on creation and creates/updates Employment and Assignment except the manager link; pass 2 (over the same run's workers) resolves each `managerExternalWorkerId` to the internal `employmentId` created in pass 1 and writes it, now that every worker in the run exists. A manager reference to a worker outside the synced set, or misspelled, fails only that item's manager resolution as `INVALID_MANAGER_REFERENCE`; the worker's own record still exists (a realistic "needs attention" case, not a lost import).
- **Idempotent re-sync**: pass 1 first looks up the employment by `(tenant, systemCode, externalWorkerId)`. Found and unchanged content (compared field by field) -> `UNCHANGED`, no business-field change or audit entry, `lastSyncedAt` still refreshed (recorded as a Phase 2 simplification — see section 17). Found and changed -> update only the changed fields, one audit entry per changed entity, actor `SYSTEM` naming the source system. Not found -> create.

### 4.5 Mock Workday provider

A small, deterministic, in-process (no HTTP) provider: 6 synthetic workers across two legal entities and two org units, with manager relationships among themselves, and enough irregularity to exercise every failure class Daniel listed: one worker with an unmappable company code (`UNKNOWN_LEGAL_ENTITY`), one with a manager reference nobody else in the set has (`INVALID_MANAGER_REFERENCE`), one with a blank required field (`MISSING_REQUIRED_FIELD`). Three named, code-level `WorkdayPullOptions` toggles reproduce the remaining scenarios deterministically rather than by chance: a changed value (a title change), a source-side `DUPLICATE_EXTERNAL_RECORD` (the same external worker id twice in one pull, detected and rejected by `SyncRunService` before it ever reaches `workforce.api`), and a `TEMPORARY_PROVIDER_FAILURE` (the provider reports one worker as unavailable instead of returning it). Every synthetic person is fictional, on a reserved email domain, clearly distinguishable from the Phase 1 seed data.

## 5. Persistence

New tables, all `integrations`-owned except the ownership columns on `workforce`'s own tables (owned there):

- `integrations.external_system` (`id, tenant_id, code, display_name, type, mode, health, last_sync_at, created_at`) — one row per tenant for `workday-simulation` in Phase 2. `mode` is always `SIMULATION`.
- `integrations.sync_run`, `integrations.sync_item` (section 4.4).
- `integrations.field_lineage` (`tenant_id, entity_type, entity_id, field_path, source_system, source_field, mapping_version, sync_run_id, sync_item_id, imported_at`, primary key `(tenant_id, entity_type, entity_id, field_path)` — current import metadata for owned fields, initial-import evidence for Person, not a growing history; the audit trail already is the history of values).
- `workforce.employment` gains `owned_by_system`, `external_worker_id`, `last_synced_at` (nullable) plus the partial unique index in section 4.3.
- `workforce.assignment` gains `owned_by_system`, `last_synced_at` (nullable).

V8 adds composite tenant-qualified integrity constraints and the IMPORT_FAILED reason; V6/V7 remain unchanged.

No table for Kafka, outbox, incidents, SLOs or legal policy. No secret is stored (the mock provider needs none).

## 6. API surface

| Method | Path | Purpose | Permission |
|---|---|---|---|
| `GET` | `/api/systems` | List configured external systems for the tenant | `VIEW_SYSTEMS` |
| `GET` | `/api/systems/{code}` | System detail (simulation mode, last-sync timestamp, scenario availability) | `VIEW_SYSTEMS` |
| `POST` | `/api/systems/{code}/sync` | Run a synchronization now; returns the completed run (Phase 2 syncs are fast and synchronous — a background job is future work) | `RUN_SYNC` |
| `GET` | `/api/systems/{code}/syncs` | Sync run history, paged | `VIEW_SYNCS` |
| `GET` | `/api/syncs/{runId}/summary` | One run’s summary for direct URLs/reloads | `VIEW_SYNCS` |
| `GET` | `/api/syncs/{runId}` | One run's items (successes and failures) | `VIEW_SYNCS` |
| `GET` | `/api/people/{personId}/lineage` | Field lineage for that person's employment/assignment fields (empty if none, including across tenants — lineage absence is not sensitive) | `VIEW_LINEAGE` |

History/items accept `page >= 0` and `size` from 1 through 100 (history default 25; items default 50). Invalid pagination returns 400. System responses include `simulationScenariosEnabled`. Sync POST accepts `scenario=BASELINE` (default) or `TITLE_CHANGE`; TITLE_CHANGE is allowed only under dev/test profiles and requires RUN_SYNC. The named scenario is selected per pull: baseline restores the original synthetic title. This is a deterministic demo/test facility, not a live provider editing API. Lineage accepts optional employmentId/assignmentId selectors, tenant-qualified, and returns externalWorkerId from the linked sync item.

Existing `PATCH /api/employments/{id}` and `PATCH /api/assignments/{id}` gain one new failure mode: `409 EXTERNALLY_OWNED` when the target is synced. `GET` responses for `Employment`/`Assignment` gain an `ownedBySystem` field (`null` when PalCheck-owned) so the frontend can show a read-only state without a second call.

## 7. Authorization

Four new permissions, added to `access.api.Permission`, tenant-wide scope only (matching Phase 1):

- `VIEW_SYSTEMS`, `VIEW_SYNCS`, `RUN_SYNC` — bundled into `HR_ADMIN` only in Phase 2 (running an import is an administrative action; `HR_VIEWER` sees workforce data a sync produces but not the sync machinery itself, matching Daniel's framing of "technical/admin users" versus "ordinary HR users" for lineage).
- `VIEW_LINEAGE` — bundled into `HR_ADMIN` too, and *not* into `HR_VIEWER`; an ordinary viewer sees the read-only field but not its "View source" disclosure. (Recorded as a Phase 2 default, easy to revisit; Daniel's brief left this an open choice.)

## 8. Audit and logging

Imports and sync-caused updates call the existing `AuditRecorder` through `workforce.application`, with `AuditActor.system("Workday Simulation")` instead of `AuditActor.user(...)` — the one change `WorkforceAuditor` needs (an actor-kind parameter instead of always assuming a human). Structured logs add `systemCode`, `syncRunId`, `externalWorkerId` alongside the existing `tenantId`, `requestId`, `correlationId`, `operation`, `result`; no provider payload is ever logged.

## 9. Frontend (implemented)

Implemented under `web/src/pages/integrations`: a **Systems** list, a **Workday Simulation** detail with a "Run sync" action and recent history, a **sync run** detail listing items with their outcome and reason, and a "Managed by Workday Simulation — View source" disclosure on the Assignment title and the Employment type/status fields, with the field itself rendered read-only when owned. Native controls, progressive disclosure, no new dependency — the same approach as [UI_SYSTEM.md](UI_SYSTEM.md) and the Phase 1 UI polish already in the tree.

## 10. Test strategy

Real PostgreSQL for every integration test, as in Phase 1. No coverage target.

| Layer | What is proven |
|---|---|
| Unit | Existing domain/service tests; mapper translation, mapping rejection, entity resolution and manager behavior are exercised through SyncIT rather than claimed as separate mapper unit tests |
| Integration (real PostgreSQL) | First import creates a full record; unchanged re-sync is a no-op; a changed source value updates only the changed field; re-sync never duplicates; missing legal entity mapping fails cleanly and creates nothing; unresolved manager reference fails only that item; one bad item does not roll back the rest of the run; a human `PATCH` on an owned Employment/Assignment is rejected with `EXTERNALLY_OWNED`; audit for a sync-caused change shows a `SYSTEM` actor naming the source system; `field_lineage` names the right source field; tenant isolation on every new endpoint and table; permission matrix for the four new permissions |
| Architecture (ArchUnit) | `integrations` depends only on its allowed neighbours; nothing outside `integrations` touches its internals; `workforce` never depends on `integrations`; provider DTOs never leave `integrations.application.provider` |
| Frontend / E2E | 10 new component tests cover systems, permission combinations, sync POST, failure recovery, counts, ownership, lazy lineage, system audit and axe. Three real-stack browser flows cover import/source, deterministic title update/re-sync, and viewer denial. All 17 previous browser flows still pass. |

## 11. Local development and CI

No new local dependency: the mock provider runs in-process, inside the backend. No secret, no new port, no Docker requirement. CI: the new tests run inside the existing `mvn verify`; no new workflow file needed.

## 12. Dependencies

None added. Same backend and frontend dependency set as Phase 1 ([PHASE_1_IMPLEMENTATION_PLAN.md](PHASE_1_IMPLEMENTATION_PLAN.md) section 14).

## 13. Implementation steps

| Step | Deliverable | Exit criteria | Status |
|---|---|---|---|
| 1 | Review Phase 1 architecture and current implementation | This plan and its ADRs reflect what exists, not what Phase 0 imagined | done |
| 2 | This plan, ADR-0024, ADR-0025 | Reviewed against Daniel's Phase 2 message | done |
| 3 | `integrations` module skeleton + `workforce.api` boundary | Packages exist; ArchUnit rules pass with nothing implemented yet | done |
| 4 | Normalized contract (`NormalizedWorker`) | Types compile; unit-testable independent of persistence | done |
| 5 | Persistence: ownership columns, sync run/item, field lineage | Migration applies; repository tests pass | done |
| 6 | Mock Workday provider | Deterministic fixtures including every failure class | done |
| 7 | Import/reconciliation flow (`WorkforceImportPort` + `SyncRunService`, two passes) | First import and idempotent re-sync integration tests pass | done |
| 8 | Ownership enforcement | `EditEmploymentService` rejects a human edit of an owned record; test passes | done |
| 9 | Audit + lineage integration | `SYSTEM`-actor audit entries; `field_lineage` rows; tests pass | done |
| 10 | Frontend: Systems / Sync UI | Pages against the real API contract | done |
| 11 | Lineage disclosure on Person/Employment detail | "Managed by" + "View source" on owned fields | done |
| 12 | Frontend and E2E tests | Component tests; real-stack import/source, changed-source/re-sync and permission flows | done |
| 13 | Documentation cleanup and Phase 2 review | Every doc matches the system; DoD table filled with evidence | done |

Claude completed steps 1–9; Codex independently reviewed the backend, corrected the findings, and completed steps 10–13. See [the review](PHASE_2_CODEX_REVIEW.md) for the baseline and final verification.

## 14. Risks and open questions

- **R1** Field-level, tenant-configurable ownership policy ([DATA_OWNERSHIP_AND_LINEAGE.md](DATA_OWNERSHIP_AND_LINEAGE.md) LQ-02) is real future work; Phase 2's entity-level shortcut (section 4.3) is a deliberate, documented simplification, not a rejection of that design.
- **R2** Extending ownership to `Person` fields, and to a person with more than one externally sourced employment, is deferred; both are mechanical extensions of the same pattern, not new architecture.
- **R3** The mock provider's controllable scenarios (changed value, duplicate, temporary failure) are toggles on one otherwise-fixed fixture set (`WorkdayPullOptions`), not independent fixture files; adding a genuinely different scenario later is a small, well-precedented addition.
- **R4** No background job runner exists; `POST /api/systems/{code}/sync` runs synchronously. Fine for a handful of synthetic workers; a real provider's data volume would need an async run with polling, which is future work flagged in [ENTERPRISE_SYSTEMS.md](ENTERPRISE_SYSTEMS.md).
- **Q1 (Daniel)** Confirm the ownership granularity choice (Employment/Assignment, not Person; not per-field) as the Phase 2 scope, with the fuller model remaining future work.
- **Q2 (Daniel)** Confirm `VIEW_LINEAGE`/`VIEW_SYSTEMS`/`VIEW_SYNCS`/`RUN_SYNC` bundled into `HR_ADMIN` only (not `HR_VIEWER`) for Phase 2.
- **Q3 (Daniel)** Confirm the systems/sync UI living inside `integrations` rather than standing up the deferred `operations` module now.

## 15. Non-negotiables carried over from Phase 1

Synthetic data only; no real credential; English UI with the existing message-catalog pattern; structured logs with no personal values; one problem-details error model; tenant taken only from the trusted server-side principal; no executable Costa Rican legal rule; incremental delivery with a worklog entry per step group.

## 16. Completion and next decision

Claude delivered the backend first; Codex completed the independent review, frontend, browser validation and documentation on 2026-09-22. [PHASE_2_CODEX_REVIEW.md](PHASE_2_CODEX_REVIEW.md) records defects, fixes, evidence and limitations. Codex recommends **ACCEPT** for each exact question in section 14. Those recommendations do not constitute Daniel’s acceptance of the Proposed ADRs. All implementation steps and definition-of-done criteria are complete within the limits below. Phase 3 remains unopened.

## 17. Exact reviewed behavior and limits

- **Identity:** first import initializes Person; re-sync never overwrites locally editable identity. Employment/Assignment stay externally owned. No automatic merge with a pre-existing local Person by email; a collision fails that worker. Unsupported legal-entity transfers and start-date changes that would invalidate the current assignment are rejected for reconciliation.
- **Mapping boundary:** provider company/department codes, enum mappings and source-field names originate inside the adapter. `NormalizedWorker.sourceFields` carries opaque provenance labels; business logic consumes normalized values, never provider DTOs or code maps. The import port receives a generic source display name for audit attribution.
- **Managers:** pass 2 considers only workers successfully imported in the current pull. A null source manager clears the link; an unresolved manager preserves the existing link and adds a failed outcome. A manager-only change promotes the worker’s UNCHANGED action to UPDATED, once. Existing cycle protection remains active.
- **Counts:** processed counts source records; failed counts failed outcomes, including manager failures on imported records. Baseline first run: 6 processed, 4 created, 0 updated, 0 unchanged, 3 failed outcomes (2 worker failures plus 1 manager failure), 7 items. Repeating baseline: 0 created, 0 updated, 4 unchanged, 3 failed outcomes. TITLE_CHANGE after baseline: 0 created, 1 updated, 3 unchanged, 3 failed outcomes; repeating it: 4 unchanged. Counts are not a partition of processed records; UI explains why.
- **Transactions/concurrency:** workforce + audit are atomic per worker; the existing tenant advisory graph lock serializes import transactions and prevents concurrent duplicate creation. A whole run is not atomic or serialized. Integration items and individual lineage rows are subsequent writes, not a single receipt/lineage transaction. Unexpected worker transaction failures roll back that worker and record safe IMPORT_FAILED; fatal orchestration failures attempt to mark the run FAILED when storage is available. A database/process crash can leave RUNNING or stale/missing metadata despite committed workforce/audit data. A later full pull reconciles owned values/current metadata without duplicating workforce records, but does not repair old run history or reconstruct missing initial Person provenance.
- **Idempotency:** the durable `(tenant, source system, external worker)` identity and field comparison make repeat full pulls idempotent in their workforce effects. Every POST creates a new run; it does not reuse Phase 1’s 24-hour request receipt. Unchanged imports refresh sync timestamps/current employment-assignment provenance but emit no value-change audit. Concurrent different source snapshots may interleave; source revision ordering and background recovery are not implemented.
- **Lineage:** six fields only: initial Person firstName/lastName/workEmail; current synchronized Employment status/employmentType and Assignment title. No full raw payload or lineage history is stored. Person lineage is initial-import evidence, not an assertion about locally edited values. Audit names the source but has no sync-run foreign key. UI exposes Employment/Assignment metadata and a link to the exact recorded run.
- **Tenant integrity:** V8 adds tenant-qualified run/item/lineage foreign keys without changing V7 or adding tables. Lineage’s externalWorkerId is projected through its tenant-qualified sync item, not duplicated in storage.
- **Scale/product limits:** one in-process synthetic provider, one external employment per worker, synchronous full pulls and code-defined mappings. No per-field policy, ownership cutover, inferred termination from absence, network failure testing, jobs, durable retries, operations console or country-policy execution. These limits define this milestone; they are not Phase 3 authorization.
