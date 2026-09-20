# 01. System Context

Who and what PalCheck talks to. Phase 1 shows solid lines; dashed lines arrive later.

```mermaid
flowchart LR
  hr["HR administrator<br/>(small employer)"]
  staff["Managers and employees"]
  ops["Technical operator<br/>(IT, integration engineer)"]

  pc["<b>PalCheck</b><br/>Workforce translation<br/>and orchestration"]

  idp["Identity provider<br/>(OIDC, authentication)"]
  pay["Payroll system<br/>(simulated in Phase 1)"]
  idt["Identity provisioning target<br/>(simulated in Phase 1)"]
  eqp["Equipment management<br/>(simulated in Phase 1)"]
  hris["External HRIS<br/>(Workday-shaped reference, Phase 2)"]
  ben["Benefits and notifications<br/>(later)"]

  hr -->|"manages people, hires"| pc
  staff -->|"views self and reports, approves"| pc
  ops -->|"monitors, retries"| pc

  pc -->|"authenticates via"| idp
  pc -->|"payroll requests"| pay
  pc -->|"account provisioning"| idt
  pc -->|"equipment requests"| eqp
  hris -.->|"workers, org changes"| pc
  pc -.->|"notifications, plans"| ben
```

**How to read this.** PalCheck is the system of record in standalone mode (primary). In overlay mode (Phase 2+), the external HRIS becomes the owner of some fields and PalCheck consumes and enriches its data
([ADR-0017](../adr/0017-primary-product-mode.md)). Every system except the identity provider is *simulated* in Phase 1 and is labelled so in the product.

Related: [ARCHITECTURE.md](../ARCHITECTURE.md), [02 container diagram](02-container.md).
