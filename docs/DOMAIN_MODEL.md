# Domain Model (conceptual)

> **Reconciliation notice (2026-09-19):** the Phase 1 domain (Tenant, Legal Entity, Org Unit, Person, Employment, Assignment) is specified concretely in [PHASE_1_IMPLEMENTATION_PLAN.md](PHASE_1_IMPLEMENTATION_PLAN.md) section 4. Phase 1 keeps valid business dates and per-row revisions; audit-derived recorded time below is **evidence, not an implemented bitemporal query model** ([ADR-0018](adr/0018-module-transactions-and-persistence.md) decision 7, provisional). `workforce` owns the committed hire fact.

Status: Phase 0 draft · Conceptual only. This is **not** a schema, and no persistence decisions are implied beyond the
open questions listed. Legal content is `UNVERIFIED` / `RESEARCH REQUIRED` / `NOT IMPLEMENTED` throughout.

## 1. Purpose

Define the vocabulary and shape of the domain so that modules, integrations, and localization all speak the same language, and so
the model survives both operating modes (standalone-first, overlay-capable; see [ADR-0017](adr/0017-primary-product-mode.md)).

## 2. Three layers of concepts

| Layer | What it is | Examples | Where it lives |
|---|---|---|---|
| **Global workforce primitives** | Stable, country-neutral ideas PalCheck models directly | Person, Employment, Assignment, Org Unit, Legal Entity, Leave, Termination | `workforce`, `lifecycle` (and deferred modules) |
| **Local employment concepts** | Country-specific ideas with native names and local meaning | Aguinaldo, Cesantía, Preaviso, CCSS, Incapacidad, Jornada laboral | `countrypolicy` (Legal Concept Registry) |
| **Integration facts** | Things that happened between systems | Payroll enrollment request, provisioning request, external worker update | `integrations` |

Bridge between the first two: the **ConceptMapping** (see [ADR-0012](adr/0012-legal-concept-registry.md)), which records how a local
concept relates to a global primitive, with an equivalence level of `EXACT`, `PARTIAL`, `NONE`, or `UNKNOWN`.

Every local concept is described on three separate layers, per the brief. They must never be merged:

1. **DOMAIN CONCEPT.** What is this thing?
2. **LEGAL RULE.** What does current law actually require? *(Requires authoritative research and professional review.)*
3. **SOFTWARE BEHAVIOR.** How does PalCheck represent or process it?

## 3. Terminology: one word, one meaning

The brief uses "organization" for three different things. PalCheck never uses the bare word.

| Term | Meaning | Notes |
|---|---|---|
| **Tenant** | A customer of PalCheck. The unit of data isolation. | Present on every record from day one ([ADR-0003](adr/0003-postgresql-and-tenancy.md)). |
| **Legal Entity** | The employer of record: a registered company in a country. | Local rules attach here, with the country of employment. A tenant has one or more. |
| **Org Unit** | A node in the internal hierarchy: department, team, cost center. | Effective-dated; a tree per tenant. |
| **Person** | A human being known to the tenant. | Exists independently of any job. Tenant-scoped. |
| **Employment** | The relationship between a Person and a Legal Entity over time. | Has a worker type, start, end, status history. A Person can have several over time (rehire) or concurrently. |
| **Assignment** | The Person's current placement within an Employment: Org Unit, Position/Job, Manager, location, schedule. | Effective-dated; changes drive "job change". |
| **Position / Job** | What the placement is called and classified as. | Provider code lists are mapped, not copied. |
| **Worker** | Umbrella term from external HRIS meaning "employee or contingent worker". | Used only at the integration boundary; PalCheck uses Person + Employment. |
| **Employee** | A role a Person holds through an Employment of the employee worker type. | Not a peer entity. |
| **Manager** | A relationship between Assignments (reports-to). | Not an entity. |

## 4. Core concepts (conceptual aggregates)

Ownership by module is in [MODULES.md](MODULES.md).

- **Person**: names, identifiers `(type, country, value)`, contact points, date of birth. High-tier PII. No job data.
- **Employment**: legal entity, worker type (employee, contractor, other: RESEARCH REQUIRED for Costa Rica), start/end, status
  history, termination facts. The lifecycle state lives here.
- **Assignment** (effective-dated): org unit, position/job, manager link, work location, schedule reference.
- **Org Unit** (effective-dated tree), **Legal Entity**, **Location**.
- **Compensation** *(deferred module)*: `Money(amount, currency)` elements over time. PalCheck records; it does not calculate pay.
- **Leave** *(deferred module)*: requests, approvals, balances. Local leave concepts (e.g., Incapacidad) are registry concepts first.
- **Local supplemental data** (PalCheck-owned): data local concepts need that a global model has no place for, attached to an
  Employment. Which fields exist is a research output, not a Phase 0 decision.

## 5. Employment lifecycle

```
Hire request (pre-hire)
   -> Hired
   -> Onboarding
   -> Active
        |-- Job change (Assignment change)
        |-- Compensation change
        |-- Leave (status overlay)
   -> Offboarding
   -> Terminated
   -> (Rehire = new Employment for the same Person)
```

