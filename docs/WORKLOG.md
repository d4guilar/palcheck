# Worklog

Append-only log of meaningful work by every contributor (Daniel, Claude Code, OpenAI Codex). Rules are in [../AGENTS.md](../AGENTS.md) section 8 and [AI_COLLABORATION.md](AI_COLLABORATION.md) section 8:
never edit or reorder earlier entries (correct yourself with a new entry that references the old one); one entry is one contiguous section appended at the end; timestamps come from the system clock; write for a reader with no chat context.

Entry format:

```
## YYYY-MM-DD HH:MM — CLAUDE | CODEX
### Task
### Work completed
### Decisions made
### Assumptions
### Open questions
### Risks
### Suggested next step
```

---

## 2026-09-19 11:24 — CLAUDE

### Task

Phase 0 first assignment from Daniel's brief ([../MASTER_PROJECT_BRIEF.md](../MASTER_PROJECT_BRIEF.md)): read and critique the brief; propose the Phase 0 repository structure; define modules; define the HRIS translation architecture, ownership model, Latin America localization
architecture, and Legal Concept Registry; define the Enterprise Systems area; produce diagrams and initial ADRs; write Phase 0 documentation, this worklog, and `docs/CODEX_HANDOFF.md`; then stop. **Documentation and diagrams only; no implementation.**

### Work completed

Created 52 Markdown files (the folder previously held only an empty `hello.txt`, which was not touched). No code, schema, endpoints, UI, Docker, CI, dependency manifests, or `git init`.

- **Root:** [README.md](../README.md), [MASTER_PROJECT_BRIEF.md](../MASTER_PROJECT_BRIEF.md) (Daniel's brief verbatim plus an Amendments table), [AGENTS.md](../AGENTS.md) (single source of shared agent rules), [CLAUDE.md](../CLAUDE.md) (imports `AGENTS.md`, adds Claude's role).
- **Core docs (`docs/`):** [PRODUCT_VISION.md](PRODUCT_VISION.md), [PHASE0_CRITIQUE.md](PHASE0_CRITIQUE.md) (assignment step 2), [ARCHITECTURE.md](ARCHITECTURE.md) (includes the Phase 0 and target repository structure, step 3), [MODULES.md](MODULES.md) (step 4),
  [DOMAIN_MODEL.md](DOMAIN_MODEL.md), [INTEGRATION_ARCHITECTURE.md](INTEGRATION_ARCHITECTURE.md) (step 5), [DATA_OWNERSHIP_AND_LINEAGE.md](DATA_OWNERSHIP_AND_LINEAGE.md) (step 6 and lineage), [ENTERPRISE_SYSTEMS.md](ENTERPRISE_SYSTEMS.md) (step 9),
  [SECURITY.md](SECURITY.md), [UI_SYSTEM.md](UI_SYSTEM.md), [ROADMAP.md](ROADMAP.md), [AI_COLLABORATION.md](AI_COLLABORATION.md), this worklog, [CODEX_HANDOFF.md](CODEX_HANDOFF.md).
- **Localization (`docs/localization/`):** [README.md](localization/README.md), [LATAM_STRATEGY.md](localization/LATAM_STRATEGY.md), [COUNTRY_POLICY_ARCHITECTURE.md](localization/COUNTRY_POLICY_ARCHITECTURE.md) (step 7), [LEGAL_CONCEPT_REGISTRY.md](localization/LEGAL_CONCEPT_REGISTRY.md) (step 8);
  Costa Rica: [README.md](localization/costa-rica/README.md), [GLOSSARY.md](localization/costa-rica/GLOSSARY.md), [LEGAL_RESEARCH_POLICY.md](localization/costa-rica/LEGAL_RESEARCH_POLICY.md), [RESEARCH_BACKLOG.md](localization/costa-rica/RESEARCH_BACKLOG.md). Contain no legal rules.
- **Diagrams (`docs/diagrams/`, 8 files, 15 Mermaid blocks; step 10):** system context, container, module map, HRIS integration, event processing, country policy, data lineage, operations topology.
- **ADRs (`docs/adr/`, step 11):** [index](adr/README.md) plus ADR-0001 to ADR-0017, all **Proposed**.

Verification (documentation-only, scripted, run at the end of the task; script kept in the session scratchpad, not in the repo): see "Verification result" below.

### Decisions made

- **Daniel decided (this session): standalone-first** as the primary design driver, with the enterprise-overlay mode preserved as a seam. Recorded in [ADR-0017](adr/0017-primary-product-mode.md) and Amendment A1. The reviewer's overlay-first recommendation is recorded once in [PHASE0_CRITIQUE.md](PHASE0_CRITIQUE.md) section 9 and not relitigated.
- **Proposed by Claude (all Proposed, none Accepted):** modular monolith with 7 modules + 2 deferred; PostgreSQL with pooled tenancy; separation of ownership policy from provenance; Adapter/Mapper/`NormalizedWorker` pipeline with capability flags; internal vs. published events, at-least-once + idempotent processing with `KEY`/`LOOKUP`/`NONE` destinations; explicit transactional outbox;
  **no broker until measurable exit criteria** ([ADR-0008](adr/0008-broker-none-yet.md)); five-entity Legal Concept Registry with verification levels and gates, content as reviewed files; capability-based country policy packs with a second-instance rule; permission x scope authorization; computed (not stored) lineage; an Operations Console derived from real flows.
- **Repository structure:** docs only in Phase 0; the brief's code-side directories are deferred with reasons ([ARCHITECTURE.md](ARCHITECTURE.md) section 11). `DATA_LINEAGE.md` became `DATA_OWNERSHIP_AND_LINEAGE.md`; the four Costa Rica topic files became one `RESEARCH_BACKLOG.md` of questions.
- **Terminology:** never use bare "organization"; use Tenant, Legal Entity, or Org Unit.
- **Non-goal made explicit:** PalCheck does not compute pay, in any mode.

### Assumptions

- Daniel's answer "Standalone-first" to the single clarifying question is a final decision for planning purposes.
- The brief's Interview Readiness list is a required coverage target; mapped in [ROADMAP.md](ROADMAP.md).
- Spanish terms *Patrono*, *Planilla*, and the CCSS role wording in the glossary are **Unverified**; they are orientation only.
- Library, standard, and vendor statements in the docs (Spring Modulith registry behavior, React Aria/Radix capabilities, Redpanda licensing, PostgreSQL features, RFC 9457) are from general knowledge and are marked `Unverified` where they appear.
- No employer-confidential knowledge was used. Vendor-related content is limited to what the brief itself states; the Workday reference HRIS is described as a Workday-shaped mock with no fidelity claim.

### Open questions

Consolidated, with owners, in [CODEX_HANDOFF.md](CODEX_HANDOFF.md) section 2. The ones that most need Daniel: acceptance of the ADRs; **BIZ-02** (review of any employer invention-assignment, outside-activity, or conflict-of-interest obligations before this becomes a business; flagged, not legal advice); **LR-01** (who will be the professional reviewer); **BIZ-05**
(trademark clearance for "PalCheck", unchecked); whether Daniel accepts the "does not compute pay" non-goal; who implements which module in Phase 1.

### Risks

- Scope creep from standalone-first toward a payroll or full-HRIS build.
- One developer carrying seven modules, a UI, and an operations plane; mitigated only by keeping Phase 1 a thin vertical slice.
- Legal content: the glossary and registry could be read as authoritative despite banners. Mitigation is the status gates and disclaimers; the residual risk is human.
- Seventeen ADRs and a five-entity registry may themselves be over-engineering; Codex is asked to challenge this.
- **Mermaid diagrams were not machine-validated** (no local Mermaid tooling; nothing installed). They were reviewed by eye and may contain syntax errors that only a renderer will reveal.
- Facts about third-party libraries and standards were not checked against current documentation.

### Suggested next step

