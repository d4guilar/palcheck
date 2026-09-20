# 08. Enterprise Systems Topology

The operations view of connected systems, with the health states the console can show. The example statuses below are **illustrative** (borrowed from the brief), not fixed content. In the product the graph is derived from the connector registry and observed flows,
and always has a table equivalent.

```mermaid
flowchart TB
  hris["Reference HRIS<br/>Category: HRIS<br/>Health: Healthy<br/>Last sync: 4 min ago"]:::healthy
  pc["PalCheck<br/>Category: Core"]:::healthy
  pay["Payroll CR (simulated)<br/>Category: Payroll<br/>Health: Degraded"]:::degraded
  idt["Identity Provider (simulated)<br/>Category: Identity<br/>Health: Healthy"]:::healthy
  eqp["Equipment (simulated)<br/>Category: IT Operations<br/>Health: Healthy"]:::healthy
  ben["Benefits (later)<br/>Health: Unknown"]:::unknown
  off["Example offline system<br/>Health: Offline"]:::offline

  hris -->|"ExternalWorkerUpdated"| pc
  pc -->|"EmployeeHired, JobChanged"| pay
  pc -->|"ProvisioningRequested"| idt
  pc -->|"EmployeeHired"| eqp
  pc -.->|"planned"| ben
  pc -.->|"example"| off

  classDef healthy fill:#e8f5e9,stroke:#2e7d32,color:#1b1b1b
  classDef degraded fill:#fff8e1,stroke:#b26a00,color:#1b1b1b
  classDef offline fill:#ffebee,stroke:#c62828,color:#1b1b1b
  classDef unknown fill:#f5f5f5,stroke:#616161,stroke-dasharray: 4 3,color:#1b1b1b
```

## Table equivalent (the accessible form)

| System | Category | Simulated | Direction | Event types | Health | Reason | Last successful interaction |
|---|---|---|---|---|---|---|---|
| Reference HRIS | HRIS | yes | inbound | ExternalWorkerUpdated | Healthy | Recent syncs succeeded | 4 min ago |
| Payroll CR | Payroll | yes | outbound | EmployeeHired, JobChanged | Degraded | Several recent attempts failed (retrying) | *(example)* |
| Identity Provider | Identity | yes | outbound | ProvisioningRequested | Healthy | Recent attempts succeeded | *(example)* |
| Equipment | IT Operations | yes | outbound | EmployeeHired | Healthy | Recent attempts succeeded | *(example)* |
| Benefits | Benefits | yes | outbound (planned) | none yet | Unknown | No data | never |

## Health states

| State | Meaning | Never means |
|---|---|---|
| Healthy | Recent signals within thresholds | "No data" |
| Degraded | Some signals outside thresholds; still working, possibly slowly or with retries | Down |
| Offline | Sustained failure or failed probes | A single failed attempt |
| Unknown | Too little recent data to judge | Healthy |

**How to read this.** Color is a redundant cue: every state has a text label and icon. Thresholds and windows are configuration chosen in Phase 1 from real delivery data. Simulated systems carry a *Simulated* label in the product. Details: [ENTERPRISE_SYSTEMS.md](../ENTERPRISE_SYSTEMS.md),
[ADR-0016](../adr/0016-operations-model.md).
