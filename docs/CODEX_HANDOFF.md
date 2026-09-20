# Codex Handoff: Phase 0 (Architecture and Product Definition)

> **Superseded in scope (2026-09-19):** this is Claude's first-pass Phase 0 handoff, preserved as a record. Codex's review responses and Daniel's decisions are in [CODEX_ARCHITECTURE_REVIEW.md](CODEX_ARCHITECTURE_REVIEW.md) and the ADR acceptance records. The proposed Phase 1 boundary in section 6 was replaced by [PHASE_1_IMPLEMENTATION_PLAN.md](PHASE_1_IMPLEMENTATION_PLAN.md).

> **Current handoff:** use [PHASE_1_REVIEW_HANDOFF.md](PHASE_1_REVIEW_HANDOFF.md) for the completed Phase 1 review and next-prompt boundary. The text below is historical.

From: Claude Code (architect / critic / documentation owner) · Date: 2026-09-19 · To: OpenAI Codex, then Daniel Aguilar
Status: Phase 0 complete pending your review. **Nothing is Accepted.** All 17 ADRs are Proposed. Only Daniel accepts, amends, or rejects.

## 0. How to use this handoff

1. Read, in order: [../README.md](../README.md), [../MASTER_PROJECT_BRIEF.md](../MASTER_PROJECT_BRIEF.md) (note the Amendments), [../AGENTS.md](../AGENTS.md), [PRODUCT_VISION.md](PRODUCT_VISION.md), [PHASE0_CRITIQUE.md](PHASE0_CRITIQUE.md),
   [ARCHITECTURE.md](ARCHITECTURE.md), [MODULES.md](MODULES.md), [DOMAIN_MODEL.md](DOMAIN_MODEL.md), [INTEGRATION_ARCHITECTURE.md](INTEGRATION_ARCHITECTURE.md),
   [DATA_OWNERSHIP_AND_LINEAGE.md](DATA_OWNERSHIP_AND_LINEAGE.md), the ADRs in [adr/](adr/README.md), [AI_COLLABORATION.md](AI_COLLABORATION.md), [WORKLOG.md](WORKLOG.md).
   Then as needed: [ENTERPRISE_SYSTEMS.md](ENTERPRISE_SYSTEMS.md), [SECURITY.md](SECURITY.md), [UI_SYSTEM.md](UI_SYSTEM.md), [localization/](localization/README.md), [diagrams/](diagrams/01-system-context.md), [ROADMAP.md](ROADMAP.md).
2. **Review as an implementer**: your unique value in Phase 0 is spotting what will not survive contact with real code, libraries, and one developer's time.
3. **Record your review** as a `CODEX` entry appended to [WORKLOG.md](WORKLOG.md), using the finding format in [AI_COLLABORATION.md](AI_COLLABORATION.md) section 5. Do not edit Claude's documents to "fix" things; disagree per section 6 of that document.
4. **Do not implement anything.** The phase gate is in [../AGENTS.md](../AGENTS.md) section 2.

## 1. Architecture decisions (all Proposed)

| ADR | Decision | Confidence | Challenge priority |
|---|---|---|---|
| [0017](adr/0017-primary-product-mode.md) | **Standalone-first**, overlay as a preserved seam. Daniel's decision, over the reviewer's overlay-first recommendation | Decided by Daniel | Challenge only the *mitigations*, not the choice |
| [0001](adr/0001-modular-monolith.md) | Modular monolith; 7 modules + 2 deferred; no cross-module FK/joins; one command = one transaction = one module | High on monolith; **Medium on the 7-module cut** | High |
| [0002](adr/0002-java-spring-boot.md) | Java + Spring Boot, framework-light domain | High (partly a stated career goal) | Low |
| [0003](adr/0003-postgresql-and-tenancy.md) | PostgreSQL; pooled multi-tenancy with `tenant_id` everywhere; RLS as backstop pending spike; schema per module | High on PostgreSQL; **Medium on tenancy details** | High |
| [0004](adr/0004-hris-adapter-boundary.md) | Adapter + Mapper + PalCheck-owned `NormalizedWorker`; capability flags; raw landing; read-only inbound | Medium-High | Medium |
| [0005](adr/0005-source-of-truth-ownership.md) | Ownership policy (per tenant, governed field) separate from provenance; single writer with write context | Medium-High | High |
| [0006](adr/0006-event-driven-integration.md) | Internal vs. published events; at-least-once + idempotent; KEY/LOOKUP/NONE destinations; no exactly-once claims | High | Medium |
| [0007](adr/0007-transactional-outbox.md) | Explicit outbox, `SKIP LOCKED` dispatcher, lease, port-based publisher | High on pattern; **open on implementation** | High |
| [0008](adr/0008-broker-none-yet.md) | **No broker** in Phase 1/2, with measurable exit criteria | High | Medium (expect pushback; be honest) |
| [0009](adr/0009-frontend-architecture.md) | React + TS SPA, generated API types, headless components + own tokens, i18n from day one | Medium | Medium |
| [0010](adr/0010-ui-principles-and-accessibility.md) | WCAG 2.2 AA in definition of done; Apple-inspired, not imitated | High | Low |
| [0011](adr/0011-country-policy-architecture.md) | Capability-based packs, no defaults/inheritance, second-instance rule | Medium (only one country exists) | Medium |
| [0012](adr/0012-legal-concept-registry.md) | Five entities; gates; content as reviewed files, DB as projection | Medium | High |
| [0013](adr/0013-authentication.md) | OIDC resource server; IdP product deferred | High on pattern | Low |
| [0014](adr/0014-authorization.md) | Permission x scope; module-supplied scope predicate; DRAFT roles | Medium | High (ergonomics) |
| [0015](adr/0015-data-lineage.md) | Lineage computed by join; no per-value store | Medium | Medium |
| [0016](adr/0016-operations-model.md) | `operations` read plane derived from real flows; phased | Medium | Medium |

