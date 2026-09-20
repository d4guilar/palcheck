# Phase 0 Critique of the Original Brief

Author: Claude (architecture reviewer) · Date: 2026-09-19 · Status: input to Daniel's decisions; nothing here is Accepted until Daniel says so.

This is the output of assignment step 2. It challenges the brief in the categories Daniel asked for. Each finding has
an ID so the ADRs, [MODULES.md](MODULES.md), and [CODEX_HANDOFF.md](CODEX_HANDOFF.md) can cite it.

**Severity tags**

- **[BLOCKER]** must be resolved (or explicitly accepted as a risk) before Phase 1 starts.
- **[DECIDE]** a real fork; a recommendation is given, Daniel decides.
- **[NOTE]** worth knowing; handled in the docs already.

## 0. What the brief gets right (keep these)

- Modular monolith over microservices for one developer.
- "Translate, do not distort" as the product's organizing idea. It is a genuinely distinctive thesis.
- The three-layer legal separation (domain concept / legal rule / software behavior) and the hard ban on invented law.
- Ownership metadata and lineage as first-class concerns. Most portfolio HR projects ignore them.
- The eight-question test for every significant decision, and the explicit warning against resume-driven architecture.
- Making failure visible (integration simulator) rather than only building the happy path.

## 1. Contradictions

**C-01 [DECIDE] "No premature abstraction" vs. a generic connector framework, normalized model, and policy packs.**
The brief forbids generic abstractions "before real differences are understood" and also asks for a provider-neutral
`HRISConnector`, a normalized global model, and country policy packs, all from a single provider and a single country.
*Resolution:* fix only the parts that are cheap and boundary-defining now: the **normalized contract** (a versioned data
shape PalCheck owns) and a **capability-flagged connector port**. Do **not** fix a shared country-policy behavior
interface until a second country exists. Adopt the **second-instance rule**: no shared implementation is extracted until
two concrete instances exist, and the second country's equivalent is *documented* (not built) first (the "Colombia test").
See [ADR-0004](adr/0004-hris-adapter-boundary.md), [ADR-0011](adr/0011-country-policy-architecture.md).

**C-02 [DECIDE] Two "fundamental" modes with one domain.**
Standalone (PalCheck is system of record) and overlay (an external HRIS is) pull the domain in opposite directions:
in standalone PalCheck writes everything; in overlay it mostly reads and annotates. Designing both equally invites a
lowest-common-denominator model. *Resolution:* Daniel chose **standalone-first** (see section 9). The seam that keeps
overlay possible is **per-field ownership policy**, in the model from day one. See [ADR-0017](adr/0017-primary-product-mode.md),
[ADR-0005](adr/0005-source-of-truth-ownership.md).

**C-03 [NOTE] A broker appears in the event flow diagram while the text says not to choose one.**
*Resolution:* default is **no broker yet**; outbox plus in-process dispatcher behind a port; explicit exit criteria.
See [ADR-0008](adr/0008-broker-none-yet.md).

**C-04 [NOTE] The proposed repository tree presupposes deployables the architecture says it does not want.**
`workers/integration-worker` and a polyglot `packages/event-contracts` imply separate processes and cross-language
contract publishing, in a project that starts as a modular monolith. *Resolution:* same deployable, separate Spring
profile until a real reason to split exists; contracts are language-neutral JSON Schema files in the repo. Neither
directory exists in Phase 0. See [ARCHITECTURE.md](ARCHITECTURE.md).

**C-05 [NOTE] "Employee", "Person", "Employment", "Manager" are listed as peer primitives; they are not peers.**
A Person is a human. An Employment is a relationship between a Person and a Legal Entity over time. "Employee" is a
role a Person holds *through* an Employment. "Manager" is a relationship between Assignments, not an entity. "Payroll
Event" and "Provisioning Event" are integration facts, not workforce primitives. See [DOMAIN_MODEL.md](DOMAIN_MODEL.md).

**C-06 [NOTE] Legal registry record conflates identity, version, and source.**
The brief's `CountryPolicyConcept` has `version`, `effectiveFrom/To`, and a singular `sourceReference` on one record. A
concept (Aguinaldo) is stable; its legal definition changes over time and rests on several sources. *Resolution:* split
into Concept, ConceptVersion, LegalSource, ConceptMapping, BehaviorBinding. See [ADR-0012](adr/0012-legal-concept-registry.md).

