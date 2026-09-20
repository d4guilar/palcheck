# 07. Data Lineage and Ownership

The lineage chain from the brief, the single capture point that feeds it, and the ownership check on every write. The provider field name is illustrative.

## Field lineage (what a technical user can inspect)

```mermaid
flowchart LR
  src["Source system field<br/>e.g. business title<br/>(illustrative)"]
  ad["Adapter<br/>provider DTO"]
  tr["Mapper transformation<br/>id + mapping version"]
  nw["NormalizedWorker<br/>assignment.jobTitle"]
  pc["PalCheck<br/>Assignment.jobTitle<br/>ownership: EXTERNAL(system)"]
  idn["Consumer: Identity<br/>(directory attribute)"]
  eq["Consumer: Equipment"]
  dir["Consumer: Directory"]

  src --> ad --> tr --> nw --> pc
  pc --> idn
  pc --> eq
  pc --> dir
```

Shown to the user: source system, source field, transformation, destination field, mapping version, last sync, downstream consumers, correlation ID. Values are not shown by default.

## Single capture point

```mermaid
flowchart TB
  w["Write to a governed field<br/>with write context<br/>(actor kind, source, sync run,<br/>mapping version, correlation ID)"]
  chk{"Ownership policy<br/>allows this writer?"}
  rej["Rejected (USER on EXTERNAL)<br/>or Divergence recorded<br/>(SYNC on PALCHECK)"]
  cr["Change record<br/>old, new, effective date, reason,<br/>correlation ID, causation ID"]
  a["Audit record"]
  p["Provenance updated<br/>source, field, run, mapping version,<br/>ownership snapshot"]
  e["Domain event<br/>+ outbox row"]
  view["Lineage view<br/>(computed by join)"]

  w --> chk
  chk -->|"no"| rej
  chk -->|"yes"| cr
  cr --> a
  cr --> p
  cr --> e
  p --> view
  e --> view
  a -.-> view
  maps["Mapping definitions<br/>+ outbound payload contracts"] --> view
```

## Ownership modes per governed field

```mermaid
flowchart LR
  subgraph modes["Per tenant, per governed field: exactly one"]
    pcm["PALCHECK<br/>PalCheck users and processes write"]
    exm["EXTERNAL(system)<br/>only that system's sync writes"]
    drm["DERIVED<br/>nobody writes; recomputed from<br/>declared inputs"]
  end
  standalone["Standalone tenant"] -->|"default"| pcm
  connector["Enable connector +<br/>previewed, audited ownership flip"] --> exm
  policy["Country-policy annotations"] --> drm
```

**How to read this.** Ownership *policy* (who may write), *provenance* (where the value came from), *audit* (who changed it), and the *event* (what others should react to) are four different records made from **one** change record in **one** transaction. The lineage view is a
join over them plus mapping definitions and payload contracts; no per-value lineage table is stored. The ownership flip on connector enablement is the test of the standalone-first decision ([ADR-0017](../adr/0017-primary-product-mode.md)).

Details: [DATA_OWNERSHIP_AND_LINEAGE.md](../DATA_OWNERSHIP_AND_LINEAGE.md), [ADR-0005](../adr/0005-source-of-truth-ownership.md), [ADR-0015](../adr/0015-data-lineage.md).