Key non-ADR decisions: Phase 0 repository contains docs only; the brief's `workers/`, `packages/`, `infrastructure/`, `scripts/`, `.github/`, `docker-compose.yml` are deferred ([ARCHITECTURE.md](ARCHITECTURE.md) section 11); the term "organization" is banned in designs
in favor of **Tenant / Legal Entity / Org Unit** ([DOMAIN_MODEL.md](DOMAIN_MODEL.md) section 3); PalCheck **does not compute pay**, in any mode.

## 2. Unresolved questions

Blocking = must be answered before the related Phase 1 work starts. IDs point to the document that owns the question.

| ID | Question | Blocking? | Owner |
|---|---|---|---|
| OQ-03 | Effective-dating history pattern (range types + exclusion constraints, history tables, temporal tables) | Yes, before first schema | Codex spike S1 |
| SQ-01 / [ADR-0003](adr/0003-postgresql-and-tenancy.md) | Is row-level security workable with the pool and data-access layer? | Yes, before first schema | Codex spike S2 |
| IQ-05 | Explicit outbox vs. Spring Modulith event publication registry | Yes, before outbox code | Codex spike S3 |
| MQ-01 to MQ-03 | Who publishes `EmploymentStarted`/`JobChanged`; split `countrypolicy`?; is `operations` a module yet? | No, but shapes packages | Codex + Daniel |
| LQ-01, LQ-02 | Governed-field list; provenance storage shape | Before `workforce` schema | Codex + Claude |
| SQ-03 | SPA-held tokens vs. same-origin session | Before first authenticated screen | Codex spike S6 |
| UQ-01 | React Aria Components vs. Radix | Before first component | Codex spike S5 |
| RQ-01 | Registry content file format and location | Before registry loader | Codex spike S7 |
| IQ-03 | Simulator in-process vs. container | No (recommendation: in-process) | Codex |
| SQ-02 | Erasure mechanism (per-subject keys vs. pseudonymization) and backups | Before any real-shaped data | Daniel + research ID-3/ID-4 |
| LR-01 | Who is the professional reviewer, on what terms and cost | Blocks *any behavior*, not Phase 1 | Daniel |
| BIZ-02 | Employer invention-assignment / outside-activity / conflict-of-interest review of Daniel's agreements | Blocks the *business* option, not the design | Daniel |
| BIZ-05 | Trademark clearance for "PalCheck" | No | Daniel |
| (build) | Gradle or Maven; single build with package modules vs. multi-module | Before scaffolding | Codex spike S8 |
| OQ-01, OQ-02, OQ-05, OQ-06 | Costa Rica worker types, employer structures, minimum local data, status taxonomy | Research-dependent | Research P1 |

## 3. Areas Codex should challenge (prioritized)