**C-07 [DECIDE] Append-only audit vs. retention and erasure obligations.**
HR data is personal data; an append-only store that copies previous and new values forever conflicts with any
retention limit or erasure request. Which regime applies is a legal question (RESEARCH REQUIRED). *Resolution:* design the
audit store so personal values can be pseudonymized or crypto-shredded without breaking the chain of custody; decide the
mechanism before Phase 1 stores real-shaped data. See [SECURITY.md](SECURITY.md).

## 2. Overengineering

**O-01 [DECIDE] Thirteen modules for one developer.**
Modules cost ceremony (APIs, tests, dependency rules) in proportion to their number. Proposed: **7 modules + 2 deferred**.
People + Organizations + Employment collapse into `workforce` (they change together and share transactions). Workflow
becomes state machines inside `lifecycle`, not a generic engine. Identity splits into `access` (PalCheck's own authn/z)
and a *provisioning target* in `integrations`. Notifications is a downstream system, not a core module. Enterprise Systems
becomes `operations`, a read-mostly plane. See [MODULES.md](MODULES.md), [ADR-0001](adr/0001-modular-monolith.md).

**O-02 [DECIDE] Kafka/Redpanda, a worker deployable, and an OpenTelemetry + Prometheus + Grafana stack, all early.**
Each is defensible later; none is needed to prove the thesis. Phase 1 gets structured logs, correlation IDs, and basic
metrics. The tracing/dashboard stack arrives when there is more than one process to trace or an SLO worth alerting on.

**O-03 [NOTE] Generic workflow module.**
Hire, change, and terminate are three known processes. Model each as an explicit state machine with tasks. A generic
workflow engine (BPMN or similar) is a product in itself and would be resume-driven here.

**O-04 [NOTE] Five simulated downstream systems.**
Start with three: Payroll, Identity, Equipment. They already exercise independent failure, retry, and idempotency.
Benefits and Notifications add rows, not insight.

**O-05 [DECIDE] The Enterprise Systems area is roughly as large as the rest of the product.**
It should be built from real flows, not faked alongside them, and only after a vertical slice exists. Phase 1 contains the
minimum that makes resilience visible (delivery attempts, retry, basic health), not the full catalog/incident/SLO/topology
surface. See [ENTERPRISE_SYSTEMS.md](ENTERPRISE_SYSTEMS.md), [ADR-0016](adr/0016-operations-model.md).

**O-06 [NOTE] Per-value lineage stored forever.**
Storing a lineage record per field per change balloons storage and duplicates the audit log. Store *provenance* on the
value (source system, source field, sync run, mapping version) and *compute* the lineage view from mappings plus delivery
records. See [DATA_OWNERSHIP_AND_LINEAGE.md](DATA_OWNERSHIP_AND_LINEAGE.md), [ADR-0015](adr/0015-data-lineage.md).

**O-07 [NOTE] SLOs as decoration.**
The brief itself warns against this. Rule adopted: an SLO is only published for an SLI that is actually measured, and it
must be able to change a decision (e.g., pause a rollout). Otherwise it stays a documented target, not a dashboard tile.

## 3. Weak or unexamined assumptions

**W-01 [DECIDE] "Public/documented API behavior" is enough to build a faithful Workday-shaped mock.**
*Assumption, unverified:* much detailed vendor API documentation is available only to customers and partners. If so, a
mock cannot be verified against it, and any claim of fidelity would be false. *Resolution:* call it a **Workday-shaped
reference HRIS** over synthetic data, make no fidelity claim, and put the contract tests on PalCheck's side (adapter to
`NormalizedWorker`). Daniel must never fill the gap from employer knowledge (see section 8, BIZ-02). Phase 2, not Phase 1.

**W-02 [DECIDE] "Latin American countries share broadly similar categories."**
This is a hypothesis, and the whole shared-abstraction strategy depends on it. It may be true for the *categories*
(annual payment, termination, social security) and false for the *shape* (who is eligible, how it is triggered, when it is
due, what it is based on). Treat it as a hypothesis to test per concept, recorded as a `ConceptMapping` with an explicit
equivalence level (`EXACT`, `PARTIAL`, `NONE`, `UNKNOWN`), not as a premise. If most mappings come back `PARTIAL`, the
right architecture is a thin shared core with fat country packs, which is what the design already allows.

