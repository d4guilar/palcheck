# ADR-0025: Ownership Granularity, Lineage Storage, and Sync Transaction Model (Phase 2)

**Status:** Proposed (implemented provisionally by Phase 2 under Daniel's direction; explicit confirmation requested)
**Date:** 2026-09-22

## Context

[DATA_OWNERSHIP_AND_LINEAGE.md](../DATA_OWNERSHIP_AND_LINEAGE.md) already designs a full model: per-governed-field ownership policy, provenance beside each governed value, and lineage computed by joining mapping definitions, payload contracts, provenance and delivery records. That design is for when a tenant can configure which fields an external system owns and lineage must explain outbound payloads too — neither exists yet. Daniel's Phase 2 message asks for "the least complex model that still demonstrates realistic behavior" and gives the concrete example (`Assignment.title`, `EXTERNAL`, source `BusinessTitle`) that the demonstration must cover. Separately, the sync needs a transaction and ordering strategy: manager relationships can reference workers imported in the same run, and one bad worker must not undo the rest.

## Decision

1. **Ownership granularity: the Employment and the Assignment, not the field and not the Person.** Each carries `ownedBySystem` (a stable system code, `null` = PalCheck-owned), and Employment additionally carries `externalWorkerId`. This is a plain fact recorded at import time, not a tenant-configurable policy (`DATA_OWNERSHIP_AND_LINEAGE.md`'s `PALCHECK`/`EXTERNAL(system)`/`DERIVED` policy modes are not implemented; Phase 2 only ever produces the `EXTERNAL` case, and only for these two entities). `Person` identity fields are initialized at creation, remain PalCheck-editable and are preserved on every re-sync. Their initial-import lineage is never presented as current ownership.
2. **Enforcement lives in `workforce.application`, not the UI**, matching the existing accepted principle: `EditEmploymentService.updateEmployment`/`updateAssignment` reject a human edit of a record whose `ownedBySystem` is set, before touching any field, with a new error code `EXTERNALLY_OWNED` (409, distinct from the optimistic-concurrency `CONFLICT`, so the client can show a specific, actionable message rather than the stale-version banner). `WorkforceImportPort`'s own write path is the only one allowed to write an owned record.
3. **Field lineage is separate from ownership enforcement**, and is metadata `integrations` owns: one `field_lineage` row per `(tenant, entityType, entityId, fieldPath)`, holding the source system, source field, mapping version, sync run and item, and when it was imported. It answers "where did this value come from" for the UI; nothing consults it to decide whether a write is allowed. `integrations` references workforce entities by opaque UUID only — it never joins into `workforce`'s tables, so `workforce` and `integrations` stay independently deployable in principle even though they share one database today.
4. **Identity linking reuses the ownership columns**: a partial unique index on `workforce.employment (tenant_id, owned_by_system, external_worker_id)` where `owned_by_system IS NOT NULL` is the "have we seen this external worker before" lookup — no separate `ExternalIdentityLink` table. One external worker maps to exactly one Employment (and, through it, one Person); a person with two concurrent externally sourced employments is out of scope, recorded as a known limitation rather than built and left untested.
5. **Legal entity mapping is a small code-defined map** inside `integrations` (external company code -> internal legal entity **name**, resolved by `workforce.api` against the tenant's existing legal entities). No new column on `workforce.legal_entity`; an unresolved name fails that one sync item (`UNKNOWN_LEGAL_ENTITY`/`MISSING_REQUIRED_FIELD`-class reason) and creates nothing. A declarative mapping UI is not built — one provider does not justify it (the same "second-instance rule" as elsewhere in this architecture).
6. **Sync transaction model: one worker, one `workforce` transaction; the run is never one transaction.** `integrations.SyncRunService` calls `WorkforceImportPort` once per worker; that call's `@Transactional` boundary lives inside `workforce`, exactly like every other workforce command. Immediately after, in a **separate** transaction, `integrations` writes that worker's `sync_item` outcome — whatever it was. A run's summary counts are written once, after every item is processed. A failing worker therefore never rolls back an already-succeeded one, and 500 workers are never one giant transaction.
7. **Manager resolution is two passes over the same run's workers**: pass 1 initializes Person on creation and creates/updates Employment/Assignment except the manager link (every worker now exists, or that worker's own item already failed and is skipped in pass 2); pass 2 resolves each `managerExternalWorkerId` to the `employmentId` created in pass 1 and writes it through a normal (System-actor) assignment update. A manager reference that resolves to nothing in the synced set fails only that item's manager link, as `INVALID_MANAGER_REFERENCE`; the worker's own record still exists.
8. **Audit distinguishes a sync from a human**: the one change `workforce`'s `WorkforceAuditor` needs is to accept the actor's *kind*, not assume `USER`; a sync-caused change uses `AuditActor.system(sourceDisplayName)` with the source display name supplied through the provider-neutral import port (currently "Workday Simulation"). No third `AuditActor.Type` is added — `SYSTEM` already means "not a human," which is exactly what an external synchronization is.
9. **Idempotent re-sync**: unchanged content on a known external worker is `UNCHANGED` — no business-field change or audit entry — but `lastSyncedAt` is still refreshed on every successful pass so "last synchronized" is honest even when nothing else changed. This one field's update outside the "no write on unchanged" rule is a deliberate, documented exception, not an oversight.

## Alternatives Considered

| Alternative | Assessment |
|---|---|
| Full field-level governed-field ownership (`DATA_OWNERSHIP_AND_LINEAGE.md`'s design) now | The right long-term model, but it needs a tenant-configurable policy, a policy-flip operation and per-field provenance storage (LQ-02, still open there) that nothing in Phase 2 needs yet; building it now would be the "data-governance platform" Daniel's brief explicitly said not to build |
| A separate `ExternalIdentityLink` table instead of reusing ownership columns | More flexible (would survive a worker moving between legal entities without losing its link) but is a second source of truth for the same fact at this scale; rejected until a concrete case needs it |
| One transaction for the whole sync run | Simpler to reason about atomically, but directly contradicts Daniel's explicit instruction and the realistic requirement that one bad worker must not invalidate 499 good ones |
| Infer termination from a worker's absence in a sync | Explicitly rejected by Daniel's brief; not built, and not silently done by omission — absence changes nothing in Phase 2 |
| A `PROPOSE_ONLY` ownership mode (PalCheck proposes, the external owner decides) | Valuable for a real overlay customer, not for one synthetic reference connector; recorded as future work in `DATA_OWNERSHIP_AND_LINEAGE.md` LQ-03, not built |

## Consequences

**Positive:** the exact behavior Daniel asked to see (an owned field rejects a local edit; re-sync does not duplicate; a bad worker does not sink the run; a resolved manager reference works even though it depended on another worker in the same run) is real and tested, at a fraction of the design cost of the full governed-field model. Reusing ownership columns for identity linking avoids a second table with the same lifecycle as Employment.

**Negative:** the model is intentionally narrower than `DATA_OWNERSHIP_AND_LINEAGE.md`'s target — no per-field granularity, no tenant-configurable policy, no `Person` coverage, no multi-employment-per-person handling. Reusing ownership columns for identity linking means an employment cannot easily change its external identity without a deliberate migration later. `lastSyncedAt` updating on an otherwise-unchanged record is a small, documented exception to "no write means no write."

**Operational burden:** one new partial unique index; no new secret, service or scheduled job (Phase 2 syncs run synchronously, on request).

## Reconsider When

A second tenant-configurable ownership rule is needed (build the full `DATA_OWNERSHIP_AND_LINEAGE.md` model then, not before); `Person` fields need to be governed; a person can have two concurrent externally sourced employments; sync volume needs an asynchronous run instead of a synchronous request/response; a real destination needs to know what PalCheck sent it (that is lineage's *downstream* half, still not built, per that document's section 4.6).

## Interview line

"Ownership lives on the employment and the assignment as a plain fact recorded at import time, enforced inside the same workforce module that already enforces version checks — so a human PATCH on a synced record fails the same way a stale version does, with its own specific reason, and nothing outside workforce ever has to remember to check."

## Related Decisions

[ADR-0005](0005-source-of-truth-ownership.md) · [ADR-0015](0015-data-lineage.md) · [ADR-0018](0018-module-transactions-and-persistence.md) · [ADR-0022](0022-phase1-technical-baseline.md) · [ADR-0024](0024-hris-adapter-boundary-phase2.md) · [Phase 2 plan](../PHASE_2_IMPLEMENTATION_PLAN.md)

## Reviewed implementation details (2026-09-22)

Codex recommends **ACCEPT** for Q1 (Employment/Assignment ownership) and Q2 (four separate permissions bundled into HR_ADMIN only). Status remains Proposed; Daniel owns acceptance. The import port requires RUN_SYNC and does not require CREATE_PERSON/EDIT_EMPLOYMENT. UI read-only controls supplement authoritative backend refusal.

Worker writes acquire the existing tenant advisory graph lock, preventing concurrent identity-creation races without serializing an entire run. Manager pass uses only workers successfully imported in this pull; null clears a link, an unresolved reference preserves the prior link and produces a separate failure, and manager-only changes count as UPDATED once. Six baseline records yield four created workers and three failed outcomes (two worker failures plus one manager failure). The UI explicitly distinguishes records from outcomes.

Clarification to decision 6: each sync-item insert and lineage upsert follows the workforce commit as an independent write; the item and all lineage rows do not form one atomic receipt transaction. Unexpected per-worker failures roll back workforce/audit and record IMPORT_FAILED with safe text. Fatal orchestration failure attempts to finalize FAILED. Process/database failure can leave RUNNING or missing/stale metadata while a workforce write is committed. Re-pulling reconciles owned values and current metadata, but does not retroactively complete a historical run or reconstruct lost initial Person lineage. Durable crash recovery is deferred, not implied.

Lineage covers initial Person firstName/lastName/workEmail and current synchronized Employment status/employmentType plus Assignment title. It is not complete field coverage or a lineage history. V8 qualifies run/item/lineage references by tenant; externalWorkerId is read from the linked item. Audit contains SYSTEM attribution with readable assignment/manager labels; it has no explicit sync-run foreign key. Source display names and provider metadata are descriptive, not permission inputs.

Re-sync uses durable external identity and field comparison, not Phase 1’s request-key receipt or 24-hour guaranteed retry window. Every POST creates a distinct run. Concurrent different snapshots can interleave; no source revision ordering exists. Unknown vocabulary and unsupported legal-entity/date reconciliation are rejected rather than silently changing workforce invariants. No automatic termination follows source absence. See the [review](../PHASE_2_CODEX_REVIEW.md) and [plan section 17](../PHASE_2_IMPLEMENTATION_PLAN.md#17-exact-reviewed-behavior-and-limits).
