# 04. External HRIS Integration

The inbound translation pipeline (Phase 2). Provider vocabulary exists only inside the adapter; everything to the right of the mapper speaks PalCheck's contract.

## Pipeline

```mermaid
flowchart LR
  ext["External HRIS<br/>(Workday-shaped reference)"]

  subgraph integ["integrations module"]
    direction LR
    adapter["Adapter<br/>protocol, auth, paging,<br/>provider DTOs"]
    raw["Raw landing<br/>redacted, retention-limited"]
    mapper["Mapper<br/>versioned mapping to<br/>NormalizedWorker + extensions"]
    recon["Reconciliation<br/>identity link, change detection,<br/>ordering, conflicts, quarantine"]
  end

  wf["workforce<br/>write with write context<br/>ownership enforced, provenance stored"]
  ev["Domain event<br/>+ outbox row<br/>(same transaction)"]
  pol["countrypolicy<br/>enrichment: annotations<br/>and derived facts"]
  orch["Downstream orchestration<br/>payroll, identity, equipment"]

  ext --> adapter --> raw --> mapper --> recon --> wf --> ev
  ev --> pol
  ev --> orch
```

## One delta sync, with the ownership decision

```mermaid
sequenceDiagram
  autonumber
  participant J as Sync job
  participant A as Adapter
  participant M as Mapper
  participant R as Reconciliation
  participant W as workforce
  participant O as Outbox

  J->>A: pull workers since watermark
  A-->>J: provider DTO page
  J->>M: map (mapping version N)
  M-->>J: NormalizedWorker + mappingReport
  J->>R: reconcile record
  R->>R: match identity link, compare hash and source version
  alt stale (older than last applied)
    R-->>J: STALE, recorded, not applied
  else changed
    R->>W: write(fields, writeContext SYNC)
    W->>W: check ownership policy per governed field
    alt field is EXTERNAL and sender is owner
      W->>W: apply, store provenance, audit
      W->>O: outbox row (same transaction)
    else field is PALCHECK-owned
      W-->>R: not applied, Divergence recorded
    end
  end
  J->>J: record per-record result and advance watermark
```

**How to read this.** Nothing skips a stage. A record that cannot be applied safely is *quarantined* with its reason, never silently dropped or force-applied. Unmapped provider data is preserved in `extensions` and reported in the mapping report. Policy enrichment and downstream
orchestration react to the *event*, so they behave identically whether a change came from a PalCheck user or from a sync.

Details: [INTEGRATION_ARCHITECTURE.md](../INTEGRATION_ARCHITECTURE.md), [DATA_OWNERSHIP_AND_LINEAGE.md](../DATA_OWNERSHIP_AND_LINEAGE.md), [ADR-0004](../adr/0004-hris-adapter-boundary.md).