**W-03 [DECIDE] "Synchronize appropriate results" back to the external HRIS.**
Two-way sync is where integrations go to die: conflict resolution, loops, partial failures, and provider write
permissions. *Resolution:* inbound is read-only by default; write-back is a connector capability flag, enabled per field
that PalCheck explicitly owns, and it is a Phase 3+ topic.

**W-04 [NOTE] A "global" normalized model is a lossy projection.**
Every provider has fields the model lacks. Silently dropping them breaks lineage and future features. *Resolution:*
the normalized contract carries an `extensions` bag for provider-specific data that is preserved but not interpreted, and the
mapper must record what it did not map.

**W-05 [DECIDE] One developer can research the law.**
Reading primary sources is necessary and not sufficient. Before any *behavior* is implemented, a qualified local
professional should review the concept. Until then the registry can educate but must not compute. See
[localization/costa-rica/LEGAL_RESEARCH_POLICY.md](localization/costa-rica/LEGAL_RESEARCH_POLICY.md).

**W-06 [DECIDE] English-only product language.**
Correct for a portfolio audience. For the stated business option (Latin American HR professionals), a Spanish UI is likely
a requirement, not a nicety. *Resolution:* ship English, but externalize every UI string from day one, and keep native legal
terms in the registry (content) rather than in UI translation bundles. Cost now: small. Cost of retrofitting: large.

**W-07 [NOTE] Kafka is assumed to add portfolio value.**
It adds more if you can explain why you did *not* use it and what would make you change your mind. That is a stronger
interview answer than a single-broker, single-consumer-group deployment.

## 4. Missing domain concepts

Each of these will be forced by real data or real customers; retrofitting most of them is expensive. None requires
inventing law. Details in [DOMAIN_MODEL.md](DOMAIN_MODEL.md).

- **M-01 [BLOCKER] Tenancy.** "Organization isolation" is mentioned in Security only. The tenant (customer of PalCheck) must be
  in every table and every query from the first migration. Retrofit is the classic multi-tenant failure.
- **M-02 [BLOCKER] Legal Entity.** The employer of record is not the customer and not the department. Local rules attach to
  the legal entity and the country of employment, so this drives localization directly.
- **M-03 [BLOCKER] Effective dating.** Job changes, status changes, and compensation are valid over time, sometimes
  future-dated or retroactive. HR systems live and die by this. It must shape the model before any schema exists.
- **M-04 [DECIDE] Person vs. Employment vs. Assignment.** Rehire, concurrent employment, transfers, and reporting lines
  all require separating the human, the employment relationship, and the current job/position placement.
- **M-05 [DECIDE] Worker type: employee vs. contractor vs. other.** Misclassification is a well-known Latin America risk area
  (RESEARCH REQUIRED for Costa Rica). The model must at least distinguish worker types and not assume everyone is an employee.
- **M-06 [NOTE] Identifiers.** National ID formats differ by country; they are high-tier PII. Model an identifier as
  `(type, country, value)`, never a single `nationalId` string.
- **M-07 [NOTE] Calendars and holidays; time zones; pay periods.** Jurisdiction-dependent reference data. Not modeled in
  Phase 1; the model must not preclude them.
- **M-08 [NOTE] Currency.** CRC is named. Whether compensation may be denominated in other currencies, and what that implies, is
  RESEARCH REQUIRED. Never model money as a bare number: `Money(amount, currency)`.
- **M-09 [NOTE] Approvals and delegation.** "Approvals" is listed; the model (who, scope, delegation, escalation) is not.
- **M-10 [NOTE] Documents and consent.** Contracts and notices are documents; privacy consent and data-subject requests are a
  cross-cutting concern. Deferred, but the security model should leave room.
- **M-11 [NOTE] Reference data / code lists** (job codes, cost centers, termination reasons). Provider code lists must be mapped,
  not copied. Ties into the mapper's versioned mappings.
