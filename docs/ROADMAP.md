# Roadmap

> **Reconciliation notice (2026-09-19):** Daniel approved refinement 6 (smaller implementation milestones) and **opened Phase 1: Authenticated Workforce Foundation**. The order of milestones is now **foundation, then provisioning, then overlay** ([ADR-0020](adr/0020-foundation-first-delivery-plan.md), accepted in part). Claude's original hire-to-provision proposal is preserved below as the *provisioning milestone*. Numbering of milestones after Phase 1 is Daniel's to assign; nothing is silently renumbered. Detailed Phase 1 plan: [PHASE_1_IMPLEMENTATION_PLAN.md](PHASE_1_IMPLEMENTATION_PLAN.md).

Status: **Phase 1 implemented and awaiting Daniel's review (opened 2026-09-19).** Evidence against the definition of done is in [PHASE_1_IMPLEMENTATION_PLAN.md](PHASE_1_IMPLEMENTATION_PLAN.md) section 1; known limitations in section 17. The independent [Codex Phase 1 review](PHASE_1_CODEX_REVIEW.md) is complete; [the handoff](PHASE_1_REVIEW_HANDOFF.md) recommends acceptance/publication validation before further features. Only Daniel opens a phase. Everything after Phase 1 is a plan, not a commitment.

## Phase gates

| Gate | Who decides | Evidence expected |
|---|---|---|
| Phase 0 -> Phase 1 | Daniel (**opened 2026-09-19**) | Codex review of the architecture recorded in the worklog; Phase 0 ADRs Accepted, amended, or rejected; open questions triaged (blocking vs. not); P1 research started or explicitly deferred; employer-IP question (BIZ-02) considered by Daniel |
| Phase 1 -> next milestone (provisioning) | Daniel | Phase 1 exit criteria met and demonstrable; retrospective in the worklog |
| Later | Daniel | Stated per phase below |

## Phase 0: Architecture and product definition (closed 2026-09-19)

**Output:** documentation and diagrams only ([../AGENTS.md](../AGENTS.md) section 2).
**Done when:** the items in the gate above are satisfied. Nothing is scaffolded: no Spring, React, schema, endpoints, Kafka, connector, Docker, or CI.

## Spikes (original Phase 0 proposal; superseded for Phase 1 by ADR-0020 and ADR-0022)

> Phase 1 replaces the eight open-ended spikes with recorded decisions in [ADR-0022](adr/0022-phase1-technical-baseline.md): Maven and packages with ArchUnit (S4, S8), Spring JDBC (S1), backend OIDC session (S6), row-level security deferred (S2). S3 (outbox) moves to the provisioning milestone, S5 (headless UI base) is answered by using native controls first, S7 (registry files) waits for content delivery.

They need code, so they belong to Phase 1. Each ends with a written result and, if warranted, an ADR update.

| Spike | Question | Feeds |
|---|---|---|
| S1 Effective dating | Range types + exclusion constraints vs. history tables, and how each maps to the chosen data-access approach | OQ-03, ADR-0003 |
| S2 Tenant isolation | Is row-level security workable with the pool and the framework, and what does it cost? | ADR-0003, SQ-01 |
| S3 Outbox | Explicit outbox vs. Spring Modulith registry; crash-between-commit-and-dispatch test | ADR-0007, IQ-05 |
| S4 Module enforcement | ArchUnit vs. Modulith verification; is "no cross-module FK/join" practical? | ADR-0001 |
| S5 Headless UI base | React Aria Components vs. Radix against the accessibility checklist | ADR-0009, UQ-01 |
| S6 Browser auth | SPA-held tokens vs. same-origin session | ADR-0013, SQ-03 |
| S7 Registry files | Format, loader, CI validation of status gates | ADR-0012, RQ-01 |
| S8 Build tooling | Gradle vs. Maven; single build with packages vs. multi-module | ARCHITECTURE |

## Phase 1: Authenticated workforce foundation (IMPLEMENTED, IN REVIEW)

**Goal.** One complete, usable vertical slice: an authenticated and authorized user can create, view and edit **synthetic** workforce records inside their Tenant, with tenant isolation, permission checks, revision history, lost-update protection, command idempotency, atomic audit, structured logs and meaningful automated tests.

**In scope:** `access` (tenant, principal mapping, roles to permissions), `audit`, `workforce` (Legal Entity, Org Unit, Person, Employment, Assignment); OIDC sign-in with a development identity provider; People list, record detail, create and edit, change history; PostgreSQL + Flyway; health endpoints; local development with Docker Compose; a focused CI workflow.