- **Pre-hire is an inbound hire request**, not a recruiting pipeline. Recruiting is a non-goal.
- Lifecycle *processes* (hire, change, terminate) are explicit state machines in `lifecycle`; the resulting *state* is owned by `workforce`.
- The status set is PalCheck's own; each external provider gets a versioned mapping to it. Whether local law requires additional
  statuses is RESEARCH REQUIRED.
- Termination is a global primitive. Its local facets (Preaviso, Cesantía, liquidación) are registry concepts, all `UNRESEARCHED`,
  `NOT IMPLEMENTED`. The model stores the *fact* of termination and its reason; it does not compute anything from it.

## 6. Time: effective dating

HR data is valid over a period, often future-dated (a promotion next month) or retroactive (a correction to last month).

- Two time axes matter: **valid time** (when a fact is true in the business) and **recorded time** (when PalCheck learned it).
- Proposal (not yet decided; see OQ-03): model **valid time explicitly** on Employment status, Assignment, and Compensation;
  obtain **recorded time from the append-only audit trail** rather than building full bitemporal tables everywhere.
- Every "current" query is "as of a date", defaulting to today. Scheduled effective changes fire events when they *become* effective.
- Retroactive changes are legal and common in payroll-adjacent domains; the model must allow them and the events must say so.

## 7. Ownership and provenance (summary)

Every field belongs to a tenant-level **ownership policy** (`PALCHECK`, `EXTERNAL`, or `DERIVED`) and every stored value carries
**provenance** (source system, source field, sync run, mapping version). Standalone mode: everything defaults to `PALCHECK`.
Full model: [DATA_OWNERSHIP_AND_LINEAGE.md](DATA_OWNERSHIP_AND_LINEAGE.md), [ADR-0005](adr/0005-source-of-truth-ownership.md).

## 8. Identifiers

National and tax identifiers differ by country in format and meaning. Model an identifier as `(type, country, value)` with
a sensitivity tier, never a single `nationalId` field. Formats and validation rules are RESEARCH REQUIRED; synthetic identifiers
in demos are invalid-by-construction ([SECURITY.md](SECURITY.md)).

## 9. Events (conceptual catalogue)

Events are **past-tense business facts**. The brief's list, refined:

| Event | Producer | Notes |
|---|---|---|
| `PersonRegistered` | workforce | Replaces the CRUD-flavored `EmployeeCreated`. |
| `EmployeeHired` | lifecycle | The hire transaction committed. |
| `EmploymentStarted` | workforce | The start date became effective; may fire later than the hire, from a scheduler. |
| `JobChanged` | workforce | Assignment change became effective. |
| `CompensationChanged` | *(deferred)* | Not in Phase 1. |
| `LeaveRequested`, `LeaveApproved` | *(deferred)* | Not in Phase 1. |
| `EmployeeTerminated` | lifecycle | Termination committed. |
| `ProvisioningRequested`, `ProvisioningCompleted` | integrations | Integration facts about a downstream system. |
| `ExternalWorkerUpdated` | integrations | Inbound from an HRIS; overlay-era. |
| `IntegrationFailed` | integrations | Operational event, feeds `operations`; not a domain fact. |

**Envelope** (all integration events): `eventId`, `eventType`, `schemaVersion`, `aggregateId`, `occurredAt`, `correlationId`,
`causationId`, `source`, `payload`. Additions proposed: `tenantId`, `recordedAt`, `effectiveAt` (when the fact takes/took effect).
Internal domain events and published integration events are different things; only the latter are versioned public contracts
([ADR-0006](adr/0006-event-driven-integration.md)).

## 10. Invariants vs. policy

Keep two kinds of rules apart:

- **Invariants** PalCheck enforces regardless of country (examples): an Employment belongs to exactly one Legal Entity; an Assignment
  belongs to exactly one Employment; a terminated Employment has an end date; every record belongs to exactly one Tenant.
- **Policy** that varies by jurisdiction (examples of *questions*, not answers): may a Person hold overlapping Employments in one
  Legal Entity? what data is mandatory at hire? Those are `countrypolicy` questions and are RESEARCH REQUIRED. Do not encode them
  as invariants.

## 11. Deliberately not modeled yet

Recruiting, payroll calculation, benefits plan design, time tracking, performance, learning, expenses, documents/attachments,
consent management, org design tools, regulatory reporting formats, multi-currency treasury. Each may return; none should be
precluded.

## 12. Open domain questions

- **OQ-01** Worker types in Costa Rica and the classification concepts around them. RESEARCH REQUIRED.
- **OQ-02** Which Costa Rican "employer structure" concepts (registration, representatives) belong on Legal Entity? RESEARCH REQUIRED.
- **OQ-03** History pattern for effective-dated data: separate history tables, range columns with exclusion constraints, or
  temporal tables? Decide with a spike before the first schema; affects [ADR-0003](adr/0003-postgresql-and-tenancy.md).
- **OQ-04** Does a Person span tenants (e.g., a contractor for two customers)? Default: no, a Person is tenant-scoped.
- **OQ-05** What is the minimum local supplemental data for a Costa Rican hire? Output of research, not a design guess.
- **OQ-06** Status taxonomy: what PalCheck set can represent both external HRIS statuses and local ones without distortion?