1. **Codex** reviews Phase 0 per [CODEX_HANDOFF.md](CODEX_HANDOFF.md) and appends a `CODEX` worklog entry with findings (challenge priorities are in that file's section 3).
2. **Daniel** reads [PHASE0_CRITIQUE.md](PHASE0_CRITIQUE.md) and the ADR index, answers the open questions above, and accepts, amends, or rejects the ADRs.
3. Only then does Daniel decide whether to open Phase 1 (spikes S1 to S8, then the hire-to-provision slice per [ROADMAP.md](ROADMAP.md)). In parallel, Costa Rica **P1 research** ([localization/costa-rica/RESEARCH_BACKLOG.md](localization/costa-rica/RESEARCH_BACKLOG.md)) can start at any time and needs no code.

### Verification result

Recorded after the last file was written (2026-09-19): 52 Markdown files; every relative link and heading anchor resolves (0 errors); code fences balanced; every ADR has Status, Date, Context, Decision, Alternatives, Consequences (positive and negative), Reconsider When, Related Decisions, and an interview line;
no scaffolding or code files; no `.git`; `hello.txt` unchanged (0 bytes); no percentages, currency amounts, or day/month counts in `docs/localization`; no partnership or certification claims. Mermaid syntax not machine-validated (see Risks).

## 2026-09-19 13:17 — CODEX

### Task

Perform the second senior engineering review of Claude's Phase 0 architecture for implementation, reliability, backend/platform, integration, testing and developer experience. Produce the requested review and proposals, update relevant documentation and stop before implementation.

### Work completed

- Read all 52 original Markdown documents, including the original brief and Amendments, all 17 original ADRs, localization research policy/backlog and all 15 original Mermaid blocks. Followed the documented replacement of DATA_LINEAGE.md with DATA_OWNERSHIP_AND_LINEAGE.md.
- Created [CODEX_ARCHITECTURE_REVIEW.md](CODEX_ARCHITECTURE_REVIEW.md), with the requested 16 sections, nine findings, all-original-ADR disposition, dependency inventory, module/API/data/transaction boundaries, database/history/migration plan, import checkpoint and ownership-cutover protocol, outbox/idempotency/retry/replay design, tests, local development, CI/deployment, security, localization and feature prioritization.
- Created Proposed [ADR-0018](adr/0018-module-transactions-and-persistence.md), [ADR-0019](adr/0019-durable-delivery-and-effect-identity.md), [ADR-0020](adr/0020-foundation-first-delivery-plan.md), and [ADR-0021](adr/0021-registry-history-and-behavior-gates.md). Created [diagram 09](diagrams/09-proposed-transaction-and-delivery.md) and [review handoff](CODEX_REVIEW_HANDOFF_2026-09-19.md).
- Added review notices to architecture, modules, domain, integrations, ownership/lineage, security, enterprise systems, roadmap and the registry/country-policy architecture documents; linked the review from README and indexed the new ADRs. The original proposals remain visible pending Daniel's decisions; no original ADR or brief text was changed.
- Verified relevant public primary documentation for PostgreSQL, Spring transaction propagation/Modulith/OIDC login, ArchUnit, Flyway, Testcontainers, Workday SOAP documentation and selected UI/standards claims. Citations and limits are in the review. No legal research or live vendor access was performed.
- Documentation checks before this append: 59 Markdown files, 17 Mermaid blocks, zero broken local links/heading anchors, balanced fences, all 21 ADRs with required sections and Proposed status; no non-Markdown files in the workspace inventory. No code, schema, migrations, endpoints, mocks, dependencies, infrastructure or CI were created; no Git repository was initialized. Mermaid inspected as text only, not rendered.

### Decisions made

No architecture decision was accepted and no phase was opened. KEEP recommendations: standalone-first, modular monolith, Java/Spring and React/TypeScript, PostgreSQL, no broker, provider isolation, workforce ownership, synthetic-only data and legal gates. Proposals: explicit shared local transaction participants and technical messaging ownership; stable business-effect identity and ambiguity-aware recovery; smaller workforce foundation before provisioning; reviewed historical registry eligibility distinct from withdrawal. Full KEEP/MODIFY/DEFER/REMOVE inventory is in the review.

### Assumptions

Assumption: Daniel's standalone-first planning decision in Amendment A1 remains binding. Initial workflows can use minimal synthetic application record IDs without national-ID fields, legal eligibility claims or payroll calculation. Retry numbers in the review are proposed simulator engineering settings, not vendor limits or legal requirements. Package/data layouts are conceptual plans only.

### Open questions

Daniel decisions D1–D5 in review §16: module/transaction/persistence changes; delivery safety; smaller foundation and browser session direction; registry history gates; inbound/lineage refinements. Claude should respond accept/fix, decline/reason or escalate for TX-01, EVT-01, IMP-01, SEC-01, LOC-01, SCP-01, LIN-01, DAT-01 and REF-01. If approved, reconcile baseline documentation and phase numbering before implementing. Opening Phase 1 remains a separate explicit decision in ROADMAP.md.

### Risks

No runtime behavior, library integration, crash recovery, performance, RLS/pool safety or Mermaid rendering was tested. Proposed alternatives and original baseline coexist explicitly pending review; neither should be mistaken for an accepted implementation contract. External key/lookup capabilities limit achievable effect guarantees. Legal facts remain UNVERIFIED / RESEARCH REQUIRED and software behavior NOT IMPLEMENTED. Real-data retention/erasure remains a separate researched decision.

### Suggested next step

Claude reviews the findings and ADR-0018–0021 using [CODEX_REVIEW_HANDOFF_2026-09-19.md](CODEX_REVIEW_HANDOFF_2026-09-19.md); Daniel accepts, amends or declines D1–D5. Reconcile the documents after his decisions, then request/record a separate Phase 1 opening if desired. Stop here at documentation.

Final preservation check after the worklog append: zero link/anchor errors; original MASTER_PROJECT_BRIEF.md and all 17 original ADRs retain their baseline SHA-256 hashes; the original 8,405-byte WORKLOG.md prefix retains its baseline hash. Seven new Markdown files were added. The pre-existing empty hello.txt was present in the initial hash inventory but absent at final verification. No review edit or command targeted/deleted it; the cause is unknown. It was not restored, to avoid undoing an external workspace change.

## 2026-09-19 14:11 — CLAUDE

### Task

Open Phase 1 (Authenticated Workforce Foundation) as directed by Daniel: read the Phase 0 material and the Codex architecture review, reconcile the documentation with the six approved implementation-review refinements, write the Phase 1 implementation plan, and record the start of Phase 1. Implementation steps 1 and 2 of the plan only; no application code yet.

### Work completed

- Read all mandatory documents, the full [Codex architecture review](CODEX_ARCHITECTURE_REVIEW.md), the review handoff, ADR-0018 to ADR-0021 and diagram 09. Noted that Daniel or Codex removed the empty `hello.txt` (already recorded by Codex); the workspace has no Git repository.
- **ADRs:** added acceptance records to [ADR-0018](adr/0018-module-transactions-and-persistence.md), [ADR-0019](adr/0019-durable-delivery-and-effect-identity.md), [ADR-0020](adr/0020-foundation-first-delivery-plan.md), [ADR-0021](adr/0021-registry-history-and-behavior-gates.md) (status "Accepted in part", per-decision ACCEPTED / PROVISIONAL / Proposed); added amendment notes to ADR-0001, 0006, 0007, 0012 (original text preserved); created [ADR-0022](adr/0022-phase1-technical-baseline.md) (Proposed); updated the [ADR index](adr/README.md).
- **Reconciliation:** replaced the "Second-review notice (Proposed)" banners with reconciliation notices in ARCHITECTURE, MODULES, DOMAIN_MODEL, INTEGRATION_ARCHITECTURE, DATA_OWNERSHIP_AND_LINEAGE, SECURITY, ENTERPRISE_SYSTEMS, COUNTRY_POLICY_ARCHITECTURE and LEGAL_CONCEPT_REGISTRY; added the accepted dependency direction ([MODULES.md](MODULES.md) 2a, [diagram 03](diagrams/03-module-map.md)); marked superseded statements inline (ARCHITECTURE rule 4, INTEGRATION 5.1 and 5.4); bannered diagram 05 and updated diagram 09's status; restructured [ROADMAP.md](ROADMAP.md) (Phase 1 opened, provisioning and overlay kept as later milestones without renumbering); updated AGENTS.md (phase gate, reading list, status vocabulary, worklog format), CLAUDE.md, README status, AI_COLLABORATION division of labor, and the CODEX_HANDOFF pointer.
- **Plan:** created [PHASE_1_IMPLEMENTATION_PLAN.md](PHASE_1_IMPLEMENTATION_PLAN.md) (scope, package structure, table ownership, domain model, persistence, idempotency, authentication, authorization, API, logging, frontend, tests, dependencies, steps, risks).
- Environment findings recorded in the plan: JDK 17.0.12, Node 24, npm 11, Git 2.50, PostgreSQL 17 running as a local Windows service; **no Docker, no Maven**; network reachable to Maven Central and npm. Latest stable Spring Boot on Maven Central is 4.1.1.

### Decisions made

- Daniel's six refinements are recorded as accepted (see the acceptance records): atomic state + audit (+ future durable publication) in one local transaction; outbox is technical `messaging`, never owned by `integrations`; business operation identity separate from event identity; failure recovery is an accepted requirement for future integration work; legal applicability vs. withdrawal; smaller milestones. Only state + audit is implemented in Phase 1; no publication, outbox, broker or events.
- Phase 1 technical baseline (ADR-0022, provisional): Maven wrapper + packages + ArchUnit (no Spring Modulith); Spring Boot 4.1.x on Java 17; Spring JDBC with explicit tenant-scoped repositories (no JPA); backend-managed OIDC session with CSRF; Keycloak as a development identity provider; permission-based authorization with tenant-wide scope; version-based optimistic concurrency with `expectedVersion`; command idempotency with `Idempotency-Key` (required on create); append-only audit; row-level security deferred; embedded real PostgreSQL for tests because no container runtime exists on this machine.
- Milestone order is foundation, then provisioning, then overlay; later milestones are not renumbered.

### Tests

None yet: documentation-only steps. Link check across all Markdown files after the edits: 0 broken relative links.

### Assumptions

- Daniel's approval of the six refinements covers exactly the decisions marked ACCEPTED in each acceptance record; decisions marked PROVISIONAL are followed under his implementation direction (database ownership, Docker Compose, OIDC, Keycloak acceptable) and await confirmation.
- Employment `status` (`PENDING`/`ACTIVE`/`ENDED`) and `employmentType` (`FULL_TIME`/`PART_TIME`) are software placeholders, not legal classifications.
- `Idempotency-Key` is required on create only.

### Open questions

Q1 to Q3 in the plan (section 16): confirm ADR-0022 provisional choices; confirm required-on-create idempotency and 24-hour expiry; confirm the placeholder employment vocabularies. Also unresolved from Phase 0: BIZ-02, LR-01, BIZ-05.

### Risks

Spring Boot 4 / Spring Security 7 / Jackson 3 are newer than the APIs I know best; Compose and the Keycloak realm cannot be run on this machine (no Docker) and will be labelled unverified until Daniel runs them; documentation now contains original and amended text side by side, deliberately, with amended statements marked.

### Suggested next step

Step 3 of the plan: create the backend (Maven wrapper, Spring Boot skeleton, Flyway, health), the frontend (Vite + React + TypeScript), Docker Compose and Keycloak realm files, `.env.example`, CI skeleton, and verify the builds and health endpoint locally.

## 2026-09-19 14:58 — CLAUDE

### Task

Phase 1 implementation steps 3 to 9: backend, frontend and local-development foundation; authentication and tenant context; workforce persistence; people read experience; create and edit commands; audit; concurrency and idempotency. Frontend feature work (steps 10 to 12) is not started in this entry.

### Work completed

- **Foundation:** Maven wrapper (Maven 3.9.16) and `backend/` Spring Boot 4.1.1 on Java 17; Flyway with one ordered stream (V1 schemas, V2 access, V3 workforce, V4 audit, V5 platform idempotency); health probes (liveness, readiness including the database); `web/` Vite + React + TypeScript project with Vitest, Testing Library and axe checks; `docker-compose.yml` (PostgreSQL 17, Keycloak 26.7.4), `.env.example`, Keycloak development realm with four synthetic users, `.gitignore`, and `.github/workflows/ci.yml`.
- **Authentication and tenant context (`access`):** OIDC authorization-code login with a backend-managed session (`oauth2Login`), CSRF with the SPA cookie/header pattern, JSON logout URL for RP-initiated logout, JSON problem responses for 401/403, trusted principal-to-tenant mapping on (issuer, subject) resolved per request, permission model and roles (`HR_ADMIN`, `HR_VIEWER`), `GET /api/session`.
- **Workforce (`workforce`):** domain model (Person, Employment, Assignment, Legal Entity, Org Unit) with shared validation for create and patch, tenant-qualified composite foreign keys, JDBC repositories that all take a tenant, directory search and paging, employment views; endpoints for list, detail, employments, audit history, create, and three PATCH resources; reference lists.
- **Audit (`audit`):** append-only `audit_entry` (database trigger rejects update, delete, truncate), recorder that requires an existing transaction, tenant-scoped history read.
- **Platform:** RFC 9457 problem details with one error model, request/correlation-ID filter with strict header validation and one access-log line per request, idempotency service (receipt reserved inside the business transaction), scheduled purge.
- **Development seed:** `dev`-profile seeder creating fictional tenants Acme Latin America (13 people, 2 legal entities, 6 org units, managers, an ended and a pending employment, one edit with history) and Globex Andina (4 people), through the real services under real seeded principals.
- Plan updated with statuses; nothing outside the plan was built.

### Decisions made

- **Vendor-neutral OIDC registration id** (`oidc`), so no PalCheck path or type names Keycloak.
- **Field rules live in the domain**, not in Bean Validation; request bodies are read field by field so every problem is reported at once and unknown fields (including a client-supplied `tenantId`) are rejected.
- **Cross-tenant ids are reported as NOT_FOUND**, indistinguishable from nonexistent ids; cross-tenant references in a request body are validation errors. `TENANT_ACCESS_DENIED` is for an identity without an active PalCheck principal or tenant.
- **Audit history key is the Person**, so one screen shows person, employment and assignment changes; entries written by one command share a `change_set_id`.
- **Idempotency:** required on create, optional on patch; scope tenant + principal + operation + key; 422 for key reuse with a different request; receipts hold a result reference, not personal data; expiry 24 hours.
- **Dependencies added** (all recorded in plan section 14): Spring Boot starters, PostgreSQL driver, Flyway; test-only embedded PostgreSQL 17 binaries, ArchUnit, mock-oauth2-server; frontend: React, React Router, TanStack Query, react-intl, Vite, Vitest, Testing Library, vitest-axe, Playwright (installed, not yet used).
- **Testcontainers not used**: no container runtime on this machine; real PostgreSQL 17 binaries run in-process instead (ADR-0022 decision 10).

### Tests

Backend: **91 integration tests + 15 architecture tests, all passing** (`mvn verify`), against real PostgreSQL 17. Coverage of the plan's list: successful create/update; validation rejection; **tenant isolation on every endpoint** plus database-level cross-tenant rejection; authorization matrix (anonymous, no role, viewer, admin) for every endpoint; not found; **concurrent modification** (sequential and parallel, six rounds); **audit atomicity** (forced audit failure leaves no person, employment, assignment, audit or receipt row, for create and edit); append-only audit; **idempotent create retry**, key reuse with a different request, failed request does not consume the key, per-principal and per-tenant scope, expiry, concurrent same-key requests, concurrent same-email race; real OIDC login flow with an in-process identity provider; log canary (personal values never appear in logs while operation/result/requestId/tenantId/userId do); health probes; seed data. Frontend: one smoke test (shell renders, no axe violations); real frontend tests come with steps 10 to 12.

### Assumptions

- Java 17 is the target (installed LTS); Spring Boot 4.1.1 was chosen because it is the newest stable line on Maven Central (3.5 is out of open-source support in this timeline).
- The Keycloak realm JSON and `docker-compose.yml` are correct for Keycloak 26.7.4 and Docker; **neither has been executed** (no container runtime here). Placeholder substitution in the realm import (`${PALCHECK_OIDC_CLIENT_SECRET}`) is assumed to work and will be checked when Keycloak is run locally (plan risk R2).
- PostgreSQL server logs can contain rejected values (constraint violations echo keys); they are treated as sensitive infrastructure logs, outside the application-log guarantee.

### Open questions

Q1 to Q3 in the plan (confirm ADR-0022 provisional choices, required-on-create idempotency, placeholder employment vocabularies). New: should `Idempotency-Key` receipts expire after 24 hours or another period? Should authorization run before request-body validation (today a malformed body from a forbidden caller gets 400 before 403)?

### Risks

- Compose, the realm import and the browser end-to-end flow are unverified on this machine until Keycloak and PostgreSQL are run (next steps will try a local Keycloak distribution).
- Spring Boot 4 / Security 7 / Jackson 3 are new; tests pass but production behavior outside tested paths is unproven.
- Row-level security is deferred; isolation rests on application checks, composite foreign keys and tests.
- Directory search uses substring matching without an index; fine for demo scale, needs review before large tenants.

### Suggested next step

Steps 10 to 12: build the React application (sign-in, People list, record detail, create, edit sections, change history, conflict and error states) against this API with keyboard-accessible, accessible-by-default components, frontend tests, then run the whole stack locally (PostgreSQL, Keycloak, backend, frontend) and add the browser end-to-end flow.

## 2026-09-19 19:25 — CLAUDE

### Task

Phase 1 steps 10 to 13: frontend create, edit and detail; accessibility and error-handling pass; focused integration and end-to-end tests; documentation cleanup and Phase 1 review. Phase 1 is now fully implemented and awaits Daniel's review.

### Work completed

- **Frontend (`web/`)**: React + TypeScript + Vite application with sign-in and session states (unauthenticated, no workspace, error, expired), People (search, table, paging, loading, empty and error states), New person (identity, employment, assignment in one atomic save), and the person record (identity, employment, assignment and change history; each part editable on its own with its own revision, conflict banner with a reload action, forbidden and not-found states). Semantic design tokens (light, dark, high contrast, forced colors, reduced motion and transparency), one English message catalog, no component library.
- **Backend additions**: unit tests for the domain rules, patch semantics, request fingerprinting, role matrix and correlation-header handling (43 tests; the plan promised a unit layer that did not exist yet); ECS JSON logging as the default outside `dev` and `test`.
- **Tests**: frontend component suite through a fake network at the `fetch` boundary; Playwright suite against the real stack (real Chrome, Keycloak, PostgreSQL, backend), including an in-browser axe-core scan.
- **Infrastructure**: `.github/workflows/e2e.yml` (manual browser suite), `playwright.config.ts`.
- **Documentation**: new [LOCAL_DEVELOPMENT.md](LOCAL_DEVELOPMENT.md); README rewritten for the current state; plan definition-of-done table filled with evidence, sections 9, 10, 12 to 16 updated and a new section 17 (findings and known limitations); SECURITY section 17 rewritten to what is actually implemented (and corrected: no rate limiting, no request-size limit, no CI scanning, history reads not audited); ARCHITECTURE section 11 now shows the real repository; UI_SYSTEM reconciliation notice; ADR-0022 implementation outcome; implementation notes on ADR-0003, 0009, 0013; ADR index, ROADMAP and AGENTS updated.

### Decisions made

- **Session probe is exempt from the "session ended" signal.** Otherwise a `401` from the session check re-triggers the session check forever (see Tests).
- **Focus policy**: skip link first on a fresh load; focus moves to the page heading only on a real navigation (waiting for the heading if the page is still loading), never on first load, redirects, paging or search. The rule is implemented once, in the application shell.
- **Failure summary focuses itself on mount** (no ref passed through the form hook), which also removed the lint noise that hid a real signal.
- **Playwright uses the installed Chrome** (`PALCHECK_E2E_CHANNEL=chrome`) because the bundled browser the installed Playwright expects was not present and a download changes the machine outside the repository. CI installs Chromium normally.
- **Documented limitations rather than hidden**: search is not accent-insensitive; manager picker lists the first 100 people; no manual screen-reader pass; sessions in memory (plan section 17).
- No architecture change was needed; nothing contradicts an accepted ADR.

### Tests

All run on 2026-09-19 and passing: **backend 150** (58 unit and architecture including 15 ArchUnit rules; 92 integration on real PostgreSQL 17 including a real OIDC login flow), **frontend 92** (Vitest, Testing Library, axe), **browser end to end 12** (sign in through Keycloak, create, edit, history, search, stale-tab conflict, viewer without controls, cross-tenant "Record not found", no-workspace identity, sign-out really ends the session, validation summary, real-browser axe on seven screens plus the dark scheme, keyboard-only journey, reduced motion). Frontend lint (oxlint) and type check are clean; production build succeeds. Guardrail greps: no legal terms and no country-specific branching in code (`CR` appears only as seed data); every email on a reserved domain; no credentials committed.

**Defects the tests found and I fixed** (worth knowing because they were real):
1. A `401` from the session check notified the "session ended" listener, which re-ran the session check, in an endless request loop (found because a test ran out of memory).
2. After a mid-session `401` the cached session kept the app in the signed-in view.
3. Focus never reached the heading when navigating to a page that starts loading, and moving focus on first load skipped the skip link.
4. Real-browser axe: two landmarks both named "Employment", and error-summary links below the WCAG 2.2 target size.
5. My own docs overstated the system in three places (request-size limits, request ids accepted from clients, CI scanning); corrected to match the code.

### Assumptions

- Chrome results stand in for other browsers; no Firefox or Safari run.
- The local Keycloak 26.7.4 and PostgreSQL 17 runs are representative of what Compose provides.
- The dev database used for the browser run is throwaway and lives outside the repository (a scratch cluster on port 5434, Keycloak from an unpacked distribution). Daniel's own local PostgreSQL service was not touched.

### Open questions

Q1 to Q3 in the plan are still open for Daniel (confirm ADR-0022's provisional choices, required-on-create idempotency and 24-hour expiry, placeholder employment vocabularies). New: should search become accent-insensitive before real use (PostgreSQL `unaccent`)? Should Phase 1 add dependency and secret scanning to CI, or wait for the provisioning milestone?

### Risks

- `docker-compose.yml`, `ci.yml` and `e2e.yml` have never been executed on Docker or GitHub. First run may need small fixes (plan R2).
- Accessibility is verified by tools and a keyboard-driven test, not by a person using a screen reader.
- Row-level security, rate limiting and a request-size limit are not in place; acceptable for synthetic local data, required before anything real.
- The browser suite depends on the seeded development database and adds fictional people on each run.

### Suggested next step

Daniel reviews Phase 1: run the app from LOCAL_DEVELOPMENT.md, read plan sections 1 and 17, confirm or amend ADR-0022 (Q1 to Q3), and decide whether to open the next milestone. Codex could review the frontend accessibility approach and the tenant-isolation tests. Until Daniel opens the next milestone, only Phase 1 fixes and review feedback should be built.

## 2026-09-19 21:15 — CODEX

### Task

Independently review Claude's completed Phase 1, polish the rendered product, fix Phase 1 correctness/security issues, and prepare public-repository documentation. Daniel authorized this review in the supplied handoff and instructed continuation after an environment approval interruption. Phase 2 remains closed.

### Work completed

- Inspected required project documents, implementation plan/Q1–Q3, application and test code; independently ran baseline suites and inspected rendered screens before changes.
- Completed [PHASE_1_CODEX_REVIEW.md](PHASE_1_CODEX_REVIEW.md), with all 22 requested sections, evidence, tradeoffs, recommendations, and limitations. Added [PHASE_1_REVIEW_HANDOFF.md](PHASE_1_REVIEW_HANDOFF.md) with consensus and a bounded proposed next prompt.
- Added tenant-scoped accent-insensitive directory/manager search through forward migration V6; server-paged employment candidates replace the first-100-person manager limitation. Stored spelling is preserved.
- Reproduced and fixed concurrent manager changes that could jointly create a cycle; per-tenant transaction advisory locking protects graph validation. Recorded reasoning in Proposed [ADR-0023](adr/0023-manager-graph-concurrency.md).
- Preserved edit drafts and their original versions across background query refresh; explicit conflict reload replaces the draft and handles reload failure.
- Added a 64 KiB API write-body limit; hardened web/security/JDBC logging defaults; replaced JSON logout URL exposure with a native CSRF-protected form and Spring OIDC redirect.
- Prepared pinned secret/dependency security workflows and Dependabot; updated README, synthetic screenshots, security/local-dev/UI/architecture/plan/roadmap/handoff documentation. Added COPYRIGHT.md and OPEN_SOURCE_STRATEGY.md without selecting a license or publishing anything.

### Decisions made

- Retain Claude's modular monolith, JDBC, backend OIDC sessions, module boundaries, and transactional audit/idempotency; no new business module, table, or application dependency.
- PostgreSQL unaccent is bundled infrastructure with explicit extension schema/privilege requirements. Folded substring scans remain unindexed within tenant candidates; do not add an unsafe IMMUTABLE wrapper or claim measured scale.
- Use native manager selection with debounced server search/paging and retained off-page selection; no load-all fallback or custom combobox framework.
- Request size: IMPLEMENT NOW (complete). Rate limiting: NEXT SECURITY MILESTONE before public hosting. RLS remains deferred for owner confirmation and runtime-role/transaction design.
- Q1–Q3 recommendations are ACCEPT / AMEND / ACCEPT; no owner decision was silently made. Q2 amendment clarifies bounded receipt lifetime and current-resource replay, without changing the existing contract.

### UI changes

Compact neutral workspace/account rail; one real People destination; native font stack; clearer directory/search toolbar, initials and subtle separators; grouped record rows/context links; readable timeline history; restrained form width and aligned controls; preserved draft/conflict behavior; intentional loading/empty/error states; responsive header/table/forms at 320px; light/dark and accessibility behavior retained. No Apple assets, bundled proprietary fonts, decorative dashboards, or fake functionality.

Rendered sign-in, directory, detail, create/edit, history, conflict, viewer, empty/error, dark and mobile states were inspected. Deterministic conflict/error screenshots used controlled responses; actual stale-write behavior was separately tested against the backend. README screenshots contain synthetic data only.

### Tests

Independent baseline: backend 149/150 (58 unit/architecture, 91/92 integration), frontend 92/92, Chrome E2E 12/12; typecheck/lint/build passed. LoggingCanaryIT failed under inherited DEBUG activation and was fixed through configuration without weakening the test. The new manager-cycle regression reproduced the race before the lock fix.

Final: **155/155 backend** (60 unit/architecture, 95 integration; real PostgreSQL 17 and test OIDC provider), **94/94 frontend** across 8 files, **15/15 Chrome E2E** against real local frontend/backend/Keycloak/PostgreSQL. Typecheck and lint clean; production build passed. E2E includes axe light/dark, keyboard, reduced motion, 320px reflow, accent search, manager discovery, real stale-tab conflict and full logout. New integration/unit/component tests cover graph concurrency, search/manager bounds/isolation, known/unknown-length bodies, logout CSRF, retained selection, and draft/version preservation.

Vitest workers are limited to two to avoid resource contention; assertions/timeouts were not weakened. A Windows clean-build log-file lock was resolved by stopping the review's running backend, then clean verification passed. The reviewed backend was restarted for final E2E. Docker is unavailable and no GitHub workflow was run.

### Security review

Local npm audit reported zero vulnerabilities. A checksum-verified Gitleaks 8.30.1 scan of publication-candidate source found no secrets. There is no `.git` directory, so historical commits could not be inspected. Only synthetic fixtures and intentional local/test placeholder credentials were identified; no real employee/employer data or confidential material was found. Prepared security workflows and hosted settings are distinct: no remote settings were enabled. A full transitive Java advisory scan remains outstanding.

Logout's ID-token hint is no longer returned to JavaScript in JSON; it still travels in the standard browser redirect to the provider. The audit remains transactional and append-only under normal runtime access, not tamper-proof against a database owner. No claim of production readiness or comprehensive vulnerability absence is made.

### Assumptions

Local PostgreSQL 17 and Keycloak runs are representative of the intended Compose stack, pending actual container verification. The review uses only synthetic local data. Gitleaks Action configuration assumes personal-repository ownership. Screenshot records and E2E additions remain in the scratch development database; Daniel's separate PostgreSQL service was not changed.

### Open questions

Daniel's Q1–Q3 decisions, ADR-0023 acceptance, publication timing/license/contribution policy, and whether to authorize a public hosted demo. Human screen-reader validation, another browser, Compose/GitHub runs, Java dependency coverage and hosted security settings need separate evidence.

### Risks

In-memory sessions, no RLS or rate limiting, history reads not audited, unmeasured folded-search/deep-page/graph-lock performance, and production privacy/operations work remain. Extension installation needs the documented package/schema/privileges. Idempotency expires after 24 hours and does not supply permanent business-effect deduplication. No executable legal rules or Phase 2 features exist.

### Suggested next step

Daniel reviews the app and Q1–Q3. Authorize only a bounded Phase 1 acceptance/publication-validation pass: execute Compose and GitHub checks where available, verify Java/hosted security coverage, and perform another-browser plus human screen-reader checks. Fix concrete failures; publish/license/deploy only with explicit authorization. The next prompt is prepared in PHASE_1_REVIEW_HANDOFF.md. Stop here; do not start Phase 2.

## 2026-09-20 16:41 — CODEX

### Task

Prepare PalCheck for the GitHub push explicitly requested by Daniel.

### Work completed

Initialized a local Git repository on main. Confirmed Git author configuration and available GitHub accounts through Git Credential Manager. Existing source/build exclusions remain in place. The prior Phase 1 review and its limitations remain recorded; this task changes repository delivery, not product scope.

### Decisions made

Use the configured Git author identity and preserve all project history/documents in the initial snapshot. Do not select a license or infer a destination/visibility. Daniel's repository URL or new-repository preference is pending.

### Tests

No application code changed. Prior results remain 155 backend, 94 frontend, and 15 Chrome E2E tests passing. Publication-candidate secret scanning and staged-file checks are performed before the initial commit.

### Assumptions

Daniel's request authorizes committing and pushing the reviewed project to his specified GitHub destination.

### Open questions

Destination repository and, if creating one, visibility and owning account.

### Risks

Remote authentication and GitHub workflow execution are not yet verified. Existing Phase 1 security and accessibility limitations remain unchanged.

### Suggested next step

Push the prepared commit after the destination is supplied; verify the remote branch and report the commit/link.

Publication preparation also records portable line endings in .gitattributes and marks backend/mvnw executable in Git for Linux CI. A trailing blank line flagged by the staged whitespace check was removed; no test behavior changed.

## 2026-09-20 16:50 — CODEX

### Task

Create and push PalCheck to a public GitHub repository under d4guilar, explicitly selected by Daniel after the public portfolio recommendation.

### Work completed

Verified the saved credential belongs to d4guilar. Created the public repository https://github.com/d4guilar/palcheck, configured origin, and pushed main with upstream tracking. GitHub's remote main matched initial commit f25a897d3f534aa8d54e582271795e1e2f2895fd. Updated README and the open-source strategy to reflect actual public visibility.

### Decisions made

Repository visibility is Public and owner is d4guilar, per Daniel's authorization. No license was selected; copyright and Phase 1 scope remain unchanged. Existing history was preserved; no force push was used.

### Tests

The initial publication candidate had 273 staged files, passed Gitleaks with no findings, and excluded environment secrets, generated output, dependencies, and local browser artifacts. The staged whitespace check passed and the Maven wrapper is executable in Git. Remote ref verification confirmed the first push. No application code changed; application suites were not rerun for this publication task. GitHub Actions results are separate from prior local test evidence.

### Assumptions

The chosen account and public visibility are intended for Daniel's recruiter-facing portfolio.

### Open questions

License/contribution policy and Phase 1 acceptance decisions remain open. Hosting a live application is a separate decision.

### Risks

Public source visibility does not establish production readiness. Previously recorded security/accessibility limitations remain; hosted workflow outcomes need inspection.

### Suggested next step

Review GitHub Actions results and use the repository link on the portfolio. Continue only the separately authorized Phase 1 acceptance/publication-validation scope; do not infer authorization for Phase 2 or a public application deployment.

## 2026-09-21 08:49 — CODEX

### Task

Record Daniel's explicit acceptance of Q1, using its exact wording in PHASE_1_IMPLEMENTATION_PLAN.md, and verify that the existing implementation matches. No Phase 2 work.

### Work completed

- Q1 is ACCEPTED on 2026-09-21: **Spring JDBC over JPA, backend OIDC session over browser-held tokens, Maven with packages and ArchUnit instead of Spring Modulith, embedded PostgreSQL for tests while no container runtime is available, row-level security deferred.**
- Updated the Phase 1 plan, review and handoff, ARCHITECTURE.md, SECURITY.md, and the ADR index. Added a dated resolution notice to the historical CODEX_ARCHITECTURE_REVIEW.md so its original recommendations cannot override the accepted choices.
- ADR-0022 now records acceptance of the Q1 scope of decisions 1, 3, 7, 9 and 10, with implementation evidence. ADR-0020 decisions 4 and 5 and its decision 6 RLS-deferral portion reflect that acceptance. Related ADR-0003 (RLS), ADR-0009 (browser session) and ADR-0013 (authentication) have explicit scoped acceptance notes and current wording.
- Inspected Maven dependencies/wrapper, package boundaries and ArchitectureTest, JdbcClient repositories, SecurityConfig and same-origin frontend proxy/session behavior, TestPostgres and integration tests, and migrations/tenant-qualified constraints. Implementation already matches all five choices; no application, configuration, migration, test or source-comment changes were required.

### Decisions made

Acceptance is scoped to Q1. ADR-0022 contains other choices, so it is Accepted in part with each Q1 choice explicitly ACCEPTED; this does not accept Q2's idempotency policy or Q3's vocabulary. The embedded-PostgreSQL condition and RLS reconsideration triggers are retained.

The inventory also found provisional references outside Q1: ADR-0018 decisions 5–7 and their DOMAIN_MODEL/plan/migration comments, ADR-0020's other focused-precondition and local-environment details, and ADR-0023. Those were left unchanged. Existing neutral references in README, LOCAL_DEVELOPMENT, ROADMAP, pom.xml, PalCheckApplication, SecurityConfig, TestPostgres and vite.config.ts already match Q1 and needed no edits. Earlier WORKLOG entries remain append-only history; this entry supersedes their Q1-open status.

### Tests

Ran existing focused verification: `backend/mvnw.cmd -B -ntp -Dtest=ArchitectureTest -Dit.test=OidcLoginFlowIT,TenantIsolationIT,WorkforceSchemaIT verify`. **32 tests passed:** 15 architecture checks, 3 OIDC integration tests, 4 tenant-isolation integration tests and 10 schema integration tests against real embedded PostgreSQL. Zero failures, errors or skips; Maven BUILD SUCCESS. No tests were added or changed because observable behavior did not change. Full frontend/E2E suites were not rerun for this documentation-only resolution.

Documentation verification includes local-link checks, a search for stale Q1 approval wording, Git whitespace checks, and confirmation that the diff contains only documentation and this appended entry.

### Assumptions

Daniel's Q1 acceptance applies to the five choices listed in that question; it does not automatically accept every decision mentioned elsewhere in ADR-0022 or complete Phase 1.

### Open questions

No residual ambiguity in Q1's five choices. Q2 and Q3, unrelated provisional ADR decisions, overall Phase 1 acceptance, licensing and production identity-provider/deployment choices retain their existing status.

### Risks

Accepted RLS deferral does not mean RLS is implemented or permanently rejected. Embedded PostgreSQL remains conditional on the container-runtime constraint. This status change does not close existing production-readiness or broader verification gaps.

### Suggested next step

Daniel resolves Q2 or Q3 separately. Preserve the accepted Q1 baseline; do not begin Phase 2.

## 2026-09-21 09:11 — CODEX

### Task

Record Daniel's Q2 ACCEPTED WITH AMENDMENT decision and enforce/document the 24-hour GUARANTEED RETRY WINDOW without implying that receipt expiry makes a business action safe to repeat.

### Work completed

- Read the plan's exact Q2 wording, review/handoff, ADR-0019/0022, API documentation, receipt service/schema/purge, all write callers/fingerprints and existing idempotency tests. Preserved uncommitted Q1 edits and append-only worklog history.
- Updated Q2 status in the plan/API contract, review, handoff, ADR-0022 and ADR index; added the Phase 1 boundary note to ADR-0019. Updated README, architecture/security references and local-development configuration guidance. Q3 stays open; no other owner decision was inferred.
- Retained scope `(tenant, authenticated principal, operation, key)`, canonical payload fingerprints, required-create/optional-patch headers, deterministic 422 payload conflicts, transactional receipts/audit, concurrent reservation behavior and current-resource replay with current authorization.
- Renamed the Java duration to guaranteedRetryWindow and removed comments suggesting expiry means safe reuse. Kept the compatible `palcheck.idempotency.ttl` setting, now explicit as PT24H in application.yml, and unchanged database retention column/index names. Existing migrations are untouched.

### Decisions made

During the guaranteed window, an authorized equivalent same-scoped-key retry reconstructs the original logical result without another business/audit mutation; a materially different request conflicts. Replays do not refresh the deadline. Cleanup uses strict comparison, retaining protection at the exact deadline.

Two small corrections were necessary: reject configured durations below 24 hours, and set the original receipt's deadline no earlier than completion plus the configured window, so time executing the original write does not shorten that guarantee. The 24-hour duration is a product choice balancing predictable interruption recovery with bounded receipt storage; longer durations remain configurable. Changing settings does not rewrite stored deadlines. A future shorter published guarantee requires an explicit contract change.

After the window, receipt availability is not guaranteed. Purge/lazy cleanup can remove the receipt while the original resource and audit remain. A later request undergoes normal rules and can be rejected or create another resource; callers must reconcile outcomes and must not assume equivalent or safe repetition. This does not implement permanent business-effect identity or any distributed platform/read-operation idempotency.

### Tests

New IdempotencyServiceTest: three cases reject negative, zero and sub-24-hour configuration; one accepts an extended 48-hour window. IdempotencyIT adds five tests: pre-boundary/exact-boundary replay and mismatch with purge/current representation/no extension; delayed original completion; post-window replay of an identical body creating another resource after legitimate data changes; tenant/operation scope; concurrent different-payload collision. Updated the prior expiry test's name and clock control to describe loss of receipt availability rather than safe key reuse. Existing same-key concurrency, principal scope, canonicalization, rollback, edit and audit tests remain.

Before correction, the three minimum-window cases failed and the real-PostgreSQL delayed-completion regression returned Fresh instead of Replay. A mocked-clock UTC-conversion omission was fixed before the valid regression run. A Windows lock on the running application's executable JAR prevented repackaging; verification was rerun with only that packaging step skipped, without stopping unrelated services.

Final command: `backend/mvnw.cmd -B -ntp -Dspring-boot.repackage.skip=true verify`. **164 tests passed (64 unit/architecture + 100 integration), zero failures/errors/skips; BUILD SUCCESS.** No frontend code changed; frontend/E2E suites were not rerun. Documentation links, whitespace and stale-Q2 wording are checked separately. No dependencies or tables were added.

### Assumptions

The existing authenticated-principal namespace remains part of the current implementation contract; Q2 does not authorize cross-principal deduplication. Successful original write completion is the receipt-completion point inside its business transaction; failed/rolled-back transactions leave no accepted effect or receipt. Retention uses the server's configured clock.

### Open questions

Q3 and other unrelated acceptance/release questions remain open. The guaranteed retry window is resolved; its future product duration can be changed deliberately. No permanent post-window safety inference is supported.

### Risks

After receipt removal an identical payload can produce another resource if present-day business constraints permit it; the tests demonstrate why callers must reconcile. Previously issued receipts keep their stored deadlines across configuration changes. Existing deployment/security/accessibility limitations remain separate from this change.

### Suggested next step

Daniel resolves Q3 separately. Keep Q1 and amended Q2 as the accepted baseline. No Phase 2 work, new distributed machinery or publishing action is part of this task.

## 2026-09-21 09:50 — CLAUDE

### Task

Complete Q3 after Codex partial implementation. Q3 (ACCEPTED by Daniel, 2026-09-21): the employment vocabularies `PENDING` / `ACTIVE` / `ENDED` and `FULL_TIME` / `PART_TIME` are software/domain vocabulary, not Costa Rican legal classifications. Q1, Q2, Apple UI polish and Phase 2 were not touched.

### Work completed

- Inspected the working tree first. The repository is now under Git (two commits; the `.git` directory belongs to the Codex sandbox account, so Git reported "dubious ownership"; I used a per-command `-c safe.directory` for read-only status and diff and changed no Git configuration). Codex's uncommitted work covered Q1, Q2 and most of Q3; Codex wrote no Q3 worklog entry before stopping.
- Verified Codex's Q3 edits are consistent with each other and with Daniel's decision, then closed the remaining gaps with small corrections (below).
- Confirmed nothing else was needed: no OpenAPI or API description files exist (the plan's API section is the API documentation and already carries the Q3 note); the UI shows one short contextual hint on the Employment type field only and no legal warnings elsewhere; no enum value was renamed or added; no legal behavior exists.

### Codex work preserved

All of it was retained:
- `EmploymentStatus.java` and `EmploymentType.java` Javadocs (substance kept; two phrases corrected, see below).
- `docs/DOMAIN_MODEL.md`: new section 4.1 "Accepted Phase 1 employment vocabulary", OQ-06 reframed as future mappings, and the invariant reworded so an `ENDED` employment needing an end date is a software record-integrity rule, not a legal requirement.
- `docs/localization/`: boundary note in `COUNTRY_POLICY_ARCHITECTURE.md`; the "Tipos de contrato" row in `costa-rica/GLOSSARY.md`; a note in `costa-rica/README.md`; a boundary note and the reworded WC-1 and WC-4 "Unblocks" entries in `costa-rica/RESEARCH_BACKLOG.md`.
- Plan: Q3 status in section 4, the employment-vocabulary paragraph in section 8, and Q3 in section 16. ADR-0022: status line, the Q3 acceptance record and owner-resolution line; ADR index row. `PHASE_1_CODEX_REVIEW.md` section 21 and `PHASE_1_REVIEW_HANDOFF.md` owner-decision status.
- Frontend: the reworded `field.employmentType.hint` in `en.ts` ("Software classification used by PalCheck. Local legal interpretation may vary by country."), the type comments in `web/src/api/types.ts`, and the two accessible-description assertions in `NewPersonPage.test.tsx` and `PersonDetailPage.test.tsx`.
- Codex's completed but uncommitted Q1 and Q2 changes and their worklog entries were present in the same tree and were left exactly as found.

### Decisions made

- **"Placeholders" means "not legally authoritative", not "temporary".** Daniel's accepted label is "software/domain placeholders", but part of the wording (Javadocs saying "Phase 1 ... placeholder", the handoff saying "placeholders for Phase 1") read as if the values would be replaced. Kept the label (it is Daniel's) and defined it once, in `DOMAIN_MODEL.md` section 4.1: these are core PalCheck software vocabulary, deliberately legally neutral, may remain valid after Costa Rican localization exists (`FULL_TIME` can stay a PalCheck concept while a verified country policy later attaches local meaning or a mapping), and localized legal concepts live in the Country Policy layer, not in these enums.
- **Corrections to Codex work** (each the smallest change that fixes a concrete problem):
  1. Both enum Javadocs: replaced "Phase 1 software/domain placeholder" with "Core PalCheck software/domain ... vocabulary ... Deliberately legally neutral"; the rest of Codex's text kept, still concise.
  2. `DOMAIN_MODEL.md` section 4.1: added the definition above and changed "automatically replace" to "necessarily replace", matching the decision that research may refine mappings without necessarily replacing core values.
  3. Plan definition-of-done row 19 and the section 17 limitations bullet, `PHASE_1_REVIEW_HANDOFF.md`, and `LOCAL_DEVELOPMENT.md`: reworded from "software placeholders" to "core software/domain vocabulary, deliberately legally neutral (Q3 accepted)".
- **Deliberately not edited:** `V3__workforce_core.sql` (its comment calls the values "software placeholders"; changing any migration file, even a comment, changes its Flyway checksum and would fail validation on existing databases, and the comment is not misleading); `EmploymentDataTest` Javadoc (already neutral); historical or conceptual mentions of "worker type" in `CODEX_HANDOFF.md`, `INTEGRATION_ARCHITECTURE.md`, `MODULES.md` and DOMAIN_MODEL section 3 (Phase 0 conceptual model or a superseded handoff, not the Phase 1 enums; section 4.1 states the boundary).
- No values renamed, none added. `PeopleCreateAndReadIT` already asserts an unlisted type (`CONTRACTOR`) is rejected as invalid.

### Tests

- Backend, focused on the employment vocabulary plus architecture rules: `backend/mvnw.cmd -B -ntp -Dspring-boot.repackage.skip=true -Dtest=EmploymentDataTest,ArchitectureTest -Dit.test=PeopleCreateAndReadIT,PeopleEditIT,WorkforceSchemaIT,WorkforceRepositoryIT,DemoDataSeederIT,AuditIT verify`: **75 tests passed (23 unit and architecture, 52 integration on real PostgreSQL), zero failures, errors or skips; BUILD SUCCESS.** (The repackage step was skipped only because a running application can lock the executable JAR, as Codex found earlier.)
- Frontend (its Q3 hint text and tests were previously unverified): `npm run typecheck`, `npm run lint` clean; **94 of 94 component tests passed**, including the two assertions that the employment-type hint is the field's accessible description.
- Documentation: link and anchor check found no broken links in project documents (the section 4.1 anchor resolves); a search for temporary-sounding phrasing ("real taxonomy", "will be replaced", "until we discover") found none in the Q3 documents or code.
- Not run: the full backend suite, the browser E2E suite and the production build. Changes to code are Javadoc text only, and the one UI text change is covered by the component tests above.

### Assumptions

- The label "software/domain placeholders" in the plan, ADR-0022, the review and the localization notes is Daniel's accepted wording and is intentionally kept; the definition in DOMAIN_MODEL section 4.1 governs how it is read.
- The uncommitted tree (Q1, Q2 and Q3 work) is intended to be committed by Daniel or authorized separately; I created no commit.

### Open questions

None on Q3. Overall Phase 1 acceptance, licensing and hosting decisions remain separate and untouched. If Daniel prefers the label itself changed from "placeholders" to "core software vocabulary", that is a wording-only change across the files listed under "Codex work preserved".

### Risks

- The word "placeholders" remains in the accepted label and in `V3__workforce_core.sql`; a reader who skips DOMAIN_MODEL section 4.1 could still read it as temporary.
- Hint text says local legal interpretation "may vary by country" but no country policy exists; that is accurate for now and states no legal fact.

### Suggested next step

Daniel reviews the Q3 wording and decides whether to commit the pending Q1, Q2 and Q3 changes. Do not begin Phase 2.

## 2026-09-22 12:16 — CODEX

### Task

Phase 1 Apple-inspired UI polish.

### Screens reviewed

Inspected the running application before and after changes: sign-in, People directory/search/paging, person identity/employment/assignment, creation and all three inline editors, change history and disclosure, stale-edit conflict, loading, empty workspace, empty search, directory error and record error. Reviewed light/dark desktop and narrow layouts using synthetic data. The final capture pass scanned 16 states; browser tests also exercise 320px reflow, viewer restrictions, tenant denial and recovery. Staged visual error/conflict/empty/loading captures use browser-local responses; real behavior remains covered by the E2E suite.

### Work completed

- Refined the existing shell, typography, neutral surfaces, button hierarchy, search placement, compact status indicators and table spacing. Narrow directory rows retain role and team; forms and record labels stack naturally.
- Introduced reusable SearchField and RecordSection components and a small useEditorFocus hook. Aligned identity/employment/history sections, removed duplicated headings/status, moved revision metadata into native disclosures, and simplified history field changes and record-load recovery copy. Existing draft/conflict, permission, search, paging, history and Q3 hint behavior is preserved.
- Added shared tokens for content widths, selected/elevated surfaces, typography, control targets and layout dimensions. Dark mode now uses graphite layers. Retained native controls and system fonts with no new dependency.
- Updated README and its four synthetic screenshots, docs/UI_SYSTEM.md, and the new docs/UI_PHASE_1_APPLE_POLISH.md report. Recorded Daniel's explicit acceptance of the functional Phase 1 foundation in AGENTS.md, the plan, review and handoff; the gate still permits only Phase 1 review work. Preserved all earlier Q1/Q2/Q3 edits and Claude's completed Q3 entry. No backend behavior, domain semantics, migration or Phase 2 feature changed in this task.

### Design decisions

Use original, restrained enterprise styling inspired by general Apple/HIG principles, with no Apple assets, proprietary fonts or copied OS chrome. Prefer typography, whitespace and separators to repeated cards. Keep native semantic controls. Revision details are one disclosure away; narrow rows prioritize name, role, team and status, with email/legal entity on the full record. These density choices are documented for Daniel's visual review.

### Accessibility

Retained explicit labels, accessible search descriptions, semantic table structure, inline errors, notices and visible focus. Opening each editor focuses its first control; closing restores its Edit button. Added keyboard disclosure/focus and narrow dark/forced-color journeys, plus 10 contrast checks for selected/elevated surfaces and control boundaries. Axe reported zero violations in the final 16-state capture pass under WCAG A/AA rules through 2.2. Human screen-reader testing and browsers other than Chrome on Windows remain unverified.

### Tests

Final results from web/: `npm run typecheck` and `npm run lint` passed; `npm test -- --maxWorkers=1 --reporter=verbose` passed **104 tests in 8 files**; `npm run build` passed TypeScript and the Vite production build. With `$env:PALCHECK_E2E_CHANNEL = 'chrome'`, `npm run e2e -- --output=.playwright/polish/final-e2e` passed **all 17 tests (3.5 minutes)**, including every existing critical journey. New browser coverage accounts for two tests; existing assertions were updated for toolbar order, metadata disclosure and record-error heading structure.

The default browser output cleanup encountered Windows locks on live service logs, so final browser output uses a separate ignored directory. An earlier concurrent component run stalled and reported a ManagerPicker timing failure; the final full serial run passed. Final screenshot capture resets scroll position to avoid a sticky-sidebar artifact in full-page images. These required no backend changes. `backend/mvnw.cmd -B -ntp -DskipTests package` succeeded to prepare the local executable; backend tests were not rerun because this UI task changed no backend behavior. Git whitespace checks passed.

### Assumptions

Daniel's supplied UI brief explicitly accepts the functional Phase 1 foundation and authorizes this presentation pass. It does not accept additional ADR choices or open Phase 2. Existing synthetic local development services are used for verification.

### Open questions

No implementation blocker remains. Daniel can review record density, metadata disclosure and narrow directory priorities in the completed UI. Hosting, licensing and publication remain separate decisions.

### Remaining visual issues

Native selects/date inputs vary by platform; long history or multiple employments still produce a long page. Search help is available as an accessible description rather than a persistent caption. These are documented tradeoffs, not new functionality requests.

### Risks

Automated checks do not establish full accessibility conformance. Human screen-reader and cross-browser review remain necessary before broader release claims. The working tree contains earlier Q1/Q2/Q3 work as well as this pass; no commit or push was made during this UI task.

### Suggested next step

Daniel reviews the running UI and updated screenshots, then chooses a separate bounded release-validation or publication task. Preserve the accepted Phase 1 foundation and Q1/Q2/Q3 decisions; do not begin Phase 2.

## 2026-09-22 13:33 — CLAUDE

### Task

Phase 2 opening: read the current implementation (post Q1-Q3 and UI polish, all still uncommitted), reconcile it against Daniel's Phase 2 message, and produce docs/PHASE_2_IMPLEMENTATION_PLAN.md plus its ADRs before writing any code.

### Work completed

- Re-inspected the working tree (`git -c safe.directory=... status/diff`; the `.git` directory is still owned by a different Windows account than mine, so I used a per-command exception and touched no Git config). The tree now carries Codex's Q1/Q2/Q3 resolutions and a full Apple-inspired UI polish pass (worklog entries through 2026-09-22 12:16), all still uncommitted; I changed none of it.
- Read AGENTS.md, README, PHASE_1_IMPLEMENTATION_PLAN, PHASE_1_CODEX_REVIEW, PHASE_1_REVIEW_HANDOFF, ARCHITECTURE (section 11), MODULES (section 2a and 3.4), DOMAIN_MODEL, INTEGRATION_ARCHITECTURE (sections 1-9), DATA_OWNERSHIP_AND_LINEAGE (in full), SECURITY (section 5), ENTERPRISE_SYSTEMS (sections 1-3), and the current backend code: `access.api` (Actor, Authorization, Permission, TenantProvisioning), `audit.api` (AuditActor, AuditRecord, AuditRecorder), `shared` (ErrorCode, ApplicationException and its subtypes), `platform.web` (ApiExceptionHandler, ProblemWriter), and every `workforce` file a new module would need to reuse or extend (RegisterWorkerService, EditPersonService, EditEmploymentService, WorkforceAuditor, the domain records, the JDBC repositories, PeopleController/EmploymentController/Dtos, ArchitectureTest, the V3/V6 migrations).
- Confirmed `docs/DATA_LINEAGE.md` named in Daniel's message does not exist; the Phase 0 document with that content was renamed `DATA_OWNERSHIP_AND_LINEAGE.md` and I read that instead.
- Wrote [docs/PHASE_2_IMPLEMENTATION_PLAN.md](../PHASE_2_IMPLEMENTATION_PLAN.md) (20-item definition of done, scope, a reconciliation of this Phase 2 against the fuller Phase 0 integration design, architecture, persistence, API, authorization, audit, test strategy, step table, risks/open questions Q1-Q3), [ADR-0024](adr/0024-hris-adapter-boundary-phase2.md) (provider-neutral adapter boundary, the new `integrations` module, the new `workforce.api` write port) and [ADR-0025](adr/0025-ownership-lineage-and-sync-model-phase2.md) (ownership granularity, lineage storage, sync transaction model, two-pass manager resolution).
- Updated AGENTS.md section 2 (phase gate now Phase 2, Phase 1 non-goals carried over, Phase 2's narrow exception to the Workday ban stated explicitly), the ADR index (0024/0025 rows, "not yet decided" list), ROADMAP.md (new Phase 2 section, reconciliation notice), and one-line reconciliation notices in INTEGRATION_ARCHITECTURE.md and DATA_OWNERSHIP_AND_LINEAGE.md pointing at the new plan and ADRs rather than rewriting either document.

### Decisions made

Recorded in the ADRs and the plan in full; summarized here:

- **New module `integrations`**, depending only on `shared`, `platform`, `access.api`, `audit.api` and a **new public port on `workforce`**, `workforce.api` (`WorkforceImportPort`) — the only way `integrations` writes workforce data. `workforce` still never depends on `integrations`. No `messaging`/outbox is created; Phase 2 has no asynchronous effect to publish.
- **Ownership granularity: Employment and Assignment, not per field, not Person, not tenant-configurable.** A deliberate, documented narrowing of `DATA_OWNERSHIP_AND_LINEAGE.md`'s fuller design (its open question LQ-02 stays open), chosen because Daniel's brief explicitly asked for "the least complex model that still demonstrates realistic behavior" and gave `Assignment.title` as the running example.
- **Enforcement** lives in `workforce.application` (a human `PATCH` on an owned Employment/Assignment is rejected before any field is touched), not the UI; a new error code `EXTERNALLY_OWNED` (409) is added, distinct from optimistic-concurrency `CONFLICT`.
- **Identity linking reuses the ownership columns** (a partial unique index on `(tenant_id, owned_by_system, external_worker_id)` where set) instead of a separate link table; one external worker maps to exactly one Employment.
- **Legal entity mapping is a small code-defined map** (external company code -> legal entity name), resolved by `workforce.api` against existing legal entities by name; no new column on `workforce.legal_entity`.
- **Sync transaction model**: one worker = one `workforce` transaction; the sync-item outcome is a separate transaction written afterward; a run is never one transaction. **Two passes** over a run's workers: pass 1 writes everything except the manager link, pass 2 resolves manager links now that every worker in the run exists.
- **Audit**: the one change `WorkforceAuditor` needs is to accept the actor's kind instead of always assuming `USER`; a sync-caused change is recorded as the existing `AuditActor.system(name)`, no new `AuditActor.Type`.
- **Field lineage** is separate, read-only metadata owned by `integrations` (one row per governed field naming its source), never consulted for write authorization.
- This session's scope: plan + ADRs + a tested backend vertical slice (steps 3-9 of the plan). Frontend, lineage disclosure and E2E (steps 10-12) are the explicitly stated next step, matching how Phase 1's own backend-then-frontend split worked.

### Tests

None yet for Phase 2 — this entry covers planning only. Implementation and its tests follow in the next worklog entry, in the same session.

### UI changes

None in this entry.

### Assumptions

- Daniel's Phase 2 message is treated as his own authorization to open Phase 2 and to make the bounded implementation decisions above, the same way his Phase 1 opening message was treated, subject to the same kind of after-the-fact confirmation (Q1-Q3) Phase 1 used.
- The large uncommitted tree from Codex's Q1/Q2/Q3 and UI-polish work is left exactly as found; Phase 2 code is written on top of it, not around it.

### Open questions

Q1-Q3 recorded in the plan section 14 (ownership granularity confirmation, permission bundling for the four new permissions, whether the Systems/Sync UI belongs inside `integrations` rather than the deferred `operations` module). Phase 1's own Q1-Q3 and the acceptance/publication-validation task from the review handoff remain separately open and untouched.

### Risks

Carried over from the plan section 14: the Employment/Assignment-only ownership model, single-employment-per-worker assumption, and synchronous (non-background) sync are documented simplifications that would need revisiting for a real second provider or real data volume.

### Suggested next step

Implement plan steps 3-9 (module skeleton, normalized contract, persistence, mock provider, import/reconciliation, ownership enforcement, audit+lineage) with backend integration tests on real PostgreSQL, then stop and report — matching this same session's continuation, not a separate one.

## 2026-09-22 14:15 — CLAUDE

### Task

Phase 2 implementation steps 3–9: the `integrations` module, the `workforce.api` write port, persistence, the mock Workday provider, the two-pass import/reconciliation flow, ownership enforcement, and audit/lineage integration, with backend tests — the vertical slice this session commits to (docs/PHASE_2_IMPLEMENTATION_PLAN.md section 16).

### Work completed

- **New module `integrations`** (`domain`, `application`, `application.provider`, `infrastructure`, `web`; no `api` package yet — nothing outside it needs one). **New public port `workforce.api.WorkforceImportPort`**, implemented by a new `workforce.application.ImportWorkerService`, the only code allowed to write an externally owned Person, Employment or Assignment.
- **Mock Workday adapter** (`integrations.application.provider`): `MockWorkdayProvider` (6 deterministic synthetic workers, provider-shaped vocabulary, package-private), `WorkdayMapper` (translates to PalCheck's own vocabulary, reports unmapped codes), `WorkdayAdapter` (the package's only public class; pulls and maps in one step so provider DTOs never leave the package). Three `WorkdayPullOptions` toggles reproduce a changed value, a source-side duplicate, and a temporary provider outage on request.
- **Normalized contract**: `integrations.domain.NormalizedWorker` (small, integration-owned; PalCheck vocabulary strings, not `workforce.domain` enums, so it never forces `workforce` types across the module boundary) and `SyncFailureReason` (the five controlled failure classes).
- **Persistence** (`V7__integrations_and_ownership.sql`): ownership columns on `workforce.employment` (`owned_by_system`, `external_worker_id`, `last_synced_at`) and `workforce.assignment` (`owned_by_system`, `last_synced_at`), with a partial unique index doubling as the external-identity lookup; `integrations.external_system`, `.sync_run`, `.sync_item`, `.field_lineage`. JDBC repositories for all of it.
- **`SyncRunService`**: orchestrates one run — pull, dedupe within the pull, pass 1 (create/update/unchanged per worker, one `workforce` transaction each), pass 2 (manager links, now that every worker in the run exists), then one final `sync_run` write with aggregate counts. `ExternalSystemService` lazily provisions the one Phase 2 system row per tenant.
- **Ownership enforcement**: `EditEmploymentService.updateEmployment`/`updateAssignment` reject a human edit of an owned record with a new `ExternallyOwnedException` (`ErrorCode.EXTERNALLY_OWNED`, HTTP 409, distinct from optimistic-concurrency `CONFLICT`, carrying the owning system's code).
- **Audit and lineage**: `WorkforceAuditor` gained `createdBySystem`/`updatedBySystem` (the one change needed — everything else reuses the existing `AuditRecord`/`AuditActor.system(...)` machinery from Phase 1). `SyncRunService` writes `field_lineage` rows for the six fields a worker actually populates (person name/email, employment status/type, assignment title).
- **API**: `GET /api/systems`, `GET /api/systems/{code}`, `POST /api/systems/{code}/sync`, `GET /api/systems/{code}/syncs`, `GET /api/syncs/{runId}`, `GET /api/people/{personId}/lineage`. Existing `Employment`/`Assignment` response DTOs gained `ownedBySystem`.
- **Authorization**: four new permissions (`VIEW_SYSTEMS`, `VIEW_SYNCS`, `RUN_SYNC`, `VIEW_LINEAGE`), bundled into `HR_ADMIN` only (`Role.HR_ADMIN` already used `EnumSet.allOf`, so no change there; `HR_VIEWER` unchanged).
- **ArchUnit**: 8 new rules (`workforceNeverDependsOnIntegrations`, `nothingOutsideIntegrationsUsesItsInternals`, `integrationsDependsOnlyOnItsAllowedNeighbours`, `providerVocabularyNeverLeavesTheAdapterPackage`, `integrationsDomainIsFrameworkFree`, `integrationsApplicationDoesNotKnowInfrastructureOrWeb`, `integrationsControllersNeverTouchRepositoriesOrSql`, `everyIntegrationsRepositoryMethodNamesATenant`), plus `integrations` added to the existing shared/platform/audit exclusion lists.
- **Documentation**: docs/PHASE_2_IMPLEMENTATION_PLAN.md's definition-of-done table (rows 1–15, 18 marked done with evidence; 2, 17, 19, 20 marked partial — backend done, frontend/E2E/doc-cleanup pending) and implementation-steps table (3–9 marked done).

### Decisions made

Recorded in ADR-0024/0025 already; two things were discovered only while implementing, both fixed and worth recording:

- **The manager link must never be diffed by the normal update path.** `ImportWorkerService`'s import request has no manager field (manager resolution is pass 2's job, via a separate `linkManager` call). The first version of `update()` built its comparison `AssignmentData` with `managerEmploymentId=null` unconditionally, so every re-sync of a worker who *had* a manager saw a false "changed" (null vs. the real value) and would have overwritten pass 2's link. Fixed by comparing against — and writing back — the assignment's *current* manager, untouched, whenever `importWorker` runs; only `linkManager` ever changes that field.
- **Test isolation**: an early version of a cross-tenant test helper counted people by email without scoping to the tenant; since every test reuses the same fixed synthetic email addresses (deliberately, for realism), counts leaked across tenants created by earlier tests in the same class. Fixed by scoping every such query to the tenant under test.
- **Audit actor display name**: `workforce` only ever receives the stable system *code* (`workday-simulation`), never a display name (`integrations` owns that, and `workforce` must not depend on `integrations`). `ImportWorkerService` translates the one Phase 2 code to "Workday Simulation" with one hardcoded, documented mapping, rather than leaving the raw code in the audit trail a human reads.
- Nothing here changes the accepted architecture; no deviation needed an ADR beyond 0024/0025.

### Tests

All run on 2026-09-22 and passing: **187 backend tests** (72 unit and architecture, including the 8 new ArchUnit rules; 115 integration on real PostgreSQL, including the 15 new `SyncIT` tests) — `mvn verify` (repackage skipped; a running instance held the executable jar, same as Codex's Q2 note). `SyncIT` covers: first import (partial success — 4 created, 3 failed across pass 1 and 2, in one run); a synthetic worker findable in the directory; ownership stamped on Employment/Assignment; a human `PATCH` on an owned record rejected with `EXTERNALLY_OWNED`; idempotent re-sync (no duplicate, no audit noise, `updatedAt` unchanged); a changed source value updating only that field, audited as a `SYSTEM` actor named "Workday Simulation"; an unmapped legal entity failing cleanly with nothing created; an unresolved manager reference failing only that link, not the worker's own record; a resolved cross-worker manager link; field lineage naming the source system and field; a source-side duplicate within one pull rejected without a second person; a temporary provider failure never reaching `workforce`; cross-tenant isolation on syncs and lineage; the permission matrix (`HR_VIEWER` forbidden, `HR_ADMIN` allowed); and the same create/read/sync flow over real HTTP. Two pre-existing Phase 1 tests (`AuthenticationIT`, `AuthorizationMatrixIT`) had a hardcoded `HR_ADMIN` permission count (7); updated to 11, the correct count after adding four permissions — a expected, mechanical consequence of the new permissions, not a regression. Not run: frontend and E2E (nothing changed there yet).

### UI changes

None. Steps 10–12 (Systems/Sync screens, lineage disclosure, frontend and E2E tests) are explicitly deferred to the next piece of work.

### Assumptions

- Daniel's Phase 2 message authorizes the bounded design decisions in ADR-0024/0025 provisionally, the same way his Phase 1 opening message did for ADR-0022, subject to later confirmation (Q1–Q3 in the plan).
- The uncommitted Q1/Q2/Q3 and UI-polish tree from the prior session is untouched by this work; Phase 2 code sits on top of it. No commit was made.
- The mock provider's fixture legal entities ("Northwind Costa Rica S.A." / "Northwind Panama S.A.") and org units ("Engineering" / "Support") are Phase 2's own synthetic names, seeded by a new test-support `IntegrationsWorld`, independent of the dev seeder's "Acme" tenant; running a real sync against the seeded dev tenant would fail every worker on `UNKNOWN_LEGAL_ENTITY` until that tenant is given matching reference data or the code map is extended. Not wired up this round (no frontend/dev-run task needed it yet).

### Open questions

Q1–Q3 in the plan section 14 (ownership granularity, permission bundling, systems/sync UI living inside `integrations` rather than a future `operations` module). New: should the dev seeder gain matching legal entities/org units (or the code map gain Acme's names) before anyone runs a sync manually against the seeded tenant?

### Risks

Carried over from the plan: Employment/Assignment-only ownership (not Person, not per-field); one employment per external worker; synchronous (non-background) sync; the duplicate/temporary-failure scenarios are toggles on one fixture set, not independent fixtures. All documented, none blocking the stated Phase 2 goal.

### Suggested next step

Steps 10–13: the Systems list, Workday Simulation detail with a "Run sync" action and history, sync-run detail, a lineage disclosure on the Employment/Assignment cards (read-only when owned), frontend component tests, one end-to-end sync-then-view-lineage browser flow, and the remaining documentation cross-links (ARCHITECTURE, MODULES, DOMAIN_MODEL, README) — matching exactly how Phase 1's frontend followed its backend.


## 2026-09-22 18:20 — CODEX

### Task

Phase 2 backend review + frontend completion.

### Backend review

Independently read the implementation, plan/ADRs, permissions, ownership, mapper, transaction boundaries, repositories, audit/lineage and tests. Verified Claude’s unchanged baseline first: 187 backend tests passed. Recorded P2-01 through P2-08 and the proposed constraint migration/summary endpoint in PHASE_2_CODEX_REVIEW.md before implementation. Preserved per-worker transactions, synchronous full pulls, stable external identity and the accepted module direction; no new dependency, table, module, broker or legal execution.

### Q1–Q3 recommendations

Read the exact questions from plan section 14. Recommend **ACCEPT** Q1 (Employment/Assignment ownership, not Person/per-field), **ACCEPT** Q2 (four independent permissions bundled into HR_ADMIN only), and **ACCEPT** Q3 (Systems/Sync UI inside integrations). These are reviewer recommendations consistent with Daniel’s preferences, not owner acceptance. ADR-0024/0025 remain Proposed; Phase 3 stays closed.

### Frontend work

Added Systems list, Workday Simulation detail/run action/history, direct-linkable run summary/items/failure detail, and lazy Employment/Assignment source disclosure under web/src/pages/integrations. Added permission-aware navigation/routes, typed PalCheck API client, ownership-aware edit controls, distinct EXTERNALLY_OWNED feedback and actual SYSTEM source attribution in person history. Added English catalog messages and small token-based style extensions. Development/test-only named fixture selection proves changed-source updates through the real API.

### UI decisions

Continue the existing native-control UI: quiet lists, typography, restrained text status, compact counts, native progressive disclosure and visible focus. Display simulation/last synchronization and actual run status, not fabricated health. Explain that failed outcomes include manager links on imported workers. Do not retry the mutation automatically after a lost response. Source metadata appears only on request with VIEW_LINEAGE; Person identity stays editable. Inspected desktop and 320px dark screenshots; corrected a malformed history arrow.

### Tests

- Baseline: 187 backend tests passed (72 unit/architecture + 115 PostgreSQL integration) before edits.
- Final backend: mvnw.cmd -B -ntp verify passed, including executable JAR repackage: **192 tests**, 72 unit/architecture + 120 integration, **0 failures/errors/skips**, real embedded PostgreSQL.
- Final frontend: typecheck, lint, production build passed; **114 tests in 9 files** passed. Ten new component tests cover systems, action/permissions, counts/failures, source disclosure, ownership and axe.
- Final full-stack Chrome suite: **20/20 passed**, including all 17 previous flows and three new real Phase 2 flows (import/ownership/keyboard lineage; changed-source/re-sync/history; viewer denial). No core integration endpoint was mocked in these browser flows.
- Additional visual verification: nine desktop/light and narrow/dark states passed axe with no detectable WCAG 2.2 AA violations and no horizontal page overflow. Existing keyboard/forced-colors/reduced-motion tests passed. Human screen-reader and other browsers remain unverified.
- Five new backend regressions cover preserved local identity/initial provenance, manager clear/relink counts, summary/pagination/scenario/read permissions, concurrent imports and a real PostgreSQL trigger-induced worker transaction failure. Seeder assertions verify the additional fictional references remain idempotent.
- Intermediate failures: a test-string encoding error and old seed-count assertion were corrected, then the full backend suite passed. First browser run was 19/20, with one existing test reporting an execution-context/navigation interruption during frontend edits; the final stable run passed all 20 without weakening assertions. Only docs/Javadoc changed after final application verification.
- Document file links and backend XML result totals checked. Docker/Compose and hosted CI were not run.

### Bugs found

Dev tenants could not import the mock workers because reference data was absent; provider maps/display-name logic escaped the adapter; sync overwrote locally editable identity; manager clearing/current-pull membership/counting were inconsistent; unexpected worker errors aborted processing; concurrent imports could race; run pages had no summary API; pagination and metadata tenant constraints needed strengthening; RUN_SYNC depended on unrelated local permissions; the update scenario was unavailable through the real UI.

### Bugs fixed

Added idempotent synthetic Northwind references; moved provider interpretation into WorkdayMapper and passed source display name through the port; preserved Person on re-sync and its initial provenance; corrected null/unresolved/current-pull manager handling and update accounting; reused the existing tenant advisory transaction lock; recorded safe per-worker IMPORT_FAILED and attempted failed-run finalization for fatal errors; added authorized summary, bounded pagination and V8 tenant-qualified constraints (V7 unchanged); required RUN_SYNC at the port; exposed a dev/test-only TITLE_CHANGE scenario. Improved readable assignment/manager audit labels and rejected unsupported entity/date reconciliation. No unrelated Phase 1 behavior was redesigned.

### Documentation

Completed PHASE_2_CODEX_REVIEW.md with all 22 requested sections; reconciled the plan/DoD/API contract, Proposed ADR-0024/0025 and ADR index. Updated README, ARCHITECTURE, MODULES, DOMAIN_MODEL, INTEGRATION_ARCHITECTURE, DATA_OWNERSHIP_AND_LINEAGE, ENTERPRISE_SYSTEMS, ROADMAP, SECURITY, UI_SYSTEM, LOCAL_DEVELOPMENT demo steps and CODEX_HANDOFF. Clarified exact count/idempotency/ownership/provenance contracts and distinguished current implementation from historical future designs. Earlier worklog entries remain untouched.

### Remaining limitations

One in-process synthetic provider; synchronous full pulls; no live provider or network coverage. Workforce/audit commit before independent integration metadata writes, so process/database failure can leave a RUNNING run or missing/stale evidence. Re-sync reconciles owned values/current metadata but does not repair old run history or missing initial Person lineage. Concurrent different snapshots can interleave; no source ordering or durable recovery. Entity ownership only, six lineage fields, no audit sync-run foreign key, no ownership cutover/automatic merge/full temporal reconciliation. Existing deployment security and hosted-verification work remains. All pre-existing uncommitted Phase 1/Q1–Q3/UI-polish/Claude Phase 2 work was preserved; no commit or push performed.

### Suggested next step

Daniel reviews Q1–Q3 and ADR-0024/0025 and walks through baseline → changed title → source/history using LOCAL_DEVELOPMENT.md. Recommend Phase 2 acceptance within the documented limits, then a reviewed repository checkpoint. Public hosting first requires the already documented security milestone. Do not infer Phase 3 authorization.

## 2026-09-22 18:29 — CODEX

### Task
Commit the completed Phase 2 milestone at Daniel's explicit request.

### Work completed
Reviewed pending files and prepared one milestone commit containing the completed Phase 2 backend/frontend/tests/documentation together with the preceding uncommitted Q1–Q3 resolutions and UI polish on which the verified application depends. Development runtime files and build outputs remain ignored.

### Decisions made
Commit title: Complete Phase 2 HRIS translation foundation. This repository checkpoint does not change the Proposed status of ADR-0024/0025 or open Phase 3. No push requested in this turn.

### Tests
No application behavior changed during commit preparation. Carried forward the completed verification: 192 backend tests, 114 frontend tests, 20 real-stack browser tests; typecheck, lint and production build passed. Check the staged diff for whitespace before committing.

### Assumptions
The requested Phase 2 commit captures the complete tested working state, including its prior review fixes.

### Open questions
Owner acceptance of Phase 2 Q1–Q3 and ADR-0024/0025 remains separate from committing the implementation.

### Risks
Known simulation and crash-recovery limits remain documented in PHASE_2_CODEX_REVIEW.md.

### Suggested next step
Review the milestone commit and complete the Phase 2 acceptance/demo walkthrough.

## 2026-09-22 21:24 — CLAUDE

### Task

Phase 3 opening: read the current implementation (Phase 2 now committed as `9fa3eee "Complete Phase 2 HRIS translation foundation"`, with two Codex worklog entries after mine finishing the frontend, review and commit), reconcile against Daniel's Phase 3 message, and produce docs/PHASE_3_IMPLEMENTATION_PLAN.md plus its ADRs before writing code.

### Work completed

- Confirmed the working tree is clean (`git status` empty) and reviewed the two Codex worklog entries (18:20, 18:29) covering the completed Phase 2 frontend (Systems list, Workday Simulation detail, run history/detail, source disclosure), a backend review (bug fixes: idempotent synthetic reference data, provider interpretation moved fully into the mapper, Person preserved on re-sync, manager-link accounting corrected, per-worker failures caught instead of aborting the run, a V8 migration adding tenant-qualified constraints, a summary/pagination endpoint, RUN_SYNC required at the port), and the milestone commit. Final counts recorded there: 192 backend tests, 114 frontend tests, 20 browser tests, all passing.
- Read AGENTS.md, ARCHITECTURE.md (current messaging/module notices), MODULES.md 2a, DOMAIN_MODEL.md, INTEGRATION_ARCHITECTURE.md (sections 1–9, especially 5–7: outbox, dispatcher, delivery-attempt state machine, retry/replay/reprocess), DATA_OWNERSHIP_AND_LINEAGE.md, ENTERPRISE_SYSTEMS.md, SECURITY.md, ROADMAP.md, PHASE_2_IMPLEMENTATION_PLAN.md, ADR-0006/0007/0008/0018/0019/0022/0024/0025, and the current backend structure (migrations through V8, module list, `access.api.Permission`).
- Wrote [docs/PHASE_3_IMPLEMENTATION_PLAN.md](../PHASE_3_IMPLEMENTATION_PLAN.md) (20-item definition of done, scope, reconciliation against the Phase 0 outbound design, architecture, persistence, API, authorization, test strategy, step table, risks/open questions Q1–Q3), [ADR-0026](adr/0026-broker-and-transactional-outbox.md) (broker choice and transactional outbox), [ADR-0027](adr/0027-business-operation-identity-and-idempotent-consumers.md) (operation identity, idempotent consumers, retry, dead-letter, replay), [ADR-0028](adr/0028-domain-vs-integration-events.md) (domain vs. integration events and the publication boundary).
- Treated Daniel's Phase 3 opening message ("Phase 1 and Phase 2 are complete and accepted") as his acceptance of both, exactly as his Phase 2 opening message was treated as accepting Phase 1: updated ADR-0024 and ADR-0025 to **Accepted** in the ADR index (Codex's independent review had already recommended ACCEPT for their Q1–Q3).
- Updated AGENTS.md section 2 (phase gate now Phase 3; Phase 1/2 non-goals consolidated; Phase 3's narrow exception to the broker/outbox ban stated explicitly) and ROADMAP.md (Phase 2 marked accepted, new Phase 3 section, reconciliation notice).

### Decisions made

Recorded in the ADRs and the plan in full; summarized here:

- **Broker: Apache Kafka, not Redpanda**, decided for this specific environment (no container runtime, no WSL2) rather than defaulting to the prompt's suggestion — Kafka's KRaft mode runs as a bare JVM process with native Windows scripts, mirroring the Keycloak precedent; Redpanda's Windows story requires Docker or WSL2, neither available here.
- **New module `messaging`** (technical, depends only on `shared`/`platform`), owning the transactional outbox and the Kafka producer. `workforce` gains its first dependency on `messaging.api` (one write port, `OutboxWriter.append`, `Propagation.MANDATORY`, the same shape as `audit.api.AuditRecorder`). `integrations` gains its own Kafka consumer client for consumption only — never publication infrastructure, per ADR-0018 decision 3.
- **One event published**: `EmploymentStarted` (domain event) -> `EmploymentStartedIntegrationEvent` v1 (the integration contract), payload is identifiers only (personId, employmentId, assignmentId, legalEntityId, orgUnitId) — no snapshot, per Daniel's explicit instruction.
- **Business operation identity from the aggregate, never the event**: `IDENTITY_PROVISION:{employmentId}`, `EQUIPMENT_PROVISION:{assignmentId}`, one row per operation (`integrations.integration_operation`), matching ADR-0019 decision 3 made concrete for the first time.
- **Retry at the operation level, not by Kafka redelivery**: a scheduled job per consumer re-attempts `FAILED_TRANSIENT` rows on a shared, configurable backoff (`5s, 30s, 2m, 10m`). Exhaustion -> `DEAD_LETTERED`; a genuinely permanent failure -> `FAILED_PERMANENT` directly, never auto-retried.
- **Dead-letter is a database status column, not a physical Kafka DLQ topic** — simpler to query and build a UI over at Phase 3's scale (two consumers, one event type).
- **Replay re-attempts from the stored operation payload**, not by going back to the outbox/broker; refused on an already-`SUCCEEDED` operation; audited through the existing `audit.api.AuditRecorder`.
- **Tests use a real embedded Kafka broker** (`spring-kafka-test`), the same "real dependency, no external process" precedent `zonky embedded-postgres` set for PostgreSQL.
- Scope for this session: plan + 3 ADRs + a tested backend vertical slice (steps 3–12 of the plan). Event Explorer/operation UI, and E2E, are the explicitly stated next step, matching how Phase 1 and Phase 2 both split backend from frontend.

### Tests

None yet for Phase 3 — this entry covers planning only. Implementation and its tests follow in the next worklog entry, in the same session.

### UI changes

None in this entry.

### Assumptions

- Daniel's Phase 3 message is treated as authorization for the bounded design decisions in ADR-0026/0027/0028, the same provisional-then-confirm pattern Phase 1 (Q1–Q3) and Phase 2 (Q1–Q3) both used.
- Kafka runs locally as a bare JVM process (KRaft mode) for manual/dev verification, and as `spring-kafka-test`'s embedded broker for automated tests; Docker Compose gains a Kafka service but is not executable on this machine, matching the existing unverified-Compose status.

### Open questions

Q1–Q3 in the plan section 14 (database-backed dead-letter vs. a physical DLQ topic; Kafka over Redpanda for this environment; one shared retry policy for both consumers). Phase 1/2's own remaining open items are unchanged and untouched.

### Risks

Carried over from the plan section 14: the broker is the first local process this project cannot fully verify end-to-end without either the native distribution or Docker; database-backed dead-lettering trades Kafka-native DLQ tooling for simplicity; one shared retry policy may not fit a future, differently-shaped consumer.

### Suggested next step

Implement plan steps 3–12 (event contract, outbox, publisher, Kafka, the published event, both consumers, idempotency, retry/dead-letter, replay) with backend integration tests on real PostgreSQL and a real embedded Kafka broker, then stop and report — matching this same session's continuation, not a separate one.

## 2026-09-23 18:29 — CLAUDE

### Task

Implement Phase 3 plan steps 3–12 (docs/PHASE_3_IMPLEMENTATION_PLAN.md): event boundaries, transactional outbox, publisher, a real Kafka broker, the `EmploymentStarted` integration event wired into `RegisterWorkerService`, both downstream consumers (Identity Provisioning, Equipment Provisioning) with idempotency/retry/dead-letter/replay, the Event Explorer's backend API, and backend tests — continuing directly from the previous entry in the same phase.

### Work completed

- `com.palcheck.integrations.application.OperationConsumer` — the abstract idempotent-processing skeleton every consumer shares: `handle(event)` (acts only on a `PENDING` row — a duplicate delivery while a retry or dead-letter is already scheduled is a pure no-op, not a fresh attempt), `retryDue(limit)`, `replay(tenantId, operationKey)` (refuses an already-`SUCCEEDED` operation), and the shared outcome-to-status transition logic (`SUCCEEDED`/`FAILED_PERMANENT`/`FAILED_TRANSIENT`-with-backoff/`DEAD_LETTERED`-on-exhaustion).
- `com.palcheck.integrations.infrastructure.IdentityProvisioningConsumer` and `EquipmentProvisioningConsumer` — concrete `@KafkaListener`-based consumers extending it, each its own Kafka consumer group, deserializing `IntegrationEvent` JSON and delegating to `handle`.
- `com.palcheck.integrations.infrastructure.IntegrationRetryJob` — the scheduled retry tick, deliberately a separate `@ConditionalOnProperty`-gated class from the two consumers (mirrors `OutboxPublisherJob`), so it can be disabled in tests without disabling the `@KafkaListener` beans themselves.
- Extracted `com.palcheck.messaging.application.OutboxPublishingService` (public, independently testable) out of `OutboxPublisherJob`'s nested `Publisher` class, leaving `OutboxPublisherJob` as a thin scheduled tick over it — a design correction made mid-session once the asymmetry with `IdempotencyPurgeJob`/`IdempotencyService` (a thin job over a real, testable service) became obvious while writing tests against the old shape.
- Three new `access.api.Permission` values (`VIEW_EVENTS`, `VIEW_INTEGRATION_OPERATIONS`, `REPLAY_INTEGRATION_OPERATION`), bundled into `HR_ADMIN` only via its existing `EnumSet.allOf(Permission.class)`.
- `com.palcheck.messaging.api.OutboxEventView`/`OutboxEventReader` — a new read port (mirrors `audit.api.AuditHistory`'s "does not authorize itself" shape, since `messaging` still depends on no domain module) so `integrations.application` can read outbox events for the Event Explorer without `messaging` ever knowing `access` exists.
- `com.palcheck.integrations.application.IntegrationOperationsService` and `EventsService`, and `com.palcheck.integrations.web.IntegrationOperationsController` — the four endpoints from the plan's API surface: `GET /api/integration-operations` (paged, filterable by consumer/status), `GET /api/integration-operations/{consumer}/{operationKey}`, `POST .../replay`, `GET /api/events/{eventId}`.
- `com.palcheck.shared.AlreadySucceededException` (new, `ErrorCode.CONFLICT`, so it becomes an HTTP 409 through the existing generic `ApplicationException` handler with no dedicated handler method needed) — thrown by `OperationConsumer.replay` when the target operation already succeeded.
- `IntegrationOperationRepository`/`JdbcIntegrationOperationRepository` gained `list(tenantId, consumer, status, page, size)` for the operations list endpoint; `JdbcOutboxRepository` gained `find` (implementing `OutboxEventReader`) for the event-detail endpoint.
- `ArchitectureTest`: added a small, explicit, documented exemption in `TAKE_A_TENANT` for `claimDueRetries` (a scheduled poller's claim query legitimately scans every tenant's due work in one statement, exactly like `JdbcOutboxRepository.claimEligible`, which sits outside this rule entirely because `messaging.infrastructure` isn't a tenant-scoped domain module) — all 28 rules pass.
- `application.yml`/`application-test.yml`: added `spring.kafka.*` configuration (bootstrap-servers, String (de)serializers, `auto-offset-reset: earliest`), `palcheck.messaging.topic`/`retry-delays` defaults, and disabled `spring.kafka.listener.auto-startup`, `palcheck.messaging.publisher-enabled`, `palcheck.integrations.retry-enabled` in the test profile so ordinary tests never dial a broker that isn't running (mirrors the existing `palcheck.idempotency.purge-enabled` precedent).
- Three new backend test classes (14 tests total, all real PostgreSQL, one real embedded Kafka broker):
  - `com.palcheck.messaging.OutboxPublishingIT` (4): atomic outbox commit with the business write; a forced outbox-insert-failure trigger rolls back the whole hire command; a fake `BrokerPublisher` (`@TestConfiguration`/`@Primary`) proves publish-success and retry-then-`FAILED`-after-exhaustion, without needing Kafka for those two scenarios.
  - `com.palcheck.integrations.IntegrationOperationsIT` (9): duplicate delivery of the same event is a no-op; the same operation key from two different event ids executes once; a forced transient failure retries to success; a forced permanent failure dead-letters without auto-retry; an authorized replay after a fix succeeds and is audited; replaying an already-succeeded operation is rejected (409); tenant isolation on the new endpoints; the three-permission matrix; correlation/causation propagate from the originating HTTP request through the outbox row to the `/api/events/{id}` response.
  - `com.palcheck.integrations.EventPipelineIT` (1): the one true real-broker proof — `@EmbeddedKafka`, hiring someone end to end through the real outbox → publisher → broker → both independent `@KafkaListener` consumers, both reaching `SUCCEEDED`.
- Updated `docs/PHASE_3_IMPLEMENTATION_PLAN.md` (DoD table and step table filled with real test-name evidence; corrected the Kafka dependency artifact names throughout), `docs/adr/0026-broker-and-transactional-outbox.md` (decision 7 corrected from plain `FOR UPDATE SKIP LOCKED` to the actual claim-and-lease mechanism; the dependency artifact name in Consequences), `docker-compose.yml` (added a single-node KRaft Kafka service), `docs/LOCAL_DEVELOPMENT.md` (Kafka row in the ports table, native-process instructions mirroring Keycloak's, both explicitly marked unverified on this machine), `docs/ROADMAP.md` (Phase 3 heading now states the backend slice is implemented and tested).

### Decisions made

- **`OutboxPublishingService` extracted as its own public, testable class**, not left as a package-private nested class inside the scheduled job — the same shape `IdempotencyPurgeJob`/`IdempotencyService` already established; the earlier shape (all logic inside a package-private nested `Publisher`) would have forced tests into an awkward same-package placement or reflection, for no benefit.
- **`IntegrationRetryJob` is a separate class from the two `@KafkaListener` consumers**, not a `@Scheduled` method on each — the retry tick needs to be disabled in tests independently of the listener containers, and a shared, single-property-gated job avoids duplicating that gating twice.
- **Replay is audited unconditionally on any accepted attempt**, regardless of whether the new attempt itself succeeds or fails again — matches Daniel's brief's own phrasing ("Integration operation replay requested" describes the request, not its outcome), and keeps the audit trail useful even when a replay doesn't immediately fix things.
- **`AlreadySucceededException` reuses `ErrorCode.CONFLICT`** rather than a new error code — it genuinely is a conflict (replaying a finished operation conflicts with its current state), distinct in class (not in code) from optimistic-concurrency `ConflictException`, so no dedicated exception handler method was needed.
- **Consumer idempotency check is keyed on `PENDING` status specifically**, not "not `SUCCEEDED`": a fresh event delivery finding a row already `FAILED_TRANSIENT`/`FAILED_PERMANENT`/`DEAD_LETTERED` is acknowledged and left alone (recovery is the scheduled retry's or a human replay's job), never re-attempted inline by the listener itself — otherwise a redelivered event would defeat the backoff schedule.

### Tests

211 backend tests, all passing (`./mvnw verify`; real PostgreSQL for every integration test; one real, in-process, embedded Kafka broker for `EventPipelineIT`): 77 unit (`mvn test`) + 134 integration across 19 `*IT` classes (`mvn verify`), including the 14 new ones listed above. `ArchitectureTest`: 28/28 rules pass. Two pre-existing tests needed a one-line update for the new permission count (`AuthenticationIT.mappedAdministratorSeesTenantAndPermissions`, `AuthorizationMatrixIT.rolesAreOnlyBundlesOfPermissions`: 11 → 14), the same maintenance every earlier phase's own new permissions required.

### UI changes

None. Backend only this session; the Event Explorer and operation-detail/replay UI are plan steps 13–14, explicitly deferred (section 16).

### Failures discovered

1. **Missing `spring-boot-starter-kafka` dependency.** `pom.xml` declared the raw `org.springframework.kafka:spring-kafka`/`spring-kafka-test` client libraries (written in the previous session). These compile cleanly — the bug is invisible to `mvn compile`, `mvn test-compile`, and `ArchitectureTest` alike — but Spring Boot 4 moved Kafka's autoconfiguration (`KafkaTemplate`, the listener container factory, etc.) into its own module, `spring-boot-kafka`, which only the starter artifact pulls in. Every `@SpringBootTest`-based integration test in the **entire suite**, not just the new Phase 3 ones, failed at context startup the moment any of them actually ran (`KafkaBrokerPublisher`'s constructor needs a `KafkaTemplate<String, String>` bean that autoconfiguration never created) — first caught by `PeopleCreateAndReadIT`, an unrelated, pre-existing Phase 1 test, when a routine full-suite run was attempted.
2. **A Java text-block trailing-whitespace bug in three `RETURNING` clauses.** `JdbcOutboxRepository.claimEligible` and `JdbcIntegrationOperationRepository.findOrCreate`/`claimDueRetries` (written in the previous session) each ended a text block with `RETURNING """ + COLUMNS`. Java text blocks strip incidental *trailing* whitespace per line, so the space before the closing `"""` was silently removed, producing `RETURNINGid, tenant_id, ...` — a PostgreSQL syntax error. Invisible to `javac` (valid Java) and to `ArchUnit` (no SQL awareness); caught only once each query actually ran against real PostgreSQL, by `OutboxPublishingIT.publishingASucceedingRowMarksItPublished` and `.aBrokerThatKeepsFailingIsNotRetriedForeverAfterItsAttemptsAreExhausted`.
3. **Two pre-existing tests hardcoded the total `Permission` enum size** (11, the Phase 2 count) and failed once Phase 3's three new permissions brought it to 14 — expected test maintenance, not a design defect, the same kind of update Phase 2's own new permissions required of Phase 1's tests.
4. **Self-inflicted process error, not a product bug:** running a foreground `mvn test-compile` against `target/` while an earlier `mvn verify` was still running in the background against the same directory corrupted that run ("There was an error in the forked process"). Recorded here only as a note to future sessions: never run a second Maven invocation against the same `target/` directory while one is already in flight; wait for the background task's own completion notification first.

### Bugs fixed

1. `pom.xml`: replaced `org.springframework.kafka:spring-kafka`/`spring-kafka-test` with `org.springframework.boot:spring-boot-starter-kafka`/`spring-boot-starter-kafka-test`. Corrected the artifact names in `docs/PHASE_3_IMPLEMENTATION_PLAN.md` sections 5 and 12, and in `docs/adr/0026-broker-and-transactional-outbox.md`'s Consequences.
2. All three `RETURNING """ + COLUMNS` (and `+ qualified()`) occurrences fixed by putting `RETURNING` alone on its own line, so the line terminator that follows it (never stripped, since it isn't trailing whitespace) supplies the separator before the concatenated column list.
3. `AuthenticationIT.mappedAdministratorSeesTenantAndPermissions` and `AuthorizationMatrixIT.rolesAreOnlyBundlesOfPermissions`: updated the hardcoded permission count from 11 to 14, with a comment explaining the three new Phase 3 permissions.
4. (Process) Re-ran the affected Maven commands cleanly, one at a time, after the concurrent-build corruption; no source change was needed.

### Assumptions

Unchanged from the previous entry: Daniel's Phase 3 opening message authorizes the bounded design decisions in ADR-0026/0027/0028; Kafka runs locally as a bare JVM process (KRaft mode) for manual/dev use and as `spring-kafka-test`'s embedded broker for automated tests. Refined this entry: only the **embedded test broker** path has actually run and passed (`EventPipelineIT`); a manually-started native Kafka process and the Docker Compose `kafka` service are both still unverified on this machine, exactly like Compose's Postgres/Keycloak services already were.

### Open questions

Q1–Q3 from the plan (section 14) remain open for Daniel's explicit confirmation, now backed by a tested implementation rather than only a documented decision: the database-backed dead-letter state over a physical Kafka DLQ topic; Kafka over Redpanda for this environment; one shared retry policy for both consumers.

### Risks

Carried over from the plan section 14, R1 sharpened: the broker is still the one dependency this project cannot fully verify end-to-end on this machine without either the native distribution or Docker — that now applies to *both* the Compose service and a manually-run native process, since only the embedded test broker has actually executed here.

### Suggested next step

Plan steps 13–15: the Event Explorer UI, the operation-detail/replay UI, and the three E2E flows from Daniel's brief (success; transient failure and retry; permanent failure and authorized replay), following the same backend-then-frontend order Phase 1 and Phase 2 both used — then step 16's final documentation pass and a request for Daniel's review of Phase 3, mirroring how Phase 1 and Phase 2 were each opened for review before acceptance.
