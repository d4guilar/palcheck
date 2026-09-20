# ADR-0015: Data Lineage

**Status:** Proposed
**Date:** 2026-09-19

## Context

The brief calls lineage a major enterprise portfolio feature: for a value, show source system, source field, transformation, destination field, last sync, downstream consumers, and correlation ID. Storing a lineage record per field per change would duplicate the audit log and grow without bound.
Lineage, ownership, audit, and events all describe the same change from different angles ([DATA_OWNERSHIP_AND_LINEAGE.md](../DATA_OWNERSHIP_AND_LINEAGE.md)).

## Decision

1. **Lineage is a computed view**, not a stored per-value table. It joins:
   1. versioned **mapping definitions** (source field -> transformation -> normalized field -> destination field),
   2. **outbound payload data contracts** (which normalized fields each destination receives, so "downstream consumers" is derivable),
   3. **provenance** stored beside each governed value,
   4. **delivery and sync records** joined by `correlationId`.
2. **Single capture point**: `workforce` produces one change record per governed change; within one transaction it drives audit, provenance, and the event/outbox row ([ADR-0005](0005-source-of-truth-ownership.md)).
3. **Two kinds of lineage**: field lineage (a value's origin and consumers) and flow lineage (an event's cause, consumers, and delivery outcomes).
4. **Metadata by default, values never by default**; viewing values follows normal authorization and audit; lineage views are permissioned (`VIEW_LINEAGE`).
5. **Declared inputs for `DERIVED` values**; lineage for manual edits is actor lineage (audit), not source-field lineage.
6. **Retention follows the underlying records**; nothing lineage-specific grows separately.
7. **Lineage becomes substantive in Phase 2**, when a connector exists; in Phase 1 it is trivial (`PALCHECK`) and the design work is capturing provenance and payload contracts from the start.

## Alternatives Considered

| Alternative | Why not |
|---|---|
| **Per-value, per-change lineage table** | Duplicates audit; unbounded growth; hard to keep consistent |
| **Adopt an open lineage standard/tool (e.g., an OpenLineage-style pipeline)** | Designed for data-platform jobs and datasets, not for record-level HR field provenance; heavy for this need; concepts may still inspire naming |
| **Manually maintained lineage documentation** | Drifts immediately; not queryable |
| **Lineage only at the entity level** | Too coarse for the field-level ownership story |

## Consequences

**Positive**

- No new large store; the view stays correct as long as mappings, contracts, and provenance are correct.
- Answers the brief's questions (where from, what downstream, which events) with data that exists for other reasons.
- Forces good discipline: versioned mappings and declared payload contracts.

**Negative**

- Query complexity: a join across several sources; may need a read model for performance.
- Accuracy depends on the discipline of declaring payload contracts and mapping versions.
- Historical lineage for a value reflects the mapping version recorded, so mapping history must be retained.
- Cannot show what a destination did with data after acknowledgement.

## Reconsider When

- Lineage queries become slow or complex (add a read model).
- Regulators or customers require immutable per-change lineage (then extend the audit design).
- More provider types make mapping definitions hard to compare (consider declarative mappings, [ADR-0004](0004-hris-adapter-boundary.md)).

## Interview line

"I don't store lineage; I compute it from versioned mappings, declared payload contracts, provenance on the value, and delivery records, all of which exist anyway, so it stays consistent instead of becoming a second, drifting history."

## Related Decisions

[ADR-0004](0004-hris-adapter-boundary.md) · [ADR-0005](0005-source-of-truth-ownership.md) · [ADR-0016](0016-operations-model.md)
