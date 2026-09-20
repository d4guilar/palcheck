# 02. Containers

Deployable units and data stores. One application process holds every module; background work is a profile of the same artifact, not a separate service.

```mermaid
flowchart TB
  user["User<br/>(browser)"]

  subgraph phase1["Phase 1"]
    web["Web app<br/>React + TypeScript SPA"]
    subgraph api["PalCheck API - Spring Boot modular monolith (one deployable)"]
      direction LR
      rest["REST API<br/>OIDC resource server"]
      mods["Modules<br/>workforce, lifecycle, countrypolicy,<br/>integrations, access, audit, operations"]
      bg["Background profile<br/>outbox dispatcher, scheduled jobs"]
      sim["Simulated downstream systems<br/>Payroll, Identity, Equipment<br/>(in-process, fault-injectable)"]
    end
    db[("PostgreSQL<br/>one database, one schema per module")]
    idp["Identity provider<br/>(OIDC, dev instance locally)"]
  end

  subgraph phase2["Phase 2"]
    mock["Reference HRIS<br/>Workday-shaped mock, synthetic data"]
  end

  subgraph later["Later, only when justified"]
    broker["Message broker<br/>(ADR-0008 exit criteria)"]
    obs["Observability stack<br/>OpenTelemetry, Prometheus, Grafana"]
  end

  user --> web
  web -->|"HTTPS, bearer token"| rest
  rest --> mods
  bg --> mods
  mods --> db
  mods --> sim
  rest -->|"token validation"| idp
  mods -.->|"HTTP adapter"| mock
  bg -.-> broker
  api -.-> obs
```

**How to read this.** Dashed lines and the *Phase 2* and *Later* groups are not built yet. There is no broker until [ADR-0008](../adr/0008-broker-none-yet.md)'s exit criteria are met. The background profile can be split into its own process
without code changes when a reason appears ([ARCHITECTURE.md](../ARCHITECTURE.md) section 10).

Related: [01 system context](01-system-context.md), [03 module map](03-module-map.md).