- **M-12 [NOTE] Employment status taxonomy.** Active, on leave, terminated, and sub-reasons differ across HRIS providers and
  jurisdictions. Model a PalCheck status set plus a per-provider mapping; validate against local concepts later.

## 5. Unclear ownership boundaries

- **B-01** "Who owns the hire?" `lifecycle` orchestrates the process; `workforce` owns the resulting state. Lifecycle
  never writes another module's tables.
- **B-02** Ownership *policy* (who may write a field) and value *provenance* (where a value came from) are two different
  things that the brief merges into one metadata block. Keep them separate. See [ADR-0005](adr/0005-source-of-truth-ownership.md).
- **B-03** Country-policy enrichment outputs (annotations, derived facts) belong to `countrypolicy`, keyed by ID to
  `workforce` entities. They never mutate normalized data. See [INTEGRATION_ARCHITECTURE.md](INTEGRATION_ARCHITECTURE.md).
- **B-04** The Systems Catalog and the connector registry would drift if kept separately. Derive the catalog from the registry;
  add only what the registry lacks (owner, description, category).
- **B-05** Domain events, audit records, and lineage overlap. Distinct purposes: events = *business facts for consumers*;
  audit = *who changed what, for accountability*; lineage = *where a value came from and where it went*. One capture path in
  `workforce` and `integrations` feeds all three; nothing is hand-written three times.
- **B-06** Cross-module database foreign keys. Recommendation: modules reference each other by ID only, no cross-module FKs
  or joins. This is the price of keeping extraction possible. See [ADR-0001](adr/0001-modular-monolith.md).

## 6. Scalability and evolution concerns

- **S-01** Outbox and delivery-attempt tables grow without bound; plan retention and partitioning before they matter.
- **S-02** Full syncs against a large HRIS need batching, idempotent upserts, and resumability. A sync run is a first-class
  record, not a loop.
- **S-03** Effective-dated history multiplies row counts; choose a history pattern deliberately (open question OQ-03 in
  [DOMAIN_MODEL.md](DOMAIN_MODEL.md)).
- **S-04** Scheduled jobs on a single node need an explicit lock so a second instance does not double-run them.
- **S-05** First extraction candidate is `integrations` (different failure and scaling profile, I/O bound, retry-heavy). It only
  stays extractable if no other module joins its tables.

## 7. Latin America strategy: where it is too vague

The brief states the destination well and the mechanism loosely. Made concrete in
[localization/LATAM_STRATEGY.md](localization/LATAM_STRATEGY.md) and
[localization/COUNTRY_POLICY_ARCHITECTURE.md](localization/COUNTRY_POLICY_ARCHITECTURE.md):

- "Localization" is five different things: presentation locale, terminology, jurisdiction rules, local data, and local
  workflow variants. Each has a different owner and change rate.
- "Country" is not always the right unit; some rules are sub-national or sector-specific. Use **jurisdiction**, and start with
  country-level for Costa Rica without assuming that generalizes.
- The mechanism that stops distortion is the `ConceptMapping` with an explicit equivalence level, plus the second-instance rule.
- Research needs a pipeline (source hierarchy, verification levels, professional review), not just a status enum.
- Spanish is not one language operationally; terminology varies by country. Registry entries carry native names per country.

## 8. HRIS translation model: refinements

Full design in [INTEGRATION_ARCHITECTURE.md](INTEGRATION_ARCHITECTURE.md). The brief's four-box pipeline needs these additions:

- **T-01** Separate *Adapter* (protocol, auth, paging, provider DTOs) from *Mapper* (versioned mapping to `NormalizedWorker`).
- **T-02** A retained, redacted **raw landing** so failed or changed mappings can be replayed without re-pulling.
- **T-03** A **reconciliation** step: identity links, delta vs. full sync, conflicts, terminations, rehires, future-dated changes,
  out-of-order updates.
- **T-04** Provider **capability flags** (delta, webhooks, write-back) so the core never assumes what a provider cannot do.
- **T-05** **Local supplemental data**: enrichment often needs data no external HRIS holds. It is PalCheck-owned and attached to
  externally-owned employment.