1. **The 7-module cut** ([MODULES.md](MODULES.md), [ADR-0001](adr/0001-modular-monolith.md)). Is `operations` too thin to be a module? Is `lifecycle` separate enough from `workforce`? Does the dependency table hold once real code exists? Is "no cross-module FK/join" livable with Spring Data/JPA, or does it force awkward read models everywhere?
2. **Outbox implementation** ([ADR-0007](adr/0007-transactional-outbox.md)). Polling with `SKIP LOCKED`, leases, per-consumer state, wake-up hints, retention. Is per-consumer delivery state modeled well? Would Spring Modulith's registry be simpler *without* hiding semantics? Design the crash-between-commit-and-dispatch test.
3. **No-broker default** ([ADR-0008](adr/0008-broker-none-yet.md)). Are the exit criteria right and measurable? Is anything in the design accidentally broker-shaped in a way that would make adoption harder?
4. **Tenancy** ([ADR-0003](adr/0003-postgresql-and-tenancy.md)). Pooled + required tenant parameter + optional RLS. Hibernate multi-tenancy support vs. explicit parameters; RLS with connection pooling; per-module schemas with Flyway; testing cross-tenant access for every endpoint.
5. **Effective dating** ([DOMAIN_MODEL.md](DOMAIN_MODEL.md) section 6). Is "valid time explicit, recorded time from audit" enough? How does it map to the persistence approach? Is JPA the right tool, or Spring Data JDBC/jOOQ for temporal queries? Retroactive change events.
6. **Ownership enforcement** ([DATA_OWNERSHIP_AND_LINEAGE.md](DATA_OWNERSHIP_AND_LINEAGE.md)). Single writer + write context: where does it get awkward? Governed-field list size; provenance storage; cost of the "one change record drives audit, provenance, and event in one transaction" rule.
7. **Legal Concept Registry as files** ([ADR-0012](adr/0012-legal-concept-registry.md)). Five entities: too many for Phase 1? File format, loader, CI validation of status gates and effective ranges. Is `orientationNote` worth the risk?
8. **Authorization ergonomics** ([ADR-0014](adr/0014-authorization.md)). Module-supplied scope predicate: how does it look in Spring Security method security or explicit service calls without becoming scattered? Is a small internal policy helper warranted?
9. **Frontend choices** ([ADR-0009](adr/0009-frontend-architecture.md)). Headless base, message-format library, OpenAPI-to-TypeScript generation, BFF vs. token-in-SPA.
10. **Testing strategy feasibility** ([ARCHITECTURE.md](ARCHITECTURE.md) section 8), especially failure-injection tests with Testcontainers and property-based effective-dating tests, on a Windows development machine.
11. **The critique itself** ([PHASE0_CRITIQUE.md](PHASE0_CRITIQUE.md)). Where is Claude wrong, overconfident, or over-engineering the design? Are 17 ADRs and five registry entities themselves portfolio-optics complexity?
12. **Claude's factual claims about libraries and standards** are marked `Unverified` where they occur (Spring Modulith registry behavior, React Aria/Radix capabilities, Redpanda licensing, PostgreSQL feature fit). Please verify against current documentation and correct.

## 4. Implementation feasibility concerns

- **One developer, seven modules, a UI, and an operations plane.** The Phase 1 boundary below is deliberately a thin slice of each. If it still looks too big, cut `operations` to a list of delivery attempts and health text first.
- **JPA and effective dating / no cross-module joins.** JPA is comfortable for aggregates and awkward for temporal queries and read models; be ready to use SQL-first access for those.
- **RLS with pooling and JPA.** Session variables and connection reuse are a known source of subtle bugs; the spike must include leak tests.
- **Module enforcement inside one Gradle/Maven module** relies on tests, not the compiler. A multi-module build gives compile-time enforcement at higher ceremony. Decide with data (spikes S4 and S8).
- **Testcontainers on Windows** needs Docker (Docker Desktop or an alternative); confirm the local dev story early, including line endings and paths in scripts.
- **Two-language contracts.** JSON Schema as source of truth with generated Java and TypeScript types must be automated, or drift returns.
- **OIDC local provider** adds startup weight and configuration; pick the smallest thing that gives realistic tokens and a tenant claim.
- **Simulator determinism** (seeded scenarios) must be built in from the start or demos and tests will flake.
- **Scheduler locking** for effective-date transitions and retention jobs on a single node still needs a lock so a second instance cannot double-run.
- **Outbox growth and index design** should be considered with the first migration, not later.
- **Time handling:** instants vs. local business dates in a jurisdiction time zone; boundary tests around midnight are easy to under-test.

## 5. Risky assumptions (please attack each)