**Out of scope (Daniel's explicit non-goals):** payroll, Workday/SAP/Oracle integration, Kafka/Redpanda, outbox dispatch, provisioning, incident management, integration topology, SLO dashboards, Prometheus/Grafana/tracing, statutory or country-policy execution, microservices, mobile app, and anything real (data or law).

Full plan, definition of done and step order: [PHASE_1_IMPLEMENTATION_PLAN.md](PHASE_1_IMPLEMENTATION_PLAN.md).

## Provisioning milestone (formerly "Phase 1: Hire-to-provision vertical slice (standalone-first)"; numbering to be assigned by Daniel)

**Goal.** One end-to-end flow that proves the architecture and makes resilience visible.

**In scope**

- `workforce`: Person, Employment, Assignment, Org Unit, Legal Entity (effective-dated); ownership policy (all `PALCHECK`) and provenance from day one.
- `access`: tenant, OIDC resource server with a dev identity provider, small permission set with scopes.
- `audit`: in-transaction audit for governed changes; audited reads for sensitive fields.
- `lifecycle`: the **hire** process as an explicit state machine.
- `integrations`: transactional outbox, dispatcher, **simulated Payroll, Identity, Equipment**, controlled failure injection, retry with backoff, dead letters, idempotency modes (`KEY`/`LOOKUP`/`NONE`).
- `countrypolicy`: read-only Costa Rica concept registry (all `UNRESEARCHED`/`DOCUMENTED`), surfaced as a "Learn" glossary with honest status banners.
- `operations` (minimal): connector list, delivery attempts with **Retry**, basic health with reasons.
- Web app: design tokens and about four screens ([UI_SYSTEM.md](UI_SYSTEM.md) section 7).
- Observability: structured logs with correlation IDs, basic metrics, health endpoints.
- Security: cross-tenant negative tests, allow-list logging with canary tests, synthetic-data generator with invalid-by-construction identifiers, secret and dependency scanning in CI.

**Out of scope**

Workday-shaped connector; any statutory calculation or policy behavior; compensation and leave modules; change and termination processes (beyond what the data model needs); message broker; OpenTelemetry/Prometheus/Grafana stack; incidents, SLOs, replay/reprocess; second country; Spanish UI (strings externalized, though); real data of any kind.

**Reference acceptance scenario**

1. An HR administrator hires a synthetic person.
2. Workforce state, audit, and outbox rows commit together.
3. Payroll and Identity succeed; Equipment fails with a simulated 503.
4. The Activity screen shows the failure, attempt count, error class, and retry schedule; automatic retry then succeeds; correlation ID links everything.
5. Killing the process between commit and dispatch and restarting still delivers every event; any duplicate deliveries are absorbed by idempotent consumers, so each effect happens once (effectively-once, not a claim of exactly-once delivery).
6. A non-idempotent (`NONE`) simulated destination with an unknown outcome halts for human confirmation instead of retrying.
7. A user in another tenant cannot see or affect any of it.

**Exit criteria**

- Scenario above is demonstrable and covered by automated tests, including the failure-injection tests.
- Cross-tenant negative tests exist for every endpoint.
- Keyboard-only walkthrough and automated accessibility checks pass on all screens; reduced-motion path works.
- ADRs affected by spikes are Accepted or superseded; docs updated; worklog current.
- Daniel can answer the "Interview readiness" questions below from the docs.

## Overlay milestone (formerly "Phase 2: Overlay seam"; numbering to be assigned by Daniel)

Workday-shaped **reference HRIS** (separate mock process, synthetic data, no fidelity claim) plus adapter, versioned mapper, raw landing, sync runs, reconciliation, external identity links, the **ownership flip** with preview and audit, field lineage view, first measured SLO (worker sync),
manual incidents, event explorer with redaction controls. **Test of success:** the ownership flip works on real fields **without changes to the core domain** ([ADR-0017](adr/0017-primary-product-mode.md)).

## Phase 3: Depth and second-instance proof

Change and termination processes; `compensation` and `timeandleave` modules (representation and orchestration only); replay and reprocess; observability stack; **the "Colombia test"** (document a second country's equivalents and compare, [localization/LATAM_STRATEGY.md](localization/LATAM_STRATEGY.md) section 5); revisit the broker decision against [ADR-0008](adr/0008-broker-none-yet.md) criteria; engage professional review for the first concepts intended to drive behavior.

## Phase 4 and beyond: business-readiness options (not commitments)

Spanish UI; enterprise SSO; hosting model and data-residency options; compliance posture; a second country; write-back and `PROPOSE_ONLY`; pilot with a real small employer. Each needs a fresh decision by Daniel and, where legal content is involved, professional review.

## Parallel track: Costa Rica research

Independent of phase numbering. Order: **P1** questions in [localization/costa-rica/RESEARCH_BACKLOG.md](localization/costa-rica/RESEARCH_BACKLOG.md) (they decide the domain model's shape), then **P2** (registry content), then **P3** (behavior; only with professional review). Progress is recorded in the worklog with sources
([localization/costa-rica/LEGAL_RESEARCH_POLICY.md](localization/costa-rica/LEGAL_RESEARCH_POLICY.md)).

## Top risks

| # | Risk | Mitigation |
|---|---|---|
| R1 | Scope creep from standalone-first toward payroll/leave/onboarding | Phase 1 is one slice; "does not compute pay" is a written non-goal |
| R2 | One developer cannot carry seven modules plus UI plus operations | Phase 1 subsets of each module; operations minimal; defer aggressively |
| R3 | Legal content wrong or outdated | Registry gates, research policy, professional review before behavior |
| R4 | Effective dating and tenancy prove harder than expected | Spikes S1/S2 first; decisions revisited before commitment |
| R5 | Abstractions built before a second instance exists | Second-instance rule; Phase 3 Colombia test |
| R6 | Portfolio-only complexity creeps in (broker, extra services) | The eight-question test; ADR-0008 exit criteria |
| R7 | Overlay seam untested until late | Ownership fields and provider-free domain from day one; Phase 2 flip test |
| R8 | Employer conflict or IP issue affects the project | Clean-room rules; Daniel's review of agreements (BIZ-02) |
| R9 | Demo data mistaken for real, or real data leaks | Synthetic-only policy, invalid-by-construction IDs, markers |
| R10 | Accessibility deferred | Part of definition of done; automated plus manual checks from the first screen |

## Interview readiness: where each question is answered

| Question (from the brief) | Answered in |
|---|---|
| Why modular monolith? Why not microservices? | [ADR-0001](adr/0001-modular-monolith.md), [ARCHITECTURE.md](ARCHITECTURE.md) |
| Why Java/Spring? | [ADR-0002](adr/0002-java-spring-boot.md) |
| Why PostgreSQL? | [ADR-0003](adr/0003-postgresql-and-tenancy.md) |
| How does PalCheck integrate with Workday? | [INTEGRATION_ARCHITECTURE.md](INTEGRATION_ARCHITECTURE.md), [diagram 04](diagrams/04-hris-integration.md) |
| What does PalCheck own versus Workday? | [DATA_OWNERSHIP_AND_LINEAGE.md](DATA_OWNERSHIP_AND_LINEAGE.md), [ADR-0005](adr/0005-source-of-truth-ownership.md) |
| How do you avoid external-provider coupling? | [ADR-0004](adr/0004-hris-adapter-boundary.md) |
| How do you normalize HR data? | [INTEGRATION_ARCHITECTURE.md](INTEGRATION_ARCHITECTURE.md) 3.3 |
| How do you model local HR concepts? | [ADR-0012](adr/0012-legal-concept-registry.md), [LEGAL_CONCEPT_REGISTRY.md](localization/LEGAL_CONCEPT_REGISTRY.md) |
| How does Costa Rican localization work? | [localization/costa-rica/README.md](localization/costa-rica/README.md), [COUNTRY_POLICY_ARCHITECTURE.md](localization/COUNTRY_POLICY_ARCHITECTURE.md) |
| How could Colombia or Mexico be added later? | [LATAM_STRATEGY.md](localization/LATAM_STRATEGY.md) sections 5 and 6 |
| How do you avoid assuming countries are identical? | [ADR-0011](adr/0011-country-policy-architecture.md), `ConceptMapping` equivalence |
| How do retries work? How do you prevent duplicate processing? | [INTEGRATION_ARCHITECTURE.md](INTEGRATION_ARCHITECTURE.md) 5 and 6, [ADR-0006](adr/0006-event-driven-integration.md) |
| Why transactional outbox? | [ADR-0007](adr/0007-transactional-outbox.md) |
| How would you monitor the system? | [ARCHITECTURE.md](ARCHITECTURE.md) 7, [ENTERPRISE_SYSTEMS.md](ENTERPRISE_SYSTEMS.md) |
| How does data lineage work? | [ADR-0015](adr/0015-data-lineage.md), [diagram 07](diagrams/07-data-lineage.md) |
| How do you handle system failures? | [ARCHITECTURE.md](ARCHITECTURE.md) 9, [INTEGRATION_ARCHITECTURE.md](INTEGRATION_ARCHITECTURE.md) 6 |
| How would you scale it? Extract a module? | [ARCHITECTURE.md](ARCHITECTURE.md) 10, [MODULES.md](MODULES.md) |
| How do you protect employee data? | [SECURITY.md](SECURITY.md) |
| How do you distinguish legal rules from software behavior? | [DOMAIN_MODEL.md](DOMAIN_MODEL.md) 2, [LEGAL_CONCEPT_REGISTRY.md](localization/LEGAL_CONCEPT_REGISTRY.md) 4 |
