# Codex Architecture Review

Author: CODEX · Date: 2026-09-19 · Status: historical Phase 0 review; recommendations below record the original proposal.

> **Q1 resolution (2026-09-21):** Daniel accepted JDBC, Maven/packages/ArchUnit, backend OIDC sessions, embedded PostgreSQL tests while no container runtime is available, and Phase 1 RLS deferral. [ADR-0022](adr/0022-phase1-technical-baseline.md#q1-acceptance-record-2026-09-21) records the binding scope. The original recommendations below are retained as historical reasoning; their proposed status and pre-schema RLS experiment do not override this acceptance.

Daniel remains the final authority. This review does not accept ADRs, open Phase 1, or change his standalone-first decision. It reviews Claude's actual proposal, including Amendments A1–A5, rather than the superseded candidate architecture in the original brief. `DATA_LINEAGE.md` was intentionally replaced by [DATA_OWNERSHIP_AND_LINEAGE.md](DATA_OWNERSHIP_AND_LINEAGE.md).

Scope: implementation, reliability, platform, backend, testing, security, and developer experience. All existing Markdown documents, all 17 original ADRs, and all 15 Mermaid blocks were read. There is no application or Git repository to build or diff. Technical references below were checked against public primary documentation on the review date; library integration and performance remain **Unverified:** until Phase 1 experiments. No employment-law research or vendor-tenant testing was performed.

## 1. Executive assessment

**Keep the product direction and monolith; revise the execution model before scaffolding.** Claude has already removed most obvious infrastructure excess. The remaining difficulty is inside the monolith: transaction rules contradict the intended atomic writes, the outbox API creates a forbidden dependency cycle, and several reliability claims lack the durable state needed to make them true.

The proposed Phase 1 is a portfolio milestone disguised as a foundation: seven modules, effective-date processing, three destinations, three idempotency modes, an operations console, legal-content projection, and four screens. One developer can build these incrementally, but they should not all gate the first useful application.

Recommended order: workforce foundation → durable provisioning demonstration → simulated inbound HRIS and ownership cutover. Preserve the overlay seam through a provider-free domain, one workforce writer, and a small governed-field policy; do not build the overlay runtime in advance.

Four proposals make the disagreements reviewable:

- [ADR-0018](adr/0018-module-transactions-and-persistence.md): dependency and transaction boundaries, database ownership, history and migration approach.
- [ADR-0019](adr/0019-durable-delivery-and-effect-identity.md): durable fan-out, business-effect identity, ordering, replay and retention.
- [ADR-0020](adr/0020-foundation-first-delivery-plan.md): smaller Phase 1, build/enforcement defaults and browser authentication direction.
- [ADR-0021](adr/0021-registry-history-and-behavior-gates.md): legal-content history and behavior gates, without asserting law.

These are proposed amendments to other **Proposed** ADRs. None supersedes or edits an Accepted decision. Earlier documents remain the baseline pending Daniel's decision; their review notices point here.

## 2. What Claude got right

**KEEP:** one deployable, PostgreSQL, no broker, and no separate integration worker yet. These match one maintainer and strong local consistency needs. Keep Java/Spring and React/TypeScript because they are explicit learning/product directions, not claims that alternatives are inferior.

**KEEP:** Tenant / Legal Entity / Org Unit; Person / Employment / Assignment; Employee as a role rather than a duplicate entity. Rehire must remain a new Employment, not a new Person by default. Keep valid business dates separate from recording instants.

**KEEP:** ownership policy separate from provenance, workforce as the single writer, explicit mapping, and provider models behind adapters. Keep the distinction between authentication and downstream identity provisioning.

**KEEP:** at-least-once delivery, explicit unknown outcomes, safe retry/replay/reprocess vocabulary, synthetic-only demonstrations, legal research gates, and no payroll calculation. The no-payroll boundary is a proposed product restriction Daniel should formally confirm alongside ADR-0017; standalone-first itself is already his planning decision.

**KEEP:** operations derived from real records, `Unknown` health, metadata-first lineage, accessibility in the definition of done, and localization before generalization. These are strong decisions worth implementing carefully.

## 3. Areas of concern

Severity follows [AI_COLLABORATION.md](AI_COLLABORATION.md): `BLOCKER` blocks the affected implementation, not this documentation task; `DECIDE` needs an explicit choice; `NOTE` is a correction or clarification. Materiality is separately minor / important / architecture-blocking.

### TX-01 — Atomic writes contradict the module rules

- **Severity:** BLOCKER · **Materiality:** architecture-blocking.
- **Where:** [ARCHITECTURE.md](ARCHITECTURE.md) §2 rules 3–4; [MODULES.md](MODULES.md) §§2, 3.2, 3.6; [ADR-0007](adr/0007-transactional-outbox.md) decision 1; [diagram 05](diagrams/05-event-processing.md).
- **Finding:** “one command, one transaction, one module” conflicts with atomic workforce, lifecycle, audit and outbox writes. Workforce must call an integrations-owned outbox API, but integrations already depends on workforce and the dependency matrix forbids the reverse edge.
- **Evidence:** the dependency matrix, hire failure mitigation, and transaction diagram describe incompatible implementations. Access also claims to depend only on the shared kernel while requiring atomic audit of its changes. This is a documentation contradiction, not a runtime failure observed in code.
- **Recommendation:** one application command owns one local transaction; explicitly allow audit and durable publication to participate. Put outbox infrastructure in a small technical `messaging` package with no domain dependency. Keep the first hire command inside workforce; defer a separate lifecycle process until it owns approvals or resumable steps. See §5 and ADR-0018.
- **Tradeoff:** honest local transaction coupling instead of premature service-like independence; extraction later requires redesign of affected transactions.
- **Impacted ADRs:** 0001, 0003, 0006, 0007, 0014, 0016.

### EVT-01 — Delivery state and effect identity are insufficient

- **Severity:** BLOCKER · **Materiality:** architecture-blocking before provisioning.
- **Where:** [INTEGRATION_ARCHITECTURE.md](INTEGRATION_ARCHITECTURE.md) §§5–6; [ADR-0006](adr/0006-event-driven-integration.md); [diagram 05](diagrams/05-event-processing.md).
- **Finding:** a processed-event marker cannot atomically protect a remote side effect. Event-ID keys also allow duplicate effects when one business fact is published in two schema versions, imported through two paths, or reprocessed into a new event. Lease expiry is unsafe if it blindly resends an ambiguous `NONE` request.
- **Evidence:** dual-version publication is recommended, but semantic deduplication is not defined; the attempt diagram omits an unknown/manual-review state; lease expiry is described as automatic retry for all claims.
- **Recommendation:** separate immutable events, consumer deliveries, immutable attempts, and destination operation records. Persist a business operation ID independent of event representation; freeze request content; recover ambiguous sends by destination capability. §8 and ADR-0019 define the crash windows.
- **Tradeoff:** additional durable records are justified by the promised reliability; implement them only when asynchronous effects arrive.
- **Impacted ADRs:** 0006, 0007, 0008, 0016.

### IMP-01 — Sync progress and missing-record semantics can lose data

- **Severity:** BLOCKER · **Materiality:** architecture-blocking before inbound sync.
- **Where:** [INTEGRATION_ARCHITECTURE.md](INTEGRATION_ARCHITECTURE.md) §3.4; [diagram 04](diagrams/04-hris-integration.md), final watermark step.
- **Finding:** advancing a watermark after a partially handled page can skip failed records permanently. Absence from a full sync can reflect scope, permissions, filters, or an incomplete run; it is not evidence of termination.
- **Evidence:** resumability is asserted without a page checkpoint protocol, and missing records may be treated as terminations. No source snapshot completeness or deletion contract is specified.
- **Recommendation:** durable page dispositions before checkpoint advance; quarantine must retain enough authorized input to resume or explicitly require re-fetch. Missing records become reconciliation candidates only after a complete comparable snapshot. Termination requires an explicit mapped source fact or authorized human decision. §7 specifies this protocol.
- **Tradeoff:** some records wait for reconciliation rather than receiving unsafe automatic updates.
- **Impacted ADRs:** 0004, 0005, 0006.

### SEC-01 — Scope checks need query and background semantics

- **Severity:** BLOCKER · **Materiality:** architecture-blocking before authenticated data access.
- **Where:** [SECURITY.md](SECURITY.md) §§5–6; [ADR-0014](adr/0014-authorization.md); [MODULES.md](MODULES.md) §2.
- **Finding:** checking one loaded record does not secure list counts, pagination, search, export or historical access. An arbitrary scope callback and caller-supplied `actorKind=SYNC` are insufficient trust boundaries.
- **Evidence:** no required query-scoping contract or authenticated construction of write contexts is described; RLS worker access remains unresolved.
- **Recommendation:** application services construct trusted contexts; owning repositories apply tenant and grant constraints before filtering/counting/pagination. Use explicit tenant-bound service principals for jobs. Start with tenant-scoped reader/editor permissions; defer manager hierarchy access. §12 specifies tests and RLS role boundaries.
- **Tradeoff:** less initial role breadth, stronger and testable enforcement.
- **Impacted ADRs:** 0003, 0005, 0013, 0014.

### LOC-01 — Expiry and deprecation conflict with historical evaluation

- **Severity:** BLOCKER · **Materiality:** architecture-blocking before any legal behavior; not a Phase 1 blocker.
- **Where:** [ADR-0012](adr/0012-legal-concept-registry.md) decision 3; [LEGAL_CONCEPT_REGISTRY.md](localization/LEGAL_CONCEPT_REGISTRY.md) §3; [COUNTRY_POLICY_ARCHITECTURE.md](localization/COUNTRY_POLICY_ARCHITECTURE.md) §§4, 10; [LEGAL_RESEARCH_POLICY.md](localization/costa-rica/LEGAL_RESEARCH_POLICY.md) §6.
- **Finding:** disabling every expired/deprecated version prevents evaluating a historical fact under the version applicable then. Requiring gap-free legal coverage can encourage invented coverage. “Any binding” versus only executable bindings has inconsistent verification gates.
- **Evidence:** effective-date selection and the blanket disable rule cannot both operate as written; examples show no ConceptVersion but attach statuses defined only on versions/bindings.
- **Recommendation:** separate normal historical expiry from withdrawal for error, preserve immutable reviewed history, and return `NOT_AVAILABLE` for gaps. No executable binding to unverified content; `DOCUMENTED` is display metadata when no binding exists. ADR-0021 and §13.
- **Tradeoff:** explicit eligibility metadata and version selection instead of one overloaded status flag. No law is being interpreted here.
- **Impacted ADRs:** 0011, 0012.

### SCP-01 — Phase 1 includes too many independent subsystems

- **Severity:** DECIDE · **Materiality:** important.
- **Where:** [ROADMAP.md](ROADMAP.md), Phase 1 and spikes; [CODEX_HANDOFF.md](CODEX_HANDOFF.md) §6; [UI_SYSTEM.md](UI_SYSTEM.md) §§6–7.
- **Finding:** the first phase requires almost every proposed module before the first milestone. Eight mandatory comparative spikes and a large component inventory further delay usable behavior.
- **Evidence:** roadmap scope includes three simulated destinations, all idempotency modes, registry projection, operations, full temporal handling and four screens.
- **Recommendation:** foundation first, provisioning as the next bounded milestone, mock HRIS after that. Keep the long-term requirements; move their acceptance tests to the milestone owning them. §15 and ADR-0020.
- **Tradeoff:** the resilience showcase arrives later, but progress has smaller demonstrable increments.
- **Impacted ADRs:** 0001, 0007, 0009, 0011, 0012, 0016, 0017 (mitigations and phase scope only).

### LIN-01 — Correlation does not prove data lineage

- **Severity:** DECIDE · **Materiality:** important before lineage.
- **Where:** [DATA_OWNERSHIP_AND_LINEAGE.md](DATA_OWNERSHIP_AND_LINEAGE.md) §§3–5; [ADR-0015](adr/0015-data-lineage.md).
- **Finding:** one correlation ID may group many workers, changes and deliveries. Looking up current workforce values on retry may send different content under the same key. Current provenance cannot prove which past value a destination received.
- **Evidence:** joins rely on correlation and no immutable outbound request or source revision reference is required. Computed lineage is also described as a cross-module join while such joins are banned.
- **Recommendation:** persist causal references (`changeId`, entity revision, event ID, operation ID, mapping/request version); freeze the minimal outbound request once. Compose lineage through owner read APIs. Show “declared consumer” separately from “observed delivery.” §7.
- **Tradeoff:** a few reference fields and bounded request storage; no full governance platform.
- **Impacted ADRs:** 0005, 0006, 0015, 0016.

### DAT-01 — Database rules prioritize extraction over present integrity

- **Severity:** DECIDE · **Materiality:** important before migrations.
- **Where:** [ADR-0001](adr/0001-modular-monolith.md) rules 2–4; [ADR-0003](adr/0003-postgresql-and-tenancy.md); [DOMAIN_MODEL.md](DOMAIN_MODEL.md) §6.
- **Finding:** blanket rejection of cross-module FKs trades enforceable tenant/reference integrity for unimplemented reconciliation. Audit-derived recorded time is not a historical query model, and module-owned migrations lack a deployment ordering authority.
- **Evidence:** no deletion/reference consistency protocol, global migration order, or historical correction query is documented.
- **Recommendation:** retain one database and schemas for actual owners; allow narrow tenant-anchor FKs, retain owner-only writes/read APIs, use one globally ordered migration stream, and preserve explicit revisions. §6 and ADR-0018.
- **Tradeoff:** documented extraction work later; simpler integrity and deployment now.
- **Impacted ADRs:** 0001, 0003, 0005.

### REF-01 — Technical uncertainty should be narrowed, not repeated

- **Severity:** NOTE · **Materiality:** minor.
- **Where:** [CODEX_HANDOFF.md](CODEX_HANDOFF.md) §3 item 12; [INTEGRATION_ARCHITECTURE.md](INTEGRATION_ARCHITECTURE.md) §8; [ADR-0007](adr/0007-transactional-outbox.md).
- **Finding:** public Workday documentation exists; Spring Modulith has a documented transactional publication registry. Neither fact proves tenant access or fit for PalCheck's destination operations.
- **Evidence:** primary sources in §§5 and 7. No dependency was installed and no tenant used.
- **Recommendation:** distinguish verified documentation capabilities from unverified application fit. Remove broad “documentation is gated” assumptions as a design premise; keep the simulator fidelity disclaimer.
- **Impacted ADRs:** 0004, 0007; no product-scope change.

### Diagram review

All existing blocks were inspected as text; no local Mermaid renderer was available and none was installed. Syntax/rendering remains **Unverified:**. Diagram 03 hides the outbox call cycle; 05 conflicts with the transaction rule and omits ambiguity recovery; 04 advances a watermark without a page barrier; 07 overstates correlation-based lineage. Diagram 02 hard-codes browser bearer tokens while the session decision is open. Diagrams 01/02 also include staff/operations breadth beyond the proposed foundation. Diagram 06 needs the historical eligibility gate from LOC-01. Diagram 08 is useful as a labelled illustration, not evidence of health. [Diagram 09](diagrams/09-proposed-transaction-and-delivery.md) records the proposed correction without replacing Claude's diagrams before acceptance.

## 4. Overengineering risks and dependency review

There is no dependency manifest. This is an inventory of all named candidates and dependency classes in the brief, ADRs, architecture, UI and handoff, not a license/security audit. Versions, licenses and compatibility must be checked when adoption becomes relevant; nothing is installed now.

| Candidate | Classification | Necessity and timing |
|---|---|---|
| Java, Spring Boot, Spring Web | KEEP | Foundation; one Java application and ordinary synchronous request handling. |
| Spring Security, OIDC | KEEP | Authentication/authorization without password management; prefer backend-mediated browser login (§12). |
| Bean Validation | KEEP | Boundary input validation; plain Java still owns invariants. |
| PostgreSQL, JDBC driver, Flyway | KEEP | Relational integrity, explicit transactions, ordered migrations. |
| Spring Data / JPA / Hibernate | MODIFY | Not mandatory. Default to Spring JDBC with explicit repositories for initial scope; one persistence style. |
| Spring Data JDBC, jOOQ | DEFER | Revisit if explicit JDBC mapping or complex query maintenance becomes painful; do not add alongside JPA speculatively. |
| PostgreSQL range constraints / `btree_gist` | MODIFY | Evaluate only where temporal overlap prevention requires them; extension adoption belongs to the Phase 1 history check. |
| Maven vs. Gradle; JPMS/multi-module build | MODIFY | Recommend one Maven build plus wrapper and package boundaries. No comparative build-tool spike unless Daniel has a preference. Defer compiler-enforced modules. |
| ArchUnit | KEEP | Test dependency DAG, internal access, provider boundaries; no runtime framework needed. |
| Spring Modulith | DEFER | Credible optional replacement for custom publication infrastructure, not an automatic architecture dependency (§5). |
| MapStruct, mapping DSL, rules libraries | REMOVE | Handwritten small mappers are clearer until repetitive mappings demonstrate value. |
| Kafka / Redpanda | DEFER | Neither needed for foundation, provisioning, or simulated inbound sync (§9). |
| RabbitMQ / NATS / SQS / Pub/Sub | DEFER | Alternatives at the same measured broker decision, not extra fallback infrastructure now. |
| Debezium / Kafka Connect / schema registry | DEFER | No CDC pipeline or external event ecosystem currently exists. |
| `LISTEN/NOTIFY` | DEFER | Optional wake-up optimization after measuring polling lag; never required for durability. |
| Spring scheduling | KEEP | Trigger bounded polling/sweeps once jobs exist; durable state remains in PostgreSQL. |
| Quartz / external scheduler | DEFER | Only for demonstrated persistent calendar, misfire or scheduling administration needs. |
| Workflow engine / BPMN / event sourcing | REMOVE | Explicit state machines and relational history fit current flows; reconsider only for actual branching workflows or historical-query needs. |
| React, TypeScript, Vite | KEEP | One frontend; Vite remains the proposed build tool, not an installed dependency. |
| OpenAPI and TypeScript generation | KEEP | Generate types once an actual API exists; compile/type tests still needed. Generated types alone cannot prevent runtime drift. |
| JSON Schema / Java and TS event generators | MODIFY | Versioned event validation at provisioning; normalized inbound schema at overlay. No frontend event DTOs unless the frontend actually consumes them. |
| TanStack Query | MODIFY | One server-state cache once list/detail/edit flows justify it; no second global store. |
| React Aria Components / Radix | MODIFY | Choose one for required widgets only. Prefer native controls first; do not build the entire listed component inventory. |
| CSS custom properties / JSON token compiler | MODIFY | Plain semantic CSS variables first. Defer a token compiler and separate token package until multiple outputs need generation. |
| FormatJS / i18next | MODIFY | Choose one message solution; ICU-style formatting with i18next requires a deliberate compatible choice. Keep locale and jurisdiction separate. |
| `Intl`, system fonts | KEEP | Platform facilities for display; no downloaded proprietary assets. |
| Tailwind / utility CSS | DEFER | Optional preference, not required by tokens or accessibility. |
| Spring-motion library / icon set / chart or topology library | DEFER | No gesture, icon collection, or interactive graph dependency without a concrete screen need and license review. |
| Next.js / SSR / micro-frontends | REMOVE | No public SEO or independently deployed UI teams. |
| JUnit, Testcontainers, PostgreSQL test images | KEEP | Unit plus real-database verification. No H2 substitute for tenant/history/outbox behavior. |
| Property-test library | DEFER | Begin with boundary/concurrency examples; add generative tests when temporal algorithms justify it. |
| Vitest, Testing Library, Playwright, axe-class checks | KEEP | Component behavior and a few critical browser flows; automated accessibility checks supplement manual review. |
| Docker-compatible runtime / Compose | KEEP | Future local PostgreSQL and development IdP only; no Docker files now. |
| Micrometer / health support | MODIFY | Health and structured logs first; instrument real deliveries when they exist, without a separate dashboard stack. |
| OpenTelemetry / Prometheus / Grafana | DEFER | Add across actual process boundaries and measured operational questions. |
| Local or managed IdP | MODIFY | Choose a maintained provider in Phase 1. Do not implement an authorization server; Spring Authorization Server is not a shortcut to operating a finished IdP. |
| OPA / Cedar / external ReBAC service | REMOVE | Central permission helper plus module-owned scope queries suffice. |
| OpenLineage / governance platform / iPaaS | REMOVE | Versioned mappings, provenance and delivery evidence meet the current need. |
| KMS / per-subject encryption / audit hash chain | DEFER | Separate researched real-data readiness work; do not block a minimal synthetic-only foundation with an invented erasure guarantee. |
| GitHub Actions / formatters / static analysis / secret and dependency scans | KEEP | Introduce with code, choose one tool per purpose; containers and deployment stages only once artifacts exist. |
| .NET, Kotlin, Go, Python, Quarkus, Micronaut, Vue, Svelte, Thymeleaf/HTMX, MySQL/document stores | REMOVE | Evaluated alternatives, not additional dependencies; no change to the selected direction is justified. |

`REMOVE` means remove from the adoption plan, not erase reasoning from existing ADRs. `DEFER` means a named trigger must justify adoption. Retain the small shared kernel, but add value types only when used; a future Money type need not exist before compensation does.

## 5. Backend feasibility

### Package boundaries and application interfaces

Recommend ordinary Java packages, one Maven artifact, Spring only at application/infrastructure boundaries, and ArchUnit tests. ArchUnit supports package/slice dependency and cycle checks; these enforce architecture at test time, not Java compiler visibility. [ArchUnit user guide](https://www.archunit.org/userguide/html/000_Index.html).

Conceptual packages below are relative to the eventual application root, not directories to create in Phase 0. Each actual module exposes `api`; `internal` contains application services, domain, repositories and adapters. No cross-module entity/repository imports or ORM associations. DTOs do not carry lazy persistence proxies. `messaging` is a technical package, not a new business domain.

| Owner / package | Owned persistent data, when needed | Public application boundary | Allowed dependencies | Events and transaction responsibility |
|---|---|---|---|---|
| `workforce` | Person, Employment, Assignment revisions, Legal Entity, Org Unit, governed policy, provenance, command receipts | Register person; record employment/hire; revise assignment; scoped current/as-of queries; apply imported change later | shared types, access authorization API, audit recorder, messaging append API | Publishes committed workforce facts. Owns workforce command transaction including audit and outbox when enabled. |
| `access` | Tenant, trusted principal membership, small permission grants | Resolve principal/tenant; require permission; enumerate allowed scope descriptors | shared types, audit recorder | Grant changes audited in their local transaction; no workforce imports. Scope membership queries stay in workforce. |
| `audit` | Append-oriented change and sensitive-read records | Append audit entry; read bounded audit records through authorized application facade | shared types only | Recorder must join caller transaction; no re-derivation from asynchronous events. No arbitrary PII-bearing event publications. |
| `messaging` (provisioning milestone) | Outbox envelopes, durable fan-out/delivery state, dispatch attempts | Append publication; delivery claim/ack; bounded operational queries | shared types only; injected consumer ports | Appends in caller transaction. Claim/finalize transactions are short. No workforce queries or remote calls in publisher append. |
| `integrations` (provisioning/overlay) | Destination configurations, operation receipts/frozen requests, outbound results; later mappings, links, sync runs and quarantine | Deliver/reconcile; retry authorized operation; sync commands; operational queries | workforce API/events, access, audit, messaging | Consumer receipt + operation intent commit together. Own operation lifecycle; remote calls outside DB transaction. Emits provisioning/sync facts. |
| `lifecycle` (deferred) | Approval/process/task state only when processes exist | Start/advance/cancel known workflow | workforce, access, audit, messaging; country requirements later | Owns process events, not duplicate workforce facts. A local atomic completion may explicitly join workforce; remote waits use persisted steps. |
| `countrypolicy` (deferred runtime) | Versioned content projection; later assessments and tenant policy config | Read concepts; later evaluate reviewed capability with immutable inputs | access/audit for adapters; no workforce dependency for glossary; immutable workforce input contract for future evaluations | No legal behavior in foundation. Assessment + audit in own transaction later; never mutates workforce. |
| `operations` (deferred module) | Only authored catalog metadata, incidents and SLO definitions when they exist | Bounded read composition; explicitly authorized retry delegation | access, audit read API, integrations operational APIs, messaging metadata queries; workforce provenance read API for lineage later | No duplicate delivery/job stores. Retry goes to the owning service; read-only dependency descriptions must not prohibit that command. |

Every dependency absent from this table is prohibited. Event consumers reference the producer's public event contract: consuming a typed Java event **is** a compile-time dependency. Do not pretend dashed arrows remove it. Integrations can depend on workforce contracts because workforce no longer depends on integrations. Future process-event consumption can add an explicit integrations → lifecycle API edge, provided lifecycle has no reverse dependency.

Audit reads are authorized in the calling application facade using access; audit persistence does not call access, which would form a cycle. Recording access to audit must not recursively audit its own internal append. Shared request context carries authenticated identity and tenant; no ambient mutable singleton or unscoped asynchronous thread-local fallback.

### Transaction boundaries

One authoritative application service starts the local transaction. Domain validation, optimistic revision check, workforce changes, command receipt, audit and required outbox append succeed together or roll back together. Recording infrastructure uses the same datasource and transaction manager. An audit append failure fails the command; it does not leave an unaudited successful hire. Do not put atomic audit or outbox writes in `REQUIRES_NEW` or an after-commit-only listener. Spring documents that `REQUIRED` can join the outer physical transaction, while `REQUIRES_NEW` commits independently. [Spring transaction propagation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html).

No network call, sleep, or user approval waits inside that transaction. Start with workforce-owned `EmployeeHired` as the committed hire fact and `EmploymentStarted` as the effective-date transition when scheduling is introduced. The provisioning trigger must be declared once per destination; do not subscribe account creation to both and hope event deduplication catches it. Lifecycle later emits process facts such as approval completion, not a second authoritative hire event.

### Spring Modulith assessment

Spring Modulith documents publication entries per transactional listener within the original transaction, completion tracking, resubmission, and publication lifecycle/staleness support. It is a credible alternative, not merely an unverified “outbox-like” feature. [Spring Modulith events](https://docs.spring.io/spring-modulith/reference/events.html).

Recommendation: package/ArchUnit enforcement without Modulith for the foundation; explicit small outbox infrastructure is the proposed provisioning default. PalCheck still needs destination-specific operation identity, frozen request data, ambiguity resolution, authorization and retention even if Modulith owns listener delivery. Before writing dispatcher code, compare the selected compatible Modulith release against those acceptance cases. Adopt it only if it eliminates a durable mechanism; never run two competing publication registries. Fit remains **Unverified:** no runtime experiment in Phase 0.

## 6. Database strategy

**One PostgreSQL database, schemas only for actual persistent owners.** Retain schemas as namespaces and migration ownership markers; they are not service isolation or permission boundaries when one application role accesses them. Do not create nine empty schemas. The foundation needs workforce, access and audit ownership; messaging and integrations arrive with delivery.

Allow normal intra-workforce tenant-qualified FKs (Employment → Person/Legal Entity; Assignment → Employment/Org Unit). Permit a narrow cross-schema tenant-anchor FK from tenant-owned data to access's Tenant. Document this deliberate dependency and prohibit cascade deletion of workforce/audit history. A tenant lifecycle is deactivation plus a later reviewed purge procedure, not cascading deletion. For future optional process references use owner APIs and explicit reconciliation; do not invent a general cross-module reporting join layer. Extraction remains possible, with deliberate migration of these constraints; it is not free.

Every uniqueness/check/reference design must consider tenant scope. A globally unique ID does not prove its parent belongs to the caller's tenant. Tenant-qualified parent identity and child references prevent mixed-tenant associations. Errors must not disclose another tenant's existence through constraint details.

**Migrations:** one globally versioned Flyway stream, one schema-history authority and one deployment migration runner. Files identify their owning module, but ordering is global across schemas. Already-applied versioned migrations are immutable; corrections use forward migrations. Repeatable migrations are limited to genuinely replaceable reference/views, not mutable legal history. Flyway documents schema-history tracking and ordered migration application. [Flyway migrations](https://documentation.red-gate.com/flyway/flyway-concepts/migrations).

Future release progression: validate from empty database and previous release; stop application startup on incompatible schema; back up before risky migration; document rollback compatibility. Prefer additive changes, backfill, then removal in a later release. One instance runs migrations before application replicas start. Restore tests are required before any hosted data matters; a backup file alone is not evidence of recoverability.

### Employment history and concurrent changes

Use stable Person/Employment/Assignment IDs and explicit immutable revision identity. Proposal: valid-from inclusive / valid-to exclusive business-date intervals, plus recorded-at, actor/change reference and supersedes reference. Current effective segments must not overlap within the appropriate tenant/aggregate stream. Historical superseded revisions can overlap; exclude them from the active-timeline constraint. Do not prohibit concurrent Employments or require continuous employment as an invented legal invariant.

Use an expected aggregate revision for edits, lock/check during correction, and preserve the replaced revision. Range/exclusion constraints are supported by PostgreSQL and can enforce non-overlap, but ORM mapping, extension requirements and active-revision constraints still need the focused history experiment. [PostgreSQL range types](https://www.postgresql.org/docs/current/rangetypes.html).

An audit trail is evidence of changes, not an efficient “what did we believe on that date?” query model. Foundation promises current/as-of-valid-date records plus revision history; full bitemporal query support is deferred. Before delivering full historical reconstruction, define recorded-time intervals and query semantics explicitly. Outbound operations reference the revision used, never “whatever is current now.”

Foundation can limit user workflows to immediate recording/editing while preserving valid dates and revisions. Future scheduling and retroactive workflow effects arrive with their tests; existing records must not be silently reinterpreted on a later midnight sweep.

### Other storage responsibilities

- Audit stores the minimal useful before/after values and changed fields, actor, reason, revision/change ID, timestamp and correlation. Separate operational logs from audit; restrict audit reads. Explicit retention decisions precede real data.
- Workforce provenance covers only the governed field set. Start with a revision-associated provenance document whose keys are governed fields, not a dynamic domain attribute system. Phase 1 records `PALCHECK` source and actor; overlay adds source connection, external reference, mapping and observation metadata.
- External identity links belong to integrations and distinguish Person and Employment references. The key includes Tenant, configured source connection/environment, external object type and external ID, not vendor name alone.
- Messaging owns durable publication/delivery state; integrations owns external operation identity. Caller-owned command receipts and consumer-owned processed-event receipts live with the state they protect. Logical shape and uniqueness are in §8; no SQL/schema is produced here.
- Legal definitions remain reviewed content. If a projection becomes useful, versions are immutable and content releases are loaded atomically with an identifiable content hash; no startup overwrite of referenced historical versions.

## 7. Integration feasibility, normalization, ownership and lineage

### Mock Workday mode

Keep the reference connector after provisioning. Workday's public SOAP directory exposes service documentation, WSDL and XSD, including Human Resources. This corrects the blanket access premise; it does not confer tenant access, establish licensed use of all materials, or validate a simulator. [Workday public Web Services directory](https://community.workday.com/sites/default/files/file-hosting/productionapi/index.html).

Use one explicitly simulated HTTP HRIS with a small documented contract and deterministic synthetic scenarios. Cite any public vendor shape actually used and pin that document version then. Keep simulator behavior distinct from verified vendor behavior. No tenant credentials, employer knowledge, generated full vendor SDK, SAP/Oracle stubs, or claim of Workday compatibility. A local server can exercise transport, timeout, pagination and auth failures; it cannot prove live vendor semantics.

The initial provider boundary needs worker paging and Legal Entity/Org Unit reference paging, an opaque cursor, connection configuration and typed failures. Replace `pullOrganizations` with distinct named scopes. Add webhook/delta/write-back interfaces only when the selected simulator scenario needs them; a list of future capabilities is documentation, not a required plugin framework. Start polling. Future webhooks are authenticated hints to pull canonical records, with replay protection, not trusted direct workforce commands.

Authentication resolves a secret reference from a tenant/source-connection context. Keep provider token refresh and protocol details inside the adapter. A mock authenticator proves contract/error handling only; do not label a simulator token as production Workday auth.

### Translation and normalization

External DTO → provider mapper → short-lived PalCheck `NormalizedWorker` → reconciliation → workforce application command → domain. DTO and mapper live together inside the provider adapter boundary; orchestration receives only normalized values and metadata. `NormalizedWorker` is a transport contract, never another persisted workforce aggregate. No domain enums or classes inherit vendor types.

An absent field, explicit null, unavailable-by-scope field and unknown code are different states. Declare full/patch semantics so omitted fields cannot erase existing data. Model distinct external references for a worker and each employment relationship; do not let a single external worker ID collapse a rehire or concurrent employment. Two-pass dependency resolution can land people/references before manager links; unresolved managers remain visible, not fabricated.

Unmapped field **names and dispositions** belong in mapping reports; unknown values do not automatically belong in permanent `extensions`. Default raw landing to metadata only. Enable bounded, access-controlled failure capture only for classified fields and a known retention purpose. Redacted data may be insufficient to reprocess; state “re-fetch required” when true. Do not promise both complete replayability and deletion of required inputs. Raw payloads stay inside integrations, never in generic workforce events or UI error messages.

### Progress, ordering and reconciliation

1. Acquire a lease per Tenant + source connection + sync scope. Persist run identity, mapping/policy version, checkpoint, snapshot scope and optional source high-water bound.
2. Fetch a bounded page outside a DB transaction. Treat cursors as opaque, potentially sensitive values; never log them. Respect rate limits and `Retry-After`; bound timeouts, pages and payload size.
3. Apply each record through workforce with an idempotent import receipt. Record a durable outcome: applied, unchanged, stale, quarantined or rejected, with enough authorized recovery input or a re-fetch requirement.
4. Advance the page checkpoint only after every page record has a durable disposition. A failed record may allow advance only when its separate durable backlog preserves recovery. Otherwise retry the page. A crash after workforce commit but before recording the sync result is resolved by the import receipt, not a second hire.
5. Advance the run watermark only after page completion through the chosen bound. If the provider lacks snapshot stability, use a documented overlap/re-fetch strategy with deduplication and reconciliation; timestamp ordering alone cannot guarantee no loss. Equal timestamps with different content are conflicts, not automatic last-writer-wins.
6. A complete full snapshot may identify missing records only against the same scope. Missing becomes a reconciliation candidate, never an inferred termination. Never reconcile absence after an incomplete or permission-changed run.

Import identity includes Tenant, source connection, object identity and source revision; retain the last accepted source ordering and canonical mapped hash. Mapping version records interpretation; a corrected mapper is an explicit reprocess operation, not permission to create the same employment twice. Without reliable source revisions, combine documented cursor/snapshot semantics, overlap windows and canonical hashes and mark remaining limits.

### Least complex ownership model

Keep tenant-default `PALCHECK` plus a **short governed-field list** and one authenticated writer check. Per-aggregate ownership is too coarse for an external title plus local supplemental data; per-record/per-value policy duplicates configuration. In Phase 1 no policy editor, effective-dated cutover scheduler, or external ownership UI is needed. Governed policy is a small explicit configuration evaluated by the workforce application service.

At overlay, use `EXTERNAL` plus an owner source-connection reference, not vendor name as an enum value. The three ownership modes remain exactly `PALCHECK`, `EXTERNAL`, `DERIVED`. Store `sourceField` as opaque provenance metadata; the core may retain it but must never branch on vendor field names. Only an authenticated derivation service writes `DERIVED` output.

Ownership cutover: pause affected imports, create a bounded snapshot/preview, compare expected workforce and policy revisions at commit, audit the accepted policy revision, then resume. A failed or expired lease must not leave cutover permanently blocked. Reject stale preview/old-policy commands; do not apply them under cached authority. Start with one source connection owning a governed field across a tenant; multiple sources partitioned by Legal Entity or record are a future requirement, not silently supported.

### Lineage worth persisting

Persist current governed-field provenance, immutable mapping identifiers, change/entity revision references, event/delivery/operation links, minimal frozen request references and request hash. Infer the static graph from versioned mapper descriptions and outbound field contracts. Compose via module-owned read APIs; add a read projection only if measured query cost justifies it.

A source such as `Workday.BusinessTitle` is **illustrative, unverified provider vocabulary**, not a validated vendor field contract. Render it as source metadata, followed by the named transformation, PalCheck Assignment field and declared consumers. Distinguish “this destination contract uses the field” from “operation X sent revision Y and received acknowledgement.” Correlation supports navigation but does not establish causality. When retention removes evidence, display “history unavailable,” not inferred success. No graph database or per-value permanent lineage archive.

## 8. Event architecture, outbox and idempotency

### Three distinct concepts

| Concept | Role | Persistence/versioning |
|---|---|---|
| Domain event | Internal fact from the domain transaction | Plain Java value; not automatically a durable subscriber contract. |
| Integration event | Deliberately published, minimal business fact | Immutable serialized JSON envelope, explicit schema version and compatibility rules. |
| Outbox record | Durable obligation to dispatch an integration event | Technical publication/delivery state; not another business event type and not the audit log. |

Published envelope: `eventId`, stable namespaced past-tense `eventType`, `schemaVersion`, `tenantId`, `aggregateType`, `aggregateId`, **`aggregateVersion`**, `changeId`, `occurredAt`, `recordedAt`, `correlationId`, optional `causationId`, logical producer `source`, and minimal `payload`. Business effectiveness is a date plus jurisdiction zone where applicable (`effectiveDate`); do not call a date an instant `effectiveAt`. A scheduled transition can additionally record its due/observed instant. Timestamps do not order concurrent aggregate writes.

Serialize through explicit event DTOs, not ORM entities or arbitrary Java class names. Additive **optional** fields can be compatible; new required fields, narrower values, enum changes or changed meaning require compatibility review. Consumer tests cover unknown fields, old versions and unsupported versions. If two representations of the same fact coexist, keep a common fact/change identity and business operation identity; route one supported representation to each effect consumer. Do not create two payroll operations because schema versions differ.

### Durable records (logical design only)

| Record | Required information / uniqueness | Owner |
|---|---|---|
| Publication | Tenant + unique event ID, immutable envelope/payload, producer revision, captured subscriber set or immutable routing revision, fan-out completion | messaging |
| Consumer delivery | Unique Tenant + event ID + consumer identity; due time, state, claim token/generation, lease deadline, attempt count | messaging |
| Attempt | Delivery/operation reference, attempt ID/number, start/end, sanitized outcome/error, timings, claim generation | corresponding dispatcher/operation owner; append-oriented |
| Processed receipt | Tenant + consumer + event ID, committed with the local state/operation it protects | consumer module |
| Destination operation | Tenant + destination **instance** + operation kind + subject/lifecycle occurrence; stable operation ID, frozen request/version/hash, receipt, ambiguity state | integrations |
| Command receipt | Tenant + authenticated caller/client scope + operation + request key, canonical request hash, result reference and expiry policy | command owner |

Subscriptions cannot be recomputed from today's code on every retry. Materialize delivery rows atomically when appending, or persist an immutable subscriber list and expand it idempotently in a later transaction. This proposal uses the latter: expand the captured list and mark fan-out complete together. Crash recovery repeats expansion safely under uniqueness constraints. Events with no subscribers are explicitly complete. New consumers do not automatically receive old events; backfill is a separate operation.

### Claim, send and acknowledge

1. Short transaction claims a bounded set of due deliveries using row locking and a fresh lease generation; commit before invoking handlers. PostgreSQL documents `SKIP LOCKED` as useful for queue-like access, not general consistent reads. It is not an ordering guarantee. [PostgreSQL SELECT locking](https://www.postgresql.org/docs/current/sql-select.html).
2. Consumer transaction records its receipt and creates the operation intent with the frozen minimal request. If already committed, acknowledge the duplicate. A failed transaction leaves neither receipt nor partial intent.
3. A separate operation executor durably records that a send is starting, commits, then calls the destination outside the DB transaction. Bound concurrency and timeout per destination so a slow system does not exhaust API threads/connections.
4. Finalize the result in a short transaction conditional on the claim generation. A stale worker cannot overwrite a newer completion. Fencing protects local state; only destination idempotency/reconciliation protects the external effect.
5. A crash after send starts is conservatively ambiguous even if the request might not have left the process. `KEY`: same request and key within the destination's deduplication contract; `LOOKUP`: reconcile first; `NONE`: manual review. Lease expiry must not bypass this rule.

For strict consumer ordering, serialize by Tenant + consumer + aggregate and process the earliest outstanding aggregate version. A failure/unknown outcome blocks later events for that stream unless an authorized semantic supersede is safe. State-projection consumers may ignore stale versions; command/effect consumers cannot assume every later event replaces earlier work. A dead letter is not permission to execute a later termination/hire out of order.

### Idempotency by use case

| Use case | Identity and atomicity | Duplicate/ambiguous result |
|---|---|---|
| User records a hire twice after response loss | Command key + request hash committed with workforce state and result reference | Same key/same request returns prior result under current authorization; different body rejects key reuse; concurrent same-key submissions converge. |
| Local event consumer | Tenant + consumer + event ID with local state or operation intent | Duplicate becomes no-op; a marker committed before state would lose work and is forbidden. |
| Mock Workday import | Source connection/object/revision + mapping context, workforce import receipt and identity linkage | Repeat page cannot create another Person/Employment; reprocess may revise data but preserves subject identity. |
| Payroll enrollment | Tenant + payroll destination + Employment + enrollment occurrence | Repeated hire facts converge on one operation; no statutory enrollment requirement is asserted. |
| Identity provisioning | Tenant + identity destination + explicitly chosen account subject/occurrence | Decide whether account lifetime follows Person or Employment before simulator work; rehire is not automatically a new account. |
| Equipment request | Tenant + equipment destination + approved request/occurrence | Duplicate events cannot place another order; a genuinely new approved request gets a new occurrence. |
| Retry / event version migration | Existing operation ID and frozen request | Never mint a new business key to make retry succeed. |
| Projection replay | Original event identity within a new projection-generation namespace | Rebuild can apply old facts despite the old generation's receipts; external effect consumers are excluded. |

A `LOOKUP` “not found” result may be stale. Resend only when the destination contract establishes authoritative absence and no earlier request can still complete; otherwise remain in review. `NONE` cannot promise automated exactly-once effects. Human confirmation records evidence and outcome; a Retry button is not evidence. Destination key expiry also limits safe retry age.

### Failure and retry model

The following are **proposed engineering defaults for the synthetic simulator**, not vendor limits, legal requirements or measured SLOs. All attempt caps include the initial attempt. Pin a retry-policy version to an operation; operator retries remain auditable and do not reset its identity.

| Class | Initial proposed policy | Stop/resume condition |
|---|---|---|
| Transient, known safe to resend | Identity: at most 5 attempts/15 minutes, exponential delay from 1 second capped at 60 seconds with full jitter | Exhaustion → dead letter; operator retries after cause fixed. |
| Downstream outage | Equipment: at most 8 attempts/2 hours, delay from 5 seconds capped at 5 minutes; per-destination pause while outage persists | Bounded probe/manual resume; do not consume all records in a hot retry loop. |
| Throttling | Respect `Retry-After` as a lower bound, with positive jitter; do not call earlier than instructed | If requested delay exceeds max operation age, pause for review instead of shortening it. |
| Ambiguous timeout/disconnect/response | Payroll simulator: `LOOKUP` policy; at most 3 reconciliation checks/30 minutes, then manual review | No blind resend. `KEY`/`NONE` follow capability rules above. HTTP status alone does not prove no effect. |
| Validation / data-quality | No timed retry of unchanged input; quarantine inbound or fail outbound | Corrected data produces an explicit revision/reprocess decision. |
| Configuration | No automatic retries; pause connector (bad URL, missing mapping/secret reference) | Validated configuration change and explicit resume. |
| Authorization/authentication | No repeated 403 retries; allow one supported credential refresh for expiry, then pause | Credential/permission fix; no indefinite token refresh loop. |
| Permanent business rejection | No automatic retry; retain sanitized reason and owner | Authorized supersede/discard or corrected operation. |

Production policies require the actual destination contract and measured latency. Do not treat all 5xx responses or connection resets as safe retries; a remote effect may already have happened. Retries are scheduled durable records, not sleeping threads. Manual actions are tenant-scoped, permissioned, reasoned and audited. Final states must distinguish dead letter, cancelled and awaiting manual reconciliation from ordinary transient failure.

### Retention and replay

Never use an outbox as an unlimited event archive. Retain pending/ambiguous work until explicitly resolved, but define escalation and eventual reviewed disposal; “never purge dead letters” is not a complete privacy/storage policy. Purge completed payloads and attempts in small batches after the supported retry/replay horizon and audit requirements are met. Retain compact operation/deduplication receipts at least as long as replays or late redelivery can arrive; reject older replay if its safety evidence has expired. Payload deletion and receipt deletion are separate policies.

Historical replay is supported only for retained, version-readable events. Projection rebuilds use a new generation and cannot invoke external side effects. Side-effect repair targets an existing operation through retry/reconcile. Reprocess uses retained authorized source material and a new mapping version with preview and ownership checks. Backup restoration must begin with dispatch disabled: restoring old delivery state does not undo effects already accepted by a destination.

## 9. Broker recommendation

**KEEP no broker.** Use durable PostgreSQL-backed dispatch when provisioning is introduced; plain in-process listeners alone are not durable. Foundation has no asynchronous subscribers, so it does not need an idle outbox or dispatcher merely to reserve a future architecture.

Turn ADR-0008's exit criteria into a decision gate: first name the consumer and its delivery/replay need; record sustained backlog/latency, DB load, worker concurrency and recovery results under a representative load; try bounded workers/index/batch fixes; then compare operational cost and semantics. A single external consumer does not automatically require Kafka—HTTP delivery may suffice. Long retained replay plus independent consumer offsets favors a log broker; competing work delivery without long replay may favor a queue. No broker product or license is selected here.

Adopt only when a measured requirement cannot be met economically by the dispatcher, or an explicitly approved learning experiment justifies its isolated cost. Keep the producer outbox. Migration must distinguish **broker acknowledgement** from **consumer completion**, define partition keys, consumer receipts, backfill/cutover and duplicate overlap handling. A publisher port reduces producer changes; it does not make consumer state, security, deployment or replay migration free. No “database throughput” threshold is invented without a workload.

## 10. Testing strategy, developer experience and CI/CD

### Behavior-first pyramid

| Milestone | Required evidence |
|---|---|
| Phase 0 (now) | Review links, fence balance, ADR format/status, append-only worklog and documentation-only inventory; diagram semantic inspection. No application tests claimed. |
| Foundation | Fast domain/date/permission unit tests; application service tests through public APIs; PostgreSQL repository/migration tests; tenant and object access negatives; audit rollback/concurrent edit/command-repeat tests; API error/contract checks; architecture tests; focused component and Playwright record/view/edit flow plus keyboard/accessibility review. |
| Provisioning | Fan-out crash recovery, crash before/after send/ack, two workers and expired lease, stale finalization, duplicate event and dual schema representations, three independent destinations, key expiry, slow destination isolation, unknown `NONE`/`LOOKUP`, ordering blockage, retention and restore-with-dispatch-paused tests. Simulator state must survive the crash being tested. |
| Overlay | Adapter fixtures, actual HTTP simulator tests for auth/paging/rate limits/timeouts, missing vs. null, source ordering, page barriers, restart mid-page, cursor expiry, missing-record reconciliation, manager dependencies, rehire, ownership cutover race and lineage to frozen revision. |
| Legal behavior later | Only verified/professionally reviewed versions; source-referenced golden cases, historical expiry/withdrawal/gaps, unavailable capability and reproducible assessment inputs. |

No coverage-percentage target replaces these cases. Avoid a Spring context for every invariant. Test module contracts through public APIs and real PostgreSQL where transaction/constraint semantics matter. Use deterministic clocks and fault barriers, not long sleeps. A genuine restart test keeps the same database and simulator receipts while restarting the application; clearing all state would prove nothing. Testcontainers requires a compatible container runtime; verify Daniel's Windows setup before relying on integration tests. [Testcontainers runtime requirements](https://java.testcontainers.org/supported_docker_environment/).

### Local development proposal

After Phase 1 approval: clone → documented Java/Node/runtime prerequisites → placeholder configuration → start PostgreSQL and one development IdP → run backend with wrapper → run frontend → unit/component tests → database/browser suites. Document PowerShell commands as well as any Unix examples; paths with spaces and CRLF must not break startup. Backend and frontend run on the host first for normal debugger use. Compose should not require a broker, dashboards, mock HRIS or every future destination to show People.

Document reset/reseed as an explicit destructive development operation scoped to disposable demo volumes. Deterministic synthetic seeds include two tenants for isolation tests. Initial identifiers are application-issued synthetic record IDs, never a field pretending to hold a national ID; defer national-ID input until invalid-by-construction construction is researched. Unit tests should explain when a container runtime is unnecessary; integration tests must fail clearly if the runtime is unavailable, not silently skip assurance.

### Future GitHub Actions and deployment progression

1. Foundation PR checks: pinned/reviewed tool versions, formatting/lint/type checks, backend unit/architecture tests, frontend component tests, secret/dependency scans, PostgreSQL migration/module tests, contract generation drift check, production frontend/backend builds and the critical browser smoke flow. No workflow files in Phase 0.
2. Provisioning/overlay: add their deterministic integration/failure suites. Run expensive stress/long browser matrices separately only when useful; never defer core tenant/atomicity safety to an optional nightly job.
3. First packaged deployment: build immutable container artifacts, scan images, use least-privilege CI permissions and environment-scoped secrets, run migrations once, smoke-check health and authenticated behavior. Security suppressions require a recorded reason and review date.
4. First hosted environment: prefer a single application deployment and managed PostgreSQL if affordable; select hosting after costs and data location requirements are known. Keep logs, backups, restore rehearsal, rollback-compatible migration and deployment instructions. No Kubernetes or multi-service CD by default. Publishing/deployment is a separate authorized task.

## 11. Observability and scheduled-job progression

Foundation: structured allow-listed logs, request correlation, stable error codes, duration/outcome and health/readiness. Liveness means process alive; readiness reflects its ability to serve requests, including required DB availability. A simulated downstream outage later must not make the entire workforce API unready. Restrict detailed health/configuration endpoints. Never promise tenant/event IDs in startup logs where no tenant/event exists.

Provisioning: outbox oldest age, due backlog, delivery success/failure/retry, ambiguous operation count/age, lease reclamations, destination latency and dead-letter age. Overlay: last complete sync, checkpoint age, page/record counts, stale/conflict/quarantine and policy revision. Logs/records connect request → change → event → delivery → operation → attempt. Metrics labels are bounded categories/destinations; no employee, event, arbitrary URL or correlation labels. Database records answer individual-operation questions.

Later: OpenTelemetry when cross-process causality becomes difficult to inspect; Prometheus/Grafana when measured SLIs need history and alerts. Propagate trace context across async work, with attempt spans/links to original causes; do not pretend a single long-running span captures a week of retries. No SLO until its numerator, denominator, window and operational consequence are written. A successful health probe does not establish successful worker sync.

### Jobs

Use Spring scheduling as a wake-up trigger with owner-held PostgreSQL job/run/checkpoint records once jobs exist. Avoid Quartz and an external scheduler initially. Fixed-delay dispatch polling is a background job even if the UI presents it as a continuously running worker; the distinction is presentation, not durability.

Each owner defines job identity, Tenant/scope, schedule revision, due occurrence, lease generation, last/next run and run counts/outcomes. Unique occurrence/receipt plus fencing prevents two replicas from finalizing the same work; locks alone do not make remote effects idempotent. Use database time for lease expiry, jurisdiction zone for business dates, and an injectable clock for tests. After downtime, process all eligible unhandled transitions through a bounded catch-up sweep; do not require observing the precise midnight instant. Workforce emits each effective transition once per revision/occurrence; a changed/cancelled future revision supersedes pending work explicitly.

Sync runs belong to integrations; effective-date runs to workforce; retention to each data owner. Operations reads them rather than owning a second scheduler database. Audit archival and legal evaluation remain deferred until retention research and verified behavior respectively justify them. Scaling starts with bounded concurrency and indexed due work, then a separate background profile only after role/lease/deployment tests; a profile is not proof that extraction needs no code changes.

## 12. Security concerns and authentication direction

Prefer **same-origin backend-managed sessions with OIDC login** for the first browser application; tokens remain server-side, and Spring Security handles the standard flow. Spring distinguishes OAuth2/OIDC login from resource-server token validation; an API called by external bearer clients can add the latter later. [Spring Security OAuth2 login](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/core.html). This is a Proposed change to ADR-0013's resource-server-only framing, not a bespoke password system or a separate BFF deployable.

Use Secure/HttpOnly cookies, deliberate SameSite settings compatible with login, CSRF protection on state changes, session fixation protection, bounded expiry, logout and role-revocation behavior. Map issuer + subject to trusted tenant membership; never accept an arbitrary request tenant or `actorKind`. Verify intended audience/issuer/signature/time for bearer flows when introduced. On a single instance an in-memory session may log users out on restart; persist sessions only when deployment requirements justify it. No custom identity server.

Permission checks belong in application services callable by HTTP, jobs and imports; domain invariants stay framework-light. Detail queries authorize the target; list/search/count/export queries apply equivalent scopes before pagination. Initially provide tenant-scoped read/edit/audit grants, not the entire role catalogue. Historical rows use the caller's **current** authorization unless a separately designed historical entitlement requirement appears. Manager access must later declare how multiple assignments, dates and reporting changes affect scope.

RLS remains a focused pre-schema experiment, not an assumed implemented control. Use a non-owner runtime role without `BYPASSRLS`; owner/migration roles are separate. PostgreSQL documents that superusers and `BYPASSRLS` roles bypass policies and table owners normally do too. Unique/reference constraints can also expose information through errors. [PostgreSQL RLS](https://www.postgresql.org/docs/current/ddl-rowsecurity.html). Set tenant context transaction-locally on the actual borrowed connection and test success, rollback and pool reuse; `SET LOCAL` lasts only through the transaction. [PostgreSQL SET](https://www.postgresql.org/docs/current/sql-set.html).

Background enumeration needs an explicit privilege design: a narrowly scoped worker can discover tenant/due-work metadata, then processes each tenant under its tenant-bound service principal and data policy. Do not give the web role unrestricted cross-tenant data access merely to simplify dispatch. Caches, idempotency receipts, external links, cursor storage and uniqueness constraints also need Tenant/source-instance scope. RLS is defense in depth against omitted filters, not a sandbox against a fully compromised application capable of selecting arbitrary tenant context.

Payload minimization is better than redacting copies later. Events carry subject/change references and essential non-sensitive facts; destination-specific requests fetch only permitted revision data, freeze it once, and receive appropriate storage controls. Exceptions, rejected values, URLs/query strings, SQL parameters and raw provider error bodies are common leak paths: use canary tests on failures as well as happy paths. Internal pseudonymous IDs still require controlled log access; correlation IDs must be validated to prevent log injection or unbounded input.

Exports remain off in the foundation. Before introduction require scoped projection, field permissions, bounded output, audit, spreadsheet-formula handling and short-lived tenant-bound download access. Operator retry permission must not imply access to unredacted employee payloads or secrets. SSRF controls need DNS/redirect and egress handling, with an isolated explicit local simulator allowance; “block internal addresses” must not accidentally block the intended development mock or become an unrestricted production bypass.

The existing crypto-shredding discussion is a candidate, not an erasure guarantee. Subject relationships, audit actors, backups and metadata may remain identifying. Keep minimal synthetic data now and defer real-data readiness until authoritative research and an explicit retention/key/restore design. Do not store T3 fields merely to demonstrate encryption.

## 13. Localization implementation concerns

**DOMAIN CONCEPT:** keep Costa Rica terminology and a clean global workforce model. **LEGAL RULE:** all existing concepts remain `UNVERIFIED` / `RESEARCH REQUIRED`; this review verifies none. **SOFTWARE BEHAVIOR:** represent absence and provenance honestly; no statutory validation, calculation, eligibility or deadlines are introduced.

For the first researched behavior, use an explicit Costa Rica package with plain functions and immutable inputs. A clear country dispatch at one boundary is not inherently unauditable; scattered country branches throughout workforce would be. Do not build capability discovery, manifests, inheritance or ten empty countries before an actual capability exists. A second country can later justify a stable interface. Legal equivalence (`EXACT`) and reusable mechanical code are different questions: date formatting or provenance transport need not imply identical law.

| Location | What belongs there |
|---|---|
| Static documentation now | Native name/English explanation, research questions, three-layer distinctions, sources/uncertainty and reasoning. |
| Reviewed versioned content later | Sourced explanatory statements, concept identity, immutable content versions, source metadata and review records. No invented effective dates to satisfy validation. |
| Database later | Optional searchable content projection, tenant policy configuration separate from statutory content, immutable assessment and version references. |
| Configuration | Explicit feature enablement, allowed reviewed capability/version and tenant choices where authorized; no arbitrary legal formulas or prose as executable rules. |
| Code later | Reviewed deterministic behavior, pure validation/calculation where authorized, version selection and enforcement gates; source IDs/version references instead of copied legal prose. |

The five registry entities are a useful conceptual separation, not a requirement for five database tables or a projection loader in Phase 1. Show `UNRESEARCHED` / `DOCUMENTED` as derived display metadata when no legal version/binding exists. Preserve orientation disclaimers; avoid expanding unsourced prose. Until a verified concept warrants product education, the existing documentation can carry this content.

Version selection must distinguish applicability at the fact's date from trust in the content today. Normal expiry/supersession does not erase the historical source; withdrawal for error blocks execution and requires review. Gaps yield `NOT_AVAILABLE`. Multiple applicable candidates yield an ambiguity result, not an arbitrary pick. A withdrawn version may remain readable for historical explanation, never silently executable. ADR-0021 proposes this metadata without changing the prescribed research/implementation vocabularies.

Assessments pin input revision, source/concept/code versions and evaluation mode. Referencing mutable current workforce rows cannot reproduce an old assessment. Corrections create new assessments referencing predecessors; no silent recomputation of old outcomes. Runtime gates supplement content CI: CI alone cannot ensure an old deployment/content projection remains authorized after withdrawal. Professional review is still required before executable legal behavior.

## 14. Enterprise Systems feature prioritization

Here **MVP** means the foundation, **PORTFOLIO PHASE** means provisioning/overlay milestones, and **FUTURE** means beyond those; these are feature classifications, not changes to Daniel's phase gate.

| Capability | Classification | Smallest credible implementation / trigger |
|---|---|---|
| Systems Catalog | PORTFOLIO PHASE | Read list derived from actual destination/source configurations; authored ownership metadata later. |
| Integration Health | PORTFOLIO PHASE | Last outcome, backlog/staleness and reasons; no data → Unknown. Foundation MVP has application health only. |
| Topology | FUTURE | Accessible table first; graph only when enough connections make relationships hard to understand. |
| Scheduled Jobs | PORTFOLIO PHASE | Bounded run list from actual owner job records; no editable scheduler product. |
| Incidents | FUTURE | Minimal human-opened incident only after failures need coordinated investigation. |
| SLOs | FUTURE | Real sync/delivery SLI with a measurement window and decision it changes. |
| Event Explorer | PORTFOLIO PHASE | One event's metadata, consumer deliveries and attempts; broad search after the core view. |
| Retry | PORTFOLIO PHASE | Existing safe operation, same key/request, permission and audit. |
| Replay / reprocess | FUTURE | Projection generation/side-effect isolation and retention safety demonstrated first. |
| Data Lineage | PORTFOLIO PHASE (overlay) | Governed synced fields, mapping/revision references, declared vs. observed consumers. |
| Audit activity | MVP | Bounded, scoped workforce change history; no general operations console needed. |

Minimal future incident: ID/title, severity, status, affected systems, started/detected time, impact, evidence/timeline, owner, root-cause note, resolution and resolved time. No ticketing/CMDB/on-call replacement. Sustained delivery failures, excessive oldest-pending age, repeated complete-sync failures or unresolved ambiguous operations could later **suggest** incidents. Group by Tenant/system/failure episode with cooldown; generate detected, threshold-crossed and recovered timeline entries, not an automatic root cause. A human owns acknowledgement, severity and closure. Avoid duplicate incidents from every retry.

## 15. Recommended Phase 1 scope

**Name: authenticated workforce foundation.** This is a proposed replacement for Phase 1 scope, not permission to implement. Formal proposal: [ADR-0020](adr/0020-foundation-first-delivery-plan.md).

In: one Java/Spring app, one React/TypeScript app, PostgreSQL and Flyway; one development OIDC provider with backend session; access/workforce/audit packages; trusted Tenant context; minimal reader/editor/audit permissions; Person, Employment, initial Assignment, Legal Entity and simple Org Unit references; explicit valid dates/revisions; small governed-field policy defaulting to `PALCHECK`; atomic command receipts and audit; deterministic synthetic seed with two tenants; People list/detail and one record/edit flow; externalized UI strings, keyboard accessibility, logs and health.

Do not add Employee as a table/entity, national IDs, birth dates, health/bank/compensation fields, manager hierarchy permissions or a configurable status taxonomy without need/research. Demo statuses and field validation are **SOFTWARE BEHAVIOR**, not representations of legal eligibility. One synthetic Legal Entity/Org Unit per tenant can be seeded; administration screens are unnecessary for the first flow.

Out: outbox runtime until an asynchronous effect exists; separate lifecycle/operations modules; all downstream providers; HRIS import; legal registry loader/projection/Learn UI; policy engine; future-effective dispatch; full retroactive workflow processing; compensation/leave; real data; broker and observability infrastructure. Documentation and cheap boundary seams remain.

### Acceptance scenarios

1. Authenticate as tenant A's editor and record a synthetic Person, Employment and Assignment; view them through list/detail; restart and verify persistence.
2. Audit and business mutation commit together. Force audit failure and prove no partial employment/command receipt survives.
3. Retry the same command after response loss: one result; conflicting key reuse fails. Two edits at the same revision cannot silently overwrite each other.
4. Tenant B cannot access, count, search, mutate or obtain audit history for A's records, including guessed IDs and wrong-tenant relationship references. Missing context fails closed. Reader cannot mutate.
5. Current/as-of-valid-date and revision queries return the documented data. Empty database and prior-release migrations pass against PostgreSQL.
6. The browser record/view/edit flow works with keyboard and accessible errors; synthetic PII canaries never appear in logs or API error internals. Run documented local commands on Daniel's Windows setup.

### Later bounded milestones

**Provisioning milestone:** durable outbox, operation identity, three simulated destinations and failure controls, effective-date scheduling where required, a small Activity view and safe retry. Here Claude's original hire-to-provision acceptance scenario becomes the release criterion, with EVT-01's crash/replay cases. This may be named Phase 1B or Phase 2 by Daniel; no numbering is silently changed.

**Overlay milestone:** mock HRIS, normalization, checkpoint-safe imports, source links, ownership cutover and field lineage. Keep this before adding unrelated enterprise-console breadth. Legal research proceeds independently; a small sourced Learn experience can be added when content warrants it.

### Focused preconditions instead of eight mandatory spikes

Before first persistence: narrow history/query and tenant/RLS experiments. Before authenticated UI: validate provider/session/logout/CSRF behavior. Choose Maven/packages/ArchUnit as defaults unless Daniel objects; do not compare every build system. Test a headless widget only when the screen needs it. Move outbox evaluation to provisioning, registry-format exploration to content delivery, and event/inbound contract generators to their actual consumer milestones. No experiment is run while Phase 0 remains closed to code.

## 16. Architecture disagreements requiring Daniel's decision

| Decision request | Claude's current proposal | Codex alternative and consequence | Materiality / impacted ADRs |
|---|---|---|---|
| D1: local atomicity and package boundaries | Seven modules now; one module per transaction; integrations owns outbox; no cross-module FK | Accept ADR-0018: workforce owns initial hire; technical messaging has no domain dependency; atomic audit/publication participants; tenant-anchor FK exception; one migration order. Some extraction work is explicitly deferred. | architecture-blocking; 0001/0003/0006/0007/0014/0016 |
| D2: delivery safety | Event-ID dedupe and simple lease retry; replay flag | Accept ADR-0019: business operation identity, frozen requests, durable fan-out, fenced claims and ambiguity-aware recovery. More durable state, narrower truthful guarantee. | architecture-blocking before delivery; 0006/0007/0008/0016 |
| D3: first milestone and auth | Full hire-to-provision + glossary, resource-server framing | Accept ADR-0020: workforce foundation, provisioning next; same-origin OIDC session; simple build/data-access defaults. Portfolio demo arrives in the next milestone. | important; 0001/0002/0009/0011/0012/0013/0016/0017 scope |
| D4: registry history | Expired/deprecated binding always disabled; gap-free coverage | Accept ADR-0021: historically applicable reviewed versions remain selectable unless withdrawn; unavailable/ambiguous content fails closed. Requires explicit trust metadata. | architecture-blocking before legal behavior; 0011/0012 |
| D5: inbound/lineage refinements | Implicit checkpoint advance, absence may terminate, correlation-based lineage | Endorse IMP-01/LIN-01 and §7 before overlay: complete-page barrier, no inferred termination, causal revision/operation links, metadata-default landing. Lower automatic coverage, stronger correctness/privacy. | important before overlay; 0004/0005/0015 |

Daniel: please accept, amend or decline D1–D5 after Claude's response; opening Phase 1 is a separate explicit decision. This request follows [AGENTS.md](../AGENTS.md) §9, “ask Daniel when the disagreement materially changes architecture.” The review and proposals are complete; no approval is needed to read or critique them.

### Disposition of original ADRs

| ADR | Classification | Reason / proposed adjustment |
|---|---|---|
| 0001 | MODIFY | Preserve monolith, fix transaction/DAG and staged modules. |
| 0002 | KEEP | Java/Spring direction; simplify persistence and choose versions at implementation. |
| 0003 | MODIFY | One DB; concrete tenant/reference/history/migration semantics. |
| 0004 | MODIFY | Keep provider boundary; narrow capabilities, checkpoint/reconciliation and raw retention. |
| 0005 | MODIFY | Keep governed-field policy; trusted context, source-instance ownership, cutover fencing. |
| 0006 | MODIFY | Add aggregate/change version, effect identity and replay distinctions. |
| 0007 | MODIFY | Keep outbox pattern; technical ownership, durable fan-out and ambiguity-aware executor. |
| 0008 | KEEP | No broker; measured adoption and explicit migration work. |
| 0009 | MODIFY | Keep React/TS; scope components/contracts to actual flows; backend session. |
| 0010 | KEEP | Accessibility and restrained design; do not claim library use proves conformance. |
| 0011 | DEFER | Retain legal boundaries; runtime capabilities emerge from first verified Costa Rica use. |
| 0012 | MODIFY | Keep conceptual separation/review gates; defer projection, correct historical eligibility. |
| 0013 | MODIFY | Keep external OIDC authentication; distinguish browser login/session from bearer API. |
| 0014 | MODIFY | Keep permission × scope; query-scoped service enforcement, fewer first scopes. |
| 0015 | MODIFY | Keep computed lineage; explicit causal/revision links and retention limits. |
| 0016 | DEFER | Console grows from provisioning data; independent module waits for owned aggregates. |
| 0017 | KEEP | Daniel's standalone-first direction; proposed change concerns milestone size only. |

### What Claude should review next

Respond accept/fix, decline/reason or escalate to each finding ID. Prioritize the new dependency DAG and atomicity exception; event-vs-business operation identity and all crash windows; source checkpoint/cutover protocol; historical registry eligibility; and whether the foundation leaves enough of the overlay seam without importing its runtime complexity.

After Daniel's decisions, reconcile all affected original documents and diagrams together; do not leave two apparent accepted architectures. Original text was preserved during this review, with links to the proposed alternatives. The next handoff is [CODEX_REVIEW_HANDOFF_2026-09-19.md](CODEX_REVIEW_HANDOFF_2026-09-19.md).

### Verified reference corrections and limits

PostgreSQL features, Spring transaction/registry behavior and public Workday documentation were checked in primary references linked beside each claim. RFC 9457 defines a problem-details response format; it does not itself choose PalCheck's error codes or prevent leaking exception details. [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html).

React Aria documents a DatePicker and internationalized date support; Radix documents accessibility primitives. These are reasons for a narrow widget evaluation, not proof that either produces an accessible application automatically. [React Aria DatePicker](https://react-aria.adobe.com/DatePicker), [Radix accessibility](https://www.radix-ui.com/primitives/docs/overview/accessibility). WCAG 2.2 AA remains the target; automated tests and a component library cannot alone establish conformance. [WCAG 2.2](https://www.w3.org/TR/WCAG22/).

Redpanda licensing, specific dependency versions, IdP product/cost, live vendor limits, hosting capacity, and legal facts remain **Unverified:** and deliberately unselected. Existing descriptions of those candidates must not be treated as procurement or implementation evidence. No performance, runtime-security, crash-recovery, or Mermaid-rendering result is claimed by this documentation review.
