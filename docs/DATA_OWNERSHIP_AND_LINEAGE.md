# Data Ownership and Lineage

> **Reconciliation notice (2026-09-19):** [Review LIN-01 and §7](CODEX_ARCHITECTURE_REVIEW.md#7-integration-feasibility-normalization-ownership-and-lineage) (explicit change/revision/operation links; correlation alone does not prove which value was sent; source-instance ownership; cutover concurrency) is accepted as a requirement for the overlay milestone under Daniel's refinement 4. Phase 1 implements only the foundations this model needs: `workforce` as the single writer of workforce data, a per-row revision, and atomic audit. The governed-field ownership policy is not implemented in Phase 1 (everything is `PALCHECK`-owned by default). Baseline text is retained.

Status: Phase 0 draft, **Proposed** ([ADR-0005](adr/0005-source-of-truth-ownership.md), [ADR-0015](adr/0015-data-lineage.md)) ·
Output of assignment step 6, plus the lineage feature from the brief.

Diagram: [07 data lineage](diagrams/07-data-lineage.md). Related: [INTEGRATION_ARCHITECTURE.md](INTEGRATION_ARCHITECTURE.md), [DOMAIN_MODEL.md](DOMAIN_MODEL.md) section 7.

## 1. Why one document

The brief describes ownership metadata and lineage as separate features. They are two views of one question: *where did this value come from, who is allowed to
change it, and where did it go?* Splitting them invites drift. This document defines five distinct things and how they connect.

| Thing | Question it answers | Nature | Lives in |
|---|---|---|---|
| **Ownership policy** | Who may write this field? | Configuration, per tenant | `workforce` (governed-field registry) + tenant policy |
| **Provenance** | Where did this stored value come from? | Fact attached to a value | `workforce`, alongside the value |
| **Audit record** | Who changed what, when, why? | Append-only history | `audit` |
| **Domain/integration event** | What business fact should others react to? | Message for consumers | Outbox (`integrations`) |
| **Lineage view** | Source to destination, and who consumes it? | Computed *view* | `operations` (read model) |

The first four are recorded once, from a **single capture point** (section 5). The fifth is computed from them and from mapping definitions.

## 2. Ownership model

### 2.1 Unit of ownership: the governed field

Ownership applies to a **governed field**: a named `(entityType, fieldPath)` such as `Assignment.jobTitle` or `Employment.status`. Not every column is governed;
the governed-field list is configuration owned by `workforce`, and is the set of fields an external system might plausibly own. Ungoverned fields are always `PALCHECK`.

### 2.2 Ownership policy

A policy is `(tenant, governedField, mode, ownerSystem?, effectiveFrom, changedBy, reason)`. Exactly one mode applies to a field for a tenant at any time.

| Mode | Who may write | Meaning in the UI | Notes |
|---|---|---|---|
| `PALCHECK` | PalCheck users and PalCheck processes | Editable | Default. Standalone tenants are entirely `PALCHECK`. An external value for the field, if seen, is advisory and recorded as a divergence. |
| `EXTERNAL(system)` | Only a sync from that system | Read-only with a source badge | A PalCheck user gets an actionable refusal ("owned by *system*; change it there"). |
| `DERIVED` | Nobody directly; recomputed from declared inputs | Read-only with an "explain" affordance | Includes country-policy annotations and computed views. It is a *provenance kind* used as an ownership mode so the write rule is uniform: nobody writes it. |

Deliberately **not** in v1: a `PROPOSE_ONLY` / change-request mode (PalCheck proposes; the external owner decides). It is plausible and valuable in overlay mode,
but it needs a write-back path (Phase 3+). Recorded as an open question, not built.

### 2.3 Enforcement

- `workforce` is the **only writer** of workforce data. Every write carries a **write context**: `actorKind` (`USER`, `SYNC`, `SYSTEM`, `POLICY`), `sourceSystem`,
  `syncRunId`, `mappingVersion`, `correlationId`, `reason`.
- `workforce` compares the context to the field's policy:

| Writer | Field mode | Result |
|---|---|---|
| `USER` | `PALCHECK` | Applied |
| `USER` | `EXTERNAL` | **Rejected** with an actionable error |
| `SYNC` from the owner system | `EXTERNAL` | Applied, provenance updated |
| `SYNC` from any system | `PALCHECK` | **Not applied**; a `Divergence` is recorded |
| `SYNC` from a non-owner system | `EXTERNAL(other)` | Rejected, recorded |
| Anyone | `DERIVED` | Rejected; only the derivation process updates it |

- Enforcement is in the domain layer, not the UI, and is covered by contract tests, because "silently overwrites externally owned data" is the failure the brief names first.

### 2.4 Changing ownership

Enabling a connector, or moving a tenant from standalone to overlay, **flips policy for the mapped governed fields**. This is an explicit, audited, previewable act
("these N fields will become read-only; these M current values differ from the provider and will be replaced"). It is a first-class operation, not a side effect of
configuring a connector. Reverting (overlay to standalone) is the mirror image and must also be previewed.

**This flip is the test of the standalone-first decision** ([ADR-0017](adr/0017-primary-product-mode.md)): it must work on real fields without changes to the core domain.

### 2.5 Local supplemental data

Data that local concepts need but a provider does not hold is always `PALCHECK`-owned and attached to an Employment. It does not extend the provider's record and
is never written back. Which fields exist is a research output ([localization/costa-rica/RESEARCH_BACKLOG.md](localization/costa-rica/RESEARCH_BACKLOG.md)).

## 3. Provenance

For each governed field value `workforce` stores a compact provenance record beside it:

| Attribute | Brief's name | Meaning |
|---|---|---|
| `sourceSystem` | `sourceSystem` | `PALCHECK`, or the external system identifier |
| `sourceField` | `externalField` | Provider field path (e.g., an HRIS business-title field; exact names are provider-specific and illustrative here) |
| `externalId` | `externalId` | Provider record identifier |
| `observedAt` | `lastSynchronizedAt` | When the provider value was observed |
| `recordedAt` | (new) | When PalCheck stored it |
| `syncRunId`, `mappingVersion` | (new) | Which run and which mapping produced it |
| `transformationRef` | (new) | Named transformation applied (e.g., code-list mapping ID) |
| `writeContext` | (new) | Actor kind and correlation ID |
| `contentHash` | (new) | For change detection |
| `ownershipMode` (from policy at write time) | `ownershipMode` | Snapshot, so history stays explainable after a policy change |

Provenance is the *current* source of each value. History of previous values is the audit trail, not extra provenance rows.

## 4. Lineage

### 4.1 Two kinds

- **Field lineage.** For a value: source system and field, adapter, transformation, normalized contract field, PalCheck destination field, and downstream consumers.
- **Flow lineage.** For an event: the business change that caused it, the event, the consumers, and each delivery attempt's outcome.

### 4.2 The chain (from the brief; provider field name illustrative)

```
Provider field (e.g. business title)
   -> Adapter (DTO)
   -> Mapper transformation (id, mapping version)
   -> NormalizedWorker.assignment.jobTitle
   -> PalCheck Assignment.jobTitle
   -> Outbound payload contracts that include it
        +--> Identity (directory attribute)
        +--> Equipment
        +--> Directory
```

### 4.3 How it is computed, not stored per value

Lineage is derived by joining four things that already exist for other reasons:

1. **Mapping definitions** (versioned): provider field -> transformation -> `NormalizedWorker` field -> destination field. This is the *static* graph.
2. **Data contracts of outbound payloads**: each downstream destination declares which normalized fields its payload includes. "Downstream consumers" is therefore
   *derivable*, not a hand-maintained list.
3. **Provenance records**: which run, mapping version, and observation produced the current value.
4. **Delivery records**: events and attempts, joined by `correlationId`, to show what actually flowed and whether it succeeded.

No per-value, per-change lineage table is kept forever; that would duplicate audit and grow without bound ([PHASE0_CRITIQUE.md](PHASE0_CRITIQUE.md) O-06). Retention follows the underlying records.

### 4.4 What a technical user sees

For a chosen field on a chosen record: source system · source field · transformation (ID and plain description) · destination field · mapping version · last sync ·
downstream consumers (from payload contracts) · most recent events and delivery outcomes · `correlationId` (linking to the event explorer).
Values are **not** shown by default; lineage is metadata. Viewing values follows normal authorization and is audited for sensitive fields.

### 4.5 Questions it must answer

- Where did this value come from, and when?
- If this field changes, what downstream systems are affected?
- Which fields does Payroll (or any destination) receive?
- Which events carried this change, and did each delivery succeed?
- Which mapping version produced this value, and what else did that mapping touch?

### 4.6 Limits (stated so they are not discovered later)

- Lineage for `DERIVED` values is only as good as the declared inputs. Derivations must declare inputs.
- Manual edits by people have *actor lineage* (audit), not source-field lineage.
- Downstream systems can change what they do with a payload; PalCheck knows what it sent and what was acknowledged, not what the destination did next.
- Lineage of values before Phase 2 is trivial (`PALCHECK`); it becomes interesting when a connector exists.

## 5. Single capture point

To prevent the same fact being written three times by hand, `workforce` produces one **change record** per governed change:

```
change record = (tenant, entity, governedField, oldValue, newValue, effectiveDate,
                 writeContext, reason, correlationId, causationId, occurredAt)
```

Within the **same database transaction**, that record drives:

1. **Audit**: an `audit` record ([MODULES.md](MODULES.md) 3.6).
2. **Provenance**: the field's provenance updated.
3. **Event**: an internal domain event, and where applicable an outbox row for a published integration event.

`integrations` performs the equivalent capture for sync runs and delivery attempts. Because everything hangs off one record with one `correlationId`,
the lineage view is a join, not a reconstruction.

## 6. Failure modes

| Failure | Consequence | Control |
|---|---|---|
| A write path bypasses the ownership check | Silent overwrite of external data | Single writer (`workforce`); architecture test forbids other writers; contract tests per mode |
| Policy changes mid-sync | Inconsistent application within one run | Policy version pinned per sync run; flip is blocked while a run is active for that connector |
| Stale policy cache | Wrong allow/deny | Policy read in the write transaction; short-lived caches only if measured |
| Provenance missing on old values | Unexplainable data | Backfill with `PALCHECK`/unknown-source markers at introduction; never fabricate a source |
| Mapping change reinterprets old data | Confusing history | Mapping versions are immutable; reprocess is an explicit operation ([INTEGRATION_ARCHITECTURE.md](INTEGRATION_ARCHITECTURE.md) 6.4) |
| Lineage view leaks values | PII exposure | Metadata only by default; separate audited permission |

## 7. Open questions

- **LQ-01** The initial governed-field list. Recommendation: derive it from the `NormalizedWorker` fields that are plausibly provider-owned; keep it short.
- **LQ-02** Provenance storage shape: columns beside each governed field, a side table keyed by `(entity, field)`, or a JSON document per entity? Decide with the OQ-03 history-pattern spike.
- **LQ-03** Is `PROPOSE_ONLY` needed before overlay mode is sold? Probably yes for real customers; not for the reference connector.
- **LQ-04** Should ownership policy be effective-dated (scheduled cutover), or immediate-only? Recommendation: immediate-only in Phase 2; effective-dated later.