- **T-06** Error classification (transient vs. permanent vs. data-quality) driving retry, dead-letter, or human review.
- **T-07** Retry vs. replay vs. reprocess are three operations with different safety properties; do not conflate them.

## 9. Decision note: standalone-first (A1)

Daniel chose **standalone-first** on 2026-09-19. Reviewer's recommendation, on record once and not repeated elsewhere: overlay-first
is the sharper wedge for the Latin America translation thesis and avoids competing with incumbent HRIS products on breadth.

Standalone-first is accepted. The costs to manage:

| Risk | Mitigation |
|---|---|
| Scope creep toward a payroll engine ("Pal" + "Paycheck" invites it) | PalCheck **represents and orchestrates** payroll concepts; it does not compute pay. Written as a non-goal in [PRODUCT_VISION.md](PRODUCT_VISION.md). |
| Domain fills with standalone-only assumptions | `Employment` and `Person` contain no Workday concepts; per-field ownership policy exists from day one, default `PALCHECK`. |
| Overlay seam rots unused | Workday-shaped reference connector is Phase 2 and must flip ownership on real fields without domain changes. That is the test. |
| Breadth pressure (leave, compensation, onboarding) dilutes the thesis | Phase 1 is one vertical slice; the localization value shows through the registry and local data even in standalone. |

## 10. Security concerns

- **SEC-01 [DECIDE]** The brief's permission list is flat. Real access is **permission × scope** (self, direct reports, org unit,
  legal entity, tenant). Without scope, "manager" cannot be expressed. See [ADR-0014](adr/0014-authorization.md).
- **SEC-02 [BLOCKER]** Some Costa Rica concepts (e.g., Incapacidad) imply health-related data, usually a higher-sensitivity
  category. Treat as special-category from the first design (RESEARCH REQUIRED for the exact legal treatment).
- **SEC-03** Event explorer, replay, and raw landing all handle payloads. Redaction by default; unredacted access is a separate
  permission and is itself audited.
- **SEC-04** The simulator's failure-injection controls are an attack surface and a source of confusion. Admin-only, demo-profile-only,
  and simulated systems are visibly labelled everywhere they appear.
- **SEC-05** Synthetic national IDs must be invalid-by-construction, so no test fixture can ever describe a real person.

## 11. Business-viability concerns (record now, do not build)

- **BIZ-01 [DECIDE]** Legal accuracy is a product liability, not just an engineering task. A real business needs professional
  review of every implemented concept, an explicit "not legal advice" posture, and a change-monitoring process.
- **BIZ-02 [BLOCKER for Daniel, not for the design]** Before this becomes a business, confirm any employer invention-assignment,
  outside-activity, or conflict-of-interest obligations that could touch an HR-technology side project. This is a flag, not legal
  advice; it needs Daniel's own review of his agreements. The clean-room rules in [AGENTS.md](../AGENTS.md) exist to protect this.
- **BIZ-03** Data-protection regime, data residency, and sub-processor obligations will shape hosting and tenancy for real
  customers. RESEARCH REQUIRED; only documented now.
- **BIZ-04** Licensing is undecided (README says no license granted). Keep it that way until Daniel decides.
- **BIZ-05** The name suggests payroll calculation. Fine for a friendly brand, but the product must be clear about what it does
  not do. Also: trademark clearance for "PalCheck" has not been checked (Unverified).
- **BIZ-06** Selling to enterprises that already run a global HRIS is an overlay sale; selling to small organizations is standalone.
  These are different buyers, sales cycles, and support burdens. Standalone-first makes the first customer the smaller one.

## 12. Process concerns

- **P-01** `AGENTS.md` and `CLAUDE.md` duplicating rules will drift. One source (`AGENTS.md`), `CLAUDE.md` imports it. Done.
- **P-02** A shared append-only worklog will conflict on merge if two agents write concurrently. One entry = one contiguous section
  at the end of the file. Documented in [AI_COLLABORATION.md](AI_COLLABORATION.md).
- **P-03** "Do not create trivial ADRs" vs. sixteen candidates. Seventeen ADRs are drafted; several are deliberately short.
  If Daniel finds one trivial, merge it rather than pad it.
- **P-04** Nothing is Accepted yet. Every ADR is Proposed. Codex should challenge them before Daniel accepts any.