| # | Assumption | Why it might be wrong |
|---|---|---|
| RA-1 | A qualified Costa Rican professional can be engaged for concept review at a cost Daniel can bear | Cost, availability, scope of review unknown |
| RA-2 | A Workday-shaped mock over synthetic data teaches the right lessons without vendor documentation | Real integrations have quirks a mock will not reproduce; fidelity is unverifiable |
| RA-3 | Standalone-first demand exists among small Costa Rican employers | Unvalidated; overlay may be the real buyer |
| RA-4 | `ConceptMapping` equivalence (`EXACT`/`PARTIAL`/`NONE`/`UNKNOWN`) is the right abstraction for "translate, do not distort" | May prove too coarse, or the differences may not fit a single label |
| RA-5 | No broker is enough for the foreseeable load and consumers | True until an exit criterion fires; the port may not make migration as cheap as claimed |
| RA-6 | Pooled tenancy with RLS is operable by one developer | RLS plus pooling pitfalls |
| RA-7 | Effective dating with explicit valid time and audit-derived recorded time is sufficient | Payroll-adjacent retroactivity may need full bitemporality |
| RA-8 | English-first is fine because strings are externalized | Retrofitting still hits layout, content, and legal-term handling |
| RA-9 | The Costa Rica glossary orientation text is harmless | It was written from the brief and general familiarity, not sources. Spanish usage for *Patrono* and *Planilla*, and the CCSS role wording, are flagged Unverified. Treat any error as a defect to fix by research, not as fact |
| RA-10 | Clean-room rules are enough to keep employer knowledge out | Depends on discipline and on Daniel's own agreements (BIZ-02) |
| RA-11 | Operations Console can be built incrementally from real flows | Health thresholds and SLO usefulness are unproven until data exists |

## 6. Proposed Phase 1 boundary

Full plan: [ROADMAP.md](ROADMAP.md). Summary:

**Name:** Hire-to-provision vertical slice, standalone-first.

**In:** `workforce` (Person, Employment, Assignment, Org Unit, Legal Entity; effective-dated; ownership + provenance, all `PALCHECK`) · `access` (tenant, OIDC resource server with dev IdP, small permission x scope set) · `audit` (in-transaction) ·
`lifecycle` (hire state machine) · `integrations` (outbox, dispatcher, simulated Payroll/Identity/Equipment, fault injection, retry/backoff/dead letters, `KEY`/`LOOKUP`/`NONE`) · `countrypolicy` (read-only Costa Rica registry, all `UNRESEARCHED`/`DOCUMENTED`, "Learn" glossary) ·
`operations` minimal (connector list, delivery attempts with Retry, basic health with reasons) · web app with tokens and about four screens (People, Hire, Activity, Learn) · structured logs, correlation IDs, basic metrics · cross-tenant tests, canary log tests, synthetic-data generator, secret and dependency scanning.

**Out:** Workday-shaped connector (Phase 2); any statutory calculation or policy behavior; compensation and leave modules; change/termination processes; broker; OpenTelemetry/Prometheus/Grafana; incidents, SLOs, replay/reprocess; second country; Spanish UI; any real data.

**First work items (spikes S1 to S8)** are listed in [ROADMAP.md](ROADMAP.md); they need code and therefore need Daniel to open Phase 1.

**Acceptance scenario** (ROADMAP): hire; commit of state + audit + outbox; Payroll and Identity succeed; Equipment fails (simulated 503); visible retry succeeds; kill-and-restart between commit and dispatch still delivers with duplicates absorbed; `NONE` destination with unknown outcome halts for a human; another tenant cannot see anything.

**Needs from Daniel before Phase 1 starts:** accept, amend, or reject the ADRs; decide build tooling direction after Codex's spikes; decide who implements which module; consider BIZ-02; confirm P1 research ordering.

## 7. Intentionally not done in Phase 0 (so it is not mistaken for an omission)

No code, schema, endpoints, UI, Docker, CI, or dependency manifests; no `git init`; no registry content files; no `sources/` directory; no legal rules, numbers, or formulas anywhere; no research on countries other than Costa Rica (and only the *questions* for Costa Rica);
no OpenTelemetry, broker, or Workday work. `hello.txt` (an empty file that was already in the folder) was left untouched.

## 8. Verification performed in Phase 0

Documentation-only checks, run by script on 2026-09-19: every relative link and heading anchor resolves across all Markdown files (final result recorded in [WORKLOG.md](WORKLOG.md)); code fences balanced; every ADR has all required sections; no scaffolding files exist; no percentages, currency amounts,
or day/month counts in `docs/localization`. **Mermaid diagrams (15 blocks) were reviewed by eye and were not machine-validated** (no local Mermaid tooling; no installs were made). Please render them in a viewer that supports Mermaid.
